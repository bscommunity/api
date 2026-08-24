package org.bscm.repository

import org.bscm.models.Contributor
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class ContributorRepository : IContributorRepository {
    companion object {
        /**
         * Batch-fetches contributors (with their users) for multiple catalog items.
         * Returns a map of catalogId -> contributors. Must be called within a transaction.
         */
        fun fetchContributorsByCatalogIds(catalogIds: List<String>): Map<String, List<Contributor>> {
            if (catalogIds.isEmpty()) return emptyMap()

            val entityIds = catalogIds.map { EntityID(it, CatalogItemTable) }
            val rows = ContributorTable
                .innerJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
                .select(ContributorTable.columns + UserTable.columns)
                .where { ContributorTable.catalogItemId inList entityIds }
                .toList()

            return rows.groupBy { it[ContributorTable.catalogItemId].value }
                .mapValues { (_, contributorRows) ->
                    contributorRows.map { row ->
                        contributorEntityToContributor(
                            row[ContributorTable.catalogItemId].value,
                            ContributorEntity.wrapRow(row),
                            UserEntity.wrapRow(row),
                        )
                    }
                }
        }

        fun contributorEntityToContributor(entity: ContributorEntity): Contributor {
            return Contributor(
                user = SimplifiedUser(
                    id = entity.user.id.value,
                    username = entity.user.username,
                    avatarUrl = entity.user.avatarUrl,
                    bannerUrl = entity.user.bannerUrl,
                    isVerified = entity.user.isVerified,
                    bio = entity.user.bio,
                    accentColor = entity.user.accentColor,
                ),
                catalogItemId = entity.catalogItem.id.value,
                role = entity.role,
                note = entity.note,
                joinedAt = entity.joinedAt,
            )
        }

        // catalogItemId passed explicitly — avoids a lazy catalogItem reference lookup per contributor
        fun contributorEntityToContributor(
            catalogItemId: String,
            entity: ContributorEntity,
            user: UserEntity,
        ): Contributor {
            return Contributor(
                user = SimplifiedUser(
                    id = user.id.value,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    bannerUrl = user.bannerUrl,
                    isVerified = user.isVerified,
                    bio = user.bio,
                    accentColor = user.accentColor,
                ),
                catalogItemId = catalogItemId,
                role = entity.role,
                note = entity.note,
                joinedAt = entity.joinedAt,
            )
        }

        fun contributorEntityToContributor(entity: ContributorEntity, user: UserEntity): Contributor {
            return contributorEntityToContributor(entity.catalogItem.id.value, entity, user)
        }
    }

    override suspend fun addContributors(catalogItemId: String, contributors: List<SimplifiedContributor>): List<Contributor> = suspendTransaction {
        CatalogItemEntity.findById(catalogItemId) ?: throw IllegalArgumentException("CatalogItem not found")
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

        val contributorEntities = contributors.map { contributor ->
            UserEntity.findById(contributor.userId) ?: throw IllegalArgumentException("User not found")

            val existing = ContributorEntity.find {
                (ContributorTable.catalogItemId eq catalogItemEntityId) and
                    (ContributorTable.userId eq contributor.userId) and
                    (ContributorTable.role eq contributor.role)
            }.singleOrNull()

            if (existing != null) {
                contributorEntityToContributor(existing)
            } else {
                val newContributor = ContributorEntity.new {
                    this.catalogItem = CatalogItemEntity[catalogItemId]
                    this.user = UserEntity[contributor.userId]
                    this.role = contributor.role
                }
                contributorEntityToContributor(newContributor)
            }
        }

        contributorEntities
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
    ): List<Contributor> = suspendTransaction {
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)

        // Remove all existing roles for this user on this catalog item
        ContributorEntity.find {
            (ContributorTable.catalogItemId eq catalogItemEntityId) and
                (ContributorTable.userId eq userId)
        }.forEach { it.delete() }

        // Add the new roles
        val userEntity = UserEntity.findById(userId) ?: throw IllegalArgumentException("User not found")

        roles.map { role ->
            val newEntity = ContributorEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.user = userEntity
                this.role = role
            }
            contributorEntityToContributor(newEntity, userEntity)
        }
    }

    override suspend fun getContributors(catalogItemId: String): List<Contributor> = suspendTransaction {
        val catalogItemEntityId = EntityID(catalogItemId, CatalogItemTable)
        ContributorEntity.find { ContributorTable.catalogItemId eq catalogItemEntityId }
            .map { contributorEntityToContributor(it) }
    }
}
