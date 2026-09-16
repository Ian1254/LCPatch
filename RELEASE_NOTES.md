# LCPatch 1.2.1-beta.2

LCPatch 1.2.1-beta.2 聚焦於底欄互動、Top-level 導航同步，以及概觀頁環境狀態入口的重新整理。

## 底欄與導航

- 懸浮底欄選中膠囊改為較緊湊的彈性樣式。
- 按住目前選中的膠囊時先縮小，開始拖曳後依方向拉伸；前緣跟手、後緣保留阻尼感。
- 拖曳期間仍只預覽底欄選擇，頁面不跟手移動；放手確認目標後才執行頁面切換。
- 放手或點擊底欄項目時先提交 Top-level 導航狀態，再由 Pager 執行切換，降低動畫途中進入 detail page 時 history 記到舊頁的風險。
- 選擇尚未 settle 前鎖定新的底欄選擇，避免連續操作造成多個 Pager 動畫互撞。
- 統一 `LOGS` 與 `SETTINGS` 的 fallback 返回層級，使 `parentPage()` 與 `NavigationState.navigateBack()` 使用同一套規則。

## 環境與權限

- 移除設定頁重複的「權限與初始設定」入口。
- 概觀頁「是否已啟用」狀態卡改為可展開的環境與權限卡片。
- 展開後可直接查看模組作用域、Root 權限與遊戲安裝狀態，並可重新檢查作用域或 Root 權限。
- 首次啟動 onboarding 保留，並與概觀頁共用相同的權限檢查動作。
- 狀態卡使用內容尺寸動畫，在收合狀態與完整環境資訊之間平滑轉換。

## 測試

- 新增 Top-level 選擇先提交狀態、再進入 detail page 的導航模型測試。
- 新增父頁 metadata 與 fallback back 行為一致性測試。
- 保留既有 detail push/pop、Top-level 清 history 與頁面 metadata 測試。

## 驗證範圍

預發布流程包含單元測試、Lint、原生元件建置、Release APK 組裝、APK 簽章及簽章驗證。