package com.eplik82.parkedvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsInputTest {
    @Test
    fun acceptsStationarySecondsWithinRange() {
        assertEquals(1, SettingsInput.parseStationarySeconds("1"))
        assertEquals(30, SettingsInput.parseStationarySeconds("30"))
        assertEquals(90, SettingsInput.parseStationarySeconds(" 90 "))
        assertEquals(600, SettingsInput.parseStationarySeconds("600"))
    }

    @Test
    fun rejectsStationarySecondsOutsideRange() {
        assertNull(SettingsInput.parseStationarySeconds(""))
        assertNull(SettingsInput.parseStationarySeconds("0"))
        assertNull(SettingsInput.parseStationarySeconds("601"))
        assertNull(SettingsInput.parseStationarySeconds("-5"))
        assertNull(SettingsInput.parseStationarySeconds("abc"))
    }

    @Test
    fun normalizesWebAddresses() {
        assertEquals("https://www.google.com", SettingsInput.normalizeUrl("www.google.com"))
        assertEquals("https://err.ee/uudised", SettingsInput.normalizeUrl(" err.ee/uudised "))
        assertEquals("http://example.com", SettingsInput.normalizeUrl("http://example.com"))
        assertEquals("HTTPS://Example.com", SettingsInput.normalizeUrl("HTTPS://Example.com"))
    }

    @Test
    fun addressOrSearch() {
        assertEquals("https://err.ee", SettingsInput.addressOrSearch("err.ee"))
        assertEquals("https://www.google.com/search?q=ilm+tallinn", SettingsInput.addressOrSearch(" ilm tallinn "))
        assertEquals("https://www.google.com/search?q=%C3%B5un", SettingsInput.addressOrSearch("õun"))
    }

    @Test
    fun rejectsNonWebAddresses() {
        assertNull(SettingsInput.normalizeUrl(""))
        assertNull(SettingsInput.normalizeUrl("google"))
        assertNull(SettingsInput.normalizeUrl("tere maailm.ee"))
        assertNull(SettingsInput.normalizeUrl("javascript://alert(1).com"))
        assertNull(SettingsInput.normalizeUrl("file:///sdcard/a.html"))
        assertNull(SettingsInput.normalizeUrl("intent://scan.com/#Intent;end"))
    }
}
