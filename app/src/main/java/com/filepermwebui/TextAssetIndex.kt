package com.lcpatch

import java.io.File

/** Explicit allowlist: a Unity asset can only select a JSON in the applied pack. */
internal fun writeTextAssetIndex(directory: File): Int {
    val candidates = linkedMapOf<String, MutableSet<String>>()
    directory.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
        val path = file.relativeTo(directory).invariantSeparatorsPath
        require(path.length <= 1024 && path.none { it in "\t\r\n:" }) { "不支援的漢化檔名：$path" }
        val normalized = path.removeSuffix(".json").replace(Regex("(^|/)(JP|KR|EN)_"), "$1")
        listOf(normalized, normalized.substringAfterLast('/')).distinct().forEach { key ->
            candidates.getOrPut(key) { linkedSetOf() }.add(path)
        }
    }
    val unique = candidates.filterValues { it.size == 1 }.toSortedMap()
    File(directory, ".lcpatch-text-assets.tsv").writeText(unique.entries.joinToString("") { (key, paths) ->
        "$key\t${paths.single()}\n"
    })
    return unique.size
}
