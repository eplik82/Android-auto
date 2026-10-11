package com.eplik82.parkedvideo

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class ParkedVideoService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = ParkedSession()
}

private class ParkedSession : Session() {
    private var screen: ParkedScreen? = null

    override fun onCreateScreen(intent: Intent): Screen = ParkedScreen(carContext).also { screen = it }

    /** Auto vahetab päeva- ja öörežiimi (carContext.isDarkMode). */
    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        screen?.applyTheme()
    }
}
