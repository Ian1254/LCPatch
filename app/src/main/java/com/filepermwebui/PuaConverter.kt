package com.lcpatch

import android.content.Context
import com.github.houbb.opencc4j.util.ZhConverterUtil

internal class PuaConverter private constructor(
    private val forward: Map<Int, Int>,
    private val reverse: Map<Int, Int>
) {
    fun convert(text: String, toPua: Boolean): String {
        val table = if (toPua) forward else reverse
        return buildString(text.length) {
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val mapped = table[codePoint] ?: if (toPua) traditionalAlias(codePoint) else null
                appendCodePoint(mapped ?: codePoint)
                offset += Character.charCount(codePoint)
            }
        }
    }

    private fun traditionalAlias(codePoint: Int): Int? {
        if (codePoint !in 0x3400..0x9FFF) return null
        val simplified = ZhConverterUtil.toSimple(String(Character.toChars(codePoint)))
        if (simplified.codePointCount(0, simplified.length) != 1) return null
        return forward[simplified.codePointAt(0)]
    }

    companion object {
        fun fromAssets(context: Context): PuaConverter = context.assets
            .open("cjk_pua_global_map.txt")
            .bufferedReader(Charsets.UTF_8)
            .use { parse(it.readLines()) }

        internal fun parse(lines: List<String>): PuaConverter {
            val forward = LinkedHashMap<Int, Int>()
            lines.forEach { line ->
                val match = Regex("^U\\+([0-9A-Fa-f]{4,6})\\s+.+?\\s+->\\s+U\\+([0-9A-Fa-f]{4,6})").find(line) ?: return@forEach
                val source = match.groupValues[1].toInt(16)
                val target = match.groupValues[2].toInt(16)
                if (Character.isValidCodePoint(source) && Character.isValidCodePoint(target)) {
                    forward[source] = target
                }
            }
            require(forward.isNotEmpty()) { "PUA 映射表無法讀取" }
            return PuaConverter(forward, forward.entries.associate { (source, target) -> target to source })
        }
    }
}
