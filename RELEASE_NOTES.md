# LCPatch 1.2.1-beta.13

這個測試版集中整理主頁導航、模糊與卡片動畫，並適配 Limbus Company 1.114.0。

## 主頁與導航
- 移除頂層頁面多餘的 nestedScroll，修復概觀／日誌／設定的上下滾動。
- 收斂懸浮底欄的拖曳、速度、選中與切頁狀態，降低重播、吞動畫與狀態重入。
- 讓底欄與頁面使用同一導航位置來源，減少兩套動畫互相追趕的感覺。
- 分離 item 幾何與膠囊寬度，修正三個項目的視覺間距。
- 邊界拉伸改成漸進阻力，避免左右端突然硬切。
- 未選中頁籤補按壓回饋，並補 selected semantics 與大字體容錯。
- 降低底欄實色遮罩比例，強化玻璃背景感。

## 頂欄與主題
- 頂部漸層模糊改成更少的 blur pass，降低分層霧感與離屏渲染負擔。
- 深淺模式改以 App 自己的主題狀態為準，不再直接混用系統模式。
- 清理 About 等頁面的固定裝飾色，改用主題色。
- 底欄、頁面與卡片統一 motion 參數。

## 已啟用卡片
- 收斂原卡片與 overlay 的共用內容，降低收合末段雙影與接合感。
- 統一收合終點的文字顏色與幾何。
- 展開開始後由 morph 接管，避免 press 動畫先彈一下再展開。
- 詳情與操作按鈕在尚未顯示時停用觸控，避免透明元件誤觸。

## Limbus Company 1.114.0
- 新增 1.114.0 Unity fingerprint。
- Font Entry 更新為 `+0xB92F30`，Accessor 更新為 `+0xB73E90`。
- 更新入口指令驗證資料，保留未知 Unity build 安全停用行為。
- FontProfiles 新增 1.114.0 驗證條目。

## 驗證
- PR CI 會執行共用 Android validation：單元測試、Lint、native build、Release APK 組裝。
- 合併 main 後由既有 Android Release workflow 自動簽章並發布預發布版本，只保留最新 prerelease。
