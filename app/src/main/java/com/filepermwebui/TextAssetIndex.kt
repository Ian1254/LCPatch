package com.lcpatch

import java.io.File
import java.util.UUID

internal const val TEXT_ASSET_INDEX_NAME = ".lcpatch-text-assets.tsv"
internal const val TEXT_ASSET_INDEX_LIMIT = 4 * 1024 * 1024
internal const val TEXT_ASSET_FILE_LIMIT = 30_000
internal const val TEXT_ASSET_INVENTORY_END = "LCPATCH_INDEX_COMPLETE\u0000"
internal data class TextAssetIndex(val text: String, val entries: Int)

/** Explicit allowlist: a Unity asset can only select a JSON in the applied pack. */
internal fun buildTextAssetIndex(paths: Sequence<String>): TextAssetIndex {
    val candidates = linkedMapOf<String, MutableSet<String>>()
    var count = 0
    paths.forEach { path ->
        require(++count <= TEXT_ASSET_FILE_LIMIT) { "漢化包檔案數過多" }
        require(path.toByteArray(Charsets.UTF_8).size <= 1024 && path.endsWith(".json") &&
            path.none { it in "\u0000\t\r\n:\\" } && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
            "不支援的漢化檔名"
        }
        val normalized = path.removeSuffix(".json").replace(Regex("(^|/)(JP|KR|EN)_"), "$1")
        listOf(normalized, normalized.substringAfterLast('/')).distinct().forEach { key ->
            candidates.getOrPut(key) { linkedSetOf() }.add(path)
        }
    }
    val unique = candidates.filterValues { it.size == 1 }.toSortedMap()
    require(unique.size <= TEXT_ASSET_FILE_LIMIT) { "內建文字索引項目過多" }
    val text = unique.entries.joinToString("") { (key, paths) -> "$key\t${paths.single()}\n" }
    require(text.toByteArray(Charsets.UTF_8).size <= TEXT_ASSET_INDEX_LIMIT) { "內建文字索引過大" }
    return TextAssetIndex(text, unique.size)
}

internal fun writeTextAssetIndex(directory: File): Int {
    val index = buildTextAssetIndex(directory.walkTopDown().filter { it.isFile && it.extension == "json" }
        .map { it.relativeTo(directory).invariantSeparatorsPath })
    File(directory, TEXT_ASSET_INDEX_NAME).writeText(index.text)
    return index.entries
}

internal fun indexShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
internal data class RuntimeIndexRepair(val entries: Int, val changed: Boolean)

/** Only path inventory crosses root; never copy or decode the installed translations. */
@Synchronized
internal fun repairRuntimeTextAssetIndex(
    directory: String,
    temporaryDirectory: File,
    run: (String) -> RootCommandResult
): RuntimeIndexRepair? {
    val target = indexShellQuote(directory)
    val inventory = run("if [ ! -d $target ]; then exit 0; fi; [ ! -L $target ] && cd $target && stat -c '%d:%i:%Y' . && find . -type f -name '*.json' -print0 && printf 'LCPATCH_INDEX_COMPLETE\\0'")
    check(inventory.success && !inventory.truncated && !inventory.timedOut) { "無法完整讀取漢化索引檔案清單" }
    if (inventory.output.isEmpty()) return null
    check(inventory.output.endsWith(TEXT_ASSET_INVENTORY_END)) { "漢化索引檔案清單未完整結束" }
    val separator = inventory.output.indexOf('\n')
    check(separator > 0) { "漢化索引檔案清單格式錯誤" }
    val identity = inventory.output.substring(0, separator).trim()
    check(Regex("[0-9]+:[0-9]+:[0-9]+").matches(identity)) { "漢化索引目錄識別失敗" }
    val paths = inventory.output.substring(separator + 1).removeSuffix(TEXT_ASSET_INVENTORY_END)
    check(paths.isEmpty() || paths.endsWith('\u0000')) { "漢化索引檔案清單不完整" }
    val index = buildTextAssetIndex(paths.splitToSequence('\u0000').filter { it.isNotEmpty() }.map {
        check(it.startsWith("./")) { "漢化索引路徑格式錯誤" }
        it.removePrefix("./")
    })
    if (index.entries == 0) return null
    temporaryDirectory.mkdirs()
    val source = File.createTempFile("runtime-index-", ".tsv", temporaryDirectory)
    val nextName = ".lcpatch-text-assets-${UUID.randomUUID()}.tmp"
    val next = indexShellQuote(nextName)
    val destination = indexShellQuote(TEXT_ASSET_INDEX_NAME)
    try {
        source.writeText(index.text)
        // A concurrent apply/toggle replaces the directory. Never publish an old inventory into it.
        val sameDirectory = "[ ! -L $target ]; [ \"\$(stat -c '%d:%i:%Y' $target)\" = ${indexShellQuote(identity)} ]"
        val command = "set -e; $sameDirectory; cd $target; [ ! -d $destination ]; if [ ! -L $destination ] && cmp -s ${indexShellQuote(source.absolutePath)} $destination; then " +
            "chmod 0644 $destination; restorecon -F $destination 2>/dev/null || true; echo unchanged; else " +
            "trap 'rm -f $nextName' EXIT; cp ${indexShellQuote(source.absolutePath)} $next; chmod 0644 $next; " +
            "restorecon -F $next 2>/dev/null || true; " +
            // Creating the temporary file changes directory mtime, so the second guard checks inode only.
            "[ \"\$(stat -c '%d:%i' $target)\" = ${indexShellQuote(identity.substringBeforeLast(':'))} ]; " +
            "mv -f $next $destination; echo repaired; fi"
        val installed = run(command)
        check(installed.success && !installed.truncated && !installed.timedOut) { "無法原子更新內建文字索引，保留原索引" }
        val outcome = installed.output.trim()
        check(outcome == "repaired" || outcome == "unchanged") { "內建文字索引更新結果不明" }
        return RuntimeIndexRepair(index.entries, outcome == "repaired")
    } finally {
        source.delete()
    }
}
