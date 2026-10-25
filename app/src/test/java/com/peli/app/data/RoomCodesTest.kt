package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCodesTest {
    @Test fun newCodesHaveTenUnambiguousCharacters() {
        repeat(200) {
            assertTrue(RoomCodes.newCode().matches(Regex("[A-HJ-NP-Z2-9]{10}")))
        }
    }

    @Test fun newCodesAreUnique() {
        assertEquals(500, List(500) { RoomCodes.newCode() }.toSet().size)
    }

    @Test fun normalizeAcceptsLowercaseDashesAndSpaces() {
        assertEquals("ABCDEFGHJK", RoomCodes.normalize("abcde-fghjk"))
        assertEquals("ABCDEFGHJK", RoomCodes.normalize("  ABCDE FGHJK  "))
        assertNotNull(RoomCodes.normalize(RoomCodes.format(RoomCodes.newCode())))
    }

    @Test fun normalizeRejectsWrongLengths() {
        assertNull(RoomCodes.normalize(""))
        assertNull(RoomCodes.normalize("ABCDE-FGHJ"))
        assertNull(RoomCodes.normalize("ABCDEFGHJKL"))
    }

    @Test fun formatSplitsInTwoGroups() {
        assertEquals("ABCDE-FGHJK", RoomCodes.format("ABCDEFGHJK"))
    }
}
