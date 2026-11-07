package com.lcpatch

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Button as COUIButton
import io.github.suqi8.coui.kmp.basic.Card as COUICard
import io.github.suqi8.coui.kmp.basic.Switch as COUISwitch
import io.github.suqi8.coui.kmp.basic.Text as COUIText
import io.github.suqi8.coui.kmp.basic.TextButton as COUITextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * First COUI component prototype, using the real Compose COUI component library.
 * Its design baseline is ColorOS 16. Not claimed to match ColorOS 17 pixel-for-pixel.
 * Unlike HyperOverview, the environment card expands inline and the launch action
 * is independent from the translation management card.
 */
internal fun LazyListScope.colorOverview(
    state: OverviewUiState,
    actions: OverviewActions,
    @Suppress("UNUSED_PARAMETER") overlayState: EnvironmentStatusOverlayState,
) {
    if (!state.rootMode) {
        item(key = "coui-rootless") {
            ColorOverviewTheme {
                COUICard(modifier = Modifier.fillMaxWidth()) {
                    COUIText("原生文件載入", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    COUIText(state.rootlessAccess.status, color = COUITheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(6.dp))
                    COUIText("套用前須下載遊戲的日語或韓語資源，並完全關閉遊戲。", fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    COUITextButton(
                        text = state.rootlessAccess.actionLabel,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        onClick = actions.onGameFolder
                    )
                }
            }
        }
    } else {
        item(key = "coui-environment") {
            ColorOverviewTheme {
                var expanded by rememberSaveable { mutableStateOf(false) }
                val healthy = state.scopeStatus == "已啟用" &&
                    state.rootStatus == "已授權" && state.game.installed
                COUICard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    onClick = { expanded = !expanded }
                ) {
                    COUIText(
                        if (healthy) "環境已就緒" else "環境需要檢查",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(5.dp))
                    COUIText(
                        "遊戲版本：${state.game.version}",
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp
                    )
                    if (expanded) {
                        Spacer(Modifier.height(16.dp))
                        ColorOverviewDetail("模組作用域", state.scopeStatus)
                        ColorOverviewDetail("Root 權限", state.rootStatus)
                        ColorOverviewDetail("遊戲", if (state.game.installed) "已安裝" else "未安裝")
                        Spacer(Modifier.height(8.dp))
                        COUIText("此頁僅檢查環境，漢化是否生效仍需進入遊戲確認。", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            COUITextButton(
                                modifier = Modifier.weight(1f),
                                text = "檢查作用域",
                                onClick = actions.onCheckScope
                            )
                            COUITextButton(
                                modifier = Modifier.weight(1f),
                                text = "檢查 Root",
                                onClick = actions.onRequestRoot
                            )
                        }
                    }
                }
            }
        }
    }
    item(key = "coui-translation") {
        ColorOverviewTheme {
            COUICard(modifier = Modifier.fillMaxWidth()) {
                COUIText("漢化管理", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                COUIText(
                    if (state.activeName == "尚未選擇") "尚未選擇漢化" else
                        "${state.activeName} · ${state.activeScript}",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        COUIText("應用漢化", fontWeight = FontWeight.Medium)
                        COUIText(
                            if (state.translationEnabled) "已啟用" else "未啟用",
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    COUISwitch(
                        checked = state.translationEnabled,
                        onCheckedChange = actions.onTranslationEnabled,
                        enabled = !state.busy && (state.rootMode || state.translationEnabled)
                    )
                }
                Spacer(Modifier.height(10.dp))
                val ready = state.runtime.ready && state.runtime.fontReady
                COUIText(
                    if (!state.rootMode) {
                        "原生語言文件覆蓋 · 遊戲語言：${state.targetLanguage.label}"
                    } else if (ready) {
                        "快取已就緒 · ${state.runtime.matchingFiles} 個 ${state.targetLanguage.prefix} 文件"
                    } else {
                        "快取未就緒 · JSON ${state.runtime.jsonFiles} · ${state.targetLanguage.prefix} 文件 ${state.runtime.matchingFiles}"
                    },
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COUITextButton(
                        modifier = Modifier.weight(1f),
                        text = "下載漢化",
                        onClick = actions.onDownload
                    )
                    COUITextButton(
                        modifier = Modifier.weight(1f),
                        text = "本機漢化",
                        onClick = actions.onDownloaded
                    )
                }
            }
        }
    }
    item(key = "coui-launch") {
        ColorOverviewTheme {
            COUIButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.game.installed && !state.busy,
                onClick = actions.onLaunchGame
            ) {
                COUIText(if (state.game.installed) "啟動 Limbus Company" else "尚未安裝 Limbus Company")
            }
        }
    }
    item(key = "coui-device") {
        ColorOverviewTheme {
            COUICard(modifier = Modifier.fillMaxWidth()) {
                COUIText("裝置", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ColorOverviewDetail("機型", "${Build.MANUFACTURER} ${Build.MODEL}")
                ColorOverviewDetail("系統", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                ColorOverviewDetail("架構", Build.SUPPORTED_ABIS.joinToString())
            }
        }
    }
}

@Composable
private fun ColorOverviewTheme(content: @Composable () -> Unit) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    COUITheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        content()
    }
}

@Composable
private fun ColorOverviewDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        COUIText(label, color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        COUIText(value, fontSize = 14.sp)
    }
    Spacer(Modifier.height(7.dp))
}
