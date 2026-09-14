package com.lcpatch

import com.github.houbb.opencc4j.util.ZhConverterUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCc4jConversionTest {
    @Test fun convertsCommonCharactersToTraditional() {
        assertEquals("漢語龍馬後發", ZhConverterUtil.toTraditional("汉语龙马后发"))
    }

    @Test fun convertsCommonCharactersToSimplified() {
        assertEquals("汉语龙马后发", ZhConverterUtil.toSimple("漢語龍馬後發"))
    }

    @Test fun convertsTextAfterRestoringPua() {
        val pua = PuaConverter.parse(listOf("U+6C49 汉 -> U+E000 "))
        val readable = pua.convert("\uE000语", toPua = false)
        assertEquals("漢語", ZhConverterUtil.toTraditional(readable))
    }

    @Test fun mapsTraditionalCharacterThroughSimplifiedPuaSlot() {
        val pua = PuaConverter.parse(listOf("U+6C49 汉 -> U+E000 "))
        assertEquals("\uE000", pua.convert("漢", toPua = true))
    }
}
