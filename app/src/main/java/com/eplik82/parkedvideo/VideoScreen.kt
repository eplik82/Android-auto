package com.eplik82.parkedvideo

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
 * Android Auto ekraan. Video käivitub ainult nupuvajutusega ja ainult siis,
 * kui [ParkingMonitor] loeb auto pargituks; liikuma hakates peatatakse
 * video kohe ja mängija suletakse täielikult (ka heli).
 *
 * Kasutatakse alati NavigationTemplate'i, et mallivahetused ei kuluks
 * Android Auto sammulimiidi arvelt – teated joonistatakse pinnale.
 */
class VideoScreen(carContext: CarContext) : Screen(carContext) {

    private val prefs = VideoPrefs(carContext)
    private val renderer = VideoSurfaceRenderer(carContext)
    private val monitor = ParkingMonitor(carContext, STATIONARY_MILLIS) { onParkingChanged() }

    private var permissionDenied = false
    private var playing = false
    private var paused = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                startMonitoring()
                refresh()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                monitor.stop()
                renderer.release()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
        when {
            playing -> {
                actions.addAction(action(if (paused) R.string.car_resume else R.string.car_pause) { togglePause() })
                actions.addAction(action(R.string.car_stop) { stopVideo() })
            }
            monitor.state == ParkingState.PARKED -> actions.addAction(action(R.string.car_play) { startVideo() })
            else -> actions.addAction(action(R.string.car_locked) { toast(R.string.car_locked_toast) })
        }
        return NavigationTemplate.Builder().setActionStrip(actions.build()).build()
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
            playing = false
            paused = false
        }
        refresh()
    }

    private fun startVideo() {
        if (monitor.state != ParkingState.PARKED) {
            toast(R.string.car_locked_toast)
            return
        }
        val videoId = prefs.videoId
        if (videoId == null) {
            toast(R.string.car_no_video_toast)
            return
        }
        playing = true
        paused = false
        renderer.playVideo(videoId)
        invalidate()
    }

    private fun togglePause() {
        paused = !paused
        if (paused) renderer.pause() else renderer.resume()
        invalidate()
    }

    private fun stopVideo() {
        playing = false
        paused = false
        refresh()
    }

    /** Näitab olekuteadet, kui video parasjagu ei mängi, ja uuendab nuppe. */
    private fun refresh() {
        if (!playing) renderer.showMessage(statusMessage())
        invalidate()
    }

    private fun statusMessage(): String = when (monitor.state) {
        ParkingState.UNKNOWN ->
            carContext.getString(if (permissionDenied) R.string.car_msg_no_permission else R.string.car_msg_unknown)
        ParkingState.MOVING -> carContext.getString(R.string.car_msg_moving)
        ParkingState.STOPPED ->
            carContext.getString(R.string.car_msg_waiting, (monitor.stationaryMillis / 1000).toInt())
        ParkingState.PARKED ->
            carContext.getString(if (prefs.videoId == null) R.string.car_msg_parked_no_video else R.string.car_msg_parked)
    }

    private fun action(title: Int, onClick: () -> Unit): Action =
        Action.Builder().setTitle(carContext.getString(title)).setOnClickListener { onClick() }.build()

    private fun toast(text: Int) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    private companion object {
        const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
        const val STATIONARY_MILLIS = 30_000L
    }
}
