package org.bscm.utils

import io.klogging.noCoLogger
import org.bscm.models.StreamingLink
import org.bscm.models.enums.StreamingPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingPlatformUtilsTest {

    private val logger = noCoLogger(StreamingPlatformUtilsTest::class)

    @Test
    fun `test link prioritization with music subdomain`() {
        val links = listOf(
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://www.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer music.youtube.com
        assertEquals("https://music.youtube.com/watch?v=yOoaNE6xo4Q", result[0].url)
    }

    @Test
    fun `test link prioritization with shortest URL`() {
        val links = listOf(
            StreamingLink(StreamingPlatform.AMAZON_MUSIC, "https://music.amazon.com/albums/B09XNC1LNL?trackAsin=B09XN9HY1K"),
            StreamingLink(StreamingPlatform.AMAZON_MUSIC, "https://amazon.com/dp/B09XN9HY1K")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer music.amazon.com (has "music" keyword) even if longer
        assertTrue(result[0].url.contains("music.amazon.com"))
    }

    @Test
    fun `test link prioritization filters out mismatched platforms`() {
        val links = listOf(
            StreamingLink(StreamingPlatform.SPOTIFY, "https://audiomack.com/song/sarcastic-sounds/disappointment"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://play.anghami.com/song/1048749578?refer=linktree"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://www.boomplay.com/songs/86525664"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://play.napster.com/track/tra.660007434"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://www.pandora.com/TR:63775309"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I")
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
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=music&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=itunes&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m")
        )

        val result = StreamingPlatformUtils.processLinksWithPrioritization(links, useKeyForDetection = true)

        assertEquals(1, result.size)
        // Should prefer the one with &app=music (shorter or music-related)
        assertTrue(result[0].url.contains("music.apple.com"))
    }

    @Test
    fun `test serialization and deserialization`() {
        val links = listOf(
            StreamingLink(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I"),
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingLink(StreamingPlatform.DEEZER, "https://www.deezer.com/track/1693211037"),
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=music"),
            StreamingLink(StreamingPlatform.SOUNDCLOUD, "https://soundcloud.com/the-sarcastic-ashole/disappointment-feat-rxseboy?utm_medium=api&utm_campaign=social_sharing&utm_source=id_314547")
        )

        val serialized = StreamingPlatformUtils.serializeLinks(links)
        logger.info("Serialized: $serialized")

        // Verify no domain names leak into serialized format
        assertFalse(serialized.contains("spotify.com"), "Serialized should not contain domain")
        assertFalse(serialized.contains("youtube.com"), "Serialized should not contain domain")
        assertFalse(serialized.contains("apple.com"), "Serialized should not contain domain")
        assertFalse(serialized.contains("soundcloud.com"), "Serialized should not contain domain")
        assertFalse(serialized.contains("utm_"), "Serialized should not contain tracking params")

        val deserialized = StreamingPlatformUtils.deserializeLinks(serialized)

        assertEquals(links.size, deserialized.size)

        // Verify platforms match
        assertEquals(links[0].platform, deserialized[0].platform)
        assertEquals(links[1].platform, deserialized[1].platform)
        assertEquals(links[2].platform, deserialized[2].platform)
        assertEquals(links[3].platform, deserialized[3].platform)
        assertEquals(links[4].platform, deserialized[4].platform)

        // Verify URLs are reconstructed properly (with tracking params removed)
        assertTrue(deserialized[0].url.contains("spotify.com/track"))
        assertTrue(deserialized[1].url.contains("music.youtube.com/watch"))
        assertTrue(deserialized[2].url.contains("deezer.com/track"))
        assertTrue(deserialized[3].url.contains("music.apple.com/us/album"))
        assertTrue(deserialized[4].url.contains("soundcloud.com/the-sarcastic-ashole"))
        assertFalse(deserialized[4].url.contains("utm_"), "Deserialized URL should not have tracking params")
    }

    @Test
    fun `test serialization space savings`() {
        val links = listOf(
            StreamingLink(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I"),
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingLink(StreamingPlatform.DEEZER, "https://www.deezer.com/track/1693211037"),
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://music.apple.com/us/album/song/1234567890"),
            StreamingLink(StreamingPlatform.TIDAL, "https://listen.tidal.com/track/221939726"),
            StreamingLink(StreamingPlatform.AMAZON_MUSIC, "https://music.amazon.com/albums/B09XNC1LNL?trackAsin=B09XN9HY1K"),
            StreamingLink(StreamingPlatform.SOUNDCLOUD, "https://soundcloud.com/artist/track-name")
        )

        val (originalSize, serializedSize) = StreamingPlatformUtils.calculateSerializationSavings(links)

        logger.info("Original size: $originalSize bytes")
        logger.info("Serialized size: $serializedSize bytes")
        logger.info("Space saved: ${originalSize - serializedSize} bytes (${100 - (serializedSize * 100 / originalSize)}%)")

        // Should save significant space
        assertTrue(serializedSize < originalSize)
        assertTrue(serializedSize < originalSize * 0.6) // Should save at least 40%
    }

    @Test
    fun `test complete Odesli response prioritization`() {
        // Real world example from the issue
        val links = listOf(
            StreamingLink(StreamingPlatform.AMAZON_MUSIC, "https://music.amazon.com/albums/B09XNC1LNL?trackAsin=B09XN9HY1K"),
            StreamingLink(StreamingPlatform.AMAZON_MUSIC, "https://amazon.com/dp/B09XN9HY1K"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://audiomack.com/song/sarcastic-sounds/disappointment"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://play.anghami.com/song/1048749578?refer=linktree"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://www.boomplay.com/songs/86525664"),
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=music&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingLink(StreamingPlatform.APPLE_MUSIC, "https://geo.music.apple.com/us/album/_/1612879515?i=1612879518&mt=1&app=itunes&ls=1&at=1000lHKX&ct=api_http&itscg=30200&itsct=odsl_m"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://play.napster.com/track/tra.660007434"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://www.pandora.com/TR:63775309"),
            StreamingLink(StreamingPlatform.SOUNDCLOUD, "https://soundcloud.com/the-sarcastic-ashole/disappointment-feat-rxseboy?utm_medium=api&utm_campaign=social_sharing&utm_source=id_314547"),
            StreamingLink(StreamingPlatform.SPOTIFY, "https://open.spotify.com/track/6Xoe3jKnSFKSzsQ8kdDT7I"),
            StreamingLink(StreamingPlatform.TIDAL, "https://listen.tidal.com/track/221939726"),
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://www.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingLink(StreamingPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=yOoaNE6xo4Q"),
            StreamingLink(StreamingPlatform.DEEZER, "https://www.deezer.com/track/1693211037")
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
