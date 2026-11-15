# LCPatch

LCPatch 是供 Android ARM64 裝置使用的 Limbus Company 漢化管理器，支援無 Root 原生語言文件覆蓋，以及 Root／LSPosed 的本地化重導與中文字型載入。不修改遊戲 APK，第三方漢化文本不包含在本儲存庫內。

## 下載與版本

- [最新正式版](https://github.com/Ian1254/LCPatch/releases/latest)：目前為 **1.2.3**。
- [Beta 版本](https://github.com/Ian1254/LCPatch/releases/tag/v1.2.4-beta.1)：目前為 **1.2.4-beta.1**，標示為預發布版。
- 本文件的 Stellar 引導與檢查對應 **1.2.4-test.4** 測試分支，尚未發布。已發布版本的介面仍顯示 Shizuku，可依 Stellar 官方相容層說明使用。

請從 Releases 的 APK 或指定 Actions 簽章測試 artifact 安裝；未簽章 artifact 供建構檢查使用。更新內容見各版本的 Release 說明。

## 支援環境

- Android 9（API 28）以上，ARM64（`arm64-v8a`）裝置。
- 已安裝 Limbus Company，並下載所選的遊戲語言資源。
- 無 Root：推薦 [Stellar](https://github.com/roro2239/Stellar)，開啟其 **Shizuku 相容層**。
- Root：Root 權限與支援 libxposed API 102 的 LSPosed 環境。

Stellar 是外部服務管理器。LCPatch 使用它官方提供的相容層，保留 Shizuku API／provider 用戶端套件，不需要安裝 Shizuku Manager，也未接入計畫於 Stellar 2.0 棄用的獨立 Stellar API。請只啟動要使用的服務，避免兩個服務同時提供連線。安裝 Stellar 不會改變原本管理器的安裝狀態。

Stellar 使用方式與相容層開關見 [官方說明](https://github.com/roro2239/Stellar#shizuku-兼容层)，獨立 API 的棄用公告見 [Stellar-API](https://github.com/roro2239/Stellar-API#重要变更)。遊戲對管理器包名的偵測目前依使用者裝置回報，未宣稱已驗證所有遊戲版本的偵測行為。

## 無 Root 使用方式

1. 安裝並啟動 [Stellar](https://github.com/roro2239/Stellar/releases/latest)，確認「Shizuku 相容層」已開啟。Stellar 可透過 ADB／無線偵錯啟動，不要求 LCPatch 自行取得 Root。
2. 在遊戲內切換至日本語或 한국어，完成對應語言資源下載。
3. 在 LCPatch 選擇「無 Root」，由概觀的「Stellar 與權限」確認 Stellar 已連線並授予 LCPatch 權限。
4. 在「設定 → 漢化」選擇對應的覆蓋語言；下載無 Root 專用漢化，或匯入包含 `Localize/jp`／`Localize/kr` 的 ZIP。
5. 下載完成直接開始套用；已有漢化包可從「已下載漢化」按「套用／重新套用」。通知與畫面顯示實際處理進度及結果。
6. 從概觀啟動遊戲時，可建立待套用通知；遊戲顯示標題畫面後，點擊整張通知再次套用，通知內沒有獨立動作按鈕。

無 Root 來源為 [pzwboy/LocalizeLimbusCompanyForAndroid](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid)。此模式保留來源的相近漢字替換，不進行 PUA 編碼、繁簡轉換或中文字型注入，只支援日語與韓語語言文件。

原生套用逐檔寫入 `files/Assets/Resources_moved/Localize/jp` 或 `kr`，不強制停止遊戲、不備份或還原原始語言目錄，也不因舊備份存在而阻擋重套。遊戲或語言資源更新後，如內容被覆寫，可重新套用。

無 Root 統一使用 Stellar，不再提供 SAF 遊戲資料夾後端。漢化包只支援匯入，不提供匯出 ZIP 功能。

## Root／LSPosed 使用方式

1. 在 LSPosed 啟用 LCPatch，作用域選擇 Limbus Company。
2. 開啟 LCPatch，切換至「Root ＋ LSPosed」，在概觀確認作用域與 Root 權限。
3. 下載零協會漢化，或匯入自訂漢化 ZIP；視需要選擇覆蓋語言及自訂字型。
4. 套用後啟動遊戲。Root 套用、停用與啟動準備仍可能強制停止遊戲，避免讀取中的文件被替換。

零協會漢化預設整合完整 PC 文字資源及劇情角色名稱，不必另外選擇整理版或開啟補齊選項。另支援一般線上清單及 `/sdcard/LCPatch/漢化/` 內已解壓的漢化目錄。

PUA 編碼、繁簡轉換、自訂 TTF／OTF 字型與 English 覆蓋僅提供給 Root 模式。繁簡轉換位於「設定 → 漢化」，直接處理目前套用的內容，保留原本啟用／停用狀態；刪除下載包後仍可處理已安裝內容。

環境顯示「已就緒」代表已取得必要條件，實際文字與字型是否生效仍應進入遊戲確認。native 核心會在執行時驗證遊戲入口；不保證所有後續遊戲版本自動相容，舊版實機回報也不等於目前版本已完成實測。

## 漢化包管理與版本

- 卡片左側顯示名稱與版本，右側提供帶圖示的「套用／重新套用」文字按鈕。
- 右上角垃圾桶進入批量刪除，可全選／取消全選、返回取消，執行前再次確認。Root 與無 Root 使用相同操作。
- 刪除只移除下載的漢化包，不影響遊戲內已套用的內容；部分刪除失敗會保留失敗項目供重試。
- 版本優先依下載來源的 Release 標籤、包內版本資訊與保存的來源網址辨識。
- 舊無 Root 包缺少版本資訊時，以完整 SHA-256 文字資源指紋比對已知來源；只有完全符合才補回來源版本。對不上時顯示「內容識別」，不使用下載時間或當前最新版本冒充。
- 漢化來源版本的辨識不代表已確認它與目前遊戲版本相容。

## 資料位置

| 內容 | Root 模式 | 無 Root 模式 |
| --- | --- | --- |
| 已下載漢化 | `/sdcard/LCPatch/漢化/<名稱>/` | App 私有空間 `translations/packs/<名稱>/` |
| 遊戲讀取內容 | 遊戲 `cache/Localize/cn/` | 遊戲 `files/Assets/Resources_moved/Localize/jp/` 或 `kr/` |
| 自訂字型 | `/sdcard/LCPatch/字體/` 及 App 字型設定 | 不提供字型注入 |

Root 的遊戲目錄位於 `/sdcard/Android/data/com.ProjectMoon.LimbusCompany/`。無 Root 不會把來源 ZIP 當作可供遊戲直接載入的文件。

## 日誌與問題回報

底欄「日誌」的右上角三點選單提供分享、儲存診斷文件與清除記錄。診斷文件包含裝置、Android、遊戲、模組及已收集事件；收集結果會標示取得、未找到、權限不足、截斷或逾時等狀態。沒有記錄不代表遊戲沒有閃退。

Root 模式可補充原生事件與遊戲相關系統記錄；無 Root 不會為診斷要求 Root。可在設定選擇診斷儲存位置；未指定時使用暫存檔，透過分享功能取得。回報請附上 LCPatch 版本、遊戲版本、運作模式及實際操作步驟。

[早期無 Root 審查](docs/non-root-audit.md) 為歷史紀錄，包含後來移除的還原／匯出流程；目前行為以本文件及 [Stellar 適配說明](docs/stellar-backend.md) 為準。

## 原始碼與建構

- `native-refactor/lcpatch_core.cpp`：Root 本地化重導、字型載入、跨版本驗證與原生記錄。
- `app/src/main/java/com/filepermwebui/NativeStorageClient.kt`：Stellar 相容層連線與文件服務。
- `app/src/main/java/com/filepermwebui/TranslationApplyService.kt`：背景套用及通知。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：下載、版本辨識、轉換與套用。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：Compose／Miuix 管理介面。

建構環境以 [共用 Android build action](.github/actions/android-build/action.yml) 為準，目前使用 Java 21、Android SDK、NDK 27.2.12479018、CMake 與 Ninja。Windows 設定 `ANDROID_SDK_ROOT` 後，可在儲存庫根目錄執行 `./build.ps1`。

CI 從原始碼建立 native 核心，執行測試、Release lint 與介面預覽。一般 CI 提供未簽章 APK；指定的同儲存庫測試分支另提供已簽章測試 artifact，不建立 Release。發布流程以 Actions Secrets 完成 APK v3 簽章、16 KB 對齊與 SHA-256 校驗；版本名稱含預發布標記時建立 GitHub Prerelease。

簽章使用 `LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS`、`LCPATCH_KEY_PASSWORD` 四個 Secrets。金鑰與建置產物不隨原始碼散布。`verification.json` 是 1.0.0 的歷史記錄，不代表目前版本的驗證結果。未進行實機測試的版本會明確註記；編譯成功不能取代裝置相容性驗證。

## 授權與第三方元件

LCPatch 原始碼採 Apache License 2.0。主要使用 Dobby、Compose Miuix、opencc4j、libxposed、Shizuku API／provider（用於 Stellar 相容層）及 AndroidX／Jetpack Compose；詳細授權見 [NOTICE](NOTICE)。Stellar 管理器為另行安裝的外部程式，不包含於 LCPatch APK。

內建字型以 Sarasa Gothic SC Bold 1.0.29 為基礎，包含 3,912 字 BMP PUA 映射。字型依 SIL Open Font License 1.1 散布，LCPatch PUA 映射為 CC0 1.0；來源、修改說明與授權文件保留在專案中。遊戲圖像與第三方翻譯內容不屬於 LCPatch 的 Apache-2.0 授權範圍。

LCPatch 為非官方第三方專案，與 Project Moon 及翻譯組沒有隸屬關係。軟體以現狀提供，不附保證；本專案由生成式 AI 協助撰寫與檢查，仍可能存在錯誤或未涵蓋的裝置差異。
