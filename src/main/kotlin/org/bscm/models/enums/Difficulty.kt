package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty {
    Normal, Hard, Extreme, Expert
}