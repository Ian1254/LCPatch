package com.lcpatch

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.json.JSONObject
import com.github.houbb.opencc4j.util.ZhConverterUtil

data class TranslationEntry(val section: String, val name: String, val author: String, val description: String, val url: String, val script: String? = null, val version: String? = null, val sourceDigest: String? = null)
data class TransferProgress(val name: String, val bytes: Long, val total: Long, val bytesPerSecond: Long, val finished: Boolean = false)
data class ApplyProgress(val stage: String, val current: Int, val total: Int) {
    val fraction: Float? get() = total.takeIf { it > 0 }?.let { (current.toFloat() / it).coerceIn(0f, 1f) }
}
data class DownloadedTranslation(
    val name: String,
    val path: String,
    val script: String = "未判定",
    val puaPrepared: Boolean = false,
    val version: String? = null
)
data class OverrideLanguage(val id: String, val label: String, val prefix: String)
data class TextConversion(val id: String, val label: String)
data class ConversionResult(val scannedFiles: Int, val changedFiles: Int)
data class RuntimeInspection(
    val jsonFiles: Int = 0,
    val matchingFiles: Int = 0,
    val fontBytes: Long = 0,
    val version: String? = null
) {
    val ready: Boolean get() = jsonFiles > 0 && matchingFiles > 0
    val fontReady: Boolean get() = fontBytes >= 12
}

class TranslationRepository(private val context: Context) {
    companion object {
        const val SOURCE = "https://textdb.online/WFd2u9fuvURB7pEJXNSCL3NX"
        private const val OFFICIAL_LATEST = "https://api.github.com/repos/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/latest"
        val LANGUAGES = listOf(
            OverrideLanguage("jp", "日本語", "JP"),
            OverrideLanguage("kr", "한국어", "KR"),
            OverrideLanguage("en", "English", "EN")
        )
        val CONVERSIONS = listOf(
            TextConversion("traditional", "轉為繁體"),
            TextConversion("simplified", "轉為簡體")
        )
        private const val PUBLIC_ROOT = "/sdcard/LCPatch"
        private const val PUBLIC_DOWNLOADS = "$PUBLIC_ROOT/漢化"
        private const val PUBLIC_FONTS = "$PUBLIC_ROOT/字體"
        private const val NATIVE_COMPAT = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn"
        private const val NATIVE_NEXT = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/.lcpatch-cn-next"
        private const val NATIVE_OLD = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/.lcpatch-cn-old"
        private const val NATIVE_DISABLED = "/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/.lcpatch-cn-disabled"
        private const val PUA_MARKER = ".lcpatch-pua"
        private const val VERSION_MARKER = ".lcpatch-version"
        private const val GAME_PACKAGE = "com.ProjectMoon.LimbusCompany"
    }
    private val downloads = File(context.filesDir, "translations/downloads").apply { mkdirs() }
    private val staging = File(context.filesDir, "translations/staging").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("translations", Context.MODE_PRIVATE)

    internal fun observeActivePack(observer: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("active_") || key.startsWith("native_active_") ||
                key == "translation_enabled" || key == "root_mode") observer()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private val localPacks = LocalPackStorage(File(context.filesDir, "translations/packs"))

    fun rootMode(): Boolean = prefs.getBoolean("root_mode", prefs.contains("active_name"))
    fun setRootMode(enabled: Boolean) {
        check(enabled == rootMode() || !rootMode() || !translationEnabled() || activeName() == "尚未選擇") { "請先停用目前漢化再切換模式" }
        prefs.edit().putBoolean("root_mode", enabled).apply()
    }

    private fun executeRoot(command: String): RootCommandResult {
        check(rootMode()) { "目前模式不允許 Root 操作，請使用無 root 文件載入方式" }
        return RootShell.run(command, timeoutMs = 120_000L)
    }

    private suspend fun prepareCharacters(directory: File, progress: suspend (ApplyProgress) -> Unit) {
        if (rootMode()) {
            progress(ApplyProgress("正在補齊劇情角色名稱",0,0))
            val converter=PuaConverter.fromAssets(context)
            val speakers=completeScenarioSpeakers(File(directory,"Localize"),
                decode={ converter.convert(decodeCjkUnicodeEscapes(decodePuaUnicodeEscapes(it)),toPua=false) },
                encode={ converter.convert(it,toPua=true) })
            progress(ApplyProgress("劇情角色名稱已處理",1,1))
            LogRepository.append(context,"INFO","translation.speakers.prepared","角色文字 ${speakers.fields}；待確認 ${speakers.unresolved}；製作註記 ${speakers.productionNotes}")
            encodeDirectoryToPua(directory, progress)
            File(directory, PUA_MARKER).writeText("1")
        } else {
            require(!File(directory, PUA_MARKER).exists()) { "此漢化包包含 PUA 轉碼，請改用無 root 日文字形漢化包" }
            require(translationJsonFiles(directory).none { file ->
                decodePuaUnicodeEscapes(file.readText()).any { it.code in 0xE000..0xF8FF }
            }) { "無 root 模式無法顯示 PUA 字元，請匯入已替換日文字形的漢化包" }
            validateJsonDirectory(File(directory, "Localize"))
        }
    }

    private fun savePack(prepared: File, name: String): String {
        val label = safeName(name)
        if (!rootMode()) return localPacks.replace(prepared, label).absolutePath
        val path = "$PUBLIC_DOWNLOADS/$label"
        val command = atomicSwapCommand(prepared.absolutePath, path, "$PUBLIC_DOWNLOADS/.$label.next", "$PUBLIC_DOWNLOADS/.$label.old")
        require(runRoot(command)) { "無法儲存已解壓漢化" }
        return path
    }

    private fun readPack(entry: DownloadedTranslation, target: File) {
        if (rootMode()) {
            require(runRoot("cp -R ${quote(entry.path)} ${quote(target.absolutePath)} && chmod -R 0755 ${quote(target.absolutePath)}")) { "無法讀取漢化目錄" }
        } else {
            val source = localPacks.list().firstOrNull { it.canonicalPath == File(entry.path).canonicalPath }
                ?: error("找不到 App 內的漢化目錄")
            source.copyRecursively(target, overwrite = true)
        }
    }

    suspend fun fetchCatalog(): List<TranslationEntry> = withContext(Dispatchers.IO) {
        if (!rootMode()) {
            val release = JSONObject(HttpClient.open("https://api.github.com/repos/pzwboy/LocalizeLimbusCompanyForAndroid/releases/latest", accept = "application/vnd.github+json").run {
                inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
            })
            val language = targetLanguage()
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .singleOrNull { it.optString("name") == "${language.id}.zip" }
                ?: error("無 root 來源缺少 ${language.id}.zip")
            val entry=TranslationEntry("漢化-普通", "日文字形漢化（${language.label}）", "pzwboy / ghcruise / 都市零協會",
                "保留來源的相近漢字，透過遊戲原生語言文件載入", asset.getString("browser_download_url"), "日文字形", release.optString("tag_name").takeIf {it.isNotBlank()}, asset.optString("digest").takeIf {it.matches(Regex("sha256:[a-f0-9]{64}"))})
            try { recoverNativeSourceVersions(entry) } catch(error: kotlinx.coroutines.CancellationException) {throw error}
            catch(error: Exception) {LogRepository.append(context,"WARN","translation.version_lookup_failed",error.message ?: "版本比對未完成，保留內容識別")}
            return@withContext listOf(entry)
        }
        val ourPlayResult = async { runCatching { OurPlayTranslation.latest().version } }
        val primaryResult = runCatching {
            HttpClient.open(SOURCE).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }
        }.map(::parseTranslationCatalog)
        val primary = primaryResult.getOrDefault(emptyList()).filterNot {
            it.name.contains("零協") || it.author.contains("零協") || it.author.contains("都市零協會") ||
                isLegacyRootSimplified(it.name)
        }
        val lctaResult = async { runCatching { fetchLctaLatest() } }
        val officialResult = runCatching { fetchOfficialLatest() }
        val community = officialResult.getOrDefault(emptyList())
        listOfNotNull(primaryResult.exceptionOrNull(), officialResult.exceptionOrNull()).forEach { error ->
            LogRepository.append(context, "WARN", "translation.catalog_source_failed", error.message ?: error.javaClass.simpleName)
        }
        val ourPlay = OurPlayTranslation.entry.copy(version = ourPlayResult.await().getOrNull())
        val lcta = lctaResult.await()
        lcta.exceptionOrNull()?.let { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            LogRepository.append(context, "WARN", "translation.lcta.catalog_failed", error.message ?: error.javaClass.simpleName)
        }
        (community + ourPlay + listOfNotNull(lcta.getOrNull()) + primary).distinctBy { it.name to it.url }
    }

    /** Legacy test.1 packs had no upstream metadata. Match content, never assign today's tag by name alone. */
    private fun recoverNativeSourceVersions(entry: TranslationEntry) {
        val version=validPackVersion(entry.version) ?: releaseAssetVersion(entry.url) ?: return
        val candidates=localPacks.list().mapNotNull {directory ->
            val current=readLocalPackVersion(directory)
            if(!current.startsWith("sha256:") || prefs.getString("version_checked.$current",null)==entry.url+entry.sourceDigest.orEmpty()) null else directory to current
        }
        if(candidates.isEmpty()) return
        val key="source_identity.${entry.url}.${entry.sourceDigest ?: ""}"
        val reference=prefs.getString(key,null) ?: run {
            val archive=File(staging,"version-reference.zip")
            val directory=File(staging,"version-reference")
            try {
                directory.deleteRecursively();directory.mkdirs()
                val connection=HttpClient.open(entry.url)
                try {
                    connection.inputStream.use {input -> archive.outputStream().use {output ->
                        val buffer=ByteArray(64*1024);var total=0L
                        while(true) {val count=input.read(buffer);if(count<0) break;total+=count;require(total<=32L*1024*1024) {"版本比對來源過大"};output.write(buffer,0,count)}
                    }}
                } finally {connection.disconnect()}
                entry.sourceDigest?.let {expected ->
                    val digest=java.security.MessageDigest.getInstance("SHA-256")
                    archive.inputStream().use {input -> val bytes=ByteArray(64*1024);while(true) {val n=input.read(bytes);if(n<0) break;digest.update(bytes,0,n)}}
                    check("sha256:"+digest.digest().joinToString("") {"%02x".format(it)}==expected) {"版本比對來源校驗失敗"}
                }
                unpackLocalize(archive,directory)
                directoryPackIdentity(directory).also {prefs.edit().putString(key,it).apply()}
            } finally {archive.delete();directory.deleteRecursively()}
        }
        candidates.forEach {(directory,identity) ->
            val matchedVersion=matchedSourceVersion(identity,reference,version)
            if(matchedVersion!=null) {
                ensurePackIdentity(directory,entry.url,matchedVersion)
                if (directory.absolutePath == prefs.getString("native_active_source_path", null) &&
                    identity == prefs.getString("native_active_version", null)) {
                    prefs.edit().putString("native_active_version", matchedVersion).apply()
                }
            }
            prefs.edit().putString("version_checked.$identity",entry.url+entry.sourceDigest.orEmpty()).apply()
        }
    }

    private fun fetchLctaLatest(): TranslationEntry {
        val connection = HttpClient.open("https://api.github.com/repos/HZBHZB1234/LCTA_auto_update/releases/latest", accept = "application/vnd.github+json")
        val raw = try { connection.inputStream.bufferedReader().use { it.readText() } }
                  finally { connection.disconnect() }
        return parseLctaRelease(JSONObject(raw))
    }

    private fun fetchOfficialLatest(): List<TranslationEntry> {
        val raw = HttpClient.open(OFFICIAL_LATEST, accept = "application/vnd.github+json").run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }
        val release = JSONObject(raw)
        val tag = release.optString("tag_name", "latest")
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).asSequence()
            .map { assets.getJSONObject(it) }
            .firstOrNull {
                val name = it.optString("name").lowercase()
                name.endsWith(".zip") && name.startsWith("limbuslocalize_")
            }?.optString("browser_download_url")?.takeIf(String::isNotBlank)
        require(!asset.isNullOrBlank()) { "官方 Release 沒有可用的 LimbusLocalize ZIP" }
        return listOf(
            TranslationEntry(
                "漢化-XP",
                "零協會漢化",
                "都市零協會",
                "自動補齊 PC 資源及劇情角色名稱，無須另選整理版",
                asset, script="簡體", version=tag
            )
        )
    }

    suspend fun download(
        entry: TranslationEntry,
        progress: suspend (TransferProgress) -> Unit,
        preparation: suspend (ApplyProgress) -> Unit = {}
    ): DownloadedTranslation = withContext(Dispatchers.IO) {
        val ourPlay = if (entry.url == OurPlayTranslation.entry.url && entry.name == OurPlayTranslation.entry.name) {
            check(rootMode()) { "OurPlay 神人漢化需要 Root ＋ LSPosed" }
            OurPlayTranslation.latest().also { asset ->
                require(!isNewerPackVersion(entry.version, asset.version)) { "OurPlay 遠端版本已變動，請重新整理漢化清單" }
            }
        } else null
        val fileName = "${safeName(entry.name)}.zip"
        val sourceKey = java.security.MessageDigest.getInstance("SHA-256").digest(entry.url.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        val part = if (ourPlay == null) File(downloads, "$fileName.$sourceKey.part")
            else File.createTempFile("ourplay-", ".part", staging)
        val output = File(downloads, fileName)
        val prepared = File(staging, "download-${safeName(entry.name)}")
        try {
            val result = downloadResumable(
                url = ourPlay?.url ?: entry.url,
                part = part,
                displayName = fileName,
                progress = progress,
                maxBytes = ourPlay?.bytes ?: if (entry.url.startsWith("https://github.com/HZBHZB1234/LCTA_auto_update/releases/download/")) 64L * 1024 * 1024 else Long.MAX_VALUE
            )
            require(part.length() > 4 && part.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }) { "下載內容不是 ZIP 漢化包" }
            ourPlay?.verify(part)
            if (ourPlay == null && entry.sourceDigest != null) {
                try { verifyPackSourceDigest(part, entry.sourceDigest) }
                catch (error: Exception) { part.delete(); throw error }
            }
            if (ourPlay == null) ZipFile(part).use {
                require(it.entries().asSequence().any { item -> localizeRelativePath(item.name) != null }) { "漢化包缺少 Localize 資料夾" }
            }
            if (output.exists()) output.delete()
            require(part.renameTo(output)) { "無法儲存下載檔" }
            prefs.edit().putString("download.${output.name}", entry.name).apply()
            prepared.deleteRecursively()
            prepared.mkdirs()
            if (ourPlay == null) unpackLocalize(output, prepared) else {
                preparation(ApplyProgress("正在取得零協會參考檔", 0, 0))
                val reference = File.createTempFile("ourplay-reference-", ".zip", staging)
                try {
                    downloadResumable(fetchOfficialLatest().single().url, reference, "零協會參考檔", {}, maxBytes = 64L * 1024 * 1024)
                    unpackLocalize(reference, prepared)
                    val matched = OurPlayTranslation.overlay(output, File(prepared, "Localize"), preparation)
                    LogRepository.append(context, "INFO", "translation.ourplay.matched", "版本 ${ourPlay.version}，已對應 $matched 個文字檔")
                } finally { reference.delete() }
            }
            val script = entry.script ?: inferScript(entry.name)
            File(prepared, ".lcpatch-script").writeText(script)
            prepareCharacters(prepared, preparation)
            val identifiedVersion = ensurePackIdentity(prepared, ourPlay?.url ?: entry.url,
                ourPlay?.version ?: validPackVersion(entry.version) ?: releaseAssetVersion(entry.url) ?: archivePackVersion(output))
            val publicPath = savePack(prepared, entry.name)
            output.delete()
            progress(TransferProgress(fileName, result.bytes, if (result.total > 0) result.total else result.bytes, 0, true))
            LogRepository.append(context, "INFO", "download.completed", "${entry.name} 已下載並解壓到獨立目錄")
            DownloadedTranslation(entry.name, publicPath, script, rootMode(),identifiedVersion)
        } catch (error: Throwable) {
            val looksLikeZip = runCatching {
                part.length() > 4 && part.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
            }.getOrDefault(false)
            if (!looksLikeZip) part.delete()
            throw error
        } finally {
            if (ourPlay != null) {
                part.delete()
                output.delete()
                prepared.deleteRecursively()
            }
        }
    }

    private suspend fun applyPrepared(raw: File, displayName: String, progress: suspend (ApplyProgress) -> Unit): String {
        val pack = safeName(displayName)
        val unpacked = File(staging, pack)
        unpacked.deleteRecursively(); unpacked.mkdirs()
        require(File(raw, "Localize").isDirectory) { "漢化包缺少 Localize 資料夾" }
        progress(ApplyProgress("正在整理漢化文件", 0, 0))
        normalizeForLanguage(raw, unpacked, targetLanguage(), progress)
        val script = File(raw, ".lcpatch-script").takeIf(File::isFile)?.readText()?.trim()?.takeIf { it in setOf("簡體", "繁體") }
            ?: inferScript(displayName)
        File(unpacked, ".lcpatch-script").writeText(script)
        val runtimeFont = File(unpacked, "Localize/cn/ChineseFont.ttf")
        customFontFile()?.copyTo(runtimeFont, overwrite = true) ?: copyBundledPuaFont(runtimeFont)
        try {
            val managed=runCatching { JSONObject(File(raw,".lcpatch-upstream.json").readText()) }.getOrNull()
            if (managed?.optInt("schema") == 1 && OurPlayTranslation.isPackageUrl(managed.optString("url"))) {
                val summary = "OurPlay 社群翻譯已對應零協會參考檔"
                prefs.edit().putString("pc_coverage_summary", summary).apply()
                File(unpacked, "Localize/cn/.lcpatch-coverage.txt").writeText("$summary\n")
            } else if(managed?.optInt("schema")==1 && isOfficialLlcUrl(managed.optString("url"))) {
                // This downloaded package already contains every official resource,
                // with speaker names completed before PUA encoding. It also works offline.
                val summary="零協會完整資源與劇情角色名稱已自動整理"
                prefs.edit().putString("pc_coverage_summary",summary).apply()
                File(unpacked,"Localize/cn/.lcpatch-coverage.txt").writeText("$summary\n來源：${managed.optString("url")}\n")
            } else supplementPcTranslations(File(unpacked, "Localize/cn"), script, progress)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException || error is VirtualMachineError || error is ThreadDeath) throw error
            prefs.edit().putString("pc_coverage_summary", "PC 翻譯處理失敗；目前遊戲內容未更新").apply()
            LogRepository.append(context, "ERROR", "translation.pc.processing_failed", error.stackTraceToString())
            val reason = generateSequence(error) { it.cause }.take(8).last()
            throw IllegalStateException("PC 翻譯處理失敗，尚未寫入遊戲：${reason.message ?: reason.javaClass.simpleName}", error)
        }
        val sourceMetadata=File(raw,".lcpatch-upstream.json").takeIf {it.isFile && it.length()<8192}?.readText()
            ?:File(raw,"Localize/cn/$VERSION_MARKER").takeIf {it.isFile && it.length()<8192}?.readText()
        val installedVersion = installedPackVersion(sourceMetadata, null) ?: ensurePackIdentity(raw)
        val runtimeMetadata = JSONObject().put("schema", 1)
        runtimeMetadata.put(if (installedVersion.startsWith("sha256:")) "content_id" else "version", installedVersion)
        sourceMetadata?.let { runCatching { JSONObject(it).optString("url") }.getOrNull() }
            ?.takeIf(String::isNotBlank)?.let { runtimeMetadata.put("url", it) }
        File(unpacked, "Localize/cn/$VERSION_MARKER").writeText(runtimeMetadata.toString())
        progress(ApplyProgress("正在驗證語言文件",0,0))
        validateJsonDirectory(File(unpacked, "Localize/cn"))
        progress(ApplyProgress("正在建立文字索引",0,0))
        val indexed = writeTextAssetIndex(File(unpacked, "Localize/cn"))
        LogRepository.append(context, "INFO", "translation.textasset.index", "$indexed 個內建文字資源名稱已建立索引")
        progress(ApplyProgress("正在寫入遊戲目錄", 0, 0))
        val nativeAction = runtimeSwapCommand(File(unpacked, "Localize/cn").absolutePath)
        val command = "am force-stop $GAME_PACKAGE && $nativeAction"
        val result = executeRoot(command)
        require(result.success) { result.output.ifBlank { "root 套用失敗" } }
        val inspection = inspectRuntime(allowVersionFallback = false)
        require(inspection.version == installedVersion) { "遊戲快取版本驗證失敗：預期 $installedVersion，實際 ${inspection.version ?: "無法讀取"}" }
        require(inspection.ready) { "遊戲快取驗證失敗：找不到 ${targetLanguage().prefix} 漢化文件" }
        require(inspection.fontReady) { "遊戲快取驗證失敗：中文字型未寫入" }
        progress(ApplyProgress("套用完成", 1, 1))
        prefs.edit().putString("active_name", displayName).putString("active_script", script).putString("active_version",installedVersion).putString("active_source_path",prefs.getString("applying_source_path",null)).apply()
        LogRepository.append(context, "INFO", "translation.applied", "已套用 $displayName")
        return displayName
    }

    fun activeVersion(): String? = prefs.getString(if(rootMode()) "active_version" else "native_active_version",null)

    private fun runtimeVersionEvidence(directory: String): Pair<String?,String?> {
        val metadata=RootShell.run("cat ${quote("$directory/$VERSION_MARKER")} 2>/dev/null || true",timeoutMs=15_000L,maxOutputChars=8192).output
        val coverage=RootShell.run("cat ${quote("$directory/.lcpatch-coverage.txt")} 2>/dev/null || true",timeoutMs=15_000L,maxOutputChars=8192).output
        return metadata.takeIf {it.isNotBlank()} to coverage.takeIf {it.isNotBlank()}
    }

    fun activePackPath(): String? = prefs.getString(if(rootMode()) "active_source_path" else "native_active_source_path",null)

    fun activeName(): String = prefs.getString(if (rootMode()) "active_name" else "native_active_name", "尚未選擇") ?: "尚未選擇"
    fun activeScript(): String = if (!rootMode()) "日文字形" else prefs.getString("active_script", null)?.takeIf { it in setOf("簡體", "繁體") }
        ?: inferScript(activeName())

    suspend fun deleteDownloaded(entry: DownloadedTranslation) = withContext(Dispatchers.IO) {
        if(rootMode()) {
            val target=validatedPackPath(File(PUBLIC_DOWNLOADS),entry.path)
            require(runRoot("test ! -L ${quote(target.absolutePath)} && rm -rf -- ${quote(target.absolutePath)} && test ! -e ${quote(target.absolutePath)}")) {"無法刪除漢化包"}
        } else localPacks.delete(entry.path)
        LogRepository.append(context,"INFO","translation.download_deleted","已刪除下載的漢化包：${entry.name}；遊戲內漢化維持原狀")
    }

    suspend fun downloaded(): List<DownloadedTranslation> = withContext(Dispatchers.IO) {
        if (!rootMode()) return@withContext localPacks.list().map { file ->
            val script = File(file, ".lcpatch-script").takeIf(File::isFile)?.readText()?.trim()
                ?.takeIf { it in setOf("簡體", "繁體") } ?: "日文字形"
            val version=readLocalPackVersion(file)
            DownloadedTranslation(file.name, file.absolutePath, script, File(file, PUA_MARKER).isFile,version)
        }
        repairStorage()
        val result = executeRoot("find ${quote(PUBLIC_DOWNLOADS)} -mindepth 1 -maxdepth 1 -type d ! -name '.*' -print")
        if (!result.success) return@withContext emptyList()
        result.output.lineSequence().filter(String::isNotBlank).mapNotNull { original ->
            val oldName = File(original).name
            if (isLegacyRootSimplified(oldName) && oldName != activeName()) {
                if (runRoot("rm -rf -- ${quote(original)}")) {
                    LogRepository.append(context, "INFO", "translation.legacy_removed", "已移除舊版 Root 簡體漢化")
                    return@mapNotNull null
                }
            }
            val cleanName = safeName(oldName)
            val canonical = "$PUBLIC_DOWNLOADS/$cleanName"
            if (original != canonical) runRoot("mv -f ${quote(original)} ${quote(canonical)}")
            DownloadedTranslation(cleanName, canonical, readScript(canonical, cleanName), rootFileExists("$canonical/$PUA_MARKER"),
                rootDownloadedPackVersion(canonical))
        }.toList().distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    private fun rootDownloadedPackVersion(directory: String): String? {
        val metadata=executeRoot("cat ${quote("$directory/.lcpatch-upstream.json")} 2>/dev/null").output
        installedPackVersion(metadata,null)?.let {return it}
        val coverage=executeRoot("cat ${quote("$directory/Localize/cn/.lcpatch-coverage.txt")} 2>/dev/null").output
        installedPackVersion(null,coverage)?.let {return it}
        val result=RootShell.run("cd ${quote(directory)} && find Localize -type f -name '*.json' ! -name '.*' -exec sha256sum {} \\;",timeoutMs=120_000L,maxOutputChars=4*1024*1024)
        if(!result.success || result.truncated) return null
        val records=mutableListOf<Pair<String,String>>()
        for(line in result.output.lineSequence().filter {it.isNotBlank()}) {
            val match=Regex("^([a-f0-9]{64})  (Localize/.*)$").matchEntire(line) ?: return null
            records+=match.groupValues[2] to match.groupValues[1]
        }
        return records.takeIf {it.isNotEmpty()}?.let(::contentPackIdentity)
    }

    suspend fun apply(
        entry: DownloadedTranslation,
        nativeProgress: ((Int, Int) -> Unit)? = null,
        progress: suspend (ApplyProgress) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        prefs.edit().putString("applying_source_path",entry.path).apply()
        progress(ApplyProgress("正在讀取已下載漢化",0,0))
        if (!rootMode()) {
            val raw = File(staging, "native-source").also { it.deleteRecursively() }
            val prepared = File(staging, "native-prepared").also { it.deleteRecursively(); it.mkdirs() }
            readPack(entry, raw)
            prepareCharacters(raw, progress)
            normalizeForLanguage(raw, prepared, targetLanguage(), progress)
            val directory = File(prepared, nativeExportPath(targetLanguage()))
            run {
                val archive = File(staging, "native-transfer.zip")
                try {
                    zipDirectory(directory, archive.outputStream())
                    progress(ApplyProgress("正在透過 Stellar 寫入原生語言文件", 0, 0))
                    NativeStorageClient.use(context) { service ->
                        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            val callback = object : INativeProgressCallback.Stub() {
                                override fun onProgress(completed: Int, total: Int) {
                                    nativeProgress?.invoke(completed, total)
                                }
                            }
                            val error = service.apply(descriptor, targetLanguage().id, callback)
                            check(error.isEmpty()) { error }
                        }
                    }
                } finally { archive.delete() }
            }
            prefs.edit().putString("native_active_name", entry.name).putString("native_active_language", targetLanguage().id)
                .putString("native_active_version",entry.version ?: ensurePackIdentity(raw)).putString("native_active_source_path",entry.path).apply()
            LogRepository.append(context, "INFO", "translation.native_applied", "已寫入原生語言目錄：${entry.name}")
            return@withContext entry.name
        }
        val raw = File(staging, "${safeName(entry.name)}.raw").also { it.deleteRecursively() }
        require(runRoot("cp -R ${quote(entry.path)} ${quote(raw.absolutePath)} && chmod -R 0755 ${quote(raw.absolutePath)}")) { "無法讀取已下載漢化目錄" }
        if (!File(raw, PUA_MARKER).isFile) {
            encodeDirectoryToPua(raw, progress)
            File(raw, PUA_MARKER).writeText("1")
            validateJsonDirectory(File(raw, "Localize"))
            require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(raw.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法儲存 PUA 轉換結果" }
        }
        applyPrepared(raw, entry.name, progress)
    }

    suspend fun importCustom(uri: Uri, displayName: String): DownloadedTranslation = withContext(Dispatchers.IO) {
        val label = safeName(displayName.substringBeforeLast('.').ifBlank { "自訂漢化" })
        val cache = File(downloads, "$label.zip")
        context.contentResolver.openInputStream(uri)?.use { input -> cache.outputStream().use(input::copyTo) } ?: error("無法讀取漢化文件")
        require(cache.length() > 4 && cache.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }) { "選擇的文件不是 ZIP" }
        ZipFile(cache).use { require(it.entries().asSequence().any { item -> localizeRelativePath(item.name) != null }) { "漢化包缺少 Localize 資料夾" } }
        val prepared = File(staging, "import-$label").also { it.deleteRecursively(); it.mkdirs() }
        unpackLocalize(cache, prepared)
        File(prepared, ".lcpatch-script").writeText(inferScript(label))
        prepareCharacters(prepared) {}
        val identifiedVersion=ensurePackIdentity(prepared,version=archivePackVersion(cache))
        val publicPath = savePack(prepared, label)
        cache.delete()
        prefs.edit().putString("download.${cache.name}", label).apply()
        LogRepository.append(context, "INFO", "translation.imported", "已匯入並解壓 $label")
        DownloadedTranslation(label, publicPath, if (rootMode()) inferScript(label) else "日文字形", rootMode(),identifiedVersion)
    }

    fun targetLanguage(): OverrideLanguage {
        val id = prefs.getString("override_language", "jp")
        return LANGUAGES.firstOrNull { it.id == id && (rootMode() || it.id != "en") } ?: LANGUAGES.first()
    }

    fun setTargetLanguage(value: OverrideLanguage) {
        require(rootMode() || value.id in setOf("jp", "kr")) { "無 root 模式只支援日語與韓語字形漢化" }
        prefs.edit().putString("override_language", value.id).apply()
    }

    /** Keep installed language switching available even after its download was deleted. */
    internal suspend fun reapplyInstalled(progress: suspend (ApplyProgress)->Unit) = withContext(Dispatchers.IO) {
        check(rootMode() && activeName()!="尚未選擇") {"目前尚未套用漢化"}
        val work=snapshotInstalled("reapply-current",progress)
        File(work,".lcpatch-script").writeText(activeScript())
        File(work,PUA_MARKER).writeText("1")
        applyPrepared(work,activeName(),progress)
    }

    private suspend fun snapshotInstalled(label: String,progress: suspend (ApplyProgress)->Unit): File {
        val directory=if(translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED
        // Separate snapshots from applyPrepared's name-based staging directory.
        val work=File(context.cacheDir,"runtime-snapshots/$label").also {it.deleteRecursively()}
        val destination=File(work,"Localize/cn")
        val uid=android.os.Process.myUid()
        progress(ApplyProgress("正在讀取目前套用的漢化",0,0))
        require(runRoot("${validLanguageDirectoryTest(directory)} && mkdir -p ${quote(destination.parentFile!!.absolutePath)} && cp -R ${quote(directory)} ${quote(destination.absolutePath)} && chown -R $uid:$uid ${quote(work.absolutePath)} && chmod -R 0755 ${quote(work.absolutePath)} && { restorecon -RF ${quote(work.absolutePath)} 2>/dev/null || true; }")) {"無法讀取目前套用的漢化"}
        val evidence=runtimeVersionEvidence(directory)
        val metadata=evidence.first?.takeIf {runCatching {JSONObject(it).optInt("schema")==1}.getOrDefault(false)}
            ?:officialReleaseUrl(evidence.second)?.let {JSONObject().put("schema",1).put("url",it).toString()}
        metadata?.let {File(work,".lcpatch-upstream.json").writeText(it)}
        return work
    }

    /** Convert the installed bytes, not a possibly newer downloaded package with the same name. */
    internal suspend fun convertActive(conversion: TextConversion,progress: suspend (ApplyProgress)->Unit): ConversionResult = withContext(Dispatchers.IO) {
        check(rootMode()) { "無 root 漢化需保留來源字形，不能進行繁簡轉換" }
        val name=activeName()
        check(name!="尚未選擇") {"目前尚未套用漢化"}
        val keepDownload=runRoot("test -d ${quote("$PUBLIC_DOWNLOADS/${safeName(name)}")}")
        val work=snapshotInstalled("convert-active",progress)
        val result=convertTextFiles(work,conversion,progress)
        require(result.scannedFiles>0) {"目前漢化沒有 JSON 文件"}
        val script=if(conversion.id=="traditional") "繁體" else "簡體"
        File(work,".lcpatch-script").writeText(script)
        File(work,PUA_MARKER).writeText("1")
        validateJsonDirectory(File(work,"Localize"))
        if(keepDownload) savePack(work,name)
        applyPrepared(work,name,progress)
        // applyPrepared replaces the disabled directory directly when disabled;
        // calling setTranslationEnabled(false) again would delete that directory.
        LogRepository.append(context,"INFO","translation.converted","目前套用的 $name ${conversion.label}完成：${result.scannedFiles} 個 JSON")
        result
    }

    internal suspend fun convertDownloaded(entry: DownloadedTranslation, conversion: TextConversion, progress: suspend (ApplyProgress) -> Unit = {}): ConversionResult = withContext(Dispatchers.IO) {
        check(rootMode()) { "無 root 漢化需保留來源字形，不能進行繁簡轉換" }
        val work = File(staging, "convert-${safeName(entry.name)}").also { it.deleteRecursively() }
        readPack(entry, work)
        val result = convertTextFiles(work, conversion, progress)
        require(result.scannedFiles > 0) { "漢化目錄內沒有 JSON 文件" }
        File(work, ".lcpatch-script").writeText(if (conversion.id == "traditional") "繁體" else "簡體")
        File(work, PUA_MARKER).writeText("1")
        validateJsonDirectory(File(work, "Localize"))
        if (rootMode()) {
            require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(work.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法寫回轉換後的漢化目錄" }
        } else localPacks.replace(work, safeName(entry.name))
        LogRepository.append(context, "INFO", "translation.converted", "${entry.name} ${conversion.label}完成：已掃描 ${result.scannedFiles} 個 JSON，內容有變更 ${result.changedFiles} 個")
        result
    }

    fun fontName(): String = if (hasCustomFont()) {
        prefs.getString("font_name", null) ?: "自訂字型"
    } else "內建 PUA 中文字型"
    fun hasCustomFont(): Boolean = customFontFile() != null

    fun translationEnabled(): Boolean = if (rootMode()) prefs.getBoolean("translation_enabled", true)
        else prefs.contains("native_active_name")

    fun migrateRuntime() {
        if (!rootMode()) return
        val fallback = File(context.filesDir, "runtime-pua-font.ttf")
        val font = customFontFile() ?: fallback.also { copyBundledPuaFont(it) }
        val cleanup = "rm -rf '/sdcard/limbus mods/zh_cn' ${quote("$PUBLIC_ROOT/目前套用")} ${quote("$PUBLIC_ROOT/.active-next")} ${quote("$PUBLIC_ROOT/.active-old")} ${quote(NATIVE_NEXT)} ${quote(NATIVE_OLD)}"
        val repairFont = "if ${validLanguageDirectoryTest(NATIVE_COMPAT)}; then cp -f ${quote(font.absolutePath)} ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} && chmod 0644 ${quote("$NATIVE_COMPAT/ChineseFont.ttf")}; restorecon -F ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} 2>/dev/null || true; fi"
        runRoot("$cleanup; $repairFont")
        repairRuntimeIndex()
    }

    private fun repairRuntimeIndex(): Boolean {
        if (!rootMode()) return true
        return runCatching {
            val directory = if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED
            val repair = repairRuntimeTextAssetIndex(directory, File(staging, "runtime-index")) { command ->
                RootShell.run(command, timeoutMs = 15_000L, maxOutputChars = TEXT_ASSET_INDEX_LIMIT, keepTail = false)
            }
            if (repair?.changed == true) LogRepository.append(context, "INFO", "translation.textasset.index.repaired",
                "已為既有漢化補建內建文字索引，共 ${repair.entries} 筆；重新啟動遊戲後生效")
            true
        }.getOrElse { error ->
            LogRepository.append(context, "WARN", "translation.textasset.index.repair_failed", error.message ?: "索引修復失敗")
            false
        }
    }

    fun prepareGameLaunch(): Boolean {
        if (!rootMode()) return true
        // Native configuration reads the index once; stop the previous process before repairing it.
        if (!runRoot("am force-stop $GAME_PACKAGE")) return false
        if (!translationEnabled()) return true
        if (!repairRuntimeIndex()) return false
        return runRoot(validLanguageDirectoryTest(NATIVE_COMPAT))
    }

    fun inspectRuntime(allowVersionFallback: Boolean = true): RuntimeInspection {
        if (!rootMode()) return RuntimeInspection()
        val prefix = targetLanguage().prefix.uppercase()
        val directory = if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED
        val command = """
            dir=${quote(directory)}
            test -d "${'$'}dir" || exit 0
            total=${'$'}(find "${'$'}dir" -type f -iname '*.json' | wc -l)
            matching=${'$'}(find "${'$'}dir" -type f -iname ${quote("${prefix}_*.json")} | wc -l)
            font=${'$'}(stat -c %s "${'$'}dir/ChineseFont.ttf" 2>/dev/null || echo 0)
            printf 'LCPATCH_COUNTS %s|%s|%s\n' "${'$'}total" "${'$'}matching" "${'$'}font"
            printf 'LCPATCH_VERSION\n'
            head -c 8192 "${'$'}dir/$VERSION_MARKER" 2>/dev/null || true
            printf '\nLCPATCH_COVERAGE\n'
            head -c 8192 "${'$'}dir/.lcpatch-coverage.txt" 2>/dev/null || true
        """.trimIndent()
        val result = executeRoot(command)
        if (!result.success) return RuntimeInspection()
        val counts = result.output.lineSequence().firstOrNull { it.startsWith("LCPATCH_COUNTS ") }
            ?.removePrefix("LCPATCH_COUNTS ")?.split('|') ?: return RuntimeInspection()
        val evidence = result.output.substringAfter("LCPATCH_VERSION\n", "").split("\nLCPATCH_COVERAGE\n", limit = 2)
        return RuntimeInspection(
            jsonFiles = counts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
            matchingFiles = counts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
            fontBytes = counts.getOrNull(2)?.trim()?.toLongOrNull() ?: 0,
            version = installedPackVersion(evidence.firstOrNull()?.trim(), null)
                ?: if (allowVersionFallback) activeVersion() ?: installedPackVersion(null, evidence.getOrNull(1)) else null
        )
    }

    suspend fun setTranslationEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        check(rootMode()) { "無 Root 模式請直接套用漢化" }
        val command = if (enabled) {
            "am force-stop $GAME_PACKAGE && if ${validLanguageDirectoryTest(NATIVE_DISABLED)}; then rm -rf ${quote(NATIVE_COMPAT)} && mv ${quote(NATIVE_DISABLED)} ${quote(NATIVE_COMPAT)}; else ${validLanguageDirectoryTest(NATIVE_COMPAT)}; fi"
        } else {
            "am force-stop $GAME_PACKAGE && rm -rf ${quote(NATIVE_DISABLED)} && if [ -d ${quote(NATIVE_COMPAT)} ]; then mv ${quote(NATIVE_COMPAT)} ${quote(NATIVE_DISABLED)}; fi"
        }
        require(runRoot(command)) { if (enabled) "尚未套用任何漢化" else "無法停用漢化" }
        prefs.edit().putBoolean("translation_enabled", enabled).apply()
        LogRepository.append(context, "INFO", "translation.${if (enabled) "enabled" else "disabled"}", "漢化已${if (enabled) "啟用" else "停用"}，重新啟動遊戲後生效")
    }

    fun setCustomFont(uri: Uri, displayName: String) {
        val extension = displayName.substringAfterLast('.', "ttf").lowercase().let { if (it in setOf("ttf", "otf")) it else "ttf" }
        val target = File(context.filesDir, "custom-font.$extension")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) } ?: error("無法讀取字型文件")
        require(target.length() in 12..64L * 1024 * 1024) { "字型文件大小不正確" }
        File(context.filesDir, if (extension == "ttf") "custom-font.otf" else "custom-font.ttf").delete()
        val publicPath = "$PUBLIC_FONTS/${safeName(displayName.substringBeforeLast('.'))}.$extension"
        if (rootMode()) require(runRoot("mkdir -p ${quote(PUBLIC_FONTS)} && cp -f ${quote(target.absolutePath)} ${quote(publicPath)} && chmod 0666 ${quote(publicPath)}")) { "無法儲存到 LCPatch 字型目錄" }
        prefs.edit().putString("font_name", displayName).putString("font_path", target.absolutePath).putString("font_public_path", publicPath).apply()
    }

    fun clearCustomFont() {
        File(context.filesDir, "custom-font.ttf").delete(); File(context.filesDir, "custom-font.otf").delete()
        prefs.edit().remove("font_name").remove("font_path").remove("font_public_path").apply()
    }

    private fun customFontFile(): File? = prefs.getString("font_path", null)?.let(::File)?.takeIf(File::isFile)

    private fun copyBundledPuaFont(target: File) {
        target.parentFile?.mkdirs()
        context.assets.open("ChineseFont-PUA.ttf").use { input ->
            target.outputStream().buffered().use(input::copyTo)
        }
        require(target.length() >= 12) { "內建中文字型無法讀取" }
    }

    private fun runtimeSwapCommand(source: String): String =
        atomicSwapCommand(source, if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED, NATIVE_NEXT, NATIVE_OLD)

    private fun atomicSwapCommand(source: String, destination: String, next: String, old: String): String =
        "mkdir -p ${quote(File(next).parent!!)} && rm -rf ${quote(next)} ${quote(old)} && mkdir -p ${quote(next)} && cp -R ${quote("$source/.")} ${quote(next)} && chmod -R 0755 ${quote(next)} && ${validLanguageDirectoryTest(next)} && if [ -e ${quote(destination)} ]; then mv ${quote(destination)} ${quote(old)}; fi && if mv ${quote(next)} ${quote(destination)}; then chmod -R 0755 ${quote(destination)}; restorecon -RF ${quote(destination)} 2>/dev/null || true; if ${validLanguageDirectoryTest(destination)}; then rm -rf ${quote(old)} ${if (destination == NATIVE_COMPAT) quote(NATIVE_DISABLED) else ""}; else rm -rf ${quote(destination)}; if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi; else if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi"

    private fun validLanguageDirectoryTest(path: String): String =
        "{ [ -d ${quote(path)} ] && [ -n \"${'$'}(find ${quote(path)} -type f -iname '*.json' -print -quit)\" ]; }"

    private suspend fun normalizeForLanguage(raw: File, output: File, language: OverrideLanguage, progress: suspend (ApplyProgress)->Unit = {}) {
        val localize = File(raw, "Localize")
        val source = if (!rootMode()) nativeLanguageSource(localize, language) else localize.listFiles()?.firstOrNull { candidate ->
            candidate.isDirectory && candidate.name.lowercase() in setOf("cn", "zh_cn", "zh-cn", "jp", "kr", "en", "hant", "zh-tw")
        } ?: localize
        val destination = File(output, if (rootMode()) "Localize/cn" else nativeExportPath(language)).apply { mkdirs() }
        copyLanguageResources(source,destination,language,rootMode(),progress)
    }

    private suspend fun convertTextFiles(output: File, conversion: TextConversion, progress: suspend (ApplyProgress) -> Unit): ConversionResult {
        if (conversion.id == "original") return ConversionResult(0, 0)
        val files = translationJsonFiles(output)
        val puaConverter = PuaConverter.fromAssets(context)
        
        progress(ApplyProgress("正在${conversion.label}", 0, files.size))
        val converter: (String) -> String = when (conversion.id) {
            "traditional" -> ZhConverterUtil::toTraditional
            "simplified" -> ZhConverterUtil::toSimple
            else -> error("不支援的繁簡轉換模式")
        }
        var changed = 0
        files.forEachIndexed { index, file ->
            val original = file.readText(Charsets.UTF_8)
            val decoded = decodeCjkUnicodeEscapes(decodePuaUnicodeEscapes(original))
            val readableText = puaConverter.convert(decoded, toPua = false)
            val converted = puaConverter.convert(converter(readableText), toPua = true)
            if (converted != original) {
                file.writeText(converted, Charsets.UTF_8)
                changed++
            }
            progress(ApplyProgress("正在${conversion.label}", index + 1, files.size))
        }
        return ConversionResult(files.size, changed)
    }

    private fun translationJsonFiles(output: File): List<File> = output.walkTopDown()
        .filter { file -> file.isFile && file.extension.equals("json", true) }
        .toList()

    private suspend fun encodeDirectoryToPua(output: File, progress: suspend (ApplyProgress) -> Unit) {
        val files = translationJsonFiles(output)
        val converter = PuaConverter.fromAssets(context)
        progress(ApplyProgress("正在自動轉換 PUA", 0, files.size))
        files.forEachIndexed { index, file ->
            val original = file.readText(Charsets.UTF_8)
            val converted = converter.convert(decodeCjkUnicodeEscapes(original), toPua = true)
            if (converted != original) file.writeText(converted, Charsets.UTF_8)
            progress(ApplyProgress("正在自動轉換 PUA", index + 1, files.size))
        }
        validateJsonDirectory(File(output, "Localize"))
    }

    private fun validateJsonDirectory(directory: File) {
        val files = translationJsonFiles(directory)
        require(files.isNotEmpty()) { "漢化目錄內沒有 JSON 文件" }
        files.forEach { file ->
            runCatching { org.json.JSONTokener(file.readText(Charsets.UTF_8)).nextValue() }
                .getOrElse { throw IllegalArgumentException("JSON 格式錯誤：${file.name}", it) }
        }
    }

    private fun rootFileExists(path: String): Boolean = runRoot("[ -f ${quote(path)} ]")

    private fun inferScript(name: String): String = when {
        name.contains("繁體", true) || name.contains("繁体", true) -> "繁體"
        name.contains("簡體", true) || name.contains("简体", true) -> "簡體"
        else -> "未判定"
    }

    private fun readScript(path: String, name: String): String {
        val result = executeRoot("cat ${quote("$path/.lcpatch-script")} 2>/dev/null")
        val value = result.output.trim()
        return if (result.success && value in setOf("簡體", "繁體")) value else inferScript(name)
    }

    private fun repairStorage() {
        val aliases = listOf("/sdcard/LCPatch/汉化", "/sdcard/lcpatch/漢化", "/sdcard/LC Patch/漢化", "/sdcard/limbus mod/汉化")
        val migrate = aliases.joinToString(" && ") { old -> "if [ -d ${quote(old)} ]; then cp -Rn ${quote("$old/.")} ${quote(PUBLIC_DOWNLOADS)}; fi" }
        runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && $migrate")
        val legacyList = executeRoot("find ${quote(PUBLIC_DOWNLOADS)} -maxdepth 1 -type f -iname '*.zip' -print")
        if (legacyList.success) legacyList.output.lineSequence().filter(String::isNotBlank).forEach { path ->
            val label = safeName(File(path).nameWithoutExtension)
            val cache = File(downloads, "legacy-$label.zip")
            val prepared = File(staging, "legacy-$label").also { it.deleteRecursively(); it.mkdirs() }
            if (runRoot("cp -f ${quote(path)} ${quote(cache.absolutePath)}")) runCatching {
                unpackLocalize(cache, prepared)
                val target = "$PUBLIC_DOWNLOADS/$label"
                if (runRoot("rm -rf ${quote(target)} && cp -R ${quote(prepared.absolutePath)} ${quote(target)} && chmod -R 0755 ${quote(target)} && rm -f ${quote(path)}")) cache.delete()
            }
        }
    }

    private fun unpackLocalize(zipFile: File, destination: File) {
        var count = 0
        var total = 0L
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relativePath = localizeRelativePath(entry.name) ?: continue
                val target = File(destination, relativePath).canonicalFile
                require(target.path.startsWith(destination.canonicalPath + File.separator)) { "漢化包包含不安全路徑" }
                if (entry.isDirectory) target.mkdirs() else {
                    require(++count <= 30_000) { "漢化包檔案數過多" }
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            total += n; require(total <= 512L * 1024 * 1024) { "漢化包解壓後過大" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
        }
        require(File(destination, "Localize").isDirectory) { "漢化包缺少 Localize 資料夾" }
    }

    fun pcCoverageSummary(): String = prefs.getString("pc_coverage_summary", "重新套用漢化後，會比對 PC 翻譯並建立內建文字索引") ?: ""

    private suspend fun supplementPcTranslations(directory: File, script: String, progress: suspend (ApplyProgress) -> Unit) {
        progress(ApplyProgress("正在比對 PC 版翻譯", 0, 0))
        val reference = File(staging, "pc-reference").also { it.deleteRecursively(); it.mkdirs() }
        val archive = File(context.cacheDir, "lcpatch-pc-reference.zip")
        var sourceUrl = ""
        try {
            val entry = fetchOfficialLatest().single()
            sourceUrl = entry.url
            val part = File(context.cacheDir, "lcpatch-pc-reference.part")
            downloadResumable(entry.url, part, "PC 版翻譯", {})
            java.nio.file.Files.move(part.toPath(), archive.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            unpackLocalize(archive, reference)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            File(directory, ".lcpatch-coverage.txt").writeText("PC 比對未完成：${error.message}\n")
            prefs.edit().putString("pc_coverage_summary", "PC 翻譯比對未完成，已保留所選漢化；詳情見日誌").apply()
            LogRepository.append(context, "WARN", "translation.pc.supplement.failed", "PC 比對未完成，保留所選漢化：${error.message}")
            reference.deleteRecursively()
            archive.delete()
            return
        }
        try {
            val converter = PuaConverter.fromAssets(context)
            val report = supplementTranslationDirectory(directory, File(reference, "Localize/cn"), targetLanguage().prefix,
                decode = { converter.convert(decodeCjkUnicodeEscapes(decodePuaUnicodeEscapes(it)), toPua = false) },
                encode = { value ->
                    val converted = when (script) {
                        "繁體" -> ZhConverterUtil.toTraditional(value)
                        else -> value
                    }
                    converter.convert(converted, toPua = true)
                })
            File(directory, ".lcpatch-coverage.txt").writeText("來源：$sourceUrl\n$report\n")
            prefs.edit().putString("pc_coverage_summary", "PC 翻譯：$report").apply()
            LogRepository.append(context, "INFO", "translation.pc.supplement", report.toString())
        } finally {
            reference.deleteRecursively()
            archive.delete()
        }
    }

    private fun runRoot(command: String): Boolean = executeRoot(command).success

    private fun runRootOutput(command: String): Pair<Boolean, String> {
        val result = executeRoot(command)
        return result.success to result.output
    }

    private fun safeName(value: String): String = safePackName(value)
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

internal fun parseLctaRelease(release: JSONObject): TranslationEntry {
    val tag = release.getString("tag_name")
    require(tag.matches(Regex("[0-9]{10}"))) { "LCTA 版本格式無效" }
    val assets = release.getJSONArray("assets")
    val filename = "LLc-CN-LCTA-$tag.zip"
    val asset = (0 until assets.length()).map(assets::getJSONObject)
        .singleOrNull { it.optString("name") == filename } ?: error("LCTA Release 缺少 ZIP")
    val url = "https://github.com/HZBHZB1234/LCTA_auto_update/releases/download/$tag/$filename"
    require(asset.optString("browser_download_url") == url) { "LCTA 下載網址無效" }
    require(asset.optLong("size") in 1..(64L * 1024 * 1024)) { "LCTA 漢化包大小無效" }
    val digest = asset.optString("digest")
    require(digest.matches(Regex("sha256:[a-f0-9]{64}"))) { "LCTA Release 缺少 SHA-256" }
    return TranslationEntry("漢化-XP", "LCTA 快速漢化", "LCTA Auto Update",
        "以零協會翻譯為基礎，AI 補全新內容；譯文可能有誤。", url, "簡體", tag, digest)
}

internal fun verifyPackSourceDigest(file: File, expected: String) {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    require(actual == expected) { "漢化包 SHA-256 驗證失敗" }
}

internal fun isLegacyRootSimplified(name: String): Boolean =
    Regex("^Root\\s*简体汉化$", RegexOption.IGNORE_CASE).matches(name)

internal fun parseTranslationCatalog(text: String): List<TranslationEntry> {
    var section = ""
    return text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1)
            null
        } else if (line.isBlank() || line.startsWith("#") ||
            !(section == "漢化-XP" || section == "汉化-XP")) null
        else {
            val columns = line.split('|').map(String::trim)
            if (columns.size < 4) null else TranslationEntry(section, columns[0], columns[1], columns[2], columns[3])
        }
    }.toList()
}

internal fun localizeRelativePath(value: String): String? {
    val path = value.replace('\\', '/').trimStart('/')
    val marker = "/Localize/"
    val officialMarker = "/Lang/LLC_zh-CN/"
    if (path.startsWith("LLc-CN-LCTA/")) {
        val relative = path.removePrefix("LLc-CN-LCTA/")
        return relative.takeIf { it.endsWith(".json", ignoreCase = true) && !it.startsWith("Info/", ignoreCase = true) }
            ?.let { "Localize/cn/$it" }
    }
    return when {
        path == "Localize" || path == "Localize/" -> "Localize/"
        path.startsWith("Localize/") -> path
        marker in path -> "Localize/" + path.substringAfter(marker)
        officialMarker in path -> path.substringAfter(officialMarker).takeUnless { it.startsWith("Info/") }?.let { "Localize/cn/"+it }
        else -> null
    }
}

internal fun correctedLanguageFileName(value: String, prefix: String): String {
    if (!value.endsWith(".json", ignoreCase = true)) return value
    val normalizedPrefix = "${prefix.uppercase()}_"
    if(value == "kr_settings-ui-donttranslate.json") return normalizedPrefix+value
    return if (Regex("^(JP|KR|EN)_", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
        value.replace(Regex("^(JP|KR|EN)_", RegexOption.IGNORE_CASE), normalizedPrefix)
    } else {
        normalizedPrefix + value
    }
}

internal fun decodeCjkUnicodeEscapes(value: String): String =
    Regex("(?<!\\\\)\\\\u([0-9a-fA-F]{4})").replace(value) { match ->
        val character = match.groupValues[1].toInt(16).toChar()
        if (character.code in 0x3400..0x9FFF || character.code in 0xF900..0xFAFF) character.toString() else match.value
    }

internal fun decodePuaUnicodeEscapes(value: String): String =
    Regex("(?<!\\\\)\\\\u([0-9a-fA-F]{4})").replace(value) { match ->
        val character = match.groupValues[1].toInt(16).toChar()
        if (character.code in 0xE000..0xF8FF) character.toString() else match.value
    }

internal fun isOfficialLlcUrl(url: String): Boolean = url.startsWith("https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/")

/** Countable work reports real batches; shell/network phases report unknown totals. */
internal suspend fun copyLanguageResources(source: File,destination: File,language: OverrideLanguage,
    includeNonJson: Boolean,progress: suspend (ApplyProgress)->Unit) {
    val files=source.walkTopDown().filter {it.isFile && (includeNonJson || it.extension.equals("json",true))}.toList()
    val stage="正在整理${language.label}覆蓋文件"
    progress(ApplyProgress(stage,0,files.size))
    files.forEachIndexed { index,input ->
        val relative=input.relativeTo(source)
        val name=correctedLanguageFileName(relative.name,language.prefix)
        val parent=relative.parent?.let {File(destination,it)}?:destination
        input.copyTo(File(parent.apply {mkdirs()},name),overwrite=true)
        if(index==files.lastIndex || (index+1)%16==0) progress(ApplyProgress(stage,index+1,files.size))
    }
}


internal fun officialReleaseUrl(value: String?): String? = value?.takeIf {
    it.lineSequence().any {line ->line.trim()=="零協會完整資源與劇情角色名稱已自動整理"}
}?.let {
    Regex("""https://github\.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/[^\s]+""")
        .find(it)?.value
}

internal fun installedPackVersion(metadata: String?,coverage: String?): String? {
    val json=metadata?.takeIf {it.length<8192}?.let {runCatching {JSONObject(it)}.getOrNull()}?.takeIf {it.optInt("schema")==1}
    validPackVersion(json?.optString("version"))?.let {return it}
    releaseAssetVersion(json?.optString("url"))?.let {return it}
    json?.optString("content_id")?.takeIf { it.matches(Regex("sha256:[a-f0-9]{64}")) }?.let { return it }
    return releaseAssetVersion(officialReleaseUrl(coverage))
}

internal fun readLocalPackVersion(directory: File): String = ensurePackIdentity(directory)
