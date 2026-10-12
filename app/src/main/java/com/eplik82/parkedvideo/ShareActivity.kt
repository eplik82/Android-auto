package com.eplik82.parkedvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Nähtamatu jagamise sihtmärk (YouTube → Jaga → Pargitud video): salvestab
 * video, käivitab selle auto ekraanil ja sulgub kohe, nii et kasutaja jääb
 * YouTube'i. Ainult lühike teade näitab, et video jõudis kohale.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val id = text?.let(YouTubeIds::parse)
        if (id == null) {
            Toast.makeText(this, R.string.setup_invalid_video, Toast.LENGTH_LONG).show()
        } else {
            val settings = AppSettings(this)
            settings.videoId = id
            settings.requestPlay()
            Toast.makeText(this, R.string.setup_shared, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
