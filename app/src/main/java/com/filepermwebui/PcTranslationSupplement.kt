package com.lcpatch

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class CoverageReport(var addedFiles: Int = 0, var addedRecords: Int = 0, var filledFields: Int = 0, var unresolvedFields: Int = 0,
    var placeholderConflicts: Int = 0) {
    override fun toString() = "新增檔案 $addedFiles；新增條目 $addedRecords；補齊文字 $filledFields；待確認文字 $unresolvedFields；格式參數不符 $placeholderConflicts"
}

private val translatedFields = setOf("content", "desc", "title", "name", "nickName", "teller", "description", "text", "summary", "subTitle", "subtitle", "displayName")
private val foreignScript = Regex("[\\u3040-\\u30ff\\uac00-\\ud7af]")
private val han = Regex("[\\u3400-\\u9fff]")
private val parameters = Regex("\\{[^{}]+}|</?[^>]+>")
private fun needsTranslation(value: String) = value.isBlank() || foreignScript.containsMatchIn(value) || !han.containsMatchIn(value)
private fun rowId(row: JSONObject): String? = when (val id = row.opt("id")) {
    is String -> "s:$id"
    is Number -> "n:$id"
    else -> null
}

/** Preserve existing Chinese wording, IDs and game structure; fill by exact ID, never by array position. */
internal fun supplementJson(target: Any, reference: Any?, report: CoverageReport, allowAddRecords: Boolean = false) {
    if (target is JSONObject) {
        val other = reference as? JSONObject
        val keys = target.keys().asSequence().toList().toMutableSet()
        other?.keys()?.asSequence()?.filter { it in translatedFields }?.forEach(keys::add)
        for (key in keys) {
            val current = target.opt(key)
            val candidate = other?.opt(key)
            if (key in translatedFields && (current == null || current is String) &&
                needsTranslation(current as? String ?: "")) {
                if (candidate is String && candidate.isNotBlank() && candidate != current && han.containsMatchIn(candidate)) {
                    val expected = parameters.findAll(current as? String ?: "").map { it.value }.sorted().toList()
                    val actual = parameters.findAll(candidate).map { it.value }.sorted().toList()
                    if (current == null || expected == actual) {
                        target.put(key, candidate); report.filledFields++
                    } else report.placeholderConflicts++
                } else if (current is String && current.any(Char::isLetter)) report.unresolvedFields++
            } else if (current is JSONObject || current is JSONArray) supplementJson(current, candidate, report, key == "dataList" || key == "list")
        }
    } else if (target is JSONArray) {
        val other = reference as? JSONArray
        val byId = mutableMapOf<String, JSONObject>()
        val duplicates = mutableSetOf<String>()
        if (other != null) for (index in 0 until other.length()) {
            val row = other.optJSONObject(index) ?: continue
            val id = rowId(row) ?: continue
            if (byId.put(id, row) != null) duplicates.add(id)
        }
        for (index in 0 until target.length()) {
            val row = target.optJSONObject(index) ?: continue
            val id = rowId(row)
            // ID-less nested structures require manual mapping, not positional guesses.
            supplementJson(row, if (id != null && id !in duplicates) byId[id] else null, report)
        }
        if (allowAddRecords) {
            val existingIds = (0 until target.length()).mapNotNull { target.optJSONObject(it)?.let(::rowId) }.toSet()
            // Only ID-addressed resource lists can be extended. Positional arrays
            // and duplicate IDs never gain guessed rows.
            if (existingIds.size == target.length()) byId.forEach { (id, row) ->
                if (id !in existingIds && id !in duplicates) {
                    target.put(JSONObject(row.toString())); report.addedRecords++
                }
            }
        }
    }
}

internal fun supplementTranslationDirectory(target: File, reference: File, prefix: String,
    decode: (String) -> String, encode: (String) -> String): CoverageReport {
    val report = CoverageReport()
    reference.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.path }.forEach { pc ->
        val relative = pc.relativeTo(reference)
        if (relative.invariantSeparatorsPath.startsWith("Info/")) return@forEach
        val destination = File(target, relative.parent?.let { "$it/${correctedLanguageFileName(relative.name, prefix)}" }
            ?: correctedLanguageFileName(relative.name, prefix))
        val pcJson = org.json.JSONTokener(pc.readText()).nextValue()
        if (!destination.exists()) {
            destination.parentFile?.mkdirs()
            destination.writeText(encode(pcJson.toString()))
            report.addedFiles++
        } else {
            val original = decode(destination.readText())
            val json = org.json.JSONTokener(original).nextValue()
            val before = report.filledFields + report.addedRecords
            supplementJson(json, pcJson, report)
            if (report.filledFields + report.addedRecords > before) destination.writeText(encode(json.toString()))
        }
    }
    return report
}
