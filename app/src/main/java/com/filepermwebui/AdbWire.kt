package com.lcpatch

import java.io.InputStream
import java.io.EOFException
import java.util.Base64

internal object AdbWire {
    const val MAX_ARCHIVE = 512L * 1024 * 1024
    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    fun encodeError(value: String): String = Base64.getEncoder().encodeToString(value.take(2000).toByteArray(Charsets.UTF_8))
    fun decodeError(value: String): String = String(Base64.getDecoder().decode(value), Charsets.UTF_8)

    class LimitedInput(private val input: InputStream, private var remaining: Long) : InputStream() {
        init { require(remaining in 1..MAX_ARCHIVE) }
        override fun read(): Int {
            if (remaining == 0L) return -1
            val value = input.read()
            if (value < 0) throw EOFException("ADB 傳輸未完成")
            remaining--
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining == 0L) return -1
            val count = input.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count < 0) throw EOFException("ADB 傳輸未完成")
            remaining -= count
            return count
        }
    }
}
