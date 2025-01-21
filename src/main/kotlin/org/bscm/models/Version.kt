// @file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class Version(
    val id: Int,
    @Serializable(with = UUIDSerializer::class)
    val chartId: UUID,
    val index: Int,
    val chartUrl: String,
    val downloadsAmount: Int,
    val knownIssues: List<KnownIssue>,
    @Serializable(with = LocalDateSerializer::class)
    val publishedAt: LocalDate,
)