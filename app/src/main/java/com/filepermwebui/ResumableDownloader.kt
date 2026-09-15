package com.lcpatch

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.coroutineContext

internal data class DownloadResult(
    val bytes: Long,
    val total: Long
)

internal suspend fun downloadResumable(
    url: String,
    part: File,
    displayName: String,
    progress: suspend (TransferProgress) -> Unit,
    accept: String? = null
): DownloadResult {
    var existing = part.takeIf { it.exists() }?.length() ?: 0L
    var connection = HttpClient.open(url, existing.takeIf { it > 0L }, accept = accept)
    var resumed = existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
    if (existing > 0L && !resumed) {
        connection.disconnect()
        part.delete()
        existing = 0L
        connection = HttpClient.open(url, accept = accept)
        resumed = false
    }

    val contentLength = connection.contentLengthLong
    val total = if (resumed && contentLength > 0L) existing + contentLength else contentLength
    var bytes = existing
    var lastBytes = bytes
    var lastTime = System.nanoTime()
    try {
        connection.inputStream.use { input ->
            FileOutputStream(part, resumed).buffered().use { target ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    target.write(buffer, 0, count)
                    bytes += count
                    val now = System.nanoTime()
                    if (now - lastTime >= 500_000_000L) {
                        val speed = ((bytes - lastBytes) * 1_000_000_000L /
                            (now - lastTime)).coerceAtLeast(0L)
                        progress(TransferProgress(displayName, bytes, total, speed))
                        lastBytes = bytes
                        lastTime = now
                    }
                }
            }
        }
    } finally {
        connection.disconnect()
    }
    return DownloadResult(bytes, total)
}
