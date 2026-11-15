package com.lcpatch

/** Preserve SAF; older Shizuku preferences now use Stellar's compatible service. */
internal fun normalizeNativeBackend(value: String?): String = if(value=="saf") "saf" else "stellar"
