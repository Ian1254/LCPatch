# LCPatch 1.2.1-beta.20

本測試版修正頂欄高對比色彩暈染與跨頁殘影、提升浮動底欄文字可讀性，並重構「已啟用」環境卡片的展開／收回交接。

## 頂欄

- 移除 beta.19 的頂端 blur suppression，狀態列最上方恢復完整漸進模糊。
- 重新設計 32 點取樣分布與空間權重，讓中心／中圈樣本占比提高，降低外圈色彩形成 halo。
- 加入輕度 range weighting，抑制高對比綠色卡片跨 kernel 大量污染黑色背景，同時保留正常模糊。
- Denoise 改為更小 footprint 與中心高權重，只平滑 stochastic noise，不再形成第二次明顯 blur。
- AnimatedContent 的 incoming／outgoing branch 各自持有 LayerBackdrop，避免不同頁面共寫同一 GraphicsLayer。
- LayerBackdrop coordinates 增加 owner identity，舊 node detach 不會清除新 node 的座標。

## 浮動底欄

- Selector 仍保留 combined backdrop、Lens 折射、色散、高光、內外陰影、spring、速度拉伸與 rubber-band。
- Container glass 以不包含子內容的 exported backdrop 提供給 Selector；NavigationRow 改在 Selector 後繪製。
- Icon 與 label 不再進入 Lens／chromatic aberration 取樣，選中項目保持清晰。
- 完整保留 beta.19 的 click commit 與 touchSlop drag 接管修正。

## 環境狀態卡片

- 移除跨 Window Dialog，改為 MainActivity 同一 Compose root 的 overlay。
- Overlay 完成真實 layout 並停在來源 bounds 後，才與來源卡片原子交接，避免 mount blank frame。
- 收回至 progress=0 後先交還來源卡片，再於下一 frame unmount overlay。
- Details 改在 58% 後淡入，actions 改在 74% 後淡入；收回時按相反順序消失。
- Title／icon 的 expanded endpoint 固定以最終 root bounds 計算，不再跟著動態 panel 尺寸漂移。

## 驗證

- Android CI 執行原生核心建置、單元測試、lintVitalRelease 與 Release APK 組裝。
- Shader 的最終 halo 強度、慢速捲動穩定性與逐幀卡片 handoff 仍需實機確認。
