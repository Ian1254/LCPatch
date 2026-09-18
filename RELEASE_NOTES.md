# LCPatch 1.2.1-beta.22

本測試版重構浮動底欄小膠囊的導航形變，讓運動更接近 HyperOS 時鐘的 leading / trailing edge 行為。

## 浮動底欄

- 移除 beta.21 以 Pager 速度經低通濾波再驅動 deformation 的第二條形變時間軸。
- Selector 改為真正的左右物理邊界 morph：向右時右側 leading edge 先走、左側 trailing edge 後追；向左時方向相反。
- Leading / trailing edge 共用 Pager navigation progress，但使用不同的單調進度曲線，不再先決定 center 再額外放寬。
- Pager 到達目標時，左右邊界也同時回到正常寬度，避免位置先到、膠囊最後再縮一下的收尾違和。
- 最大額外邊界分離限制為實際像素距離，避免跨兩頁導航把 selector 拉成過長膠囊。
- 快速 retarget 時從當下實際 rendered left / right edge 接續，不先 reset 成正常寬度再反向。
- 保留 beta.21 的 Pager navigation、drag / release-hold ownership、press feedback、NavigationRow 過渡，以及 beta.20 的 backdrop ownership。

## 其他

- 「已啟用」環境卡片 morph 沿用 beta.21，未修改。
- 發布流程額外保留 v1.2.1-beta.21，不會在 beta.22 發布後刪除上一版。

## 驗證

- PR Android CI 已驗證新 selector edge morph 可通過共用 Android validation。
- Release workflow 會再次執行 validation、簽章並發布 prerelease。
