package com.lcpatch

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Lifted from MainActivity without changing the original MIUIX screen hierarchy.
 * Keep existing item order and visual behaviour as the zero-regression baseline.
 */
internal fun LazyListScope.hyperOverview(
    state: OverviewUiState,
    actions: OverviewActions,
    overlayState: EnvironmentStatusOverlayState,
) {
    val rootMode = state.rootMode
    val busy = state.busy
    val loadStatus = state.rootlessAccess.status
    val loadAction = state.rootlessAccess.actionLabel
    val onGameFolder = actions.onGameFolder
    val game = state.game
    val activeName = state.activeName
    val activeScript = state.activeScript
    val scopeStatus = state.scopeStatus
    val rootStatus = state.rootStatus
    val onCheckScope = actions.onCheckScope
    val onRequestRoot = actions.onRequestRoot
    val translationEnabled = state.translationEnabled
    val onTranslationEnabled = actions.onTranslationEnabled
    val targetLanguage = state.targetLanguage
    val runtimeInspection = state.runtime
    val onDownload = actions.onDownload
    val onDownloaded = actions.onDownloaded

        if (!rootMode) item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("無 root · 原生文件載入", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                Text(loadStatus)
                Text("使用遊戲原有字型。請先下載對應語言資源，並在套用、停用前完全關閉遊戲。", fontSize = 14.sp)
                TextButton(modifier = Modifier.fillMaxWidth(), text = loadAction, enabled = !busy, onClick = onGameFolder)
            }
        }
        if (rootMode) item {
            EnvironmentStatusOverviewCard(
                overlayState = overlayState,
                scopeStatus = scopeStatus,
                rootStatus = rootStatus,
                gameInstalled = game.installed,
                gameVersion = game.version,
                onCheckScope = onCheckScope,
                onRequestRoot = onRequestRoot
            )
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("當前漢化", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(6.dp))
                Text("$activeName · $activeScript", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (!rootMode) {
                        if (translationEnabled) "已寫入原生語言文件；停用可還原備份" else "尚未套用；可先下載或匯入無 root 專用漢化"
                    } else if (runtimeInspection.ready && runtimeInspection.fontReady) {
                        "遊戲快取已就緒 · ${runtimeInspection.jsonFiles} 個 JSON · ${runtimeInspection.matchingFiles} 個 ${targetLanguage.prefix} 文件 · 字型 ${formatOverviewBytes(runtimeInspection.fontBytes)}"
                    } else {
                        "遊戲快取未就緒 · JSON ${runtimeInspection.jsonFiles} · ${targetLanguage.prefix} 文件 ${runtimeInspection.matchingFiles} · ${if (runtimeInspection.fontReady) "字型已寫入" else "缺少中文字型"}"
                    },
                    color = if (!rootMode || runtimeInspection.ready && runtimeInspection.fontReady) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                Text("遊戲內語言需設為 ${targetLanguage.label}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                SwitchPreference(
                    modifier = HyperPreferenceModifier,
                    title = if (rootMode) "應用漢化" else "原生漢化（關閉以還原）",
                    enabled = !busy && (rootMode || translationEnabled),
                    summary = if (translationEnabled) "已啟用，重新啟動遊戲後載入" else "已停用，下載內容仍會保留",
                    checked = translationEnabled,
                    onCheckedChange = onTranslationEnabled
                )
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) { Text("下載漢化") }
                Spacer(Modifier.height(8.dp))
                TextButton(modifier = Modifier.fillMaxWidth(), text = "選擇已下載漢化", onClick = onDownloaded)
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("遊戲", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                OverviewDetail("Limbus Company", if (game.installed) "已安裝" else "未安裝")
                OverviewDetail("版本", game.version)
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), enabled = game.installed, onClick = actions.onLaunchGame) { Text("啟動 Limbus Company") }
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("裝置資訊", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                OverviewDetail("機型", "${Build.MANUFACTURER} ${Build.MODEL}")
                OverviewDetail("裝置", Build.DEVICE)
                OverviewDetail("系統版本", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                OverviewDetail("架構", Build.SUPPORTED_ABIS.joinToString())
            }
        }

}

private val HyperPreferenceModifier = Modifier.clip(RoundedCornerShape(18.dp))

@Composable
private fun OverviewDetail(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
        Text(value)
    }
    Spacer(Modifier.height(6.dp))
}

private fun formatOverviewBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    else -> "%.1f MB".format(value / 1024.0 / 1024.0)
}
