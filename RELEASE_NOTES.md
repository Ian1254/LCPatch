# LCPatch 1.2.1-beta.21

本測試版完成 PR #53 的動畫同步重構，並補上底欄拖曳接管範圍修正。

## 浮動底欄

- 正常導航改由 Pager 的 pagePosition 驅動 selector 基礎位置，底欄與頁面不再各跑一套獨立導航動畫。
- Selector 改為依實際導航速度計算方向性左右邊界形變，移除固定寬度 + scaleX / pivot 切換的模擬方式。
- 速度經 frame timing 與低通濾波後再驅動 deformation，降低方向反轉與收尾時的細碎抖動。
- Icon / label 改依 visualPosition 連續過渡，不再於 target 更新時立即跳成選中狀態。
- 普通點按只在放開後提交導航；按下階段只保留 press feedback。
- 保留 beta.19 的快速 retarget、rubber-band、release hold 與 gesture ownership。
- 補充修正：只有從目前 selector 膠囊起手並超過 touchSlop 才能進入 drag takeover，避免從底欄其他區域橫拖誤接管 selector。
- 保留 beta.20 的 backdrop ownership；selector 不折射 icon / label。

## 已啟用環境卡片

- Source summary 與 destination detail content 改為重疊 crossfade，移除中段幾乎空白的綠色容器時段。
- Morph panel 改以實際像素 left / top / right / bottom 四邊插值，再由四邊推導尺寸，避免 offset 與 size 各自取整造成的最後一幀 snap。
- 關閉中的 overlay 可從目前 morph progress 反向重新展開，不需要先完成舊動畫。
- 保留 beta.20 的 source / overlay visual ownership handoff。

## 驗證

- Release workflow 會在 version.properties 合併進 main 後執行共用 Android validation、簽章與 prerelease 發布。
- 仍建議以實機錄影確認 HyperOS 參考動畫的主觀運動感、快速反向 retarget 與卡片逐幀 handoff。
