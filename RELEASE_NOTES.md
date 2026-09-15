# LCPatch 1.2.0-beta.8

本版依實際操作錄影重新調整浮動底欄的互動模型，並完成一批低風險基礎架構重構。相較 beta.7，底欄拖曳不再直接拖動頁面，而是先預覽選中位置，放手後才切換主頁。

## 導航與底欄

- 浮動底欄只允許從目前選中的膠囊開始橫向拖曳。
- 拖曳期間頁面保持不動，只更新底欄膠囊與圖示／文字的選中程度。
- 膠囊跨項目時加入拉伸與收縮形變，讓移動過程更接近參考介面的彈性效果。
- 放手後才決定目標頁，再由 Pager 執行一次完整切頁動畫；不再以底欄拖曳進度直接控制頁面。
- 浮動模式仍禁止從頁面內容區域直接左右切換主頁。
- 子頁 push/pop 使用更明確的整頁進入與反向返回動畫，方向依實際導航堆疊決定。

## 下載、Root 與背景工作

- App 更新與漢化下載改用共用 HTTP／下載層，統一 timeout、redirect、User-Agent、256 KiB buffer 與進度更新。
- 漢化下載加入 `.part` 與伺服器支援時的 Range 續傳，並在下載迴圈支援協程取消。
- Root 指令集中到共用 `RootShell`，統一 timeout、輸出、exit code 與序列化執行，減少各處重複的 `su -c` 邏輯。
- 移除固定約 2 秒一次的模組狀態輪詢，改為進入相關頁面及手動重檢時更新。
- CrashDiagnostics 保留既有節流，並改用共用 Root 執行層。

## 介面與日誌重構

- 「關於」與「應用程式更新」不再共用 `about(updateOnly)`，改為獨立頁面實作。
- LogProvider 改為追加寫入，僅在達到筆數或大小門檻時進行有界 trim，避免每新增一筆就重寫整份日誌。
- LogProvider 的 caller 授權模型維持現狀，以避免在未充分驗證下破壞 Limbus／模組相容性。

## 驗證

- Android CI 已通過 unit tests、Lint、Release APK 組裝與 native core 建置。
- 本版沒有宣稱完成尚未實作的 GitHub ETag／rate-limit cache、完整 predictive-back、MainActivity／TranslationRepository 全面拆分或 CI reusable workflow 重構。

## Beta 提醒

- 本版仍為預發布版本。建議更新後實際測試：底欄慢速拖曳、膠囊拉伸、放手切頁、設定子頁進出、漢化下載中斷後續傳、App 內更新與 Root 套用流程；若手感或功能仍有異常，請附上錄影或日誌方便比對。
