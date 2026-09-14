package com.lcpatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val sha256Url: String
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
            val releases = JSONArray(json)
            (0 until releases.length()).asSequence()
                .map(releases::getJSONObject)
                .filter { !it.optBoolean("draft", false) }
                .maxWithOrNull { a, b -> Versioning.compare(
                    a.optString("tag_name").removePrefix("v"),
                    b.optString("tag_name").removePrefix("v")
                ) }
                ?: error("GitHub Releases 沒有可用版本")
        } else JSONObject(json)

        val version = release.getString("tag_name").removePrefix("v")
        val expectedApk = "LCPatch-$version.apk"
        val expectedSha = "$expectedApk.sha256"
        val assets = release.getJSONArray("assets")
        val byName = (0 until assets.length()).asSequence()
            .map(assets::getJSONObject)
            .associateBy { it.optString("name") }
        val apk = byName[expectedApk] ?: error("Release 缺少預期 APK：$expectedApk")
        val sha = byName[expectedSha] ?: error("Release 缺少 SHA-256：$expectedSha")

        AppRelease(
            version = version,
            notes = release.optString("body").trim().take(1200),
            apkUrl = apk.getString("browser_download_url"),
            apkName = expectedApk,
            sha256Url = sha.getString("browser_download_url")
        )
    }

    fun isNewer(candidate: String, current: String): Boolean = Versioning.compare(candidate, current) > 0

    suspend fun download(
        release: AppRelease,
        progress: suspend (TransferProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(directory, release.apkName + ".part")
        val output = File(directory, release.apkName)
        var completed = false
        try {
            var existing = part.takeIf { it.exists() }?.length() ?: 0L
            var connection = open(release.apkUrl, existing.takeIf { it > 0 })
            val resumed = existing > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (existing > 0 && !resumed) {
                connection.disconnect()
                part.delete()
                existing = 0L
                connection = open(release.apkUrl)
            }
            val contentLength = connection.contentLengthLong
            val total = if (resumed && contentLength > 0) existing + contentLength else contentLength
            var bytes = existing
            var lastBytes = bytes
            var lastTime = System.nanoTime()
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(part, resumed).buffered().use { target ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
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
            verifySha256(part, expectedSha256(release.sha256Url))
            if (output.exists()) output.delete()
            require(part.renameTo(output)) { "無法保存更新 APK" }
            completed = true
            progress(
                TransferProgress(
                    release.apkName,
                    output.length(),
                    total.takeIf { it > 0 } ?: output.length(),
                    0,
                    finished = true
                )
            )
            output
        } finally {
            if (!completed) part.delete()
        }
    }

    private fun expectedSha256(url: String): String {
        val connection = open(url)
        return try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            Regex("^[A-Fa-f0-9]{64}").find(text)?.value?.lowercase()
                ?: error("SHA-256 檔案格式無效")
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) { "更新檔 SHA-256 驗證失敗" }
    }

    private fun open(value: String, rangeStart: Long? = null): HttpURLConnection =
        (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LCPatch/" + BuildConfig.VERSION_NAME)
            if (rangeStart != null) setRequestProperty("Range", "bytes=$rangeStart-")
            val code = responseCode
            require(code in 200..299) { "GitHub 回應 $code" }
        }
}
