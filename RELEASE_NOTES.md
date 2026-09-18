# LCPatch 1.2.1-beta.19

本測試版修正 beta.18 漸進模糊的頂端邊界取樣，以及快速點按浮動底欄時偶爾不切頁的問題。

## 修正

- 頂欄 blur 與 denoise shader 改為分軸限制取樣：X 軸限制在真實內容範圍，Y 軸禁止讀取螢幕頂端外的透明 padding，同時保留向下取樣頁面內容的能力。
- 最頂端使用平滑強度保護曲線，仍保留狀態列背景模糊與 12dp 最大模糊半徑，減少高亮卡片顏色向上暈染。
- 底欄普通點按改為提交實際按下的 Tab，不再依賴 selector 動畫當下是否已超過一半。
- 按在 selector 上不再立即視為拖曳，水平位移超過 touch slop 後才交由拖曳狀態接管。
- 拖曳接管時取消尚未完成的 click-to-travel mutation，避免快速點按、拖曳和吸附動畫競爭。

## 保留

- 保留 beta.18 的共享 Backdrop、隱藏 Tab Capture、Lens 折射、輕微色散、高光、內外陰影、速度拉伸與 rubber-band。
- 保留 MainActivity 到 Pager 的原有導航鏈，以及 NexioSchedule／Kyant backdrop 的 Apache-2.0 歸屬說明。

## 驗證

- Android CI 會執行單元測試、lintVitalRelease、原生元件建置與 Release APK 組裝。
- 頂端模糊視覺、快速連點與快速反向拖曳仍需在實機上確認。
