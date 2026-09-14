package com.lcpatch

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.json.JSONObject
import com.github.houbb.opencc4j.util.ZhConverterUtil

data class TranslationEntry(val section: String, val name: String, val author: String, val description: String, val url: String)
data class TransferProgress(val name: String, val bytes: Long, val total: Long, val bytesPerSecond: Long, val finished: Boolean = false)
data class ApplyProgress(val stage: String, val current: Int, val total: Int) {
    val fraction: Float? get() = total.takeIf { it > 0 }?.let { (current.toFloat() / it).coerceIn(0f, 1f) }
}
data class DownloadedTranslation(
    val name: String,
    val path: String,
    val script: String = "未判定",
    val puaPrepared: Boolean = false
)
data class OverrideLanguage(val id: String, val label: String, val prefix: String)
data class TextConversion(val id: String, val label: String)
data class ConversionResult(val scannedFiles: Int, val changedFiles: Int)
data class RuntimeInspection(
    val jsonFiles: Int = 0,
    val matchingFiles: Int = 0,
    val fontBytes: Long = 0
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
        private const val GAME_PACKAGE = "com.ProjectMoon.LimbusCompany"
        private val ROOT_OPERATION_LOCK = Any()
    }
    private val downloads = File(context.filesDir, "translations/downloads").apply { mkdirs() }
    private val staging = File(context.filesDir, "translations/staging").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("translations", Context.MODE_PRIVATE)

    suspend fun fetchCatalog(): List<TranslationEntry> = withContext(Dispatchers.IO) {
        val primary = runCatching {
            connection(SOURCE).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }
        }.map(::parseTranslationCatalog).getOrDefault(emptyList()).filterNot {
            it.name.contains("零協") || it.author.contains("零協") || it.author.contains("都市零協會")
        }
        val community = runCatching { fetchOfficialLatest() }.getOrDefault(emptyList())
        (primary + community).distinctBy { it.name to it.url }
            .also { require(it.isNotEmpty()) { "下載清單格式已變更，請稍後重試" } }
    }

    private fun fetchOfficialLatest(): List<TranslationEntry> {
        val raw = connection(OFFICIAL_LATEST).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }
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
                "都市零協會官方簡體漢化",
                "都市零協會",
                "官方版本 $tag；由 LCPatch 自動轉為 Android 格式",
                asset
            )
        )
    }

    suspend fun download(
        entry: TranslationEntry,
        progress: suspend (TransferProgress) -> Unit,
        preparation: suspend (ApplyProgress) -> Unit = {}
    ): DownloadedTranslation = withContext(Dispatchers.IO) {
        val connection = connection(entry.url)
        val total = connection.contentLengthLong
        val fileName = "${safeName(entry.name)}.zip"
        val part = File(downloads, "$fileName.part")
        val output = File(downloads, fileName)
        var bytes = 0L
        var lastBytes = 0L
        var lastTime = System.nanoTime()
        connection.inputStream.use { input ->
            part.outputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    stream.write(buffer, 0, count); bytes += count
                    val now = System.nanoTime()
                    if (now - lastTime >= 150_000_000L) {
                        val speed = ((bytes - lastBytes) * 1_000_000_000L / (now - lastTime)).coerceAtLeast(0)
                        progress(TransferProgress(fileName, bytes, total, speed))
                        lastBytes = bytes; lastTime = now
                    }
                }
            }
        }
        connection.disconnect()
        require(part.length() > 4 && part.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }) { "下載內容不是 ZIP 漢化包" }
        ZipFile(part).use { require(it.entries().asSequence().any { item -> localizeRelativePath(item.name) != null }) { "漢化包缺少 Localize 資料夾" } }
        if (output.exists()) output.delete()
        require(part.renameTo(output)) { "無法保存下載檔" }
        prefs.edit().putString("download.${output.name}", entry.name).apply()
        val prepared = File(staging, "download-${safeName(entry.name)}").also { it.deleteRecursively(); it.mkdirs() }
        unpackLocalize(output, prepared)
        File(prepared, ".lcpatch-script").writeText(inferScript(entry.name))
        encodeDirectoryToPua(prepared, preparation)
        File(prepared, PUA_MARKER).writeText("1")
        val publicPath = "$PUBLIC_DOWNLOADS/${safeName(entry.name)}"
        require(runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && rm -rf ${quote(publicPath)} && cp -R ${quote(prepared.absolutePath)} ${quote(publicPath)} && chmod -R 0755 ${quote(publicPath)}")) { "無法保存已解壓漢化" }
        output.delete()
        progress(TransferProgress(fileName, bytes, if (total > 0) total else bytes, 0, true))
        LogRepository.append(context, "INFO", "download.completed", "${entry.name} 已下載並解壓到獨立目錄")
        DownloadedTranslation(entry.name, publicPath, inferScript(entry.name), true)
    }

    private suspend fun applyPrepared(raw: File, displayName: String, progress: suspend (ApplyProgress) -> Unit): String {
        val pack = safeName(displayName)
        val unpacked = File(staging, pack)
        unpacked.deleteRecursively(); unpacked.mkdirs()
        require(File(raw, "Localize").isDirectory) { "漢化包缺少 Localize 資料夾" }
        progress(ApplyProgress("正在整理漢化文件", 0, 1))
        normalizeForLanguage(raw, unpacked, targetLanguage())
        val script = File(raw, ".lcpatch-script").takeIf(File::isFile)?.readText()?.trim()?.takeIf { it in setOf("簡體", "繁體") }
            ?: inferScript(displayName)
        File(unpacked, ".lcpatch-script").writeText(script)
        val runtimeFont = File(unpacked, "Localize/cn/ChineseFont.ttf")
        customFontFile()?.copyTo(runtimeFont, overwrite = true) ?: copyBundledPuaFont(runtimeFont)
        validateJsonDirectory(File(unpacked, "Localize/cn"))
        progress(ApplyProgress("正在寫入遊戲目錄", 0, 1))
        val nativeAction = if (translationEnabled()) runtimeSwapCommand(File(unpacked, "Localize/cn").absolutePath)
            else "rm -rf ${quote(NATIVE_DISABLED)} && mv ${quote(NATIVE_COMPAT)} ${quote(NATIVE_DISABLED)} 2>/dev/null || true"
        val command = "am force-stop $GAME_PACKAGE && $nativeAction"
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val response = process.inputStream.bufferedReader().use { it.readText() }
        require(process.waitFor() == 0) { response.ifBlank { "root 套用失敗" } }
        val inspection = inspectRuntime()
        require(inspection.ready) { "遊戲快取驗證失敗：找不到 ${targetLanguage().prefix} 漢化文件" }
        require(inspection.fontReady) { "遊戲快取驗證失敗：中文字型未寫入" }
        progress(ApplyProgress("套用完成", 1, 1))
        prefs.edit().putString("active_name", displayName).putString("active_script", script).apply()
        LogRepository.append(context, "INFO", "translation.applied", "已套用 $displayName")
        return displayName
    }

    fun activeName(): String = prefs.getString("active_name", "尚未選擇") ?: "尚未選擇"
    fun activeScript(): String = prefs.getString("active_script", null)?.takeIf { it in setOf("簡體", "繁體") }
        ?: inferScript(activeName())

    suspend fun downloaded(): List<DownloadedTranslation> = withContext(Dispatchers.IO) {
        repairStorage()
        val process = ProcessBuilder("su", "-c", "find ${quote(PUBLIC_DOWNLOADS)} -mindepth 1 -maxdepth 1 -type d -print").redirectErrorStream(true).start()
        val paths = process.inputStream.bufferedReader().readLines()
        if (process.waitFor() != 0) return@withContext emptyList()
        paths.mapNotNull { original ->
            val oldName = File(original).name
            val cleanName = safeName(oldName)
            val canonical = "$PUBLIC_DOWNLOADS/$cleanName"
            if (original != canonical) runRoot("mv -f ${quote(original)} ${quote(canonical)}")
            DownloadedTranslation(cleanName, canonical, readScript(canonical, cleanName), rootFileExists("$canonical/$PUA_MARKER"))
        }.distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    suspend fun apply(entry: DownloadedTranslation, progress: suspend (ApplyProgress) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val raw = File(staging, "${safeName(entry.name)}.raw").also { it.deleteRecursively() }
        require(runRoot("cp -R ${quote(entry.path)} ${quote(raw.absolutePath)} && chmod -R 0755 ${quote(raw.absolutePath)}")) { "無法讀取已下載漢化目錄" }
        if (!File(raw, PUA_MARKER).isFile) {
            encodeDirectoryToPua(raw, progress)
            File(raw, PUA_MARKER).writeText("1")
            validateJsonDirectory(File(raw, "Localize"))
            require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(raw.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法保存 PUA 轉換結果" }
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
        encodeDirectoryToPua(prepared) {}
        File(prepared, PUA_MARKER).writeText("1")
        val publicPath = "$PUBLIC_DOWNLOADS/$label"
        require(runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && rm -rf ${quote(publicPath)} && cp -R ${quote(prepared.absolutePath)} ${quote(publicPath)} && chmod -R 0755 ${quote(publicPath)}")) { "無法保存到 LCPatch 目錄" }
        cache.delete()
        prefs.edit().putString("download.${cache.name}", label).apply()
        LogRepository.append(context, "INFO", "translation.imported", "已匯入並解壓 $label")
        DownloadedTranslation(label, publicPath, inferScript(label), true)
    }

    fun targetLanguage(): OverrideLanguage {
        val id = prefs.getString("override_language", "jp")
        return LANGUAGES.firstOrNull { it.id == id } ?: LANGUAGES.first()
    }

    fun setTargetLanguage(value: OverrideLanguage) {
        prefs.edit().putString("override_language", value.id).apply()
    }

    internal suspend fun convertDownloaded(entry: DownloadedTranslation, conversion: TextConversion, progress: suspend (ApplyProgress) -> Unit = {}): ConversionResult = withContext(Dispatchers.IO) {
        val work = File(staging, "convert-${safeName(entry.name)}").also { it.deleteRecursively() }
        require(runRoot("cp -R ${quote(entry.path)} ${quote(work.absolutePath)} && chmod -R 0755 ${quote(work.absolutePath)}")) { "無法讀取漢化目錄" }
        val result = convertTextFiles(work, conversion, progress)
        require(result.scannedFiles > 0) { "漢化目錄內沒有 JSON 文件" }
        File(work, ".lcpatch-script").writeText(if (conversion.id == "traditional") "繁體" else "簡體")
        File(work, PUA_MARKER).writeText("1")
        validateJsonDirectory(File(work, "Localize"))
        require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(work.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法寫回轉換後的漢化目錄" }
        LogRepository.append(context, "INFO", "translation.converted", "${entry.name} ${conversion.label}完成：已掃描 ${result.scannedFiles} 個 JSON，內容有變更 ${result.changedFiles} 個")
        result
    }

    fun fontName(): String = prefs.getString("font_name", "內建 PUA 中文字型") ?: "內建 PUA 中文字型"

    fun translationEnabled(): Boolean = prefs.getBoolean("translation_enabled", true)

    fun migrateRuntime() {
        val fallback = File(context.filesDir, "runtime-pua-font.ttf")
        val font = customFontFile() ?: fallback.also { copyBundledPuaFont(it) }
        val cleanup = "rm -rf '/sdcard/limbus mods/zh_cn' ${quote("$PUBLIC_ROOT/目前套用")} ${quote("$PUBLIC_ROOT/.active-next")} ${quote("$PUBLIC_ROOT/.active-old")} ${quote(NATIVE_NEXT)} ${quote(NATIVE_OLD)}"
        val repairFont = "if ${validLanguageDirectoryTest(NATIVE_COMPAT)}; then cp -f ${quote(font.absolutePath)} ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} && chmod 0644 ${quote("$NATIVE_COMPAT/ChineseFont.ttf")}; restorecon -F ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} 2>/dev/null || true; fi"
        runRoot("$cleanup; $repairFont")
    }

    fun prepareGameLaunch(): Boolean {
        val command = if (translationEnabled()) {
            "am force-stop $GAME_PACKAGE && ${validLanguageDirectoryTest(NATIVE_COMPAT)}"
        } else {
            "am force-stop $GAME_PACKAGE"
        }
        return runRoot(command)
    }

    fun inspectRuntime(): RuntimeInspection {
        val prefix = targetLanguage().prefix.uppercase()
        val command = "if [ -d ${quote(NATIVE_COMPAT)} ]; then total=${'$'}(find ${quote(NATIVE_COMPAT)} -type f -iname '*.json' | wc -l); matching=${'$'}(find ${quote(NATIVE_COMPAT)} -type f -iname ${quote("${prefix}_*.json")} | wc -l); font=${'$'}(stat -c %s ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} 2>/dev/null || echo 0); echo \"${'$'}total|${'$'}matching|${'$'}font\"; else echo '0|0|0'; fi"
        val (success, output) = runRootOutput(command)
        if (!success) return RuntimeInspection()
        val values = output.lineSequence().lastOrNull { it.count { char -> char == '|' } == 2 }
            ?.trim()?.split('|') ?: return RuntimeInspection()
        return RuntimeInspection(
            jsonFiles = values.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
            matchingFiles = values.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
            fontBytes = values.getOrNull(2)?.trim()?.toLongOrNull() ?: 0
        )
    }

    suspend fun setTranslationEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
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
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) } ?: error("無法讀取字體文件")
        require(target.length() in 12..64L * 1024 * 1024) { "字體文件大小不正確" }
        File(context.filesDir, if (extension == "ttf") "custom-font.otf" else "custom-font.ttf").delete()
        val publicPath = "$PUBLIC_FONTS/${safeName(displayName.substringBeforeLast('.'))}.$extension"
        require(runRoot("mkdir -p ${quote(PUBLIC_FONTS)} && cp -f ${quote(target.absolutePath)} ${quote(publicPath)} && chmod 0666 ${quote(publicPath)}")) { "無法保存到 LCPatch 字體目錄" }
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
        atomicSwapCommand(source, NATIVE_COMPAT, NATIVE_NEXT, NATIVE_OLD)

    private fun atomicSwapCommand(source: String, destination: String, next: String, old: String): String =
        "mkdir -p ${quote(File(next).parent!!)} && rm -rf ${quote(next)} ${quote(old)} && mkdir -p ${quote(next)} && cp -R ${quote("$source/.")} ${quote(next)} && chmod -R 0755 ${quote(next)} && ${validLanguageDirectoryTest(next)} && if [ -e ${quote(destination)} ]; then mv ${quote(destination)} ${quote(old)}; fi && if mv ${quote(next)} ${quote(destination)}; then chmod -R 0755 ${quote(destination)}; restorecon -RF ${quote(destination)} 2>/dev/null || true; if ${validLanguageDirectoryTest(destination)}; then rm -rf ${quote(old)} ${quote(NATIVE_DISABLED)}; else rm -rf ${quote(destination)}; if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi; else if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi"

    private fun validLanguageDirectoryTest(path: String): String =
        "{ [ -d ${quote(path)} ] && [ -n \"${'$'}(find ${quote(path)} -type f -iname '*.json' -print -quit)\" ]; }"

    private fun normalizeForLanguage(raw: File, output: File, language: OverrideLanguage) {
        val localize = File(raw, "Localize")
        val source = localize.listFiles()?.firstOrNull { candidate ->
            candidate.isDirectory && candidate.name.lowercase() in setOf("cn", "zh_cn", "zh-cn", "jp", "kr", "en", "hant", "zh-tw")
        } ?: localize
        val destination = File(output, "Localize/cn").apply { mkdirs() }
        source.walkTopDown().filter(File::isFile).forEach { input ->
            val relative = input.relativeTo(source)
            val correctedName = correctedLanguageFileName(relative.name, language.prefix)
            val parent = relative.parent?.let { File(destination, it) } ?: destination
            input.copyTo(File(parent.apply { mkdirs() }, correctedName), overwrite = true)
        }
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
        val process = ProcessBuilder("su", "-c", "cat ${quote("$path/.lcpatch-script")} 2>/dev/null").redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText().trim() }
        return if (process.waitFor() == 0 && value in setOf("簡體", "繁體")) value else inferScript(name)
    }

    private fun repairStorage() {
        val aliases = listOf("/sdcard/LCPatch/汉化", "/sdcard/lcpatch/漢化", "/sdcard/LC Patch/漢化", "/sdcard/limbus mod/汉化")
        val migrate = aliases.joinToString(" && ") { old -> "if [ -d ${quote(old)} ]; then cp -Rn ${quote("$old/.")} ${quote(PUBLIC_DOWNLOADS)}; fi" }
        runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && $migrate")
        val legacyList = ProcessBuilder("su", "-c", "find ${quote(PUBLIC_DOWNLOADS)} -maxdepth 1 -type f -iname '*.zip' -print").redirectErrorStream(true).start()
        val legacyPaths = legacyList.inputStream.bufferedReader().readLines()
        if (legacyList.waitFor() == 0) legacyPaths.forEach { path ->
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

    private fun runRoot(command: String): Boolean = synchronized(ROOT_OPERATION_LOCK) {
        try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0
        } catch (_: Throwable) { false }
    }

    private fun runRootOutput(command: String): Pair<Boolean, String> = synchronized(ROOT_OPERATION_LOCK) {
        try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            (process.waitFor() == 0) to output
        } catch (_: Throwable) { false to "" }
    }

    private fun connection(value: String): HttpURLConnection = (URL(value).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LCPatch/${BuildConfig.VERSION_NAME}")
        require(responseCode in 200..299) { "伺服器回應 $responseCode" }
    }
    private fun safeName(value: String): String = value.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "translation" }
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

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
    return when {
        path == "Localize" || path == "Localize/" -> "Localize/"
        path.startsWith("Localize/") -> path
        marker in path -> "Localize/" + path.substringAfter(marker)
        officialMarker in path -> "Localize/cn/" + path.substringAfter(officialMarker)
        else -> null
    }
}

internal fun correctedLanguageFileName(value: String, prefix: String): String {
    if (!value.endsWith(".json", ignoreCase = true)) return value
    val normalizedPrefix = "${prefix.uppercase()}_"
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
