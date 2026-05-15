@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Version(
    val id: String,
    val catalogItemId: String,
    val versionCode: Int,
    val downloadsAmount: Int = 0,
    val fileSizeBytes: Long,
    val changelog: String?,
    val createdAt: LocalDateTime,
)