package org.bscm.repository

import org.bscm.models.ActivityEntry
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.tables.UserActivityTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
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

	/**
	 * Batch-insert multiple activity entries for the same user and type in a single
	 * transaction to improve performance. Returns the count of inserted rows.
	 * Individual entries are not returned because [batchInsert] does not guarantee
	 * the same ordering as the input list when fetching generated IDs,
	 * and callers don't need them.
	 */
	override suspend fun batchLogActivity(
		userId: UUID,
		type: ActivityType,
		targetIds: List<String>,
		createdAt: LocalDateTime
	): Int = newSuspendedTransaction {
		if (targetIds.isEmpty()) return@newSuspendedTransaction 0

		UserActivityTable.batchInsert(targetIds, ignore = true) { targetId ->
			this[UserActivityTable.userId] = userId
			this[UserActivityTable.type] = type
			this[UserActivityTable.targetId] = targetId
			this[UserActivityTable.createdAt] = createdAt
		}.size
	}

	override suspend fun removeActivity(
		userId: UUID,
		type: ActivityType,
		targetId: String
	): Int = newSuspendedTransaction {
		UserActivityTable.deleteWhere {
			(UserActivityTable.userId eq userId) and
				(UserActivityTable.type eq type) and
				(UserActivityTable.targetId eq targetId)
		}
	}

	override suspend fun batchRemoveActivity(
		userId: UUID,
		type: ActivityType,
		targetIds: List<String>
	): Int = newSuspendedTransaction {
		if (targetIds.isEmpty()) return@newSuspendedTransaction 0

		UserActivityTable.deleteWhere {
			(UserActivityTable.userId eq userId) and
				(UserActivityTable.type eq type) and
				(UserActivityTable.targetId inList targetIds)
		}
	}

	override suspend fun removeActivityByTypeAndTarget(
		type: ActivityType,
		targetId: String
	): Int = newSuspendedTransaction {
		UserActivityTable.deleteWhere {
			(UserActivityTable.type eq type) and
				(UserActivityTable.targetId eq targetId)
		}
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