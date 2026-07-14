package org.bscm.models.interfaces

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.ActivityEntry
import org.bscm.models.enums.ActivityType
import java.util.*

interface IActivityRepository {
	suspend fun logActivity(
		userId: UUID,
		type: ActivityType,
		targetId: String,
		createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
	): ActivityEntry
	suspend fun batchLogActivity(
		userId: UUID,
		type: ActivityType,
		targetIds: List<String>,
		createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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