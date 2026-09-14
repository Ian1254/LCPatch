package com.lcpatch

internal object Versioning {
    fun compare(left: String, right: String): Int {
        val a = SemVer.parse(left)
        val b = SemVer.parse(right)
        compareValues(a.major, b.major).takeIf { it != 0 }?.let { return it }
        compareValues(a.minor, b.minor).takeIf { it != 0 }?.let { return it }
        compareValues(a.patch, b.patch).takeIf { it != 0 }?.let { return it }
        if (a.pre.isEmpty() && b.pre.isNotEmpty()) return 1
        if (a.pre.isNotEmpty() && b.pre.isEmpty()) return -1
        val count = maxOf(a.pre.size, b.pre.size)
        repeat(count) { index ->
            val av = a.pre.getOrNull(index) ?: return -1
            val bv = b.pre.getOrNull(index) ?: return 1
            val ai = av.toIntOrNull()
            val bi = bv.toIntOrNull()
            val result = when {
                ai != null && bi != null -> compareValues(ai, bi)
                ai != null -> -1
                bi != null -> 1
                else -> av.compareTo(bv)
            }
            if (result != 0) return result
        }
        return 0
    }

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val pre: List<String>
    ) {
        companion object {
            fun parse(value: String): SemVer {
                val normalized = value.removePrefix("v").substringBefore('+')
                val coreAndPre = normalized.split('-', limit = 2)
                val core = coreAndPre[0].split('.')
                require(core.size == 3) { "無效版本號：$value" }
                return SemVer(
                    major = core[0].toInt(),
                    minor = core[1].toInt(),
                    patch = core[2].toInt(),
                    pre = coreAndPre.getOrNull(1)?.split('.')?.filter { it.isNotEmpty() } ?: emptyList()
                )
            }
        }
    }
}
