package com.lcpatch

import org.junit.Assert.assertEquals
import org.junit.Test

class PuaConverterTest {
    private val converter = PuaConverter.parse(listOf(
        "U+4E00 一 -> U+E000 ",
        "U+4E01 丁 -> U+E001 "
    ))

    @Test fun convertsBothDirectionsWithoutTouchingOtherText() {
        val encoded = converter.convert("一丁 A", toPua = true)
        assertEquals("\uE000\uE001 A", encoded)
        assertEquals("一丁 A", converter.convert(encoded, toPua = false))
    }

    @Test fun restoresEscapedBmpPuaFromJson() {
        assertEquals("{\"text\":\"\uE000\"}", decodePuaUnicodeEscapes("{\"text\":\"\\uE000\"}"))
    }

    @Test fun supportsSupplementaryPlaneMappings() {
        val supplementary = PuaConverter.parse(listOf("U+20000 𠀀 -> U+F0000 󰀀"))
        assertEquals("\uDB80\uDC00", supplementary.convert("\uD840\uDC00", toPua = true))
        assertEquals("\uD840\uDC00", supplementary.convert("\uDB80\uDC00", toPua = false))
    }
}
