package com.lcpatch

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBackendPreferenceTest {
    @Test fun newAndLegacyShizukuPreferencesUseStellarWithoutChangingSaf() {
        assertEquals("stellar",normalizeNativeBackend(null))
        assertEquals("stellar",normalizeNativeBackend("shizuku"))
        assertEquals("stellar",normalizeNativeBackend("stellar"))
        assertEquals("saf",normalizeNativeBackend("saf"))
    }
}
