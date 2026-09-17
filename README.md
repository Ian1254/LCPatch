# LCPatch

LCPatch 是供 Android ARM64 裝置使用的 Limbus Company 漢化管理器與 Xposed 模組。它可以在 App 內下載、匯入、轉換及切換漢化，並由開源 native 核心將遊戲的本地化文件讀取導向已套用的內容，同時載入中文字型。

## 主要功能

- 從線上清單取得漢化，顯示下載速度與進度，下載完成後自動解壓、轉換 PUA 並套用。
- 匯入自訂 ZIP，或掃描 `/sdcard/LCPatch/漢化/` 內已解壓的漢化目錄。
- 使用 `opencc4j 1.14.0` 對已下載目錄內的 JSON 進行整體繁簡轉換，並顯示處理進度。
- 自動修正可辨識的目錄結構與語言文件名稱，可選擇覆蓋日本語、한국어或 English。
- 支援自訂 TTF／OTF 字型、漢化啟用開關、深淺色模式、Miuix 介面及模組日誌。
- 使用 LSPosed API 102；native 核心與第三方 native 依賴均包含可重建原始碼。

漢化清單包含一般線上來源，以及都市零協會官方 GitHub 儲存庫的最新 Release。LCPatch 儲存庫本身不包含第三方漢化內容。

## 支援環境

- Android 9（API 28）以上
- ARM64（`arm64-v8a`）裝置
- Root 權限
- 支援 libxposed API 102 的 LSPosed 環境
- 已安裝 Limbus Company

目前完整驗證的遊戲版本為 `1.113.1`（versionCode `468`）與 `1.114.0`（versionCode `469`）。遇到其他遊戲版本時，native 核心會先檢查 Unity Build ID、指令特徵及唯一候選；無法安全確認時會停用字型 hook，避免套用猜測位址。遊戲更新後仍應重新進行實機驗證。

## 安裝與使用

1. 從 [Releases](https://github.com/Ian1254/LCPatch/releases) 下載 APK 並安裝。
2. 在 LSPosed 中啟用 LCPatch，作用域選擇 Limbus Company。
3. 開啟 LCPatch，確認 LSPosed 與 Root 檢查皆已通過。
4. 在「下載漢化」下載資源，或從設定匯入自訂漢化 ZIP。
5. 視需要選擇覆蓋語言、繁簡格式及自訂字型，再套用漢化。
6. 完整關閉並重新啟動遊戲。若結果異常，可在設定的日誌頁匯出診斷文件。

套用與停用漢化時，LCPatch 會先強制停止遊戲，避免遊戲正在讀取文件時替換目錄。

## 資料位置

- 已下載並解壓的漢化：`/sdcard/LCPatch/漢化/<名稱>/`
- 匯入的字型：`/sdcard/LCPatch/字體/`
- 目前供遊戲讀取的內容：`/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn/`
- native 診斷記錄：`/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log`

套用流程會先在暫存位置完成結構整理、PUA 處理、JSON 驗證與字型準備，通過後才以可回復的目錄交換方式更新遊戲快取。

## 字型與 PUA

內建字型以 Sarasa Gothic SC Bold 1.0.29 為基礎，加入 LCPatch 使用的 3,912 字 BMP PUA 映射。字型依 SIL Open Font License 1.1 散布，來源、修改說明與完整授權文件均保留在專案內。

下載或匯入漢化後會立即完成 PUA 轉換。繁簡轉換會先還原既有 PUA 字元，使用 opencc4j 轉換完整 JSON 文字，再重新編碼為相同 PUA 映射，因此可直接作用於已下載的漢化目錄。

## 日誌

模組事件由遊戲程序寫入 LCPatch 的受限 ContentProvider。Provider 會核對呼叫端 UID，只接受 LCPatch 或已安裝的 Limbus Company，並最多保存 500 筆事件。

匯出的診斷文件包含裝置、Android、遊戲、模組與 ABI 資訊，以及已收集的模組事件。可在設定中選擇保存位置，或透過 Android 分享功能附於問題回報。

## 原始碼與建置

- `native-refactor/lcpatch_core.cpp`：本地化文件重導、字型載入、跨版本 guard 與 native breadcrumbs。
- `app/src/main/java/com/filepermwebui/ModernModule.java`：LSPosed API 102 模組入口。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：下載、解壓、轉換、儲存修正與 Root 套用。
- `app/src/main/java/com/filepermwebui/LogProvider.kt`：跨程序日誌儲存。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：Compose／Miuix 管理介面。

GitHub Actions 會在推送至 `main`、建立 Pull Request 或手動執行時，從原始碼建立 native 核心、執行單元測試與 release lint，並上傳未簽署 APK artifact。推送與 App 版本一致的 `v*` 標籤時，工作流程會使用加密的 Actions Secrets 完成 zipalign、APK v3 簽章與 16 KB 對齊驗證，再建立 GitHub Release 並附上 SHA-256 文件。

自動簽署使用 `LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS` 與 `LCPATCH_KEY_PASSWORD` 四個 repository Secrets。本機的 `signing/`、APK、native 產物與建置快取均已由 `.gitignore` 排除。可重現的 CI 建置步驟以 [Android build workflow](.github/workflows/android.yml) 為準。

Windows 本機建置需準備 Java 17 以上、Android SDK、NDK 27.2.12479018、CMake 與 Ninja，設定 `ANDROID_SDK_ROOT` 後在儲存庫根目錄執行 `./build.ps1`。腳本會以 Gradle Wrapper 產生未簽署的 release APK；正式散布仍應使用自行保管的簽署金鑰。

## 授權與第三方元件

LCPatch 原始碼依 Apache License 2.0 授權。實際使用的開源元件如下：

- [Dobby](https://github.com/jmpews/Dobby)：Apache License 2.0
- [Compose Miuix](https://github.com/compose-miuix-ui/miuix)：Apache License 2.0
- [opencc4j](https://github.com/houbb/opencc4j)：Apache License 2.0
- [libxposed API／service](https://github.com/libxposed)：Apache License 2.0
- [AndroidX／Jetpack Compose](https://source.android.com/docs/setup/about/licenses)：Apache License 2.0
- [Sarasa Gothic](https://github.com/be5invis/Sarasa-Gothic)：SIL Open Font License 1.1
- LCPatch PUA 映射：CC0 1.0

詳細來源、版本與授權範圍見 [NOTICE](NOTICE) 及各授權文件。遊戲圖像與第三方漢化內容不包含在 LCPatch 的 Apache-2.0 授權範圍內。

## 免責聲明

LCPatch 是非官方第三方專案，與 Project Moon 及各漢化組沒有隸屬或授權關係。專案不提供遊戲本體或受著作權保護的遊戲資源；漢化內容的權利與責任由各來源及使用者依其授權條款承擔。

本工具需要 Root 與 LSPosed，並可能建立、替換或清理 LCPatch 管理目錄及遊戲可讀取的本地化快取文件。使用前請備份重要資料，確認下載來源與目標語言設定，並自行承擔資料遺失、遊戲異常、帳號限制或系統不相容等風險。

本專案由生成式 AI 協助設計、撰寫與檢查，仍可能存在實作錯誤、安全缺陷或未涵蓋的裝置差異。AI 協助不等同完整的人工安全審核。依 Apache License 2.0，本軟體以現狀提供，不附帶任何明示或默示保證；使用者應在理解原始碼與風險後自行決定是否使用。
