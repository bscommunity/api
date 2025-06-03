package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class StreamingPlatform {
    SPOTIFY,
    APPLE_MUSIC,
    YOUTUBE_MUSIC,
    DEEZER,
    TIDAL,
    AMAZON_MUSIC,
    SOUNDCLOUD,
    LAST_FM
}