package org.bscm.repository

import org.bscm.models.ActivityEntry
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.tables.UserActivityTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class ActivityRepository : IActivityRepository {
	override suspend fun logActivity(
		userId: UUID,
		type: ActivityType,
		targetId: String,
		createdAt: LocalDateTime
	): ActivityEntry = newSuspendedTransaction {
		val id = UserActivityTable.insertAndGetId {
			it[UserActivityTable.userId] = userId
			it[UserActivityTable.type] = type
			it[UserActivityTable.targetId] = targetId
			it[UserActivityTable.createdAt] = createdAt
		}

		ActivityEntry(
			id = id.value,
			type = type,
			targetId = targetId,
			createdAt = createdAt
		)
	}

	override suspend fun getUserActivity(userId: UUID, limit: Int, offset: Int): List<ActivityEntry> =
		newSuspendedTransaction {
			UserActivityTable
				.selectAll()
				.where { UserActivityTable.userId eq userId }
				.orderBy(UserActivityTable.createdAt to SortOrder.DESC)
				.limit(limit)
				.offset(offset.toLong())
				.map { row ->
					ActivityEntry(
						id = row[UserActivityTable.id].value,
						type = row[UserActivityTable.type],
						targetId = row[UserActivityTable.targetId],
						createdAt = row[UserActivityTable.createdAt]
					)
				}
		}

	override suspend fun getRecentActivity(userId: UUID, limit: Int): List<ActivityEntry> =
		getUserActivity(userId, limit, 0)
}