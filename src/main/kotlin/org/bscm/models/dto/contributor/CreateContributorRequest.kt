@file:UseSerializers(UUIDSerializer::class)

package org.bscm.models.dto.contributor

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.UUIDSerializer

@Serializable
data class CreateContributorRequest (
    val contributors: List<SimplifiedContributor>
)