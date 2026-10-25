package com.peli.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterVersionTest {
    @Test fun newerVersionsAreDetectedNumberByNumber() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("1.1.0", "1.0.9"))
        assertTrue(Updater.isNewer("2.0.0", "1.9.9"))
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("1.0.9", "1.1.0"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
    }

    @Test fun missingPartsCountAsZero() {
        assertTrue(Updater.isNewer("1.0.1", "1.0"))
    }

    @Test fun garbageIsNeverNewer() {
        assertFalse(Updater.isNewer("x", "1.0.0"))
        assertFalse(Updater.isNewer("", "1.0.0"))
    }
}
