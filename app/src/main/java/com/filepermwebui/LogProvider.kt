package com.lcpatch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class LogProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.lcpatch.logs"
        const val GAME = "com.ProjectMoon.LimbusCompany"
        private const val MAX_ENTRIES = 500
        private const val TRIM_EVERY_APPENDS = 32
        private const val TRIM_SIZE_BYTES = 512 * 1024L
    }

    private val lock = Any()
    private val store: File get() = File(requireNotNull(context).filesDir, "events.jsonl")
    private var appendsSinceTrim = 0

    override fun onCreate() = true

    private fun authorized(): Boolean {
        val context = context ?: return false
        val uid = Binder.getCallingUid()
        if (uid == context.applicationInfo.uid) return true
        return context.packageManager.getPackagesForUid(uid)?.contains(GAME) == true
    }

    private fun trimIfNeeded(force: Boolean = false) {
        if (!store.isFile) return
        if (!force && appendsSinceTrim < TRIM_EVERY_APPENDS && store.length() < TRIM_SIZE_BYTES) return
        val lines = store.readLines().takeLast(MAX_ENTRIES)
        store.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
        appendsSinceTrim = 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (!authorized()) throw SecurityException("caller is not LCPatch or Limbus Company")
        return synchronized(lock) {
            when (method) {
                "append" -> {
                    val event = JSONObject()
                        .put("time", Instant.now().toString())
                        .put("level", extras?.getString("level") ?: "INFO")
                        .put("code", extras?.getString("code") ?: "event")
                        .put("message", extras?.getString("message") ?: "")
                        .put("process", extras?.getString("process") ?: "")
                    store.parentFile?.mkdirs()
                    store.appendText(event.toString() + "\n")
                    appendsSinceTrim++
                    trimIfNeeded()
                    context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY"), null)
                    Bundle().apply { putBoolean("ok", true) }
                }
                "read" -> Bundle().apply {
                    trimIfNeeded(force = true)
                    putString("events", JSONArray(if (store.isFile) store.readLines().takeLast(MAX_ENTRIES).map(::JSONObject) else emptyList<JSONObject>()).toString())
                }
                "clear" -> Bundle().apply {
                    if (store.exists()) store.writeText("")
                    appendsSinceTrim = 0
                    context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY"), null)
                    putBoolean("ok", true)
                }
                else -> super.call(method, arg, extras) ?: Bundle.EMPTY
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
