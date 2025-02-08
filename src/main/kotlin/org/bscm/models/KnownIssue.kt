package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class KnownIssue(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val description: String,
    @Serializable(with = LocalDateSerializer::class)
    val createdAt: LocalDate = LocalDate.now()
)