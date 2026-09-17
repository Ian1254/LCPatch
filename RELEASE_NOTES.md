# LCPatch 1.2.1-beta.17

本測試版重構底部導航列、頁面切換與頂層頁面頂欄，供實機驗證 HyperOS／MIUIX 式互動。

## 底部導航

- 選中背景改為獨立 Selection Overlay，不再由各 NavigationItem 各自繪製。
- Overlay 左右邊界分開動畫，移動時會先向目標方向拉伸，再由後方邊界跟上。
- 按壓回饋與導航動畫分離；只有放開確認後才切換頁面。
- 移除延遲計時、導航 Job、generation 與多套拖曳狀態，避免動畫重播或頁面不同步。
- Pager 與 Overlay 由同一個 navigation transaction 同時開始。
- navigation bar inset 僅處理一次，避免底部額外色塊。

## 頂部欄

- 使用獨立的 Expanded Title 與 Collapsed Title，不再將單一文字縮放搬移。
- 各頁捲動位置分別控制 collapseProgress。
- contentUnderTopBar 獨立控制 Blur／Material，只在內容進入頂欄後顯示材質。
- 移除容易閃爍的漸層 Blur mask，改用穩定的均勻材質層。
- 頂欄僅顯示目前頁面名稱。

## 驗證

- 已通過單元測試、Lint、原生元件建置與 Release APK 組裝。
- 本版本主要供實機檢查快速切頁、慢速捲動、Blur 穩定性及系統導覽列 inset。
