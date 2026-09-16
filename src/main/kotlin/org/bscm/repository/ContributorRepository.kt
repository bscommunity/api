package org.bscm.repository

import org.bscm.models.ContributorWithRoles
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.ContributorEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.models.interfaces.IContributorRepository
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class ContributorRepository : IContributorRepository {
    companion object {
        /**
         * Batch-fetches contributors (with their users) for multiple catalog items,
         * grouped to one entry per user with all their roles.
         * Returns a map of catalogId -> contributors. Must be called within a transaction.
         */
        fun fetchContributorsByCatalogIds(catalogIds: List<String>): Map<String, List<ContributorWithRoles>> {
            if (catalogIds.isEmpty()) return emptyMap()

            val entityIds = catalogIds.map { EntityID(it, CatalogItemTable) }
            val rows = ContributorTable
                .innerJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
                .select(ContributorTable.columns + UserTable.columns)
                .where { ContributorTable.catalogItemId inList entityIds }
                .toList()

            return rows.groupBy { it[ContributorTable.catalogItemId].value }
                .mapValues { (_, contributorRows) ->
                    groupRows(contributorRows)
                }
        }

        private fun toSimplifiedUser(user: UserEntity): SimplifiedUser {
            return SimplifiedUser(
                id = user.id.value,
                username = user.username,
                avatarUrl = user.avatarUrl,
                bannerUrl = user.bannerUrl,
                isVerified = user.isVerified,
                bio = user.bio,
                accentColor = user.accentColor,
            )
        }

        /**
         * Groups raw joined rows (same catalog item) into one entry per user.
         * Roles are sorted by enum id for a stable order; joinedAt is the earliest
         * row; note is the first non-null note, if any.
         */
        private fun groupRows(rows: List<ResultRow>): List<ContributorWithRoles> {
            return rows.groupBy { it[ContributorTable.userId].value }.map { (_, userRows) ->
                val first = userRows.first()
                val catalogItemId = first[ContributorTable.catalogItemId].value
                val user = toSimplifiedUser(UserEntity.wrapRow(first))
                val roles = userRows.map { ContributorEntity.wrapRow(it).role }.distinct()
                    .sortedBy { it.id }
                ContributorWithRoles(
                    user = user,
                    catalogItemId = catalogItemId,
                    roles = roles,
                    note = userRows.mapNotNull { it.getOrNull(ContributorTable.note) }.firstOrNull(),
                    joinedAt = userRows.minOf { it[ContributorTable.joinedAt] },
                )
            }.sortedWith(compareBy({ it.joinedAt }, { it.user.username }))
        }

        private fun groupEntities(catalogItemId: String, entities: List<ContributorEntity>): List<ContributorWithRoles> {
            return entities.groupBy { it.user.id.value }.map { (_, userEntities) ->
                val first = userEntities.first()
                val roles = userEntities.map { it.role }.distinct().sortedBy { it.id }
                ContributorWithRoles(
                    user = toSimplifiedUser(first.user),
                    catalogItemId = catalogItemId,
                    roles = roles,
                    note = userEntities.mapNotNull { it.note }.firstOrNull(),
                    joinedAt = userEntities.minOf { it.joinedAt },
                )
            }.sortedWith(compareBy({ it.joinedAt }, { it.user.username }))
        }

        /**
         * Persists the author row plus any extra [contributors] for a newly created
         * catalog item. Must be called within an existing transaction — repositories
         * own their `suspendTransaction {}` blocks and call this inside them.
         *
         * Mirrors the chart creation contract: the author is always added as AUTHOR,
         * duplicate (user, AUTHOR) entries for the author are skipped to respect the
         * (catalogItemId, userId, role) unique index, unknown users fail fast, and
         * existing (user, role) rows are left untouched (idempotent).
         */
        fun persistCreationContributors(
            catalogItemId: String,
            authorId: UUID,
            contributors: List<SimplifiedContributor>,
        ) {
            ContributorEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.user = UserEntity[authorId]
                this.role = ContributorRole.AUTHOR
            }

            val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

            // Single batched lookup instead of one findById per contributor (N+1):
            // validates existence up front and warms the EntityCache, so the
            // UserEntity references below resolve without extra queries.
            // Flatten grouped input to one (userId, role) pair per role.
            val extras = contributors
                .flatMap { contributor -> contributor.roles.distinct().map { contributor.userId to it } }
                .filterNot { (userId, role) -> role == ContributorRole.AUTHOR && userId == authorId }
                .distinct()
            val userEntities = if (extras.isEmpty()) {
                emptyMap()
            } else {
                UserEntity.find { UserTable.id inList extras.map { it.first }.distinct() }
                    .associateBy { it.id.value }
            }
            extras.forEach { (userId, _) ->
                if (userId !in userEntities) {
                    throw IllegalArgumentException("User not found: $userId")
                }
            }

            extras.forEach { (userId, role) ->
                val existing = ContributorEntity.find {
                    (ContributorTable.catalogItemId eq catalogItemEntityId) and
                        (ContributorTable.userId eq userId) and
                        (ContributorTable.role eq role)
                }.singleOrNull()

                if (existing == null) {
                    ContributorEntity.new {
                        this.catalogItem = CatalogItemEntity[catalogItemId]
                        this.user = userEntities.getValue(userId)
                        this.role = role
                    }
                }
            }
        }
    }

    override suspend fun addContributors(catalogItemId: String, contributors: List<SimplifiedContributor>): List<ContributorWithRoles> = suspendTransaction {
        CatalogItemEntity.findById(catalogItemId) ?: throw IllegalArgumentException("CatalogItem not found")
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

        val affectedUserIds = contributors.map { it.userId }.distinct()

        contributors.flatMap { contributor ->
            contributor.roles.distinct().map { contributor.userId to it }
        }.distinct().forEach { (userId, role) ->
            UserEntity.findById(userId) ?: throw IllegalArgumentException("User not found")

            val existing = ContributorEntity.find {
                (ContributorTable.catalogItemId eq catalogItemEntityId) and
                    (ContributorTable.userId eq userId) and
                    (ContributorTable.role eq role)
            }.singleOrNull()

            if (existing == null) {
                ContributorEntity.new {
                    this.catalogItem = CatalogItemEntity[catalogItemId]
                    this.user = UserEntity[userId]
                    this.role = role
                }
            }
        }

        val entities = ContributorEntity.find {
            (ContributorTable.catalogItemId eq catalogItemEntityId) and
                (ContributorTable.userId inList affectedUserIds)
        }.toList()

        groupEntities(catalogItemId, entities)
    }

    override suspend fun removeContributor(catalogItemId: String, userId: UUID, role: ContributorRole?): Boolean = suspendTransaction {
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

        if (role != null) {
            val entity = ContributorEntity.find {
                (ContributorTable.catalogItemId eq catalogItemEntityId) and
                    (ContributorTable.userId eq userId) and
                    (ContributorTable.role eq role)
            }.singleOrNull() ?: throw IllegalArgumentException("Contributor not found")

            entity.delete()
        } else {
            val entities = ContributorEntity.find {
                (ContributorTable.catalogItemId eq catalogItemEntityId) and
                    (ContributorTable.userId eq userId)
            }

            if (entities.empty()) throw IllegalArgumentException("Contributor not found")
            entities.forEach { it.delete() }
        }

        true
    }

    override suspend fun updateContributorRoles(
        catalogItemId: String,
        userId: UUID,
        roles: List<ContributorRole>
    ): List<ContributorWithRoles> = suspendTransaction {
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

        // Remove all existing roles for this user on this catalog item
        ContributorEntity.find {
            (ContributorTable.catalogItemId eq catalogItemEntityId) and
                (ContributorTable.userId eq userId)
        }.forEach { it.delete() }

        // Add the new roles
        val userEntity = UserEntity.findById(userId) ?: throw IllegalArgumentException("User not found")

        val newEntities = roles.distinct().map { role ->
            ContributorEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.user = userEntity
                this.role = role
            }
        }
        groupEntities(catalogItemId, newEntities)
    }

    override suspend fun getContributors(catalogItemId: String): List<ContributorWithRoles> = suspendTransaction {
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)
        val entities = ContributorEntity.find { ContributorTable.catalogItemId eq catalogItemEntityId }.toList()
        groupEntities(catalogItemId, entities)
    }
}
