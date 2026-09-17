# LCPatch 1.2.1-beta.15

這個測試版修復 beta.14 頂層頁面無法上下滑動的回歸。

## 捲動修復

- 概觀、日誌、設定移除多餘的頂欄 nestedScroll 連線，恢復 LazyColumn 的正常上下滑動。
- 關於、更新、顯示等內頁繼續保留既有 nestedScroll，維持內頁頂欄行為。
- 不修改 beta.12 基底的懸浮底欄、頁面轉場、頂欄模糊與「已啟用」卡片動畫。

## 相容性

- 繼續保留 Limbus Company 1.114.0 支援。
