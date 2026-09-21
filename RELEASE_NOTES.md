# LCPatch 1.2.1-beta.25

本測試版修正浮動底欄在滑動放開後，selector 可能短暫停住、等待 Pager 追上才繼續移動的問題。

## 浮動底欄

- 移除跨頁放手後的 release-hold catch-up 階段；放手時立即從上一幀實際 rendered selector 左右邊界建立 Pager motion segment。
- selector 容器與 NavigationRow 共用同一個映射後 visual position，避免內容先跳、外框後追。
- 同頁返回仍使用原本 local settle；跨頁導航、快速新手勢、rapid retarget 與快速反向都從當前 rendered geometry 接續。
- motion segment 同時保存起點 visual position、Pager position、左右邊界與 target，handoff 後 progress 從新 segment 起點計算。
- 保留 440ms Pager transition、PageTransitionEasing、balanced leading/trailing edge 曲線、stretch cap、玻璃與 backdrop ownership。

## 驗證

- 功能 PR #62 已通過共用 Android CI：單元測試、release lint、release APK 組裝與 artifact 上傳。
- 發布工作流會再次執行相同 validation、APK 簽章、簽章驗證與 16 KB 對齊檢查。
- 發布清理仍明確保留 v1.2.1-beta.23。
