package com.lcpatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

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
        val connection = HttpClient.open(endpoint, accept = "application/vnd.github+json")
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
        try {
            val result = downloadResumable(
                url = release.apkUrl,
                part = part,
                displayName = release.apkName,
                progress = progress
            )
            require(part.length() > 4 && part.inputStream().use {
                it.read() == 0x50 && it.read() == 0x4b
            }) { "下載內容不是有效 APK" }
            verifySha256(part, expectedSha256(release.sha256Url))
            if (output.exists()) output.delete()
            require(part.renameTo(output)) { "無法保存更新 APK" }
            progress(
                TransferProgress(
                    release.apkName,
                    output.length(),
                    result.total.takeIf { it > 0L } ?: output.length(),
                    0,
                    finished = true
                )
            )
            output
        } catch (error: Throwable) {
            if (part.length() <= 4L) part.delete()
            throw error
        }
    }

    private fun expectedSha256(url: String): String {
        val connection = HttpClient.open(url)
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
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) { "更新檔 SHA-256 驗證失敗" }
    }
}
