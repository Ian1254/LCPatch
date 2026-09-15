package com.lcpatch

import java.net.HttpURLConnection
import java.net.URL

internal object HttpClient {
    fun open(
        value: String,
        rangeStart: Long? = null,
        accept: String? = null,
        useCaches: Boolean = false
    ): HttpURLConnection = (URL(value).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        this.useCaches = useCaches
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "LCPatch/${BuildConfig.VERSION_NAME} (Android)")
        if (accept != null) setRequestProperty("Accept", accept)
        if (rangeStart != null && rangeStart > 0L) {
            setRequestProperty("Range", "bytes=$rangeStart-")
        }
        val code = responseCode
        require(code in 200..299) { "伺服器回應 $code" }
    }
}
