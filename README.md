# LCPatch

LCPatch 是供 Android ARM64 裝置使用的 Limbus Company 漢化管理器，支援無 Root 原生語言文件覆蓋，以及 Root／LSPosed 的本地化重導與中文字型載入。

## 下載與版本

- [最新正式版](https://github.com/Ian1254/LCPatch/releases/latest)：**1.2.4**。
- 舊版及預發布版可在 [Releases](https://github.com/Ian1254/LCPatch/releases) 查看，更新內容見各版本說明。

請安裝 Releases 的已簽章 APK。Actions 測試產物僅供指定版本測試，未簽章產物供建構檢查使用。

## 支援環境

- Android 9（API 28）以上，ARM64（`arm64-v8a`）裝置。
- 已安裝 Limbus Company，並下載所選的遊戲語言資源。
- 無 Root：使用 [Stellar](https://github.com/roro2239/Stellar)，開啟其 **Shizuku 相容層**。
- Root：Root 權限與支援 libxposed API 102 的 LSPosed 環境。

Stellar 是外部服務管理器。LCPatch 透過其官方 Shizuku 相容層連線及授權。請啟動 Stellar，並保持相容層開啟。

Stellar 使用方式與相容層開關見 [官方說明](https://github.com/roro2239/Stellar#shizuku-兼容层)。

## 無 Root 使用方式

1. 安裝並透過 ADB／無線偵錯啟動 [Stellar](https://github.com/roro2239/Stellar/releases/latest)，確認「Shizuku 相容層」已開啟。
2. 在遊戲內切換至日本語或 한국어，完成對應語言資源下載。
3. 在 LCPatch 選擇「無 Root」，由概觀的「Stellar 與權限」確認 Stellar 已連線並授予 LCPatch 權限。
4. 在「設定 → 漢化」選擇對應的覆蓋語言；下載無 Root 專用漢化，或匯入包含 `Localize/jp`／`Localize/kr` 的 ZIP。
5. 下載完成直接開始套用。要切換或重新套用已下載的漢化，點選概觀上的漢化包名稱。下載頁顯示各處理階段的進度。
6. 從概觀啟動遊戲時，可建立待套用通知；遊戲顯示標題畫面後，點擊整張通知再次套用。

無 Root 來源為 [pzwboy/LocalizeLimbusCompanyForAndroid](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid)，使用來源提供的相近漢字替換，支援日語與韓語語言文件。

原生套用逐檔寫入 `files/Assets/Resources_moved/Localize/jp` 或 `kr`。遊戲或語言資源更新後，如內容被覆寫，可重新套用。

## Root／LSPosed 使用方式

1. 在 LSPosed 啟用 LCPatch，作用域選擇 Limbus Company。
2. 開啟 LCPatch，切換至「Root ＋ LSPosed」，在概觀確認作用域與 Root 權限。
3. 下載零協會漢化，或匯入自訂漢化 ZIP；視需要選擇覆蓋語言及自訂字型。
4. 套用後啟動遊戲。Root 套用、停用與啟動準備仍可能強制停止遊戲，避免讀取中的文件被替換。

零協會漢化預設整合完整 PC 文字資源及劇情角色名稱。另支援一般線上清單及 `/sdcard/LCPatch/漢化/` 內已解壓的漢化目錄。

「下載漢化」也提供 **OurPlay 神人漢化**。按「下載並套用」後，LCPatch 會從 OurPlay 取得社群版，並使用零協會文字檔對應遊戲資源。無法可靠對應的文字會保留零協會翻譯。

PUA 編碼、繁簡轉換、自訂 TTF／OTF 字型與 English 覆蓋僅提供給 Root 模式。繁簡轉換位於「設定 → 漢化」，直接處理目前套用的內容，保留原本啟用／停用狀態；刪除下載包後仍可處理已安裝內容。

「環境與權限」顯示模組作用域與 Root 授權狀態，實際文字與字型效果可進入遊戲確認。native 核心會在執行時驗證遊戲入口。

## 漢化包管理與版本

- 已下載漢化清單顯示名稱、版本及已套用標示。從概觀的漢化包名稱開啟選單即可套用或切換。
- 右上角垃圾桶進入批量刪除，可全選／取消全選、返回取消，執行前再次確認。Root 與無 Root 使用相同操作。
- 刪除只移除下載的漢化包，不影響遊戲內已套用的內容；部分刪除失敗會保留失敗項目供重試。
- 版本優先依下載來源的 Release 標籤、包內版本資訊與保存的來源網址辨識。
- 舊無 Root 包缺少版本資訊時，以完整 SHA-256 文字資源指紋比對已知來源；完全符合時補回來源版本，其餘顯示「內容識別」。

## 狀態顯示

- 「已啟用」搭配綠色圈勾，「已停用」搭配紅色圈叉。
- 「已授權」搭配綠色圈勾，「未授權」搭配紅色圈叉。
- 「等待套用」旁顯示持續旋轉的載入圓圈，套用結果以完成／失敗訊息為準。

## 資料位置

| 內容 | Root 模式 | 無 Root 模式 |
| --- | --- | --- |
| 已下載漢化 | `/sdcard/LCPatch/漢化/<名稱>/` | App 私有空間 `translations/packs/<名稱>/` |
| 遊戲讀取內容 | 遊戲 `cache/Localize/cn/` | 遊戲 `files/Assets/Resources_moved/Localize/jp/` 或 `kr/` |

Root 的遊戲目錄位於 `/sdcard/Android/data/com.ProjectMoon.LimbusCompany/`，自訂字型位於 `/sdcard/LCPatch/字體/`，也可透過 App 字型設定選取。

## 日誌與問題回報

底欄「日誌」的右上角三點選單提供分享、儲存診斷文件與清除記錄。診斷文件包含裝置、Android、遊戲、模組及已收集事件；收集結果會標示取得、未找到、權限不足、截斷或逾時等狀態。

Root 模式可補充原生事件與遊戲相關系統記錄。可在設定選擇診斷儲存位置；未指定時使用暫存檔，透過分享功能取得。回報請附上 LCPatch 版本、遊戲版本、運作模式及實際操作步驟。

服務整合細節見 [Stellar 適配說明](docs/stellar-backend.md)。

## 原始碼與建構

- `native-refactor/lcpatch_core.cpp`：Root 本地化重導、字型載入、跨版本驗證與原生記錄。
- `app/src/main/java/com/filepermwebui/NativeStorageClient.kt`：Stellar 相容層連線與文件服務。
- `app/src/main/java/com/filepermwebui/TranslationApplyService.kt`：背景套用及通知。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：下載、版本辨識、轉換與套用。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：Compose／Miuix 管理介面。

建構環境以 [共用 Android build action](.github/actions/android-build/action.yml) 為準，目前使用 Java 21、Android SDK、NDK 27.2.12479018、CMake 與 Ninja。Windows 設定 `ANDROID_SDK_ROOT` 後，可在儲存庫根目錄執行 `./build.ps1`。

CI 從原始碼建立 native 核心，執行測試、Release lint 與介面預覽。一般 CI 提供未簽章 APK；指定的同儲存庫測試分支另提供已簽章測試 artifact。發布流程以 Actions Secrets 完成 APK v3 簽章、16 KB 對齊與 SHA-256 校驗；版本名稱含預發布標記時建立 GitHub Prerelease。

簽章使用 `LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS`、`LCPATCH_KEY_PASSWORD` 四個 Secrets。各版本的建構結果見 Actions，驗證範圍見 Release 說明。

撰寫發布正文時請參考 [更新說明規範](docs/release-notes.md)。

## 授權與第三方元件

LCPatch 原始碼採 Apache License 2.0。主要使用 Dobby、Compose Miuix、opencc4j、libxposed、Shizuku API／provider（用於 Stellar 相容層）及 AndroidX／Jetpack Compose；詳細授權見 [NOTICE](NOTICE)。Stellar 管理器為另行安裝的外部程式。

內建字型以 Sarasa Gothic SC Bold 1.0.29 為基礎，包含 3,912 字 BMP PUA 映射。字型依 SIL Open Font License 1.1 散布，LCPatch PUA 映射為 CC0 1.0；來源、修改說明與授權文件保留在專案中。遊戲圖像與第三方翻譯內容不屬於 LCPatch 的 Apache-2.0 授權範圍。

LCPatch 為非官方第三方專案，與 Project Moon 及翻譯組沒有隸屬關係。軟體以現狀提供，不附保證；本專案由生成式 AI 協助撰寫與檢查，仍可能存在錯誤或未涵蓋的裝置差異。
