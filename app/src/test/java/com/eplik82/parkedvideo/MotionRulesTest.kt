package com.eplik82.parkedvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionRulesTest {
    @Test
    fun withoutSimulationRealSpeedIsUsed() {
        assertEquals(12f, MotionRules.effectiveSpeed(SimulatedMotion.OFF, 12f))
        assertNull(MotionRules.effectiveSpeed(SimulatedMotion.OFF, null))
    }

    @Test
    fun simulatedMovingAlwaysWins() {
        val parkedCar = MotionRules.effectiveSpeed(SimulatedMotion.MOVING, 0f)!!
        assertFalse(MotionRules.isStationary(parkedCar, 0.3f))
        val noData = MotionRules.effectiveSpeed(SimulatedMotion.MOVING, null)!!
        assertFalse(MotionRules.isStationary(noData, 1.0f))
    }

    @Test
    fun simulatedStationaryIgnoresRealSpeed() {
        // Emulaator (DHU) annab ise kiiruse – test peab seda eirama.
        assertEquals(0f, MotionRules.effectiveSpeed(SimulatedMotion.STATIONARY, 15f))
        assertEquals(0f, MotionRules.effectiveSpeed(SimulatedMotion.STATIONARY, null))
    }

    @Test
    fun stationaryThreshold() {
        assertTrue(MotionRules.isStationary(0f, 0.3f))
        assertTrue(MotionRules.isStationary(0.3f, 0.3f))
        assertFalse(MotionRules.isStationary(0.31f, 0.3f))
        assertFalse(MotionRules.isStationary(-1f, 0.3f))
        // GPS-i lubatud kõikumine.
        assertTrue(MotionRules.isStationary(0.8f, 1.0f))
        assertFalse(MotionRules.isStationary(1.2f, 1.0f))
    }
}
