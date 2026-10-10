package com.eplik82.parkedvideo

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlin.math.abs

enum class ParkingState {
    /** Kiiruseandmeid pole – käsitletakse nagu sõitu. */
    UNKNOWN,
    MOVING,
    /** Auto seisab, kuid veel liiga lühikest aega (nt foori taga). */
    STOPPED,
    /** Auto on seisnud vähemalt [ParkingMonitor.stationaryMillis]. */
    PARKED,
}

/**
 * Jälgib auto kiirust. Android Auto ei anna rakendustele käigu- ega
 * seisupiduri andmeid, seega loetakse auto pargituks alles siis, kui see on
 * katkematult seisnud [stationaryMillis]. Vähimgi liikumine katkestab kohe.
 */
class ParkingMonitor(
    private val carContext: CarContext,
    val stationaryMillis: Long,
    private val onChange: (ParkingState) -> Unit,
) {
    var state = ParkingState.UNKNOWN
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val promoteToParked = Runnable { if (state == ParkingState.STOPPED) update(ParkingState.PARKED) }
    private val listener = OnCarDataAvailableListener<Speed> { onSpeed(it) }
    private var listening = false

    private val carInfo
        get() = carContext.getCarService(CarHardwareManager::class.java).carInfo

    fun start() {
        if (listening) return
        try {
            carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            listening = true
        } catch (e: SecurityException) {
            update(ParkingState.UNKNOWN)
        }
    }

    fun stop() {
        if (listening) {
            carInfo.removeSpeedListener(listener)
            listening = false
        }
        handler.removeCallbacks(promoteToParked)
    }

    private fun onSpeed(speed: Speed) {
        val raw = speed.rawSpeedMetersPerSecond
        val metersPerSecond = raw.value
        if (raw.status != CarValue.STATUS_SUCCESS || metersPerSecond == null) {
            handler.removeCallbacks(promoteToParked)
            update(ParkingState.UNKNOWN)
            return
        }
        if (abs(metersPerSecond) > STATIONARY_SPEED_MPS) {
            handler.removeCallbacks(promoteToParked)
            update(ParkingState.MOVING)
            return
        }
        if (state == ParkingState.STOPPED || state == ParkingState.PARKED) return
        update(ParkingState.STOPPED)
        handler.postDelayed(promoteToParked, stationaryMillis)
    }

    private fun update(newState: ParkingState) {
        if (newState == state) return
        state = newState
        onChange(newState)
    }

    private companion object {
        /** ~1 km/h – sellest kiiremini liikuv auto ei ole pargitud. */
        const val STATIONARY_SPEED_MPS = 0.3f
    }
}
