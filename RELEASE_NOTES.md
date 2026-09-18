# LCPatch 1.2.1-beta.23

本測試版調慢頂層頁面切換，並重新平衡底欄小膠囊的 leading / trailing edge 速度分布。

## 頁面切換

- Top-level Pager transition 從 360ms 調整為 440ms。
- 保留既有 PageTransitionEasing，不改變頁面導航架構與 ownership。
- 讓概觀／日誌／設定之間的切換不再過於急促。

## 浮動底欄

- Selector 的 leading / trailing edge 不再使用前半段過度前衝的曲線。
- 改成以 Pager progress 為中心做對稱偏移：leading 稍微領先、trailing 稍微落後。
- 使用中段最強、起點與終點回到 0 的 stretch profile，避免前緣在動畫前半段就幾乎跑完。
- Selector 中心速度維持與 Pager 一致，仍保留 beta.22 的 true edge morph。
- 保留既有快速 retarget、drag / release-hold、press feedback、NavigationRow 過渡與 backdrop ownership。

## 其他

- 「已啟用」環境卡片 morph 未修改。
- 發布流程保留 v1.2.1-beta.22，不會在 beta.23 發布後刪除上一版。

## 驗證

- PR Android CI 會先執行共用 Android validation。
- 合併後 Release workflow 會再次執行 validation、簽章並發布 prerelease。
