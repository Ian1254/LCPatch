package com.lcpatch

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

internal object OurPlayTranslation {
    private const val ENDPOINT = "https://gapi.ourplay.com.cn/depends/zhapk"
    private const val GAME = "com.ProjectMoon.LimbusCompany"
    private const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    private const val MAX_TEXT_CHARS = 2 * 1024 * 1024
    private val hashName = Regex("[a-fA-F0-9]{32}")

    val entry = TranslationEntry(
        section = "漢化-XP",
        name = "OurPlay 神人漢化",
        author = "OurPlay 社群版",
        description = "下載目前的社群漢化，使用零協會文字檔對應遊戲資源。",
        url = ENDPOINT,
        script = "簡體"
    )

    data class Asset(val version: String, val url: String, val bytes: Long, val md5: String) {
        fun verify(file: File) {
            require(file.length() == bytes) { "OurPlay 漢化包大小不符" }
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == md5) { "OurPlay 漢化包校驗失敗" }
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry("manifest.json")?.let { zip.readDocument(it) }
                require(manifest?.optString("pkg") == GAME && manifest.optString("verCode") == version) {
                    "OurPlay 漢化包版本或遊戲識別不符"
                }
            }
        }
    }

    fun latest(): Asset {
        // This profile contains only generic platform and application versions.
        val profile = """{"os":"Android","mainver":306800,"productId":6000,"release":"9","abi":"arm64-v8a","pkg":"com.excean.gspace","vc":11020,"apiPublicFlag":110,"vn":"8.5.9","packageName":"com.excean.gspace","api":28,"isHOS":0,"compver":127200,"language":"zh","country":"cn"}"""
        val form = "pkg=$GAME&language_type=chinese&language_ver=0&split=1&is_unofficial=3&ver=406"
        val connection = (URI(ENDPOINT).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "okhttp/3.12.13")
            setRequestProperty("device-user", android.util.Base64.encodeToString(profile.toByteArray(), android.util.Base64.NO_WRAP))
        }
        try {
            connection.outputStream.use { it.write(form.toByteArray()) }
            require(connection.responseCode == 200) { "OurPlay 回應 ${connection.responseCode}" }
            return parseAsset(connection.inputStream.bufferedReader().use { it.readBounded(16_384) })
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseAsset(text: String): Asset {
        val response = JSONObject(text)
        require(response.optInt("code") == 1) { "OurPlay 未提供社群漢化包" }
        val data = response.getJSONObject("data")
        val url = data.getString("url")
        val version = data.optLong("versionCode").also { require(it > 0) { "OurPlay 漢化包缺少版本" } }
        val digest = data.optString("md5").also { require(hashName.matches(it)) { "OurPlay 漢化包缺少校驗值" } }
        val bytes = data.optLong("size").also { require(it in 1..MAX_ARCHIVE_BYTES) { "OurPlay 漢化包大小無效" } }
        require(isPackageUrl(url)) { "OurPlay 漢化包網址無效" }
        return Asset(version.toString(), url, bytes, digest.lowercase())
    }

    fun isPackageUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "cdnvc.excean.com" && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.path.startsWith("/xspace/news/")
    }.getOrDefault(false)

    private data class RowKey(val id: String, val fields: Set<String>)
    private data class Bundle(val entry: ZipEntry, val rowCount: Int)

    /** Replace matching rows and keep the reference files and rows the community pack does not cover. */
    suspend fun overlay(archive: File, reference: File, progress: suspend (ApplyProgress) -> Unit): Int {
        val files = reference.walkTopDown().filter { it.isFile && it.extension.equals("json", true) }.toList()
        require(files.isNotEmpty()) { "找不到可對應的零協會文字檔" }
        return ZipFile(archive).use { zip ->
            require(zip.size() <= 10_000) { "OurPlay 漢化包檔案數過多" }
            val index = HashMap<RowKey, MutableList<Bundle>>()
            var unpackedBytes = 0L
            zip.entries().asSequence().filter { entry ->
                val path = entry.name.replace('\\', '/').split('/')
                !entry.isDirectory && path.size == 2 && path[0] == GAME && hashName.matches(path[1])
            }.forEach { entry ->
                checkActive()
                unpackedBytes += entry.size.coerceAtLeast(0)
                require(unpackedBytes <= 128L * 1024 * 1024) { "OurPlay 漢化包解壓後過大" }
                val document = zip.readDocument(entry) ?: return@forEach
                val rows = document.optJSONArray("dataList")?.keyedRows().orEmpty()
                if (rows.isEmpty()) return@forEach
                val bundle = Bundle(entry, rows.size)
                rows.keys.forEach { index.getOrPut(it, ::mutableListOf).add(bundle) }
            }
            var changed = 0
            files.forEachIndexed { position, file ->
                checkActive()
                if (mergeFile(zip, index, file)) changed++
                if (position % 40 == 0 || position == files.lastIndex) {
                    progress(ApplyProgress("正在對應 OurPlay 文字檔", position + 1, files.size))
                }
            }
            require(changed > 0) { "OurPlay 社群漢化無法對應到目前的零協會版本" }
            changed
        }
    }

    private fun mergeFile(zip: ZipFile, index: Map<RowKey, List<Bundle>>, file: File): Boolean {
        if (file.length() > MAX_TEXT_CHARS) return false
        val document = jsonOrNull(file.readText().trimStart('\uFEFF')) ?: return false
        val original = document.optJSONArray("dataList") ?: return false
        val keys = original.keyedRows().keys
        if (keys.isEmpty()) return false
        val scores = mutableMapOf<Bundle, Int>()
        keys.forEach { key -> index[key].orEmpty().forEach { scores[it] = (scores[it] ?: 0) + 1 } }
        val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<Bundle, Int>> { it.value }
            .thenBy { it.key.rowCount - it.value })
        val best = ranked.firstOrNull()?.takeIf { it.value * 2 >= keys.size } ?: return false
        val next = ranked.getOrNull(1)
        if (next != null && next.value == best.value && next.key.rowCount == best.key.rowCount) return false
        val translated = zip.readDocument(best.key.entry)?.optJSONArray("dataList")?.keyedRows().orEmpty()
        var changed = false
        for (i in 0 until original.length()) {
            val row = original.optJSONObject(i) ?: continue
            val key = row.key()?.takeIf { it in keys } ?: continue
            val replacement = translated[key] ?: continue
            original.put(i, replacement)
            changed = true
        }
        if (changed) file.writeText(document.toString())
        return changed
    }

    private fun JSONObject.key(): RowKey? = opt("id")?.takeUnless { it == JSONObject.NULL }
        ?.toString()?.takeIf(String::isNotBlank)?.let { RowKey(it, keys().asSequence().toSet()) }

    private fun JSONArray.keyedRows(): Map<RowKey, JSONObject> {
        val rows = LinkedHashMap<RowKey, JSONObject>()
        val duplicates = HashSet<RowKey>()
        for (i in 0 until length()) {
            val row = optJSONObject(i) ?: continue
            val key = row.key() ?: continue
            if (rows.put(key, row) != null) duplicates.add(key)
        }
        duplicates.forEach(rows::remove)
        return rows
    }

    private fun ZipFile.readDocument(entry: ZipEntry): JSONObject? {
        if (entry.size !in 1..MAX_TEXT_CHARS.toLong()) return null
        return getInputStream(entry).bufferedReader().use { jsonOrNull(it.readBounded(MAX_TEXT_CHARS).trimStart('\uFEFF')) }
    }

    private fun jsonOrNull(value: String): JSONObject? = try { JSONObject(value) } catch (_: org.json.JSONException) { null }

    private suspend fun checkActive() = kotlin.coroutines.coroutineContext.ensureActive()

    private fun java.io.Reader.readBounded(limit: Int): String = buildString {
        val buffer = CharArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(length + count <= limit) { "OurPlay 回應內容過大" }
            append(buffer, 0, count)
        }
    }
}
