# LCPatch 1.2.1-beta.13

這個測試版一次完成主頁滾動、導航同步、底欄材質與無障礙、頂欄模糊、已啟用卡片 Morph、主題一致性，以及 Limbus Company 1.114.0 適配。

## 主頁與導航
- 頂層頁面移除多餘的 nestedScroll，修復概觀／日誌／設定上下滾動。
- 簡化底欄手勢狀態，拖曳只做預覽，放手後才選擇目標頁。
- Pager 成為頁面位移的唯一進度來源，底欄膠囊直接跟隨 pager progress，不再另跑一套 settle 動畫。
- 分離 item 寬度與膠囊寬度，改善三個項目的視覺間距。
- 左右邊界拉伸改為漸進阻力，減少硬切與卡住感。
- 未選中頁籤加入按壓回饋。
- 底欄降低實色遮罩比例，強化背景玻璃感。
- 底欄支援 selected semantics，並依 font scale 增加高度容錯。

## 頂欄、Motion 與主題
- 頂部漸層模糊由多層 blur pass 改為單一路徑 mask blur，降低分層霧感與離屏渲染負擔。
- Pager、子頁轉場與卡片 Morph 統一為 340 ms、相同 CubicBezierEasing。
- 深淺模式與底欄／卡片配色改以 MiuixTheme 為來源，不再各自直接讀系統模式。
- About shimmer、日誌狀態色與半透明卡片移除固定紫色／獨立深淺判斷，改跟隨 App 主題。

## 已啟用卡片
- 原卡片與 overlay 共用同一個 collapsed content composable，收合終點文字、顏色與幾何一致。
- overlay 開啟後來源卡片停止重複繪製，避免最後一幀雙影、接合感與小上滑。
- Morph 開始後由 overlay 接管顯示，避免 press 回彈和展開動畫互相打架。
- 詳情／操作按鈕未完全顯示前直接 disabled，避免透明元件誤觸。
- 收合只保留一個 frame handoff，減少尾段閃動。

## Limbus Company 1.114.0
- 新增 1.114.0 Unity fingerprint。
- Font Entry 更新為 `+0xB92F30`，Accessor 更新為 `+0xB73E90`。
- 更新入口指令驗證資料，保留未知 Unity build 安全停用行為。
- FontProfiles 新增 1.114.0 驗證條目。

## 驗證與發布
- PR CI 執行共用 Android validation：單元測試、Lint、native build、Release APK 組裝。
- 合併 main 後由 Android Release workflow 自動簽章並發布 prerelease，維持只保留最新預發布版本。
