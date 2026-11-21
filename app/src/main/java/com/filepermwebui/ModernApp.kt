package com.lcpatch

import android.app.Application
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class ModuleScopeStatus(val label: String) {
    CONNECTING("正在連接"), DISCONNECTED("尚未連接"),
    ENABLED("已啟用"), NOT_GRANTED("尚未授權遊戲")
}

class ModernApp : Application() {
    override fun onCreate() {
        super.onCreate()
        background.launch {
            try { CrashDiagnostics.capture(this@ModernApp) }
            catch (error: Exception) { Log.e("LCPatch", "Background diagnostic collection failed", error) }
            TranslationRepository(this@ModernApp).migrateRuntime()
        }
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(value: XposedService) = bindScope(value)
            override fun onServiceDied(value: XposedService) = unbindScope(value)
        })
    }

    companion object {
        private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutableScope = MutableStateFlow(ModuleScopeStatus.CONNECTING)
        internal val scopeStatus = mutableScope.asStateFlow()
        private var service: XposedService? = null
        private var requestId = 0L

        @Synchronized private fun bindScope(value: XposedService) {
            service = value
            mutableScope.value = ModuleScopeStatus.CONNECTING
            refreshScope()
        }

        @Synchronized private fun unbindScope(value: XposedService) {
            if (service !== value) return
            service = null
            requestId++
            mutableScope.value = ModuleScopeStatus.DISCONNECTED
        }

        @Synchronized internal fun refreshScope() {
            val current = service
            val request = ++requestId
            if (current == null) {
                mutableScope.value = ModuleScopeStatus.DISCONNECTED
                return
            }
            background.launch {
                val status = try {
                    if (current.scope.contains(LogProvider.GAME)) ModuleScopeStatus.ENABLED else ModuleScopeStatus.NOT_GRANTED
                } catch (_: Exception) {
                    ModuleScopeStatus.DISCONNECTED
                }
                synchronized(Companion) {
                    if (request == requestId) mutableScope.value = status
                }
            }
        }
    }
}
