package com.eplik82.parkedvideo

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat

/**
 * Kust kiirus tuleb. GPS-i kiirus kõigub ka seisvas autos, seega on selle
 * „seisab“-piir veidi kõrgem kui auto anduril.
 */
enum class SpeedSourceType(val permissions: List<String>, val stationaryThresholdMps: Float) {
    /** Auto enda kiirusandur Android Auto kaudu. */
    CAR(listOf("com.google.android.gms.permission.CAR_SPEED"), 0.3f),

    /** Telefoni GPS; kasulik, kui auto kiirust ei edasta. */
    GPS(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"), 1.0f),
}

/** Annab kiiruse m/s või null, kui see pole (enam) teada. */
interface SpeedSource {
    /** @return false, kui luba puudub või allikat pole – siis andmeid ei tule. */
    fun start(): Boolean
    fun stop()
}

class CarSpeedSource(
    private val carContext: CarContext,
    private val onSpeed: (Float?) -> Unit,
) : SpeedSource {
    private val listener = OnCarDataAvailableListener<Speed> { speed ->
        val raw = speed.rawSpeedMetersPerSecond
        onSpeed(raw.value.takeIf { raw.status == CarValue.STATUS_SUCCESS })
    }

    private val carInfo
        get() = carContext.getCarService(CarHardwareManager::class.java).carInfo

    override fun start(): Boolean = try {
        carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
        true
    } catch (e: SecurityException) {
        false
    }

    override fun stop() = carInfo.removeSpeedListener(listener)
}

class GpsSpeedSource(
    private val carContext: CarContext,
    private val onSpeed: (Float?) -> Unit,
) : SpeedSource {
    private val locationManager = carContext.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var lastFixAt = 0L

    /** Kui uut asukohta pole piisavalt kaua tulnud (tunnel, parkimismaja), on kiirus teadmata. */
    private val staleCheck = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - lastFixAt > STALE_MILLIS) onSpeed(null)
            handler.postDelayed(this, CHECK_INTERVAL_MILLIS)
        }
    }

    // Kõik meetodid on üle kirjutatud, sest Android 8–10 peal pole need vaikimisi teostatud.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderDisabled(provider: String) = onSpeed(null)
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @SuppressLint("MissingPermission") // Luba kontrollitakse allpool.
    override fun start(): Boolean {
        if (carContext.checkSelfPermission(SpeedSourceType.GPS.permissions.first()) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper(),
            )
            lastFixAt = 0L
            handler.postDelayed(staleCheck, CHECK_INTERVAL_MILLIS)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false // Seadmel pole GPS-i.
        }
    }

    override fun stop() {
        handler.removeCallbacks(staleCheck)
        locationManager.removeUpdates(listener)
    }

    private fun onLocation(location: Location) {
        if (!location.hasSpeed()) return
        lastFixAt = SystemClock.elapsedRealtime()
        val tooInaccurate = location.hasSpeedAccuracy() &&
            location.speedAccuracyMetersPerSecond > MAX_SPEED_ACCURACY_MPS
        onSpeed(if (tooInaccurate) null else location.speed)
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val CHECK_INTERVAL_MILLIS = 1_000L
        const val STALE_MILLIS = 5_000L
        const val MAX_SPEED_ACCURACY_MPS = 2f
    }
}
