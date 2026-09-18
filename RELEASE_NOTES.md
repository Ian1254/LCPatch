# LCPatch 1.2.1-beta.22

本測試版修正 beta.21 底欄拖曳時 selector 提前離開當前頁、導致頁面與底欄割裂的問題。

## 浮動底欄

- 拖曳手勢不再直接接管 selector 的可視位置。
- 按住／拖曳期間只計算候選 target；selector 保持在目前 Pager 位置。
- 手指放開後才提交 navigation，之後由 Pager pagePosition 同時驅動頁面與 selector。
- 因此不再出現「底欄先被拖到下一格、頁面放開後才追上」的斷裂感。
- 保留 beta.21 的方向性 left/right edge deformation；形變只在 Pager 真正開始移動後出現。
- Drag takeover 仍只允許從目前 selector 起手，且重新加入水平手勢判定，避免垂直滑動或輕微手抖誤觸發。
- Drag target 計算保留完整起始位移，不會因跨過 touchSlop 的第一段距離被吃掉。
- 保留 beta.19 的快速 retarget / rubber-band 目標計算，以及 beta.20 的 backdrop ownership。

## 已啟用環境卡片

- 沿用 beta.21 的 shared-container crossfade、pixel-edge morph 與反向 reopen 修正，未更改其動畫系統。

## 驗證

- PR CI 執行 Android 共用 validation。
- 合併後 Release workflow 再次執行 validation、簽章並發布 prerelease。
- 仍建議以實機錄影比較 HyperOS 時鐘的 selector deformation 收尾節奏。
