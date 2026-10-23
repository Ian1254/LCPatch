package com.lcpatch

import java.io.File

internal fun nativeExportPath(language: OverrideLanguage): String {
    require(language.id in setOf("jp", "kr")) { "無 root 模式只支援日語與韓語" }
    return "Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/${language.id}"
}

internal fun nativeLanguageSource(localize: File, language: OverrideLanguage): File {
    require(language.id in setOf("jp", "kr"))
    return localize.listFiles()?.singleOrNull { it.isDirectory && it.name.equals(language.id, true) }
        ?: error("請使用包含 Localize/${language.id} 的無 root 專用漢化包；此包不適用 ${language.label}")
}

internal fun safePackName(value: String): String = value
    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .trim().take(80).trimEnd('.')
    .takeIf { it.isNotBlank() && !it.startsWith('.') } ?: "translation"
