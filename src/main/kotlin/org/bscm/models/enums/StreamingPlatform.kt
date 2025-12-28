package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class StreamingPlatform(val id: Int) {
    SPOTIFY(0),
    APPLE_MUSIC(1),
    YOUTUBE_MUSIC(2),
    DEEZER(3),
    TIDAL(4),
    AMAZON_MUSIC(5),
    SOUNDCLOUD(6),
    LAST_FM(7)
}