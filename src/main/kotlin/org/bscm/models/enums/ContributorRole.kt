package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ContributorRole {
    AUTHOR, CHART, AUDIO, REVISION, EFFECTS, SYNC, GAMEPLAY
}