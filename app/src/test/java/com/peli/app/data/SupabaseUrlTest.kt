package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseUrlTest {
    private val expected = "https://abc.supabase.co"

    @Test fun keepsCleanUrl() = assertEquals(expected, SupabaseUrl.normalize("https://abc.supabase.co"))
    @Test fun dropsTrailingSlash() = assertEquals(expected, SupabaseUrl.normalize("https://abc.supabase.co/"))
    @Test fun dropsDataApiPath() = assertEquals(expected, SupabaseUrl.normalize("https://abc.supabase.co/rest/v1/"))
    @Test fun dropsAnyPathAndSpaces() = assertEquals(expected, SupabaseUrl.normalize("  https://abc.supabase.co/auth/v1/token?x=1 \n"))
}
