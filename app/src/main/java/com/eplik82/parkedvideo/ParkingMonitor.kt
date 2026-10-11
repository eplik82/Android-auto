package com.eplik82.parkedvideo

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext

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
    /**
     * @param realSpeed tegelik kiirus (m/s) või null, kui see pole teada
     * @param speedAccess kas rakendusel on luba ja kiiruse kuulamine käib
     * @return arvestatav kiirus või null (= teadmata, videot ei näidata)
     */
    fun effectiveSpeed(simulation: SimulatedMotion, realSpeed: Float?, speedAccess: Boolean): Float? = when {
        // „Liigub“ on ohutu suund ja kehtib alati.
        simulation == SimulatedMotion.MOVING -> Float.MAX_VALUE
        // Päris kiirus on alati tähtsam kui „seisab“-test.
        realSpeed != null -> realSpeed
        // „Seisab“-test ainult siis, kui luba on olemas, aga kiirust ei tule (nt DHU emulaator).
        simulation == SimulatedMotion.STATIONARY && speedAccess -> 0f
        else -> null
    }

    fun isStationary(speed: Float, thresholdMps: Float): Boolean = kotlin.math.abs(speed) <= thresholdMps
}

/**
 * Jälgib valitud allika ([SpeedSourceType]) kiirust. Android Auto ei anna
 * rakendustele käigu- ega seisupiduri andmeid, seega loetakse auto
 * pargituks alles siis, kui see on katkematult seisnud [stationaryMillis]
 * (loetakse seisma jäämise hetkel). Vähimgi liikumine katkestab kohe.
 *
 * [onStateChange] kutsutakse oleku muutumisel, [onSpeedUpdate] iga uue
 * kiiruse lugemi järel (ekraanil kuvamiseks).
 */
class ParkingMonitor(
    private val carContext: CarContext,
    private val stationaryMillis: () -> Long,
    private val onStateChange: () -> Unit,
    private val onSpeedUpdate: () -> Unit,
) {
    var state = ParkingState.UNKNOWN
        private set

    var sourceType = SpeedSourceType.CAR
        private set

    /** Kas valitud allikas töötab (luba olemas ja kuulamine käib). */
    var hasAccess = false
        private set

    /** Viimane tegelik kiirus m/s või null. */
    var realSpeed: Float? = null
        private set

    /** Testrežiim telefoni seadetest; vt [MotionRules.effectiveSpeed]. */
    var simulation = SimulatedMotion.OFF
        set(value) {
            if (field == value) return
            field = value
            evaluate()
        }

    /** „Seisab“-test on sisse lülitatud, kuid allikas annab päris kiiruse, seega seda eiratakse. */
    val simulationIgnored: Boolean
        get() = simulation == SimulatedMotion.STATIONARY && realSpeed != null

    private var source: SpeedSource? = null
    private val handler = Handler(Looper.getMainLooper())
    private val promoteToParked = Runnable { if (state == ParkingState.STOPPED) update(ParkingState.PARKED) }

    /** (Taas)käivitab jälgimise valitud allikaga; allika vahetus nõuab uuesti ooteaega. */
    fun start(type: SpeedSourceType) {
        stop()
        sourceType = type
        val newSource = when (type) {
            SpeedSourceType.CAR -> CarSpeedSource(carContext, ::onSpeed)
            SpeedSourceType.GPS -> GpsSpeedSource(carContext, ::onSpeed)
        }
        hasAccess = newSource.start()
        source = newSource.takeIf { hasAccess }
        evaluate()
    }

    fun stop() {
        source?.stop()
        source = null
        hasAccess = false
        realSpeed = null
        handler.removeCallbacks(promoteToParked)
    }

    private fun onSpeed(speed: Float?) {
        realSpeed = speed
        evaluate()
        onSpeedUpdate()
    }

    private fun evaluate() {
        val speed = MotionRules.effectiveSpeed(simulation, realSpeed, hasAccess)
        when {
            speed == null -> {
                handler.removeCallbacks(promoteToParked)
                update(ParkingState.UNKNOWN)
            }
            !MotionRules.isStationary(speed, sourceType.stationaryThresholdMps) -> {
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
        onStateChange()
    }
}
