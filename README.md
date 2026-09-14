# LCPatch

LCPatch 是使用可完整重建之 `liblcpatch_core.so` 的 ARM64 Xposed 模組。核心原始碼位於 `native-refactor/lcpatch_core.cpp`，負責 Localize 唯讀文件重導與中文字型替換。

介面採用 Miuix 元件，包含緊湊頂欄、卡片式資訊區塊、浮動底欄及清楚的運作狀態；技術事件集中在設定的日誌頁。

「下載漢化」會讀取原版資源清單，並加入零協 Android 日語與韓語覆蓋包；GitHub 來源會解析最新 Release，並以 `releases/latest/download` 作為備援。下載完成後會驗證並預設套用。漢化包統一保存至 `/sdcard/LCPatch/漢化`；「選擇已下載漢化」會先掃描該目錄，遷移舊目錄並修正名稱後列出可切換項目。

下載或匯入 ZIP 後會立即解壓成 `/sdcard/LCPatch/漢化/名稱/` 的獨立目錄；繁簡轉換會遍歷所選目錄內全部 JSON，並由 `opencc4j 1.14.0` 處理完整文字內容。PUA 轉換使用 3,912 字映射，並支援 JSON 跳脫字元還原及補充平面碼位。設定頁可選擇日本語／한국어／English 覆蓋目標，字體保存於 `/sdcard/LCPatch/字體/`。套用時會整理成 `Localize/cn` 與所選的 `JP_`、`KR_` 或 `EN_` 文件名，再同步至 native 元件讀取的相容路徑。

## 字型修正

模組使用 LSPosed API 102 載入。介面使用中性色卡片、圓滑開關、懸浮底欄，以及高模糊半徑與高不透明度的頂／底欄；分頁與底欄指示器採用緩入緩出過渡。支援跟隨系統、淺色和深色模式；關於頁採用系統資訊配置與整頁背景流光。

內建字型恢復為原版已通過實機使用的 Sarasa Gothic SC Bold 1.0.29 靜態 TrueType。該字型由官方以 SIL Open Font License 1.1 發佈，PUA 映射表、可再散布的字型檔與完整授權文件均保留在專案內。遊戲圖標依使用者要求保留，不納入 LCPatch 的 Apache-2.0 授權聲明。

此安全回退以 Limbus Company 1.113.1 為相容基準。新版遊戲需先建立並驗證新 profile，再更新 native hook。

## 日誌

遊戲程序透過 `content://com.lcpatch.logs` 傳送模組事件。Provider 會核對 Binder UID，只接受本 App 或已安裝的 `com.ProjectMoon.LimbusCompany`。最多保存 500 筆。設定頁可變更診斷文件儲存資料夾；匯出的文字檔包含機型、Android／遊戲／模組版本、ABI 及模組事件，可直接附到 Codex 對話。

主要代碼：

- `app/src/main/java/com/filepermwebui/ModernModule.java`：LSPosed API 102 模組入口。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：漢化清單、App 內下載、驗證與 root 套用。
- `app/src/main/java/com/filepermwebui/LogProvider.kt`：`com.lcpatch` 跨程序日誌儲存。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：概觀／設定底欄與 Miuix 0.9.3 管理介面。

## 建置與驗證

執行 `build.ps1` 會先由 C++ 原始碼建立 native 核心，再建立 release APK；簽署前會另外執行 zipalign。

專案已通過 release、lint、簡繁轉換與語言文件名修正測試、APK v3 簽章與 16 KB zipalign。成品只保留 LSPosed API 102 入口，且 APK 不含 `liblcscanner.so`、舊核心或舊式 `assets/xposed_init`。首次引導只檢查 LSPosed 與 Root 狀態，不會導向其他 App；仍須在 ARM64 Android／LSPosed 上進行實機啟動與畫面測試。

## GitHub Actions

`.github/workflows/android.yml` 會在 main、Pull Request 及手動執行時，由原始碼重建 native 核心、執行測試與 lint，並上傳未簽署 APK artifact。推送 `v*` 標籤時會使用 GitHub Secrets 內的 PKCS12 金鑰簽署 APK，驗證 v3 簽章與 16 KB 對齊後建立 GitHub Release。

儲存庫需設定四個 Actions Secrets：`LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS`、`LCPATCH_KEY_PASSWORD`。本機的 `signing/`、建置快取與 APK 已由 `.gitignore` 排除，不可提交到 GitHub。

## 免責聲明

LCPatch 是非官方第三方專案，與 Project Moon 及各漢化組沒有隸屬或授權關係。專案不提供遊戲本體或受著作權保護的遊戲資源；漢化內容的權利與責任由各來源及使用者依其授權條款承擔。

本工具需要 Root 與 LSPosed，並可能建立、替換或清理 LCPatch 管理目錄及遊戲可讀取的本地化快取文件。使用前請備份重要資料，確認下載來源與目標語言設定，並自行承擔資料遺失、遊戲異常、帳號限制或系統不相容等風險。

本專案由生成式 AI 協助設計、撰寫與檢查，仍可能存在實作錯誤、安全缺陷或未涵蓋的裝置差異。AI 協助不等同完整的人工安全審核。依 Apache License 2.0，本軟體以現狀提供，不附帶任何明示或默示保證；使用者應在理解原始碼與風險後自行決定是否使用。
