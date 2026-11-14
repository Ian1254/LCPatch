package com.lcpatch

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class CoverageReport(var addedFiles: Int = 0, var addedRecords: Int = 0, var filledFields: Int = 0, var unresolvedFields: Int = 0,
    var placeholderConflicts: Int = 0, var alignedFields: Int = 0, var speakerFields: Int = 0, var unresolvedSpeakers: Int = 0) {
    override fun toString() = "新增檔案 $addedFiles；新增條目 $addedRecords；補齊文字 $filledFields；與 PC 一致 $alignedFields；待確認文字 $unresolvedFields；格式參數不符 $placeholderConflicts；角色名稱 $speakerFields；待確認角色 $unresolvedSpeakers"
}

// Display fields observed in LLC 2026111301. Structural IDs, model/voice keys,
// color codes and other game data are intentionally not text candidates.
private val translatedFields = setOf(
    "content", "desc", "title", "name", "nickName", "teller", "description", "text", "summary",
    "subTitle", "subtitle", "displayName", "dlg", "speaker", "place", "simpleDesc", "flavor",
    "message", "abName", "messageDesc", "story", "behaveDesc", "eventDesc", "prevDesc", "subDesc",
    "nameWithTitle", "codeName", "clue", "dialog", "usage", "statText", "panicName", "panicDescription",
    "lowMoraleDescription", "shortName", "openCondition", "relatedChapterText", "company", "area",
    "chaptertitle", "timeline", "sentence", "teacher", "mainText", "subText", "rawDesc", "longName",
    "abnormalityName", "specialName", "skinItemTitle", "skinItemDesc", "parttitle", "askLevelUp"
) + (1..8).map { "goalDescription$it" }
private val foreignScript = Regex("[\\u3040-\\u30ff\\uac00-\\ud7af]")
private val han = Regex("[\\u3400-\\u9fff]")
// Android's ICU regex parser rejects an unescaped closing brace here, even
// though java.util.regex on the host JVM accepts it. This must stay escaped:
// otherwise class initialization fails before applying any translation.
private val parameters = Regex("\\{[^{}]+\\}|</?[^>]+>")
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
            // An exact upstream match is alignment, not evidence of a missing
            // translation: Latin names and notation such as E.G.O are valid.
            if (key in translatedFields && current is String && candidate == current) {
                report.alignedFields++
                continue
            }
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
        val parent = relative.parent?.let { File(target, it) } ?: target
        val normalized = File(parent, correctedLanguageFileName(relative.name, prefix))
        // LLC's literal `kr_settings-ui-donttranslate` basename is preserved
        // by the builder as JP_kr_settings..., whereas older import code may
        // normalize it to JP_settings.... Prefer an already present exact
        // resource instead of creating a second, unused filename.
        val literalName = File(parent, "${prefix.uppercase()}_${relative.name}")
        val destination = if (!normalized.exists() && literalName.exists()) literalName else normalized
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
    val speakers=completeScenarioSpeakers(target,decode,encode)
    report.speakerFields=speakers.fields
    report.unresolvedSpeakers=speakers.unresolved
    return report
}

internal data class SpeakerCoverage(var fields: Int=0,var unresolved: Int=0)

/** Resolve story speakers by the upstream's exact model ID, never by row order.
 * Existing explicit labels, including masked identities, are retained. Conflicting model IDs
 * are rejected rather than choosing an arbitrary translation. */
internal fun completeScenarioSpeakers(root: File, decode: (String)->String = { it }, encode: (String)->String = { it }): SpeakerCoverage {
    val result=SpeakerCoverage()
    root.walkTopDown().filter { it.isFile && it.name.removePrefix("JP_").removePrefix("KR_").removePrefix("EN_")=="ScenarioModelCodes-AutoCreated.json" }.toList().forEach { models ->
        val rows=JSONObject(decode(models.readText())).optJSONArray("dataList") ?: return@forEach
        val names=mutableMapOf<String,Pair<String,String>>()
        val conflicts=mutableSetOf<String>()
        for(i in 0 until rows.length()) {
            val row=rows.optJSONObject(i) ?: continue
            val id=row.opt("id") as? String ?: continue
            val name=row.opt("name") as? String ?: continue
            val nick=row.opt("nickName") as? String ?: ""
            val pair=name to nick
            if(names.put(id,pair)?.let { it!=pair }==true) conflicts.add(id)
        }
        conflicts.forEach(names::remove)
        val stories=File(models.parentFile,"StoryData")
        stories.walkTopDown().filter { it.isFile && it.extension.equals("json",true) }.forEach { file ->
            val value=org.json.JSONTokener(decode(file.readText())).nextValue()
            val before=result.fields
            fun visit(node: Any?) {
                when(node) {
                    is JSONArray -> for(i in 0 until node.length()) visit(node.opt(i))
                    is JSONObject -> {
                        val model=(node.opt("model") as? String)?.takeIf(String::isNotBlank)
                        if(model!=null && node.has("content")) {
                            val pair=names[model]
                            if(pair==null) {
                                if(!node.has("teller") || !node.has("title")) result.unresolved++
                            } else {
                                listOf("teller" to pair.first,"title" to pair.second).forEach { (key,text) ->
                                    val old=node.opt(key)
                                    if(old==null) {
                                        if(old==null || parameters.findAll(old as String).map { it.value }.toList()==parameters.findAll(text).map { it.value }.toList()) {
                                            if(old!=text) { node.put(key,text); result.fields++ }
                                        }
                                    }
                                }
                            }
                        }
                        node.keys().asSequence().toList().forEach { key ->
                            val child=node.opt(key)
                            if(child is JSONArray || child is JSONObject) visit(child)
                        }
                    }
                }
            }
            visit(value)
            if(result.fields>before) file.writeText(encode(value.toString()))
        }
    }
    return result
}
