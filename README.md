# LCPatch

LCPatch 是供 Android ARM64 裝置使用的 Limbus Company 漢化管理器，支援無 root 原生文件覆蓋與 Root／LSPosed 模式。無 root 模式不修改遊戲 APK，使用已替換相近日語漢字的漢化文件，直接交由遊戲原有的語言載入流程讀取；Root 模式由開源 native 核心在文字載入階段替換本地化 JSON 路徑並載入中文字型。

## 無 root 模式

1. 在設定或首次設定中選擇「無 root」，文件載入方式預設為 Shizuku。
2. 在遊戲內切換至日本語或 한국어，完成語言資源下載。
3. 啟動 [Shizuku](https://shizuku.rikka.app/guide/setup/)，在 LCPatch 授予權限。
4. 選擇對應語言，下載並套用無 root 專用漢化，或匯入包含 `Localize/jp`／`Localize/kr` 的 ZIP。
5. LCPatch 透過 Shizuku 關閉遊戲、備份原始語言目錄，再寫入 `files/Assets/Resources_moved/Localize/jp` 或 `kr`。重新啟動遊戲即可由原生流程讀取 `JP_*.json`／`KR_*.json`。
6. 停用漢化或在設定選擇「還原原始語言文件」，即可恢復備份並移除漢化新增的文件。更新遊戲前先停用漢化，更新語言資源後再套用。

無 root 來源為 [pzwboy/LocalizeLimbusCompanyForAndroid](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid)，保留來源的日文字形替換，不進行 PUA 編碼、繁簡轉換或字型注入；介面只在 Root 模式提供這些功能。第三方文本不包含在本儲存庫內。

亦可選擇 SAF 授權遊戲的 `Localize` 目錄，或匯出含完整 Android 目錄結構的 ZIP 交由具權限的檔案管理器套用。Android 11 以上的標準系統選擇器限制存取 `Android/data`；SAF 不是這項限制的繞過方式。SAF 套用和還原前須自行完全關閉遊戲；手動匯出的文件由檔案管理器負責備份與還原。

Shizuku 文件服務與實際遊戲載入仍需裝置驗證；本機單元測試不代表已在所有 Android／遊戲版本上驗證。實作與檢查結果見 [無 root 載入與 App 檢查](docs/non-root-audit.md)。

## 主要功能

- 從線上清單取得漢化，顯示下載速度與進度，下載完成後自動解壓、轉換 PUA 並套用。
- 匯入自訂 ZIP，或掃描 `/sdcard/LCPatch/漢化/` 內已解壓的漢化目錄。
- 使用 `opencc4j 1.14.0` 對已下載目錄內的 JSON 進行整體繁簡轉換，並顯示處理進度。
- 自動修正可辨識的目錄結構與語言文件名稱，可選擇覆蓋日本語、한국어 或 English。
- 支援自訂 TTF／OTF 字型、漢化啟用開關、深淺色模式、Miuix 介面及模組日誌。
- 使用 LSPosed API 102；native 核心與第三方 native 依賴均包含可重建原始碼。

漢化清單包含一般線上來源、都市零協會官方 GitHub 儲存庫的最新 Release，以及 [LCPatch-Localization-Builder](https://github.com/Ian1254/LCPatch-Localization-Builder) 的合併漢化。合併版會自動從最新 Release 取得，但 App 不核對遊戲版本；若遊戲更新快於資源包，套用後可能有缺字或缺文。LCPatch 儲存庫本身不包含第三方漢化內容。

舊版 Root 簡體漢化已從下載清單移除；本機同名舊包會在不再套用時清理。清單卡片只顯示來源，不顯示內部分類名稱。

## 支援環境

- Android 9（API 28）以上
- ARM64（`arm64-v8a`）裝置
- 無 root：Shizuku（或系統允許的 SAF 目錄授權）
- Root 模式：Root 權限與支援 libxposed API 102 的 LSPosed 環境
- 已安裝 Limbus Company

目前遊戲版本 `1.113.1`（versionCode `468`）與 `1.114.0`（versionCode `469`）已有實機驗證；`1.115.0` 的 Unity Build ID 與字型入口／accessor 指令已由 ARM64 APK 核對並加入已知 profile，仍需實機確認字型交換。已知 Unity build 必須同時符合 Build ID、可執行區段、固定 offset 與指令特徵。未知 build 會嘗試嚴格動態驗證：accessor、合法 caller 與函式入口均須唯一，入口結構也須符合既有 fingerprint；任一驗證失敗就安全停用字型 hook。這只支援實作維持一致而位址漂移的版本，並不保證所有未來版本自動相容。

## Root 模式安裝與使用

1. 從 [Releases](https://github.com/Ian1254/LCPatch/releases) 下載 APK 並安裝。
2. 在 LSPosed 中啟用 LCPatch，作用域選擇 Limbus Company。
3. 開啟 LCPatch，確認 LSPosed 與 Root 檢查皆已通過。
4. 在「下載漢化」下載資源，或從設定匯入自訂漢化 ZIP。
5. 視需要選擇覆蓋語言、繁簡格式及自訂字型，再套用漢化。
6. 完整關閉並重新啟動遊戲。若結果異常，可在底欄「日誌」頁匯出診斷文件。

套用與停用漢化時，LCPatch 會先強制停止遊戲，避免遊戲正在讀取文件時替換目錄。

1.2.3-beta.2 的 Root 文字核心不再攔截底層檔案操作；僅在已定位的同步／非同步文字載入流程替換路徑，讓其他資源檢查讀取官方原檔。入口不相容時會保留原文，並記錄 `core.localize.unsupported`。本版尚未完成 LSPosed 遊戲內載入與連續啟動驗證。

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

匯出的診斷文件包含裝置、Android、遊戲、模組與 ABI 資訊，以及已收集的模組事件。可在設定中選擇儲存資料夾；若未選擇，「儲存」會寫入 App 暫存區，可透過「分享診斷文件」取得檔案並附於問題回報。

## 原始碼與建置

- `native-refactor/lcpatch_core.cpp`：本地化文件重導、字型載入、跨版本 guard 與 native breadcrumbs。
- `app/src/main/java/com/filepermwebui/ModernModule.java`：LSPosed API 102 模組入口。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：下載、解壓、轉換、儲存修正與 Root 套用。
- `app/src/main/java/com/filepermwebui/LogProvider.kt`：跨程序日誌儲存。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：Compose／Miuix 管理介面。

GitHub Actions 會在建立 Pull Request 或手動執行 Android CI 時，從原始碼建立 native 核心、執行單元測試與 release lint，並上傳未簽署 APK artifact。推送 `main` 且變更 `version.properties`，或手動執行 Android Release 工作流程時，會使用加密的 Actions Secrets 完成 zipalign、APK v3 簽章與 16 KB 對齊驗證，再建立對應版本的 `v*` 標籤及 GitHub Release，附上 SHA-256 文件。若該版本已發布，推送觸發會略過重複發布。

自動簽署使用 `LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS` 與 `LCPATCH_KEY_PASSWORD` 四個 repository Secrets。本機的 `signing/`、APK、native 產物與建置快取均已由 `.gitignore` 排除。可重現的 CI 建置步驟以 [Android build action](.github/actions/android-build/action.yml) 為準。`verification.json` 記錄舊版 1.0.0 的建置驗證，不代表目前版本的測試結果。

Windows 本機建置需準備 Java 17 以上、Android SDK、NDK 27.2.12479018、CMake 與 Ninja，設定 `ANDROID_SDK_ROOT` 後在儲存庫根目錄執行 `./build.ps1`。腳本會以 Gradle Wrapper 產生未簽署的 release APK；正式散布仍應使用自行保管的簽署金鑰。

## 授權與第三方元件

LCPatch 原始碼依 Apache License 2.0 授權。實際使用的開源元件如下：

- [Dobby](https://github.com/jmpews/Dobby)：Apache License 2.0
- [Compose Miuix](https://github.com/compose-miuix-ui/miuix)：Apache License 2.0
- [opencc4j](https://github.com/houbb/opencc4j)：Apache License 2.0
- [libxposed API／service](https://github.com/libxposed)：Apache License 2.0
- [Shizuku API／provider](https://github.com/RikkaApps/Shizuku-API)：MIT License
- [AndroidX／Jetpack Compose](https://source.android.com/docs/setup/about/licenses)：Apache License 2.0
- [Sarasa Gothic](https://github.com/be5invis/Sarasa-Gothic)：SIL Open Font License 1.1
- LCPatch PUA 映射：CC0 1.0

詳細來源、版本與授權範圍見 [NOTICE](NOTICE) 及各授權文件。遊戲圖像與第三方漢化內容不包含在 LCPatch 的 Apache-2.0 授權範圍內。

## 免責聲明

LCPatch 是非官方第三方專案，與 Project Moon 及各漢化組沒有隸屬或授權關係。專案不提供遊戲本體或受著作權保護的遊戲資源；漢化內容的權利與責任由各來源及使用者依其授權條款承擔。

Root 模式需要 Root 與 LSPosed；無 root 模式需要取得遊戲文件的寫入授權。兩者均可能建立、替換或清理 LCPatch 管理目錄及遊戲可讀取的本地化文件。使用前請備份重要資料，確認下載來源與目標語言設定，並自行承擔資料遺失、遊戲異常、帳號限制或系統不相容等風險。

本專案由生成式 AI 協助設計、撰寫與檢查，仍可能存在實作錯誤、安全缺陷或未涵蓋的裝置差異。AI 協助不等同完整的人工安全審核。依 Apache License 2.0，本軟體以現狀提供，不附帶任何明示或默示保證；使用者應在理解原始碼與風險後自行決定是否使用。
