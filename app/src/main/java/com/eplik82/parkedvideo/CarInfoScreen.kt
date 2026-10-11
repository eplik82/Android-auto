package com.eplik82.parkedvideo

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

/**
 * Diagnostika: näitab kõiki andmeid, mida auto Android Auto kaudu edastab.
 * Iga väärtus on kas päris lugem, „pole saadaval“ (auto ei saada),
 * „auto ei toeta“, „luba puudub“ või „ootan…“ (andmeid pole veel tulnud).
 */
class CarInfoScreen(carContext: CarContext) : Screen(carContext) {

    private val hardware = carContext.getCarService(CarHardwareManager::class.java)
    private val executor = ContextCompat.getMainExecutor(carContext)
    private val handler = Handler(Looper.getMainLooper())
    private var refreshPending = false

    private val waiting = carContext.getString(R.string.info_waiting)
    private val noPermission = carContext.getString(R.string.info_no_permission)

    private var model = waiting
    private var fuelTypes = waiting
    private var connectors = waiting
    private var speedRaw = waiting
    private var speedDisplay = waiting
    private var fuel = waiting
    private var battery = waiting
    private var range = waiting
    private var energyLow = waiting
    private var odometer = waiting
    private var tollCard = waiting
    private var compass = waiting
    private var accelerometer = waiting
    private var gyroscope = waiting
    private var location = waiting

    private val speedListener = OnCarDataAvailableListener<Speed> {
        speedRaw = it.rawSpeedMetersPerSecond.show(::kmh)
        speedDisplay = it.displaySpeedMetersPerSecond.show(::kmh)
        scheduleRefresh()
    }
    private val energyListener = OnCarDataAvailableListener<EnergyLevel> {
        fuel = it.fuelPercent.show(::percent)
        battery = it.batteryPercent.show(::percent)
        range = it.rangeRemainingMeters.show(::km)
        energyLow = it.energyIsLow.show(::yesNo)
        scheduleRefresh()
    }
    private val mileageListener = OnCarDataAvailableListener<Mileage> {
        odometer = it.odometerMeters.show(::km)
        scheduleRefresh()
    }
    private val tollListener = OnCarDataAvailableListener<TollCard> {
        tollCard = it.cardState.show(::tollState)
        scheduleRefresh()
    }
    private val compassListener = OnCarDataAvailableListener<Compass> {
        compass = it.orientations.show { o -> o.firstOrNull()?.let { a -> "%.0f°".format(Locale.US, a) } ?: "–" }
        scheduleRefresh()
    }
    private val accelerometerListener = OnCarDataAvailableListener<Accelerometer> {
        accelerometer = it.forces.show(::vector)
        scheduleRefresh()
    }
    private val gyroscopeListener = OnCarDataAvailableListener<Gyroscope> {
        gyroscope = it.rotations.show(::vector)
        scheduleRefresh()
    }
    private val locationListener = OnCarDataAvailableListener<CarHardwareLocation> {
        location = it.location.show { l -> "%.5f, %.5f".format(Locale.US, l.latitude, l.longitude) }
        scheduleRefresh()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = requestPermissionsThenStart()
            override fun onDestroy(owner: LifecycleOwner) = stopListening()
        })
    }

    override fun onGetTemplate(): Template {
        val night = carContext.getString(if (carContext.isDarkMode) R.string.info_yes else R.string.info_no)
        val rows = listOf(
            row(R.string.info_vehicle, model, carContext.getString(R.string.info_vehicle_detail, fuelTypes, connectors)),
            row(R.string.info_speed, carContext.getString(R.string.info_speed_detail, speedRaw, speedDisplay)),
            row(
                R.string.info_energy,
                carContext.getString(R.string.info_energy_detail, fuel, battery),
                carContext.getString(R.string.info_range_detail, range, energyLow),
            ),
            row(R.string.info_odometer, odometer, carContext.getString(R.string.info_toll_detail, tollCard)),
            row(
                R.string.info_sensors,
                carContext.getString(R.string.info_sensors_detail, compass, accelerometer),
                carContext.getString(R.string.info_gyro_detail, gyroscope),
            ),
            row(
                R.string.info_system,
                carContext.getString(R.string.info_location_detail, location),
                carContext.getString(R.string.info_system_detail, night, carContext.carAppApiLevel),
            ),
        )
        val list = ItemList.Builder().apply { rows.forEach { addItem(it) } }.build()
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.info_title))
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }

    private fun requestPermissionsThenStart() {
        val missing = PERMISSIONS.filter { carContext.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            startListening()
        } else {
            carContext.requestPermissions(missing) { _, _ -> startListening() }
        }
    }

    private fun startListening() {
        val info = hardware.carInfo
        val sensors = hardware.carSensors
        val rate = CarSensors.UPDATE_RATE_NORMAL

        guarded({ model = it; fuelTypes = it; connectors = it }) {
            info.fetchModel(executor) { m: Model ->
                model = listOf(m.manufacturer.show { it }, m.name.show { it }, m.year.show { it.toString() })
                    .joinToString(" ")
                scheduleRefresh()
            }
            info.fetchEnergyProfile(executor) { p: EnergyProfile ->
                fuelTypes = p.fuelTypes.show { types -> types.joinToString(", ", transform = ::fuelTypeName) }
                connectors = p.evConnectorTypes.show { types ->
                    if (types.isEmpty()) "–" else types.joinToString(", ", transform = ::connectorName)
                }
                scheduleRefresh()
            }
        }
        guarded({ speedRaw = it; speedDisplay = it }) { info.addSpeedListener(executor, speedListener) }
        guarded({ fuel = it; battery = it; range = it; energyLow = it }) {
            info.addEnergyLevelListener(executor, energyListener)
        }
        guarded({ odometer = it }) { info.addMileageListener(executor, mileageListener) }
        guarded({ tollCard = it }) { info.addTollListener(executor, tollListener) }
        guarded({ compass = it }) { sensors.addCompassListener(rate, executor, compassListener) }
        guarded({ accelerometer = it }) { sensors.addAccelerometerListener(rate, executor, accelerometerListener) }
        guarded({ gyroscope = it }) { sensors.addGyroscopeListener(rate, executor, gyroscopeListener) }
        guarded({ location = it }) { sensors.addCarHardwareLocationListener(rate, executor, locationListener) }
        scheduleRefresh()
    }

    private fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        val info = hardware.carInfo
        val sensors = hardware.carSensors
        runCatching { info.removeSpeedListener(speedListener) }
        runCatching { info.removeEnergyLevelListener(energyListener) }
        runCatching { info.removeMileageListener(mileageListener) }
        runCatching { info.removeTollListener(tollListener) }
        runCatching { sensors.removeCompassListener(compassListener) }
        runCatching { sensors.removeAccelerometerListener(accelerometerListener) }
        runCatching { sensors.removeGyroscopeListener(gyroscopeListener) }
        runCatching { sensors.removeCarHardwareLocationListener(locationListener) }
    }

    /** Käivitab päringu; kui luba puudub või auto keeldub, märgitakse see väljadele. */
    private inline fun guarded(onFailure: (String) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            onFailure(noPermission)
        } catch (e: RuntimeException) {
            onFailure(carContext.getString(R.string.info_error))
        }
    }

    /** Andurid annavad palju lugemeid; ekraani uuendatakse kuni kord sekundis. */
    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        handler.postDelayed({
            refreshPending = false
            invalidate()
        }, REFRESH_MILLIS)
    }

    private fun row(title: Int, vararg lines: String): Row =
        Row.Builder().setTitle(carContext.getString(title)).apply { lines.forEach { addText(it) } }.build()

    private fun <T> CarValue<T>.show(format: (T) -> String): String = when (status) {
        CarValue.STATUS_SUCCESS -> value?.let(format) ?: carContext.getString(R.string.info_unknown)
        CarValue.STATUS_UNAVAILABLE -> carContext.getString(R.string.info_unavailable)
        CarValue.STATUS_UNIMPLEMENTED -> carContext.getString(R.string.info_unsupported)
        else -> carContext.getString(R.string.info_unknown)
    }

    private fun kmh(mps: Float) = carContext.getString(R.string.car_speed_kmh, Math.round(mps * 3.6f))
    private fun km(meters: Float) = "%,d km".format(Locale.US, Math.round(meters / 1000f)).replace(',', ' ')
    private fun percent(value: Float) = "%.0f %%".format(Locale.US, value)
    private fun yesNo(value: Boolean) = carContext.getString(if (value) R.string.info_yes else R.string.info_no)
    private fun vector(values: List<Float>) = values.joinToString(" / ") { "%.1f".format(Locale.US, it) }

    private fun tollState(state: Int) = carContext.getString(
        when (state) {
            TollCard.TOLLCARD_STATE_VALID -> R.string.info_toll_valid
            TollCard.TOLLCARD_STATE_INVALID -> R.string.info_toll_invalid
            TollCard.TOLLCARD_STATE_NOT_INSERTED -> R.string.info_toll_none
            else -> R.string.info_unknown
        }
    )

    private fun fuelTypeName(type: Int): String = when (type) {
        EnergyProfile.FUEL_TYPE_UNLEADED -> "bensiin"
        EnergyProfile.FUEL_TYPE_LEADED -> "pliibensiin"
        EnergyProfile.FUEL_TYPE_DIESEL_1, EnergyProfile.FUEL_TYPE_DIESEL_2 -> "diisel"
        EnergyProfile.FUEL_TYPE_BIODIESEL -> "biodiisel"
        EnergyProfile.FUEL_TYPE_E85 -> "E85"
        EnergyProfile.FUEL_TYPE_LPG -> "LPG"
        EnergyProfile.FUEL_TYPE_CNG -> "CNG"
        EnergyProfile.FUEL_TYPE_LNG -> "LNG"
        EnergyProfile.FUEL_TYPE_ELECTRIC -> "elekter"
        EnergyProfile.FUEL_TYPE_HYDROGEN -> "vesinik"
        else -> "tüüp $type"
    }

    private fun connectorName(type: Int): String = when (type) {
        EnergyProfile.EVCONNECTOR_TYPE_J1772 -> "Type 1"
        EnergyProfile.EVCONNECTOR_TYPE_MENNEKES -> "Type 2"
        EnergyProfile.EVCONNECTOR_TYPE_CHADEMO -> "CHAdeMO"
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_1 -> "CCS1"
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_2 -> "CCS2"
        else -> "tüüp $type"
    }

    private companion object {
        const val REFRESH_MILLIS = 1_000L
        val PERMISSIONS = listOf(
            "com.google.android.gms.permission.CAR_SPEED",
            "com.google.android.gms.permission.CAR_FUEL",
            "com.google.android.gms.permission.CAR_MILEAGE",
        )
    }
}
