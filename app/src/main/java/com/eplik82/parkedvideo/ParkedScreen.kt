package com.eplik82.parkedvideo

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
    private val renderer = SurfaceRenderer(carContext, onEditableTapped = { openKeyboard() })
    private val monitor = ParkingMonitor(
        carContext,
        stationaryMillis = { settings.stationarySeconds * 1000L },
        onStateChange = { onParkingChanged() },
        onSpeedUpdate = { if (mode == Mode.IDLE) renderer.showMessage(fullStatus()) },
    )
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_PLAY_REQUEST) {
            onPlayRequested()
            return@OnSharedPreferenceChangeListener
        }
        applyTheme()
        monitor.simulation = settings.simulatedMotion
        if (settings.speedSource != monitor.sourceType) startMonitoring()
        refresh()
    }

    private var permissionDenied = false

    /** Telefonis jagati video: mängi kohe, kui auto on pargitud (või saab pargituks ilma vahepeal sõitmata). */
    private var pendingPlay = false
    private val handler = Handler(Looper.getMainLooper())

    /** Kuni luba puudub, vaatab perioodiliselt, kas see on telefonis antud. */
    private val permissionPoll = object : Runnable {
        override fun run() {
            recheckPermission()
            if (permissionDenied) handler.postDelayed(this, PERMISSION_POLL_MILLIS)
        }
    }
    private var mode = Mode.IDLE
    private var paused = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                settings.registerListener(settingsListener)
                applyTheme()
                monitor.simulation = settings.simulatedMotion
                startMonitoring()
                // Video jagati veidi enne Android Autoga ühendamist.
                if (System.currentTimeMillis() - settings.playRequestAt < RECENT_SHARE_MILLIS) onPlayRequested()
                refresh()
            }

            override fun onResume(owner: LifecycleOwner) = recheckPermission()

            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacks(permissionPoll)
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
                actions.addAction(action(R.string.car_text) { openKeyboard() })
                actions.addAction(action(R.string.car_close) { close() })
                // PAN lubab lehte auto ekraanil kerida.
                template.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            }
            monitor.state == ParkingState.PARKED -> {
                actions.addAction(action(R.string.car_play) { startVideo() })
                actions.addAction(action(R.string.car_browser) { openBrowser() })
                actions.addAction(infoAction())
            }
            else -> {
                actions.addAction(action(R.string.car_locked) { toast(R.string.car_locked_toast) })
                actions.addAction(infoAction())
            }
        }
        return template.setActionStrip(actions.build()).build()
    }

    /**
     * Õigusi auto ekraanil ei küsita: CarContext.requestPermissions avab
     * telefonis tegevuse auto ekraanile, mida uuem Android keelab
     * (SecurityException → krahh). Luba antakse telefoni rakenduses
     * ([MainActivity]); siin ainult kontrollitakse ja näidatakse teadet.
     */
    private fun startMonitoring() {
        val type = settings.speedSource
        permissionDenied = !hasPermission(type)
        // Kuni luba pole, jääb allikas teadmata olekusse (video lukus).
        monitor.start(type)
        handler.removeCallbacks(permissionPoll)
        if (permissionDenied) handler.postDelayed(permissionPoll, PERMISSION_POLL_MILLIS)
    }

    /** Luba võidi vahepeal telefonis anda – siis alusta kuulamist uuesti. */
    private fun recheckPermission() {
        if (!permissionDenied || !hasPermission(settings.speedSource)) return
        startMonitoring()
        refresh()
    }

    private fun hasPermission(type: SpeedSourceType) =
        carContext.checkSelfPermission(type.permissions.first()) == PackageManager.PERMISSION_GRANTED

    private fun onPlayRequested() {
        when (monitor.state) {
            ParkingState.PARKED -> playShared()
            ParkingState.MOVING -> {
                pendingPlay = false
                toast(R.string.car_locked_toast)
            }
            // Ootab paigalseisu aega (või kiiruse andmeid) – mängib, kui auto saab pargituks.
            else -> pendingPlay = true
        }
    }

    private fun playShared() {
        pendingPlay = false
        // Klaviatuur või „Auto info“ võib olla peal – video tuleb ette.
        screenManager.popToRoot()
        startVideo()
    }

    private fun onParkingChanged() {
        if (monitor.state == ParkingState.MOVING) pendingPlay = false
        if (monitor.state == ParkingState.PARKED && pendingPlay) {
            playShared()
            return
        }
        if (monitor.state != ParkingState.PARKED) {
            mode = Mode.IDLE
            paused = false
            if (screenManager.top is TextInputScreen) screenManager.pop()
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

    /** Auto klaviatuur: tekst läheb aktiivsele väljale või aadressiribale. */
    private fun openKeyboard() {
        if (mode != Mode.WEB || screenManager.top !== this) return
        screenManager.push(TextInputScreen(carContext) { text ->
            if (mode == Mode.WEB) renderer.enterText(text)
        })
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
        if (mode == Mode.IDLE) renderer.showMessage(fullStatus())
        invalidate()
    }

    private fun fullStatus(): String = statusMessage() + "\n\n" + speedLine() + simulationNote()

    /** Diagnostika: millist allikat kasutatakse ja mis kiirus sealt tuleb. */
    private fun speedLine(): String {
        val source = carContext.getString(
            if (monitor.sourceType == SpeedSourceType.GPS) R.string.car_source_gps else R.string.car_source_car
        )
        val speed = monitor.realSpeed
            ?.let { carContext.getString(R.string.car_speed_kmh, Math.round(it * 3.6f)) }
            ?: carContext.getString(R.string.car_speed_none)
        return carContext.getString(R.string.car_speed_line, source, speed)
    }

    private fun simulationNote(): String = when {
        monitor.simulation == SimulatedMotion.MOVING -> "\n\n" + carContext.getString(R.string.car_test_moving)
        monitor.simulation == SimulatedMotion.STATIONARY -> "\n\n" + carContext.getString(R.string.car_test_stationary)
        else -> ""
    }

    private fun statusMessage(): String = when (monitor.state) {
        ParkingState.UNKNOWN -> carContext.getString(
            when {
                !permissionDenied -> R.string.car_msg_unknown
                monitor.sourceType == SpeedSourceType.GPS -> R.string.car_msg_no_location_permission
                else -> R.string.car_msg_no_permission
            }
        )
        ParkingState.MOVING -> carContext.getString(R.string.car_msg_moving)
        ParkingState.STOPPED -> carContext.getString(R.string.car_msg_waiting, settings.stationarySeconds)
        ParkingState.PARKED -> carContext.getString(R.string.car_msg_parked)
    }

    private fun infoAction() = action(R.string.car_info) { screenManager.push(CarInfoScreen(carContext)) }

    private fun action(title: Int, onClick: () -> Unit): Action =
        Action.Builder().setTitle(carContext.getString(title)).setOnClickListener { onClick() }.build()

    private fun toast(text: Int) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    private companion object {
        const val PERMISSION_POLL_MILLIS = 2_000L
        const val RECENT_SHARE_MILLIS = 120_000L
    }
}
