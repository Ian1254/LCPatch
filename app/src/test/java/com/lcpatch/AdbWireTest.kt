package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException

class AdbWireTest {
    @Test fun binaryPayloadStopsAtFrameBoundary() {
        val source = byteArrayOf(0, 13, 10, -1, 42).inputStream()
        assertArrayEquals(byteArrayOf(0, 13, 10, -1), AdbWire.LimitedInput(source, 4).readBytes())
        assertEquals(42, source.read())
    }
    @Test(expected = EOFException::class) fun truncatedTransferFails() {
        AdbWire.LimitedInput(byteArrayOf(1).inputStream(), 2).readBytes()
    }
    @Test(expected = IllegalArgumentException::class) fun excessivePayloadIsRejected() {
        AdbWire.LimitedInput(byteArrayOf().inputStream(), AdbWire.MAX_ARCHIVE + 1)
    }
    @Test fun errorsCannotInjectProtocolLines() {
        val error = "寫檔失敗\nLCPATCH OK\r\n"
        val encoded = AdbWire.encodeError(error)
        assertFalse(encoded.contains('\n'))
        assertEquals(error, AdbWire.decodeError(encoded))
    }
    @Test fun apkPathsAreShellQuoted() {
        assertEquals("'/data/app/a'\\''b/base.apk'", AdbWire.quote("/data/app/a'b/base.apk"))
    }
}
