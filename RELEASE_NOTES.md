# LCPatch 1.2.1-beta.14

這個測試版撤回 beta.13 的 UI 重構，直接恢復 beta.12 的介面、導航與動畫實作，同時保留 Limbus Company 1.114.0 相容性。

## UI 回復

- MainActivity 恢復 beta.12，撤回 beta.13 的 Pager、捲動與頁面轉場重寫。
- 懸浮底欄恢復 beta.12 的拖曳、速度形變、放手交接與邊界處理。
- 頂欄恢復 beta.12 的多層漸變模糊實作。
- 「已啟用」卡片恢復 beta.12 的展開、返回與來源卡片交接。
- 後續 UI 修正將從這個基準逐項重做，不再一次重寫多個動畫系統。

## Limbus Company 1.114.0

- 保留 1.114.0 Unity fingerprint。
- Font Entry 為 `+0xB92F30`，Accessor 為 `+0xB73E90`。
- 保留新版入口指令驗證與未知 Unity build 安全停用行為。
- 保留 FontProfiles 的 1.114.0 驗證條目。
