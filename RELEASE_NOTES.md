# LCPatch 1.2.1-beta.1

LCPatch 1.2.1-beta.1 為 1.2.1 開發週期的首個預發布版本。本版聚焦於導航結構重構與可測試性改善，維持 1.2.0 的既有介面、完整畫面水平轉場、主頁 Pager 與浮動底欄互動行為。

## 架構調整

- 新增獨立的 `AppNavigation.kt`，集中管理頁面識別、Top-level 頁面集合、父子頁層級與頁面標題。
- 將 detail page 的 push/pop、導航歷史與轉場方向決策集中至純 Kotlin `NavigationState`。
- `MainActivity` 改為套用 `NavigationState` 的結果，不再自行維護重複的導航分支與父頁規則。
- 頁面標題統一由導航模型提供，降低 UI 層與路由定義分散造成的不一致風險。
- 不變更 1.2.0 已建立的固定尺寸 Screen/Scaffold 轉場架構與動畫參數。

## 測試

- 新增 detail page 進入與返回的 history push/pop 測試。
- 新增切換至 Top-level 頁面時清除 detail history 的測試。
- 新增無 history 時的父頁返回規則測試。
- 新增 Top-level 頁面集合與頁面標題映射測試。

## 驗證範圍

預發布流程包含單元測試、Lint、原生元件建置、Release APK 組裝、APK 簽章及簽章驗證。