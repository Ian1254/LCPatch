package com.lcpatch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

/** Writes the game's native language files using an explicitly granted document tree. */
internal class GameDocumentStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("translations", Context.MODE_PRIVATE)

    fun selected(): Uri? = prefs.getString("game_tree", null)?.let(Uri::parse)

    fun authorized(): Boolean = selected()?.let { uri ->
        runCatching { tree(uri); true }.getOrDefault(false)
    } ?: false

    fun select(uri: Uri) {
        val id = DocumentsContract.getTreeDocumentId(uri).replace('\\', '/').trimEnd('/')
        require(id.endsWith("Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize")) {
            "請選擇遊戲 files/Assets/Resources_moved/Localize 資料夾"
        }
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val tree = DocumentFile.fromTreeUri(context, uri) ?: error("無法開啟遊戲資料夾")
        require(tree.canRead() && tree.canWrite()) { "遊戲資料夾未授予讀寫權限" }
        prefs.edit().putString("game_tree", uri.toString()).apply()
    }

    private fun tree(uri: Uri): DocumentFile = DocumentFile.fromTreeUri(context, uri)
        ?.takeIf { it.isDirectory && it.canRead() && it.canWrite() }
        ?: error("遊戲資料夾授權已失效，請重新選擇")

    private fun find(root: DocumentFile, path: String, create: Boolean): DocumentFile? {
        val parts = path.split('/')
        require(parts.all { it.isNotBlank() && it !in setOf(".", "..") && '\\' !in it })
        var parent = root
        parts.dropLast(1).forEach { part ->
            parent = parent.findFile(part) ?: if (create) parent.createDirectory(part)
                ?: error("無法建立遊戲子目錄") else return null
            require(parent.isDirectory) { "遊戲路徑不是資料夾" }
        }
        return parent.findFile(parts.last()) ?: if (create)
            parent.createFile("application/json", parts.last()) ?: error("無法建立遊戲文件") else null
    }

    private fun write(target: DocumentFile, source: File) {
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: error("無法寫入 ${target.name}")
        val actual = context.contentResolver.openInputStream(target.uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest()
        } ?: error("無法驗證 ${target.name}")
        val expected = source.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest()
        }
        check(actual.contentEquals(expected)) { "遊戲文件寫入驗證失敗：${target.name}" }
    }

    suspend fun apply(source: File, language: OverrideLanguage, progress: suspend (ApplyProgress) -> Unit) {
        val uri = selected() ?: error("請先授權遊戲 Localize 資料夾；在設定中選擇載入位置")
        val root = tree(uri)
        val languageFolder = root.findFile(language.id)
        require(languageFolder?.isDirectory == true && languageFolder.listFiles().isNotEmpty()) {
            "請先在遊戲切換至 ${language.label} 並下載語言資源，再套用漢化"
        }
        val files = source.walkTopDown().filter { it.isFile && it.extension.equals("json", true) }.toList()
        require(files.isNotEmpty()) { "漢化包沒有 JSON 文件" }
        files.forEachIndexed { index, file ->
            val path = "${language.id}/${file.relativeTo(source).invariantSeparatorsPath}"
            write(requireNotNull(find(root, path, true)), file)
            progress(ApplyProgress("正在寫入原生語言文件", index + 1, files.size))
        }
    }
}
