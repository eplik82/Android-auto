package com.eplik82.parkedvideo

import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Android Auto ekraan. Video ja brauser avanevad ainult nupuvajutusega ja
 * ainult siis, kui [ParkingMonitor] loeb auto pargituks; liikuma hakates
 * suletakse need kohe täielikult (ka heli).
 *
 * Kasutatakse alati NavigationTemplate'i, et mallivahetused ei kuluks
 * Android Auto sammulimiidi arvelt – teated joonistatakse pinnale.
 */
class ParkedScreen(carContext: CarContext) : Screen(carContext) {

    private enum class Mode { IDLE, VIDEO, WEB }

    private val settings = AppSettings(carContext)
    private val renderer = SurfaceRenderer(carContext)
    private val monitor = ParkingMonitor(carContext, { settings.stationarySeconds * 1000L }) { onParkingChanged() }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyTheme()
        refresh()
    }

    private var permissionDenied = false
    private var mode = Mode.IDLE
    private var paused = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                settings.registerListener(settingsListener)
                applyTheme()
                startMonitoring()
                refresh()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                settings.unregisterListener(settingsListener)
                monitor.stop()
                renderer.release()
            }
        })
    }

    /** Päeva/öö režiim: seadetest või AUTO puhul auto enda järgi (nt esitulede sisselülitamisel). */
    fun applyTheme() = renderer.setDarkMode(settings.isDark(carContext.isDarkMode))

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
        val template = NavigationTemplate.Builder()
        when {
            mode == Mode.VIDEO -> {
                actions.addAction(action(if (paused) R.string.car_resume else R.string.car_pause) { togglePause() })
                actions.addAction(action(R.string.car_stop) { close() })
            }
            mode == Mode.WEB -> {
                actions.addAction(action(R.string.car_back) { renderer.goBack() })
                actions.addAction(action(R.string.car_close) { close() })
                // PAN lubab lehte auto ekraanil kerida.
                template.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            }
            monitor.state == ParkingState.PARKED -> {
                actions.addAction(action(R.string.car_play) { startVideo() })
                actions.addAction(action(R.string.car_browser) { openBrowser() })
            }
            else -> actions.addAction(action(R.string.car_locked) { toast(R.string.car_locked_toast) })
        }
        return template.setActionStrip(actions.build()).build()
    }

    private fun startMonitoring() {
        if (carContext.checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) {
            monitor.start()
            return
        }
        carContext.requestPermissions(listOf(CAR_SPEED)) { granted, _ ->
            permissionDenied = CAR_SPEED !in granted
            if (!permissionDenied) monitor.start()
            refresh()
        }
    }

    private fun onParkingChanged() {
        if (monitor.state != ParkingState.PARKED) {
            mode = Mode.IDLE
            paused = false
        }
        refresh()
    }

    private fun isParkedOrWarn(): Boolean {
        if (monitor.state == ParkingState.PARKED) return true
        toast(R.string.car_locked_toast)
        return false
    }

    private fun startVideo() {
        if (!isParkedOrWarn()) return
        val videoId = settings.videoId
        if (videoId == null) {
            toast(R.string.car_no_video_toast)
            return
        }
        mode = Mode.VIDEO
        paused = false
        renderer.playVideo(videoId)
        invalidate()
    }

    private fun openBrowser() {
        if (!isParkedOrWarn()) return
        mode = Mode.WEB
        renderer.openWeb(settings.homeUrl)
        invalidate()
    }

    private fun togglePause() {
        paused = !paused
        if (paused) renderer.pause() else renderer.resume()
        invalidate()
    }

    private fun close() {
        mode = Mode.IDLE
        paused = false
        refresh()
    }

    /** Näitab olekuteadet, kui midagi parasjagu avatud pole, ja uuendab nuppe. */
    private fun refresh() {
        if (mode == Mode.IDLE) renderer.showMessage(statusMessage())
        invalidate()
    }

    private fun statusMessage(): String = when (monitor.state) {
        ParkingState.UNKNOWN ->
            carContext.getString(if (permissionDenied) R.string.car_msg_no_permission else R.string.car_msg_unknown)
        ParkingState.MOVING -> carContext.getString(R.string.car_msg_moving)
        ParkingState.STOPPED -> carContext.getString(R.string.car_msg_waiting, settings.stationarySeconds)
        ParkingState.PARKED -> carContext.getString(R.string.car_msg_parked)
    }

    private fun action(title: Int, onClick: () -> Unit): Action =
        Action.Builder().setTitle(carContext.getString(title)).setOnClickListener { onClick() }.build()

    private fun toast(text: Int) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    private companion object {
        const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    }
}
