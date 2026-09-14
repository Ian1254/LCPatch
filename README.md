# LCPatch

> **為 Android 打造的 Limbus Company 漢化管理器與 LSPosed 模組。**

[![Android](https://img.shields.io/badge/Android-9%2B-brightgreen?logo=android)](https://www.android.com/)
[![Platform](https://img.shields.io/badge/ABI-arm64--v8a-blue)](#支援環境)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)](https://github.com/LSPosed/LSPosed)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Ian1254/LCPatch?include_prereleases)](https://github.com/Ian1254/LCPatch/releases)

LCPatch 讓 Android ARM64 裝置上的 Limbus Company 漢化安裝與管理更簡單。你可以直接在 App 內下載、匯入、轉換與切換漢化，並透過 LSPosed 模組與開源 native 核心將遊戲的本地化文件導向已套用的內容，同時載入中文字型。

## ✨ 特色

- **一站式漢化管理** — 下載、匯入、切換與套用都能直接在 App 內完成。
- **線上漢化下載** — 顯示下載速度與進度，完成後自動解壓、處理 PUA 並套用。
- **自訂漢化匯入** — 支援 ZIP，亦可掃描 `/sdcard/LCPatch/漢化/` 內已解壓的漢化。
- **繁簡轉換** — 使用 `opencc4j 1.14.0` 對漢化 JSON 進行整體繁簡轉換並顯示進度。
- **自訂字型** — 支援 TTF／OTF 字型，並內建適用於 LCPatch PUA 映射的中文字型。
- **自動結構修正** — 可辨識並整理漢化目錄與語言文件名稱，支援覆蓋日本語、한국어或 English。
- **Miuix 介面** — 支援深色／淺色模式與較現代化的管理介面。
- **診斷與安全保護** — 提供模組日誌、診斷匯出與跨遊戲版本的 native guard，無法安全確認 hook 時不會猜測位址。

> [!NOTE]
> LCPatch 本身不包含第三方漢化內容。漢化清單可包含一般線上來源，以及都市零協會官方 GitHub 儲存庫的最新 Release。

## 🚀 快速開始

1. 從 [Releases](https://github.com/Ian1254/LCPatch/releases) 下載 APK 並安裝。
2. 在 LSPosed 中啟用 **LCPatch**，作用域選擇 **Limbus Company**。
3. 開啟 LCPatch，確認 LSPosed 與 Root 環境檢查皆已通過。
4. 進入「下載漢化」取得漢化，或從設定匯入自己的漢化 ZIP。
5. 選擇需要的覆蓋語言、繁簡格式與字型後套用。
6. 完整關閉並重新啟動遊戲。

> [!TIP]
> 如果套用結果異常，可從設定中的日誌頁匯出診斷文件，用於問題回報。

## 📱 支援環境

| 項目 | 要求 |
| --- | --- |
| Android | Android 9（API 28）以上 |
| ABI | ARM64（`arm64-v8a`） |
| Root | 必須 |
| LSPosed | 支援 libxposed API 102 的環境 |
| 遊戲 | 已安裝 Limbus Company |

目前完整驗證的遊戲版本為 **`1.113.1`（versionCode `468`）**。遇到其他遊戲版本時，native 核心會先檢查 Unity Build ID、指令特徵及唯一候選；無法安全確認時會停用字型 hook，避免套用猜測位址。遊戲更新後仍應重新進行實機驗證。

套用與停用漢化時，LCPatch 會先強制停止遊戲，避免遊戲正在讀取文件時替換目錄。

## 📂 資料位置

| 用途 | 路徑 |
| --- | --- |
| 已下載並解壓的漢化 | `/sdcard/LCPatch/漢化/<名稱>/` |
| 匯入的字型 | `/sdcard/LCPatch/字體/` |
| 遊戲目前讀取的漢化 | `/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/Localize/cn/` |
| Native 診斷記錄 | `/sdcard/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log` |

套用流程會先在暫存位置完成結構整理、PUA 處理、JSON 驗證與字型準備，通過後才以可回復的目錄交換方式更新遊戲快取。

## 🔤 字型與 PUA

內建字型以 Sarasa Gothic SC Bold 1.0.29 為基礎，加入 LCPatch 使用的 3,912 字 BMP PUA 映射。字型依 SIL Open Font License 1.1 散布，來源、修改說明與完整授權文件均保留在專案內。

下載或匯入漢化後會立即完成 PUA 轉換。繁簡轉換會先還原既有 PUA 字元，使用 opencc4j 轉換完整 JSON 文字，再重新編碼為相同 PUA 映射，因此可直接作用於已下載的漢化目錄。

## 🧾 日誌與診斷

模組事件由遊戲程序寫入 LCPatch 的受限 ContentProvider。Provider 會核對呼叫端 UID，只接受 LCPatch 或已安裝的 Limbus Company，並最多保存 500 筆事件。

匯出的診斷文件包含裝置、Android、遊戲、模組與 ABI 資訊，以及已收集的模組事件。可在設定中選擇保存位置，或透過 Android 分享功能附於問題回報。

## 🛠️ 原始碼與建置

主要元件：

- `native-refactor/lcpatch_core.cpp`：本地化文件重導、字型載入、跨版本 guard 與 native breadcrumbs。
- `app/src/main/java/com/filepermwebui/ModernModule.java`：LSPosed API 102 模組入口。
- `app/src/main/java/com/filepermwebui/TranslationRepository.kt`：下載、解壓、轉換、儲存修正與 Root 套用。
- `app/src/main/java/com/filepermwebui/LogProvider.kt`：跨程序日誌儲存。
- `app/src/main/java/com/filepermwebui/MainActivity.kt`：Compose／Miuix 管理介面。

GitHub Actions 會在推送至 `main`、建立 Pull Request 或手動執行時，從原始碼建立 native 核心、執行單元測試與 release lint，並上傳未簽署 APK artifact。推送與 App 版本一致的 `v*` 標籤時，工作流程會使用加密的 Actions Secrets 完成 zipalign、APK v3 簽章與 16 KB 對齊驗證，再建立 GitHub Release 並附上 SHA-256 文件。

自動簽署使用 `LCPATCH_KEYSTORE_BASE64`、`LCPATCH_STORE_PASSWORD`、`LCPATCH_KEY_ALIAS` 與 `LCPATCH_KEY_PASSWORD` 四個 repository Secrets。本機的 `signing/`、APK、native 產物與建置快取均已由 `.gitignore` 排除。可重現的 CI 建置步驟以 [Android build workflow](.github/workflows/android.yml) 為準。

Windows 本機建置需準備 Java 17 以上、Android SDK、NDK 27.2.12479018、CMake 與 Ninja，設定 `ANDROID_SDK_ROOT` 後在儲存庫根目錄執行 `./build.ps1`。腳本會以 Gradle Wrapper 產生未簽署的 release APK；正式散布仍應使用自行保管的簽署金鑰。

## 📜 授權與第三方元件

LCPatch 原始碼依 Apache License 2.0 授權。實際使用的開源元件如下：

- [Dobby](https://github.com/jmpews/Dobby)：Apache License 2.0
- [Compose Miuix](https://github.com/compose-miuix-ui/miuix)：Apache License 2.0
- [opencc4j](https://github.com/houbb/opencc4j)：Apache License 2.0
- [libxposed API／service](https://github.com/libxposed)：Apache License 2.0
- [AndroidX／Jetpack Compose](https://source.android.com/docs/setup/about/licenses)：Apache License 2.0
- [Sarasa Gothic](https://github.com/be5invis/Sarasa-Gothic)：SIL Open Font License 1.1
- LCPatch PUA 映射：CC0 1.0

詳細來源、版本與授權範圍見 [NOTICE](NOTICE) 及各授權文件。遊戲圖像與第三方漢化內容不包含在 LCPatch 的 Apache-2.0 授權範圍內。

## ⚠️ 免責聲明

LCPatch 是非官方第三方專案，與 Project Moon 及各漢化組沒有隸屬或授權關係。專案不提供遊戲本體或受著作權保護的遊戲資源；漢化內容的權利與責任由各來源及使用者依其授權條款承擔。

本工具需要 Root 與 LSPosed，並可能建立、替換或清理 LCPatch 管理目錄及遊戲可讀取的本地化快取文件。使用前請備份重要資料，確認下載來源與目標語言設定，並自行承擔資料遺失、遊戲異常、帳號限制或系統不相容等風險。

本專案由生成式 AI 協助設計、撰寫與檢查，仍可能存在實作錯誤、安全缺陷或未涵蓋的裝置差異。AI 協助不等同完整的人工安全審核。依 Apache License 2.0，本軟體以現狀提供，不附帶任何明示或默示保證；使用者應在理解原始碼與風險後自行決定是否使用。
