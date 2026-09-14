package com.lcpatch

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** Keeps long-running translation UI state alive across Activity recreation. */
class MainTaskViewModel : ViewModel() {
    val transfer = mutableStateOf<TransferProgress?>(null)
    val applyProgress = mutableStateOf<ApplyProgress?>(null)
    val applying = mutableStateOf(false)
    val processingPackPath = mutableStateOf<String?>(null)
    val applyingPackPath = mutableStateOf<String?>(null)
    val message = mutableStateOf<String?>(null)
    val messageIsError = mutableStateOf(false)
    val latestRelease = mutableStateOf<AppRelease?>(null)
    val checkingUpdate = mutableStateOf(false)
    val updateProgress = mutableStateOf<TransferProgress?>(null)
    val downloadedUpdate = mutableStateOf<File?>(null)

    var retryAction: (() -> Unit)? = null
        private set

    fun launchTask(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(block = block)

    fun success(value: String) {
        retryAction = null
        messageIsError.value = false
        message.value = value
    }

    fun error(value: String, retry: (() -> Unit)? = null) {
        retryAction = retry
        messageIsError.value = true
        message.value = value
    }

    fun retry() {
        val action = retryAction ?: return
        message.value = null
        action()
    }

    fun dismissNotice() {
        message.value = null
        retryAction = null
        messageIsError.value = false
    }

    fun clearError() {
        retryAction = null
        messageIsError.value = false
    }
}
