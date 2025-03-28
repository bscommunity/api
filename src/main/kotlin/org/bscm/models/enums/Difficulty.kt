package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty {
    NORMAL, HARD, EXTREME, EXPERT
}