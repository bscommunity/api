@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer

@Serializable
data class UserProfileResponse(
    val user: SimplifiedUser,
    val charts: List<CatalogItem>,
    val collections: List<Collection>? = null, // Only for owner
    val likes: List<CatalogItem>? = null,
    val bookmarks: List<CatalogItem>? = null,
    val stats: UserStats
)

