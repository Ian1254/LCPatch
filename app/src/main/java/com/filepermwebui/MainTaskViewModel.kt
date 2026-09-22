package com.lcpatch

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

enum class UiNoticeKind { Info, Success, Error }

data class UiNotice(
    val message: String,
    val kind: UiNoticeKind
)

/** Keeps long-running translation UI state alive across Activity recreation. */
class MainTaskViewModel : ViewModel() {
    val transfer = mutableStateOf<TransferProgress?>(null)
    val applyProgress = mutableStateOf<ApplyProgress?>(null)
    val applying = mutableStateOf(false)
    val processingPackPath = mutableStateOf<String?>(null)
    val applyingPackPath = mutableStateOf<String?>(null)
    val installingEntryKey = mutableStateOf<String?>(null)
    val changingTargetLanguage = mutableStateOf(false)
    val notice = mutableStateOf<UiNotice?>(null)
    val latestRelease = mutableStateOf<AppRelease?>(null)
    val checkingUpdate = mutableStateOf(false)
    val updateProgress = mutableStateOf<TransferProgress?>(null)
    val downloadedUpdate = mutableStateOf<File?>(null)

    var retryAction: (() -> Unit)? = null
        private set

    fun launchTask(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(block = block)

    fun info(value: String) {
        retryAction = null
        notice.value = UiNotice(value, UiNoticeKind.Info)
    }

    fun success(value: String) {
        retryAction = null
        notice.value = UiNotice(value, UiNoticeKind.Success)
    }

    fun error(value: String, retry: (() -> Unit)? = null) {
        retryAction = retry
        notice.value = UiNotice(value, UiNoticeKind.Error)
    }

    fun retry() {
        val action = retryAction ?: return
        notice.value = null
        action()
    }

    fun dismissNotice() {
        notice.value = null
        retryAction = null
    }

    fun clearError() {
        retryAction = null
        if (notice.value?.kind == UiNoticeKind.Error) notice.value = null
    }
}
