package com.lcpatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String
)

class AppUpdateRepository(private val context: Context) {
    companion object {
        private const val LATEST_RELEASE =
            "https://api.github.com/repos/Ian1254/LCPatch/releases/latest"
    }

    suspend fun latestRelease(includePrerelease: Boolean = false): AppRelease = withContext(Dispatchers.IO) {
        val endpoint = if (includePrerelease) {
            "https://api.github.com/repos/Ian1254/LCPatch/releases?per_page=20"
        } else LATEST_RELEASE
        val connection = open(endpoint)
        val json = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val release = if (includePrerelease) {
            val releases = org.json.JSONArray(json)
            (0 until releases.length()).asSequence()
                .map(releases::getJSONObject)
                .firstOrNull { !it.optBoolean("draft", false) }
                ?: error("GitHub Releases 沒有可用版本")
        } else JSONObject(json)
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).asSequence()
            .map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: error("最新版 Release 沒有 APK")
        AppRelease(
            version = release.getString("tag_name").removePrefix("v"),
            notes = release.optString("body").trim().take(1200),
            apkUrl = apk.getString("browser_download_url"),
            apkName = apk.getString("name")
        )
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val left = versionParts(candidate)
        val right = versionParts(current)
        repeat(maxOf(left.size, right.size)) { index ->
            val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    suspend fun download(
        release: AppRelease,
        progress: suspend (TransferProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(directory, release.apkName + ".part")
        val output = File(directory, release.apkName)
        val connection = open(release.apkUrl)
        val total = connection.contentLengthLong
        var bytes = 0L
        var lastBytes = 0L
        var lastTime = System.nanoTime()
        try {
            connection.inputStream.use { input ->
                part.outputStream().buffered().use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        bytes += count
                        val now = System.nanoTime()
                        if (now - lastTime >= 150_000_000L) {
                            val speed = ((bytes - lastBytes) * 1_000_000_000L /
                                (now - lastTime)).coerceAtLeast(0)
                            progress(TransferProgress(release.apkName, bytes, total, speed))
                            lastBytes = bytes
                            lastTime = now
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        require(part.length() > 4 && part.inputStream().use {
            it.read() == 0x50 && it.read() == 0x4b
        }) { "下載內容不是有效 APK" }
        if (output.exists()) output.delete()
        require(part.renameTo(output)) { "無法保存更新 APK" }
        progress(
            TransferProgress(
                release.apkName,
                bytes,
                total.takeIf { it > 0 } ?: bytes,
                0,
                finished = true
            )
        )
        output
    }

    private fun open(value: String): HttpURLConnection =
        (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LCPatch/" + BuildConfig.VERSION_NAME)
            require(responseCode in 200..299) { "GitHub 回應 $responseCode" }
        }

    private fun versionParts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { part ->
            part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }
}
