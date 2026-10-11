package com.eplik82.parkedvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView

/** Telefonis: video, brauseri avaleht, ooteaeg ja päeva/öö režiim. */
class MainActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var videoInput: EditText
    private lateinit var homeUrlInput: EditText
    private lateinit var stationaryInput: EditText
    private lateinit var themeGroup: RadioGroup
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = AppSettings(this)
        setTheme(
            if (settings.isDark(AppSettings.isSystemDark(this))) android.R.style.Theme_Material
            else android.R.style.Theme_Material_Light
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        videoInput = findViewById(R.id.video_input)
        homeUrlInput = findViewById(R.id.home_url_input)
        stationaryInput = findViewById(R.id.stationary_input)
        themeGroup = findViewById(R.id.theme_group)
        status = findViewById(R.id.status_text)
        stationaryInput.hint = getString(
            R.string.setup_stationary_hint,
            SettingsInput.MIN_STATIONARY_SECONDS,
            SettingsInput.MAX_STATIONARY_SECONDS,
        )
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }

        if (savedInstanceState == null) loadFields()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun loadFields() {
        videoInput.setText(settings.videoId?.let { "https://youtu.be/$it" }.orEmpty())
        homeUrlInput.setText(settings.homeUrl)
        stationaryInput.setText(settings.stationarySeconds.toString())
        themeGroup.check(
            when (settings.themeMode) {
                ThemeMode.AUTO -> R.id.theme_auto
                ThemeMode.DAY -> R.id.theme_day
                ThemeMode.NIGHT -> R.id.theme_night
            }
        )
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val id = YouTubeIds.parse(shared)
        if (id == null) {
            status.setText(R.string.setup_invalid_video)
            return
        }
        settings.videoId = id
        videoInput.setText("https://youtu.be/$id")
        status.setText(R.string.setup_saved)
    }

    private fun save() {
        val errors = mutableListOf<String>()

        val videoText = videoInput.text.toString()
        val videoId = if (videoText.isBlank()) null else YouTubeIds.parse(videoText)
        if (videoText.isNotBlank() && videoId == null) errors += getString(R.string.setup_invalid_video)

        val homeUrl = SettingsInput.normalizeUrl(homeUrlInput.text.toString())
        if (homeUrl == null) errors += getString(R.string.setup_invalid_url)

        val seconds = SettingsInput.parseStationarySeconds(stationaryInput.text.toString())
        if (seconds == null) {
            errors += getString(
                R.string.setup_invalid_stationary,
                SettingsInput.MIN_STATIONARY_SECONDS,
                SettingsInput.MAX_STATIONARY_SECONDS,
            )
        }

        if (errors.isNotEmpty()) {
            status.text = errors.joinToString("\n")
            return
        }

        val newTheme = when (themeGroup.checkedRadioButtonId) {
            R.id.theme_day -> ThemeMode.DAY
            R.id.theme_night -> ThemeMode.NIGHT
            else -> ThemeMode.AUTO
        }
        val themeChanged = newTheme != settings.themeMode

        settings.videoId = videoId
        settings.homeUrl = homeUrl!!
        settings.stationarySeconds = seconds!!
        settings.themeMode = newTheme
        homeUrlInput.setText(homeUrl)
        status.setText(R.string.setup_saved)

        if (themeChanged) recreate()
    }
}
