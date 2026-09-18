# LCPatch 1.2.1-beta.18

本測試版重構頂欄與底部導航的渲染架構，實作基於真實頁面內容取樣的漸進模糊與液態玻璃效果。

## 頂部欄

- 頁面內容改為共享 Backdrop Source，模糊背景與標題／操作按鈕分層繪製。
- Android 13 以上使用全解析度 AGSL 多點採樣，模糊半徑依 Y 座標連續下降，底部以 softer fade 自然融入頁面。
- 第二階段輕量去噪降低慢速捲動時的取樣顆粒與閃爍。
- 不支援 RuntimeShader 的裝置保留漸層表面色降級，不會因 Shader 缺失崩潰。
- 頂層頁面改為固定小標題，標題位置與透明度直接取用 Pager progress，與頁面動畫同時開始及結束。
- 移除由頂欄同時管理模糊、標題 collapse 與頁面導航的舊結構。

## 底部導航

- 導航列改為共享頁面 Backdrop 的玻璃容器，加入 vibrancy、8dp blur、輕度 lens 與低對比邊緣高光。
- 新增不可見 Tab Capture Layer；Selector 同時取樣頁面與 Tab 圖示，形成真正的透鏡折射，不再只是放大前景 Icon。
- Selector 使用連續浮點位置，可停留在兩個 Tab 之間並跟隨拖曳。
- 點擊、按壓、拖曳接管、放開吸附統一由單一 pointer gesture state machine 管理。
- 點擊其他 Tab 時 Selector 立即移動並維持按壓材質；超過 touch slop 後可直接轉入拖曳。
- 速度形變、按壓高光、inner shadow、edge highlight 與輕微色散皆由同一組 selector 狀態驅動。
- 左右邊界加入受限 rubber-band，放開後以 spring 回到有效 Tab。
- 移除原先左右邊界 Animatable、preview stretch 與選中動畫互相重播的結構。

## 效能與授權

- RuntimeShader、RenderEffect、Backdrop、GraphicsLayer 與效果 lambda 均使用可重用實例；每幀只更新動畫值與 shader uniform。
- Backdrop Layer 仍於內容變動時錄製當前幀，避免慢速滑動取樣舊畫面。
- NexioSchedule／Kyant backdrop 衍生程式碼依 Apache-2.0 授權使用，歸屬已加入 NOTICE。

## 驗證

- 已通過 Android CI：單元測試、lintVitalRelease、原生元件建置與 Release APK 組裝。
- 頂欄閃爍、折射強度、快速連點、快速反向拖曳與深淺色可讀性仍需實機確認。
