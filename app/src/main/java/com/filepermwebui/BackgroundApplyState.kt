package com.lcpatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class BackgroundApplySnapshot(
    val busy: Boolean = false,
    val packPath: String? = null,
    val progress: ApplyProgress? = null,
    val result: String? = null,
    val failed: Boolean = false,
    val revision: Long = 0,
    val waiting: Boolean = false
)

/** Shared by the foreground service and every Activity instance. */
internal object BackgroundApplyState {
    private val mutable = MutableStateFlow(BackgroundApplySnapshot())
    val state = mutable.asStateFlow()

    fun prepared(path: String) {
        mutable.update { if(it.busy) it else BackgroundApplySnapshot(packPath=path, revision=it.revision, waiting=true) }
    }

    fun clearPrepared() {
        mutable.update { if(it.busy) it else it.copy(waiting=false) }
    }

    fun begin(path: String): Boolean {
        while (true) {
            val old = mutable.value
            if (old.busy) return false
            if (mutable.compareAndSet(old, BackgroundApplySnapshot(true, path,
                    ApplyProgress("正在準備漢化文件", 0, 0), revision = old.revision))) return true
        }
    }

    fun progress(value: ApplyProgress) { mutable.update { it.copy(progress = value) } }
    fun finish(message: String, failed: Boolean) {
        mutable.update { it.copy(busy = false, result = message, failed = failed, revision = it.revision + 1) }
    }
}
