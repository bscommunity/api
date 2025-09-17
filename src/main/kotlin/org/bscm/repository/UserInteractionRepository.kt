package org.bscm.repository

import org.bscm.models.UserInteraction
import org.bscm.models.enums.ContentType
import java.util.*

interface UserInteractionRepository {
    suspend fun likeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun unlikeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun favoriteContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun unfavoriteContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun getUserInteraction(userId: UUID, contentType: ContentType, contentId: ULong): UserInteraction?
    suspend fun getUserLikedContent(userId: UUID, contentType: ContentType, limit: Int? = null, offset: Int? = null): List<ULong>
    suspend fun getUserFavoritedContent(userId: UUID, contentType: ContentType, limit: Int? = null, offset: Int? = null): List<ULong>
    suspend fun getContentInteractionStats(contentType: ContentType, contentId: ULong): Map<String, Int>
}
