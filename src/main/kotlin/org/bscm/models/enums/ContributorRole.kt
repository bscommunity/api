package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ContributorRole {
    Author, Chart, Audio, Revision, Effects, Sync, Preview
}