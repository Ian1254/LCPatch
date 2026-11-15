package com.lcpatch

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

enum class UiNoticeKind { Info, Success, Error }

data class UiNotice(
    val message: String,
    val kind: UiNoticeKind,
    val id: Long
)

/** Keeps long-running translation UI state alive across Activity recreation. */
class MainTaskViewModel(application: Application) : AndroidViewModel(application) {
    private var noticeSequence = 0L
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

    val updateCheckError=mutableStateOf<String?>(null)
    private val updateRequests=LatestUpdateRequest()
    private var updateCheckJob: Job?=null
    private var checkedChannel: String?=null

    fun checkForUpdate(repository: AppUpdateRepository,channel: String) {
        if(updateProgress.value!=null) return
        val previousRelease=latestRelease.value
        if(checkedChannel!=channel) downloadedUpdate.value=null
        checkedChannel=channel
        val request=updateRequests.begin()
        updateCheckJob?.cancel()
        checkingUpdate.value=true
        updateCheckError.value=null
        latestRelease.value=null
        updateCheckJob=viewModelScope.launch {
            try {
                val result=repository.latestRelease(includePrerelease=channel=="beta")
                if(updateRequests.accepts(request)) {
                    if(previousRelease?.apkUrl!=result.apkUrl) downloadedUpdate.value=null
                    latestRelease.value=result
                }
            } catch(error: kotlinx.coroutines.CancellationException) {throw error
            } catch(error: Exception) {
                if(updateRequests.accepts(request)) updateCheckError.value="檢查更新失敗：${error.message?:error.javaClass.simpleName}"
            } finally {
                if(updateRequests.accepts(request)) checkingUpdate.value=false
            }
        }
    }

    var retryAction: (() -> Unit)? = null
        private set

    fun launchTask(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(block = block)

    fun info(value: String) {
        retryAction = null
        notice.value = UiNotice(value, UiNoticeKind.Info, ++noticeSequence)
    }

    fun success(value: String) {
        retryAction = null
        notice.value = UiNotice(value, UiNoticeKind.Success, ++noticeSequence)
    }

    fun error(value: String, retry: (() -> Unit)? = null) {
        retryAction = retry
        // The short notice may disappear, but the full diagnostic stays in Logs.
        runCatching { LogRepository.append(getApplication<Application>(), "ERROR", "APP_ACTION", value) }
        notice.value = UiNotice(value, UiNoticeKind.Error, ++noticeSequence)
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

/** A completed response must still belong to the most recently selected channel. */
internal class LatestUpdateRequest {
    private var generation=0L
    fun begin(): Long=++generation
    fun accepts(request: Long): Boolean=request==generation
}
