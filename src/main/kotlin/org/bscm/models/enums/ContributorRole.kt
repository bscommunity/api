package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ContributorRole(val id: Int) {
    // Declaration order is persisted as the DB ordinal (INT column) — only ever
    // append new roles at the end, never reorder or remove.
    AUTHOR(0), CHART(1), AUDIO(2), REVISION(3), EFFECTS(4), SYNC(5), GAMEPLAY(6),
    ART(7), TEXTURES(8), CURATION(9), VIDEO(10);

    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}