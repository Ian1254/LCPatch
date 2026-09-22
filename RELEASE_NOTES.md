# LCPatch 1.2.1

這是 1.2.1 系列的正式版，基於已完成 Android CI、簽章與實機測試用 beta 的最新程式碼發布。

## 浮動底欄

- 修正滑動放開後 selector 可能短暫停住、等待 Pager 追上才繼續移動的問題。
- 跨頁放手時，從上一幀實際 rendered selector 左右邊界直接建立 Pager motion segment。
- selector 容器與 NavigationRow 共用同一個映射後 visual position，避免內容先跳、外框後追。
- 同頁返回、快速新手勢、rapid retarget 與快速反向都從當前 rendered geometry 接續。
- 保留 440ms Pager transition、PageTransitionEasing、balanced leading/trailing edge 曲線、stretch cap、玻璃與 backdrop ownership。

## 已啟用環境卡片

- 保留 beta.24 已完成的 shared-container handoff 修正：live source bounds、來源與 overlay 共用 collapsed content、明確重疊幀及快速反向 continuity。
- 保留來源卡片非幾何 press feedback 與 collapsed／expanded content 的重疊 crossfade。

## 發布驗證

- 正式版 PR 會先執行共用 Android validation。
- 合併後的 Release workflow 會重新執行 validation、產生 release APK、簽章驗證、16 KB 對齊檢查，並上傳 APK 與 SHA-256。
