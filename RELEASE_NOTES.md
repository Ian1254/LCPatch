# LCPatch 1.2.1-beta.14

這個測試版捨棄 beta.13 的 UI 重構，直接以 beta.12 的穩定介面為基底重新建立。

## UI 基底

- 完整保留 beta.12 的主頁、懸浮底欄、頂欄模糊與「已啟用」卡片動畫。
- 不移植 beta.13 對 MainActivity、SukiFloatingBottomBar、TintedBar 與 EnvironmentStatusCard 的重寫。
- 避免 beta.13 帶回底欄交接、膠囊形變、頂欄卡片感與卡片返回閃動等回歸風險。

## Limbus Company 1.114.0

- 新增 1.114.0 Unity fingerprint。
- Font Entry 更新為 `+0xB92F30`，Accessor 更新為 `+0xB73E90`。
- 更新入口指令驗證資料，保留未知 Unity build 安全停用行為。
- FontProfiles 新增 1.114.0 驗證條目。

## 後續原則

後續 UI 修正將以 beta.12 為基準逐項加入，每次只處理一個可驗證區域，避免再次同時重寫導航、模糊與卡片動畫。
