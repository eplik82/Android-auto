package com.eplik82.parkedvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeIdsTest {
    private val id = "dQw4w9WgXcQ"

    @Test
    fun parsesCommonLinkFormats() {
        listOf(
            id,
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=42",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id",
            "Vaata seda! https://youtu.be/$id",
        ).forEach { assertEquals(it, id, YouTubeIds.parse(it)) }
    }

    @Test
    fun rejectsNonVideoInput() {
        assertNull(YouTubeIds.parse(null))
        assertNull(YouTubeIds.parse(""))
        assertNull(YouTubeIds.parse("https://www.youtube.com/"))
        assertNull(YouTubeIds.parse("https://youtu.be/tooShort"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/watch?v=${id}EXTRA"))
    }
}
