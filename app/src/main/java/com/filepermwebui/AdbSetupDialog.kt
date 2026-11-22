package com.lcpatch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AdbSetupDialog(context: Context, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var pairingPort by rememberSaveable { mutableStateOf("") }
    // Deliberately not saveable: codes expire and must not reach saved state.
    var code by remember { mutableStateOf("") }
    var connectionPort by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(NativeStorageClient.status(context)) }
    val scope = rememberCoroutineScope()
    val supported = Build.VERSION.SDK_INT >= 30
    fun work(label: String, action: suspend () -> String) {
        if (busy) return
        busy = true
        message = label
        scope.launch {
            try { message = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "操作失敗，請檢查無線偵錯後重試" }
            finally { code = ""; busy = false; onChanged() }
        }
    }
    SystemModal("無線 ADB", onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Android 11 以上可直接配對本機，不需安裝 Stellar。請先連上 Wi-Fi，開啟開發人員選項 → 無線偵錯。", fontSize = 14.sp)
            SystemSecondaryButton("開啟開發人員選項", {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                    .onFailure { message = "請手動開啟系統設定 → 開發人員選項 → 無線偵錯" }
            }, Modifier.fillMaxWidth(), enabled = !busy && supported)
            Text("1. 配對（首次使用）", fontSize = 16.sp)
            Text("使用分割畫面保留系統「使用配對碼配對裝置」視窗，再輸入該視窗的連接埠與六位數配對碼。", fontSize = 14.sp)
            AdbNumberField("配對連接埠", pairingPort, { pairingPort = it }, !busy && supported)
            AdbNumberField("六位數配對碼", code, { code = it }, !busy && supported, secret = true)
            SystemButton({
                work("正在配對…") {
                    NativeStorageClient.pair(context, pairingPort.toInt(), code)
                    "配對成功。請關閉系統配對碼視窗，輸入無線偵錯首頁的連接埠。"
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && supported && (pairingPort.toIntOrNull() ?: 0) in 1..65535 && code.length == 6) {
                Text("配對")
            }
            Text("2. 連線（每次服務重新啟動後）", fontSize = 16.sp)
            Text("這裡使用無線偵錯首頁「IP 位址與連接埠」的連接埠，通常與配對連接埠不同。只會連至本機。", fontSize = 14.sp)
            AdbNumberField("連線連接埠", connectionPort, { connectionPort = it }, !busy && supported)
            SystemButton({
                work("正在連線…") {
                    NativeStorageClient.connect(context, connectionPort.toInt())
                    "已連線，可以套用漢化。遊戲到標題畫面後，仍需點擊通知再次套用。"
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && supported && (connectionPort.toIntOrNull() ?: 0) in 1..65535) {
                Text("連線")
            }
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
            SystemSecondaryButton("中斷連線", {
                work("正在中斷連線…") { NativeStorageClient.disconnect(); "已中斷連線。可在系統無線偵錯中移除 LCPatch 的配對。" }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            SystemSecondaryButton("完成", onDismiss, Modifier.fillMaxWidth(), enabled = !busy)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AdbNumberField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, secret: Boolean = false) {
    val color = MiuixTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp)
        BasicTextField(value, { onChange(it.filter { char -> char in '0'..'9' }.take(if (secret) 6 else 5)) },
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .semantics { contentDescription = label }
                .border(1.dp, color.copy(alpha = .4f), RoundedCornerShape(12.dp)).padding(14.dp),
            enabled = enabled, singleLine = true,
            textStyle = TextStyle(color = color, fontSize = 16.sp), cursorBrush = SolidColor(color),
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.NumberPassword else KeyboardType.Number),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { field -> field() })
    }
}
