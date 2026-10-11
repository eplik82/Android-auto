package com.eplik82.parkedvideo

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat

enum class ParkingState {
    /** Kiiruseandmeid pole – käsitletakse nagu sõitu. */
    UNKNOWN,
    MOVING,
    /** Auto seisab, kuid veel liiga lühikest aega (nt foori taga). */
    STOPPED,
    /** Auto on seisnud vähemalt seadetes määratud aja. */
    PARKED,
}

/** Telefoni seadetes valitav testrežiim. */
enum class SimulatedMotion { OFF, MOVING, STATIONARY }

/** Otsus, millist kiirust arvestada; eraldi, et seda saaks ilma autota testida. */
object MotionRules {
    /** ~1 km/h – sellest kiiremini liikuv auto ei ole pargitud. */
    const val STATIONARY_SPEED_MPS = 0.3f

    /**
     * @param realSpeed auto tegelik kiirus (m/s) või null, kui see pole teada
     * @param speedAccess kas rakendusel on luba ja kiiruse kuulamine käib
     * @return arvestatav kiirus või null (= teadmata, videot ei näidata)
     */
    fun effectiveSpeed(simulation: SimulatedMotion, realSpeed: Float?, speedAccess: Boolean): Float? = when {
        // „Liigub“ on ohutu suund ja kehtib alati.
        simulation == SimulatedMotion.MOVING -> Float.MAX_VALUE
        // Päris kiirus on alati tähtsam kui „seisab“-test.
        realSpeed != null -> realSpeed
        // „Seisab“-test ainult siis, kui luba on olemas, aga auto kiirust ei anna (nt DHU emulaator).
        simulation == SimulatedMotion.STATIONARY && speedAccess -> 0f
        else -> null
    }

    fun isStationary(speed: Float): Boolean = kotlin.math.abs(speed) <= STATIONARY_SPEED_MPS
}

/**
 * Jälgib auto kiirust. Android Auto ei anna rakendustele käigu- ega
 * seisupiduri andmeid, seega loetakse auto pargituks alles siis, kui see on
 * katkematult seisnud [stationaryMillis] (loetakse seisma jäämise hetkel).
 * Vähimgi liikumine katkestab kohe.
 */
class ParkingMonitor(
    private val carContext: CarContext,
    private val stationaryMillis: () -> Long,
    private val onChange: (ParkingState) -> Unit,
) {
    var state = ParkingState.UNKNOWN
        private set

    /** Testrežiim telefoni seadetest; vt [MotionRules.effectiveSpeed]. */
    var simulation = SimulatedMotion.OFF
        set(value) {
            if (field == value) return
            field = value
            evaluate()
        }

    /** „Seisab“-test on sisse lülitatud, kuid auto annab päris kiiruse, seega seda eiratakse. */
    val simulationIgnored: Boolean
        get() = simulation == SimulatedMotion.STATIONARY && realSpeed != null

    private var realSpeed: Float? = null
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private val promoteToParked = Runnable { if (state == ParkingState.STOPPED) update(ParkingState.PARKED) }
    private val listener = OnCarDataAvailableListener<Speed> { onSpeed(it) }

    private val carInfo
        get() = carContext.getCarService(CarHardwareManager::class.java).carInfo

    fun start() {
        if (listening) return
        try {
            carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            listening = true
        } catch (e: SecurityException) {
            listening = false
        }
        evaluate()
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
        realSpeed = raw.value.takeIf { raw.status == CarValue.STATUS_SUCCESS }
        evaluate()
    }

    private fun evaluate() {
        val speed = MotionRules.effectiveSpeed(simulation, realSpeed, listening)
        when {
            speed == null -> {
                handler.removeCallbacks(promoteToParked)
                update(ParkingState.UNKNOWN)
            }
            !MotionRules.isStationary(speed) -> {
                handler.removeCallbacks(promoteToParked)
                update(ParkingState.MOVING)
            }
            state != ParkingState.STOPPED && state != ParkingState.PARKED -> {
                update(ParkingState.STOPPED)
                handler.postDelayed(promoteToParked, stationaryMillis())
            }
        }
    }

    private fun update(newState: ParkingState) {
        if (newState == state) return
        state = newState
        onChange(newState)
    }
}
