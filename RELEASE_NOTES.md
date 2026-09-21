# LCPatch 1.2.1-beta.24

本測試版修正浮動底欄與「已啟用」環境卡片在 animation ownership 交接時的 continuity 問題。

## 浮動底欄

- gesture、release-hold、release-settle、Pager 與快速 retarget 交接時，都從上一幀實際 rendered selector 左右邊界建立新的 motion segment。
- Pager 在 selector 由手勢持有時已前進的隱藏 progress，不再消耗後續可見定位動畫。
- 新手勢會先 snapshot 當下 geometry，再取消舊 settle；快速反向不會 reset 到 Pager geometry。
- logical target 相同時，只有 Pager 已 settled 且 offset 為 0 才忽略；未完成導航可重新建立 transaction。
- 保留 beta.23 的 440ms Pager transition、PageTransitionEasing、balanced leading/trailing edge 曲線、玻璃與 backdrop ownership。

## 已啟用環境卡片

- 返回端點改用來源卡片持續更新的 live bounds，避免回到舊座標後再 snap。
- 首頁 Card 與 overlay progress=0 共用同一份 collapsed composable，不再手算 summary 位置。
- opening 與 closing 都加入明確的 source/overlay 重疊幀，避免同幀交換造成接棒感。
- 移除來源卡片的 geometry press scale，改用 alpha feedback。
- collapsed 與 expanded 完整內容層在同一 morph progress 上重疊 crossfade，中段不再出現空白 panel。
- opening/closing 快速反向從當前 progress 繼續，duration 依剩餘距離縮短。

## 驗證

- 功能 PR 已通過共用 Android CI：單元測試、release lint、release APK 組裝與 artifact 上傳。
- 發布工作流會再次執行相同 validation、簽章與 16 KB 對齊檢查。
- 發布清理明確保留 v1.2.1-beta.23。
