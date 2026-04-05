package org.bscm.models.interfaces

import org.bscm.models.ActivityEntry
import org.bscm.models.enums.ActivityType
import java.time.LocalDateTime
import java.util.*

interface IActivityRepository {
	suspend fun logActivity(
		userId: UUID,
		type: ActivityType,
		targetId: String,
		createdAt: LocalDateTime = LocalDateTime.now()
	): ActivityEntry
	suspend fun batchLogActivity(
		userId: UUID,
		type: ActivityType,
		targetIds: List<String>,
		createdAt: LocalDateTime = LocalDateTime.now()
	): Int

	suspend fun removeActivity(
		userId: UUID,
		type: ActivityType,
		targetId: String
	): Int

	suspend fun batchRemoveActivity(
		userId: UUID,
		type: ActivityType,
		targetIds: List<String>
	): Int

	suspend fun removeActivityByTypeAndTarget(
		type: ActivityType,
		targetId: String
	): Int

	suspend fun getUserActivity(userId: UUID, limit: Int, offset: Int): List<ActivityEntry>
	suspend fun getRecentActivity(userId: UUID, limit: Int): List<ActivityEntry>
}