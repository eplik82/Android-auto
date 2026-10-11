package com.eplik82.parkedvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionRulesTest {
    @Test
    fun withoutSimulationRealSpeedIsUsed() {
        assertEquals(12f, MotionRules.effectiveSpeed(SimulatedMotion.OFF, 12f, speedAccess = true))
        assertNull(MotionRules.effectiveSpeed(SimulatedMotion.OFF, null, speedAccess = true))
    }

    @Test
    fun simulatedMovingAlwaysWins() {
        val parkedCar = MotionRules.effectiveSpeed(SimulatedMotion.MOVING, 0f, speedAccess = true)!!
        assertFalse(MotionRules.isStationary(parkedCar))
        val noData = MotionRules.effectiveSpeed(SimulatedMotion.MOVING, null, speedAccess = false)!!
        assertFalse(MotionRules.isStationary(noData))
    }

    @Test
    fun simulatedStationaryNeverOverridesRealSpeed() {
        assertEquals(15f, MotionRules.effectiveSpeed(SimulatedMotion.STATIONARY, 15f, speedAccess = true))
    }

    @Test
    fun simulatedStationaryOnlyWithSpeedAccessAndNoRealData() {
        assertEquals(0f, MotionRules.effectiveSpeed(SimulatedMotion.STATIONARY, null, speedAccess = true))
        // Kiiruse loa keelamine ei tohi testrežiimi kaudu videot avada.
        assertNull(MotionRules.effectiveSpeed(SimulatedMotion.STATIONARY, null, speedAccess = false))
    }

    @Test
    fun stationaryThreshold() {
        assertTrue(MotionRules.isStationary(0f))
        assertTrue(MotionRules.isStationary(0.3f))
        assertFalse(MotionRules.isStationary(0.31f))
        assertFalse(MotionRules.isStationary(-1f))
    }
}
