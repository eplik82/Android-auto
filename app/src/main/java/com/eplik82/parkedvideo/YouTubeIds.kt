package com.eplik82.parkedvideo

/** Leiab YouTube'i lingist (või toorest ID-st) 11-märgilise video ID. */
object YouTubeIds {
    private val RAW_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val IN_URL = Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])")

    fun parse(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (RAW_ID.matches(text)) return text
        return IN_URL.find(text)?.groupValues?.get(1)
    }
}
