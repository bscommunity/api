package org.bscm.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoIdUtilsTest {

    @Test
    fun `raw id passes through`() {
        assertEquals("dQw4w9WgXcQ", VideoIdUtils.extractYoutubeId("dQw4w9WgXcQ"))
    }

    @Test
    fun `watch urls resolve`() {
        assertEquals(
            "dQw4w9WgXcQ",
            VideoIdUtils.extractYoutubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            "dQw4w9WgXcQ",
            VideoIdUtils.extractYoutubeId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=abc"),
        )
    }

    @Test
    fun `short and embed forms resolve`() {
        assertEquals("dQw4w9WgXcQ", VideoIdUtils.extractYoutubeId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(
            "dQw4w9WgXcQ",
            VideoIdUtils.extractYoutubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"),
        )
        assertEquals(
            "dQw4w9WgXcQ",
            VideoIdUtils.extractYoutubeId("https://www.youtube.com/embed/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `blank and garbage return null`() {
        assertNull(VideoIdUtils.extractYoutubeId(null))
        assertNull(VideoIdUtils.extractYoutubeId("   "))
        assertNull(VideoIdUtils.extractYoutubeId("not a video id at all"))
        assertNull(VideoIdUtils.extractYoutubeId("https://example.com/trailer.mp4"))
    }
}
