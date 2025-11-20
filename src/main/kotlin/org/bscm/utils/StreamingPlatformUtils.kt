package org.bscm.utils

import org.bscm.models.StreamingLink
import org.bscm.models.enums.StreamingPlatform

object StreamingPlatformUtils {
    fun fromKey(key: String): StreamingPlatform {
        return when (key.lowercase()) {
            "spotify" -> StreamingPlatform.SPOTIFY
            "applemusic", "itunes" -> StreamingPlatform.APPLE_MUSIC
            "youtubemusic" -> StreamingPlatform.YOUTUBE_MUSIC
            "deezer" -> StreamingPlatform.DEEZER
            "tidal" -> StreamingPlatform.TIDAL
            "amazonmusic", "amazonstore" -> StreamingPlatform.AMAZON_MUSIC
            "soundcloud" -> StreamingPlatform.SOUNDCLOUD
            "lastfm" -> StreamingPlatform.LAST_FM
            else -> StreamingPlatform.SPOTIFY // Default fallback
        }
    }

    fun fromUrl(url: String): StreamingPlatform {
        val lowerUrl = url.lowercase()
        return when {
            "spotify" in lowerUrl -> StreamingPlatform.SPOTIFY
            "apple" in lowerUrl || "itunes" in lowerUrl -> StreamingPlatform.APPLE_MUSIC
            "youtube" in lowerUrl -> StreamingPlatform.YOUTUBE_MUSIC
            "deezer" in lowerUrl -> StreamingPlatform.DEEZER
            "tidal" in lowerUrl -> StreamingPlatform.TIDAL
            "amazon" in lowerUrl -> StreamingPlatform.AMAZON_MUSIC
            "soundcloud" in lowerUrl -> StreamingPlatform.SOUNDCLOUD
            "last.fm" in lowerUrl -> StreamingPlatform.LAST_FM
            else -> StreamingPlatform.SPOTIFY
        }
    }

    fun processLinksWithPrioritization(links: List<StreamingLink>, preferOdesli: Boolean): List<StreamingLink> {
        // Prioritize by platform order
        val priorityOrder = listOf(
            StreamingPlatform.SPOTIFY,
            StreamingPlatform.APPLE_MUSIC,
            StreamingPlatform.YOUTUBE_MUSIC,
            StreamingPlatform.DEEZER,
            StreamingPlatform.TIDAL,
            StreamingPlatform.AMAZON_MUSIC,
            StreamingPlatform.SOUNDCLOUD,
            StreamingPlatform.LAST_FM
        )
        val sorted = links.sortedBy { priorityOrder.indexOf(it.platform) }
        return if (preferOdesli) sorted else sorted.reversed()
    }
}

