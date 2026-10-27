package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {
    @Test fun usernameRules() {
        assertTrue(Usernames.isValid("richi_99"))
        assertTrue(Usernames.isValid("  ana  "))
        assertFalse(Usernames.isValid("ab"))
        assertFalse(Usernames.isValid("a".repeat(21)))
        assertFalse(Usernames.isValid("con espacio"))
        assertFalse(Usernames.isValid("ñandú"))
        assertNull(Usernames.explain(""))
        assertNull(Usernames.explain("richi_99"))
        assertNotNull(Usernames.explain("ab"))
        assertNotNull(Usernames.explain("con espacio"))
    }

    @Test fun friendCodeIsGroupedInFours() {
        assertEquals("ABCD-EFGH", FriendCodes.format("ABCDEFGH"))
    }

    @Test fun parsesFriendRequestResults() {
        assertEquals(FriendRequestResult.Sent("ana"), FriendRequestResult.parse("sent:ana"))
        assertEquals(FriendRequestResult.Accepted("ana"), FriendRequestResult.parse("accepted:ana"))
        assertEquals(FriendRequestResult.AlreadyExists("ana"), FriendRequestResult.parse("exists:ana"))
        assertEquals(FriendRequestResult.NotFound, FriendRequestResult.parse("not_found"))
        assertEquals(FriendRequestResult.Self, FriendRequestResult.parse("self"))
        assertNull(FriendRequestResult.parse("???"))
    }

    @Test fun sessionExpiresSoonWithOneMinuteMargin() {
        val s = Session("a", "r", expiresAt = 1_000, userId = "u", email = null)
        assertTrue(s.expiresSoon(nowSec = 941))
        assertTrue(s.expiresSoon(nowSec = 2_000))
        assertFalse(s.expiresSoon(nowSec = 900))
    }

    @Test fun userEntryRoundTripKeepsMarks() {
        val e = com.peli.app.data.Entry(
            movieId = 7, owner = OWNER_ME, title = "T", overview = "o", posterPath = "/p.jpg", year = "2020",
            liked = true, seen = true, watchlist = false, genres = "1,2", rating = 7.5, updatedAt = 99, myRating = 9,
        )
        val back = e.toUserEntry("u1").toEntry()
        assertEquals(OWNER_PARTNER, back.owner)
        assertEquals(e.copy(owner = OWNER_PARTNER), back)
    }
}
