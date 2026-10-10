package com.eplik82.parkedvideo

import android.content.Context

/** Telefonis valitud video, mida auto ekraan esitab. */
class VideoPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video", Context.MODE_PRIVATE)

    var videoId: String?
        get() = prefs.getString(KEY_VIDEO_ID, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_ID, value).apply()

    private companion object {
        const val KEY_VIDEO_ID = "video_id"
    }
}
