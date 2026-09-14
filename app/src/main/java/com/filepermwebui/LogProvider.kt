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
    }

    private val lock = Any()
    private val store: File get() = File(requireNotNull(context).filesDir, "events.jsonl")

    override fun onCreate() = true

    private fun authorized(): Boolean {
        val context = context ?: return false
        val uid = Binder.getCallingUid()
        if (uid == context.applicationInfo.uid) return true
        return context.packageManager.getPackagesForUid(uid)?.contains(GAME) == true
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
                    val lines = if (store.isFile) store.readLines().takeLast(MAX_ENTRIES - 1) else emptyList()
                    store.parentFile?.mkdirs()
                    store.writeText((lines + event.toString()).joinToString("\n", postfix = "\n"))
                    context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY"), null)
                    Bundle().apply { putBoolean("ok", true) }
                }
                "read" -> Bundle().apply {
                    putString("events", JSONArray(if (store.isFile) store.readLines().takeLast(MAX_ENTRIES).map(::JSONObject) else emptyList<JSONObject>()).toString())
                }
                "clear" -> Bundle().apply {
                    if (store.exists()) store.writeText("")
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
