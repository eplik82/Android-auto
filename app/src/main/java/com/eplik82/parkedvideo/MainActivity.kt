package com.eplik82.parkedvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Telefonis: kaasreisija valib siin video, mida pargitud autos vaadata. */
class MainActivity : Activity() {
    private lateinit var prefs: VideoPrefs
    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = VideoPrefs(this)
        input = findViewById(R.id.video_input)
        status = findViewById(R.id.status_text)
        findViewById<Button>(R.id.save_button).setOnClickListener { save(input.text.toString()) }

        showCurrent()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        input.setText(shared)
        save(shared)
    }

    private fun save(text: String) {
        val id = YouTubeIds.parse(text)
        if (id == null) {
            status.setText(R.string.setup_invalid)
            return
        }
        prefs.videoId = id
        status.text = getString(R.string.setup_saved) + "\n" + getString(R.string.setup_current, id)
    }

    private fun showCurrent() {
        val id = prefs.videoId
        status.text = if (id == null) getString(R.string.setup_none) else getString(R.string.setup_current, id)
    }
}
