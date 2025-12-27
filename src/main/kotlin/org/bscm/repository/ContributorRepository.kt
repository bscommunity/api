package org.bscm.repository

import org.bscm.models.Contributor
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.ContributorEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.models.repository.IContributorRepository
import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ContributorRepository : IContributorRepository {
    companion object {
        fun contributorEntityToContributor(entity: ContributorEntity): Contributor {
            val compositeId = entity.id.value // This is a CompositeID
            val chartId = compositeId[ContributorTable.chartId].value

            return Contributor(
                user = SimplifiedUser(
                    id = entity.user.id.value,
                    username = entity.user.username,
                    imageUrl = entity.user.imageUrl,
                    createdAt = entity.user.createdAt,
                    isVerified = entity.user.isVerified,
                    isPublic = entity.user.isPublic,
                    followingCount = entity.user.followingCount,
                    followerCount = entity.user.followerCount,
                ),
                chartId = chartId.toString(),
                roles = entity.roles,
                note = entity.note,
                joinedAt = entity.joinedAt,
            )
        }

        fun contributorEntityToContributor(entity: ContributorEntity, user: UserEntity): Contributor {
            val compositeId = entity.id.value // This is a CompositeID
            val chartId = compositeId[ContributorTable.chartId].value

            return Contributor(
                user = SimplifiedUser(
                    id = user.id.value,
                    username = user.username,
                    imageUrl = user.imageUrl,
                    createdAt = user.createdAt,
                    isVerified = entity.user.isVerified,
                    isPublic = entity.user.isPublic,
                    followingCount = entity.user.followingCount,
                    followerCount = entity.user.followerCount,
                ),
                chartId = chartId.toString(),
                roles = entity.roles,
                joinedAt = entity.joinedAt,
            )
        }
    }

    override suspend fun addContributors(chartId: ULong, contributors: List<SimplifiedContributor>): List<Contributor> = newSuspendedTransaction {
            // Check if the user and chart exist
            ChartEntity.findById(chartId) ?: throw IllegalArgumentException("Chart not found")

            val contributorEntities = contributors.map { contributor ->
                UserEntity.findById(contributor.userId) ?: throw IllegalArgumentException("User not found")

                val contributorId = CompositeID {
                    it[ContributorTable.chartId] = chartId
                    it[ContributorTable.userId] = contributor.userId
                }

                val newContributor = ContributorEntity.new(contributorId) {
                    roles = contributor.roles
                }

                contributorEntityToContributor(newContributor)
            }

            contributorEntities
        }

    override suspend fun removeContributor(chartId: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val contributorId = CompositeID {
            it[ContributorTable.chartId] = chartId
            it[ContributorTable.userId] = userId
        }

        val contributor = ContributorEntity.findById(contributorId)

        contributor?.delete() ?: throw IllegalArgumentException("Contributor not found")

        true
    }

    override suspend fun updateContributorRoles(
        chartId: ULong,
        userId: UUID,
        roles: List<ContributorRole>
    ): Contributor = newSuspendedTransaction {
        val contributorId = CompositeID {
            it[ContributorTable.chartId] = chartId
            it[ContributorTable.userId] = userId
        }

        val contributor = ContributorEntity.findByIdAndUpdate(contributorId) {
            it.roles = roles
        } ?: throw IllegalArgumentException("Contributor not found")

        contributorEntityToContributor(contributor)
    }

    override suspend fun getContributors(chartId: ULong): List<UUID> = newSuspendedTransaction {
        ContributorEntity.find { ContributorTable.chartId eq chartId }
            .map { it.id.value[ContributorTable.userId].value }
    }
}