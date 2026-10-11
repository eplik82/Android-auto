package com.eplik82.parkedvideo

/** Telefoni seadetes sisestatud väärtuste kontroll. */
object SettingsInput {
    /** Alampiir hoiab ära video avanemise tavalise foori- või ummikupeatuse ajal. */
    const val MIN_STATIONARY_SECONDS = 30
    const val MAX_STATIONARY_SECONDS = 600
    const val DEFAULT_STATIONARY_SECONDS = 30
    const val DEFAULT_HOME_URL = "https://www.google.com"

    fun parseStationarySeconds(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in MIN_STATIONARY_SECONDS..MAX_STATIONARY_SECONDS }

    /** Lisab puuduva https:// eesliite; lubab ainult http- ja https-aadresse. */
    fun normalizeUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val url = if ("://" in trimmed) trimmed else "https://$trimmed"
        val scheme = url.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty() || '.' !in host) return null
        return url
    }
}
