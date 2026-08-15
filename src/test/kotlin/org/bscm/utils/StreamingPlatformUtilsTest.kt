package org.bscm.utils


import io.ktor.util.logging.*
import org.bscm.models.StreamingRef
import org.bscm.models.enums.StreamingPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingPlatformUtilsTest {

    private val logger = KtorSimpleLogger("StreamingPlatformUtilsTest")

    @Test
    fun `test link prioritization with music subdomain`() {
        val links = listOf(
            StreamingRef(StreamingPlatform.YOUTUBE_MUSIC, "https://www.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingRef(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer music.youtube.com
        assertEquals("https://music.youtube.com/watch?v=yOoaNE6xo4Q", result[0].url)
    }

    @Test
    fun `test link prioritization with shortest URL`() {
        val links = listOf(
            StreamingRef(StreamingPlatform.AMAZON_MUSIC, "https://music.amazon.com/albums/B09XNC1LNL?trackAsin=B09XN9HY1K"),
            StreamingRef(StreamingPlatform.AMAZON_MUSIC, "https://amazon.com/dp/B09XN9HY1K")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer music.amazon.com (has "music" keyword) even if longer
        assertTrue(result[0].url.contains("music.amazon.com"))
    }

    @Test
    fun `test link prioritization filters out mismatched platforms`() {
        val links = listOf(
            StreamingRef(StreamingPlatform.SPOTIFY, "https://audiomack.com/song/sarcastic-sounds/disappointment"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://play.anghami.com/song/1048749578?refer=linktree"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://www.boomplay.com/songs/86525664"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://play.napster.com/track/tra.660007434"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://www.pandora.com/TR:63775309"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should only include the actual Spotify URL
        assertEquals(StreamingPlatform.SPOTIFY, result[0].platform)
        assertEquals("https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I", result[0].url)
    }

    @Test
    fun `test link prioritization with Apple Music variations`() {
        val links = listOf(
            StreamingRef(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=music&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingRef(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=itunes&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer the one with &app=music (shorter or music-related)
        assertTrue(result[0].url.contains("music.apple.com"))
    }

    @Test
    fun `test complete Odesli response prioritization`() {
        // Real world example from the issue
        val links = listOf(
            StreamingRef(StreamingPlatform.AMAZON_MUSIC, "https://music.amazon.com/albums/B09XNC1LNL?trackAsin=B09XN9HY1K"),
            StreamingRef(StreamingPlatform.AMAZON_MUSIC, "https://amazon.com/dp/B09XN9HY1K"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://audiomack.com/song/sarcastic-sounds/disappointment"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://play.anghami.com/song/1048749578?refer=linktree"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://www.boomplay.com/songs/86525664"),
            StreamingRef(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=music&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingRef(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=itunes&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://play.napster.com/track/tra.660007434"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://www.pandora.com/TR:63775309"),
            StreamingRef(StreamingPlatform.SOUNDCLOUD, "https://soundcloud.com/the-sarcastic-ashole/disappointment-feat-rxseboy?utm_medium=api&utm_campaign=social_sharing&utm_source=id_314547"),
            StreamingRef(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I"),
            StreamingRef(StreamingPlatform.TIDAL, "https://listen.tidal.com/track/221939726"),
            StreamingRef(StreamingPlatform.YOUTUBE_MUSIC, "https://www.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingRef(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingRef(StreamingPlatform.DEEZER, "https://www.deezer.com/track/1693211037")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        logger.info("Resolved links:")
        result.forEach { logger.info("  ${it.platform}: ${it.url}") }

        // Should have one link per actual platform (not the fake Spotify assignments)
        assertTrue(result.any { it.platform == StreamingPlatform.SPOTIFY && it.url.contains("spotify.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.YOUTUBE_MUSIC && it.url.contains("music.youtube.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.APPLE_MUSIC && it.url.contains("apple.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.DEEZER && it.url.contains("deezer.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.TIDAL && it.url.contains("tidal.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.SOUNDCLOUD && it.url.contains("soundcloud.com") })
        assertTrue(result.any { it.platform == StreamingPlatform.AMAZON_MUSIC && it.url.contains("amazon.com") })
    }
}
