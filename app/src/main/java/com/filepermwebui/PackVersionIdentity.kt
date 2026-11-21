package com.lcpatch

import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.zip.ZipFile

private val numberedPackVersion = Regex("v?[0-9]+")
private val dottedPackVersion = Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.+-]+)?")

internal fun isNewerPackVersion(candidate: String?, current: String?): Boolean {
    val newer = validPackVersion(candidate) ?: return false
    val installed = validPackVersion(current) ?: return false
    return when {
        numberedPackVersion.matches(newer) && numberedPackVersion.matches(installed) ->
            newer.removePrefix("v").toBigInteger() > installed.removePrefix("v").toBigInteger()
        dottedPackVersion.matches(newer) && dottedPackVersion.matches(installed) ->
            runCatching { Versioning.compare(newer, installed) > 0 }.getOrDefault(false)
        else -> false
    }
}

internal fun downloadPackAction(entry: TranslationEntry, downloaded: DownloadedTranslation?): String = when {
    downloaded == null -> "下載並套用"
    isNewerPackVersion(entry.version, downloaded.version) -> "更新並套用"
    else -> "重新下載並套用"
}

internal fun validPackVersion(value: String?): String? = value?.trim()?.takeIf {
    it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")) &&
        it.lowercase() !in setOf("latest", "null", "unknown")
}

internal fun releaseAssetVersion(url: String?): String? = runCatching {
    val uri=URI(url ?: return null)
    if(uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null) return null
    val parts=uri.rawPath.split('/')
    if(parts.size != 7 || parts[3] != "releases" || parts[4] != "download") return null
    validPackVersion(URLDecoder.decode(parts[5], "UTF-8"))
}.getOrNull()

internal fun contentPackIdentity(records: List<Pair<String,String>>): String {
    require(records.isNotEmpty()) {"漢化包沒有可辨識的文字資源"}
    val digest=MessageDigest.getInstance("SHA-256")
    records.sortedBy {it.first}.forEach {(path,sha) ->
        digest.update(path.toByteArray(Charsets.UTF_8));digest.update(0.toByte())
        digest.update(sha.toByteArray(Charsets.US_ASCII));digest.update(0.toByte())
    }
    return "sha256:"+digest.digest().joinToString("") {"%02x".format(it)}
}

internal fun directoryPackIdentity(directory: File): String = contentPackIdentity(
    File(directory,"Localize").walkTopDown().filter {it.isFile && it.extension.equals("json",true) && !it.name.startsWith(".")}
        .map {file ->
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use {input -> val bytes=ByteArray(64*1024);while(true) {val n=input.read(bytes);if(n<0) break;digest.update(bytes,0,n)}}
            file.relativeTo(directory).invariantSeparatorsPath to digest.digest().joinToString("") {"%02x".format(it)}
        }.toList())

private fun manifestVersion(text: String): String? {
    if(text.length>8192) return null
    validPackVersion(text)?.let {return it}
    val json=runCatching {JSONObject(text)}.getOrNull() ?: return null
    return listOf("translationVersion","translation_version","version","tag_name").firstNotNullOfOrNull {validPackVersion(json.optString(it))}
}

internal fun archivePackVersion(archive: File): String? = ZipFile(archive).use {zip ->
    zip.entries().asSequence().filter {!it.isDirectory && it.size in 1..8192 &&
        it.name.substringAfterLast('/').lowercase() in setOf("version.txt","version.json","manifest.json",".lcpatch-upstream.json")}
        .mapNotNull {entry -> zip.getInputStream(entry).bufferedReader().use {manifestVersion(it.readText())}}.firstOrNull()
}

internal fun ensurePackIdentity(directory: File, url: String?=null, version: String?=null) : String {
    val file=File(directory,".lcpatch-upstream.json")
    val metadata=runCatching {JSONObject(file.takeIf {it.isFile && it.length()<8192}?.readText() ?: "{}")}.getOrDefault(JSONObject())
    metadata.put("schema",1)
    url?.let {metadata.put("url",it)}
    val declared=validPackVersion(version) ?: installedPackVersion(metadata.toString(),null)?.takeUnless {it.startsWith("sha256:")}
        ?: listOf("version.txt","version.json","manifest.json").firstNotNullOfOrNull {name ->
            File(directory,name).takeIf {it.isFile && it.length()<8192}?.readText()?.let(::manifestVersion)
        }
    if(declared!=null) metadata.put("version",declared)
    if(declared==null) metadata.put("content_id",directoryPackIdentity(directory))
    file.writeText(metadata.toString())
    return declared ?: metadata.getString("content_id")
}

internal fun packVersionLabel(value: String?): String = when {
    value==null -> "版本資料無法讀取"
    value.startsWith("sha256:") -> "內容識別 ${value.removePrefix("sha256:").take(16)}"
    else -> "版本 $value"
}

internal fun matchedSourceVersion(candidate: String, reference: String, version: String): String? =
    if(candidate.matches(Regex("sha256:[a-f0-9]{64}")) && candidate==reference) validPackVersion(version) else null
