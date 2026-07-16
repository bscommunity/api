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
    LAST_FM(7),
    PANDORA(8),
    NAPSTER(9),
    QOBUZ(10),
    YANDEX_MUSIC(11),
    BOOMPLAY(12),
    ANGHAMI(13),
    AUDIOMACK(14),
    SHAZAM(15),
    JIOSAAVN(16);

    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}