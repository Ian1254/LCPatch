> 歷史文件：內建無線 ADB 分支已替換此後端，現行設計見 [內建無線 ADB](embedded-adb.md)。

# Stellar 文件後端（1.2.4-test.4）

本次改為引導使用 Stellar 管理器，透過其官方 Shizuku 相容層連接既有文件服務。沒有接入獨立 Stellar API，也沒有把 Shizuku Manager 包含在 APK 內。

## 選擇相容層的依據

- [Stellar 官方 README](https://github.com/roro2239/Stellar#shizuku-兼容层) 明確列出 Shizuku API 用戶端相容性，涵蓋狀態、權限及 UserService。
- [Stellar-API 公告](https://github.com/roro2239/Stellar-API#重要变更) 說明未來 Stellar 2.0 將棄用獨立 API。
- 實際橋接見上游 [ShizukuServiceIntercept](https://github.com/roro2239/Stellar/blob/main/server/src/main/kotlin/roro/stellar/server/shizuku/ShizukuServiceIntercept.kt) 與 [ShizukuUserServiceAdapter](https://github.com/roro2239/Stellar/blob/main/server/src/main/kotlin/roro/stellar/server/shizuku/ShizukuUserServiceAdapter.kt)。

## 行為

- 無 Root 僅使用 Stellar；移除 SAF 遊戲資料夾授權、寫入後端與載入方式選擇。舊的後端偏好值不再參與分流。
- 保留已下載內容、已套用名稱／版本與原本選用包的記錄。
- 從概觀「Stellar 與權限」操作；先檢查 `roro.stellar.manager` 是否可用，再檢查相容層 Binder 與權限。
- 未安裝時授權入口開啟 Stellar 的官方下載頁；未連線時開啟管理器，提示啟動服務與相容層。
- UserService 仍使用既有 Shizuku 用戶端協定，由 Stellar 的轉接層處理。需要在 Stellar 開啟「Shizuku 相容層」；不要同時啟動兩個提供相同協定的服務。
- 服務改稱 Stellar，不改 Root／LSPosed native 核心、套用內容或直接套用流程。

## 驗證界線

本次不執行實機或模擬器測試。建構與單元測試不能驗證遊戲的包名偵測；相關原因依使用者裝置回報記錄。安裝 Stellar 不會自動改變原本管理器的安裝狀態。

不建立 Tag、不合併主分支、不發布 Release；以簽章 Actions artifact 交由使用者測試。
