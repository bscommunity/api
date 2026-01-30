package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ContributorRole(val id: Int) {
    AUTHOR(0), CHART(1), AUDIO(2), REVISION(3), EFFECTS(4), SYNC(5), GAMEPLAY(6);

    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}