# LCPatch 1.2.2-beta.1

本測試版精修底部浮動導航、操作進度與提示通知；不修改漢化核心、Native hook、PUA／OpenCC 或已啟用環境卡片動畫。

## 浮動底欄

- 移除 lens、refraction、vibrancy、selector backdrop 與玻璃高光，改為低存在感的模糊底欄及普通半透明 selector。
- 保留既有 Pager motion、leading／trailing edge、stretch cap、release settle、rapid retarget 與快速反向 continuity。
- selector 與 icon／label 繼續共用相同 visual position，不更改導航 ownership。

## 操作進度

- App 更新、漢化下載並套用、已下載漢化套用改為按鈕本體顯示進度，尺寸與文字位置保持固定。
- 下載完成後進度維持 100%，準備、處理與套用階段只切換文字，不再出現 100% 跳回 0%。
- 使用 URL 或 pack path 綁定任務 owner，避免列表內其他按鈕一起顯示進度。
- Preference／Dropdown 發起的處理保留在對應 row 內顯示輕量進度。

## 提示通知

- 成功與資訊提示改為較小的 floating notice，顯示約 2.8 秒並稍微上移。
- 統一 Info、Success、Error 狀態，避免失敗流程被當作普通提示自動關閉。
- Error notice 保留重試、查看日誌與關閉操作。

## 發布驗證

- 發布 PR 會先執行共用 Android validation。
- 合併後的 Release workflow 會重新執行 validation、產生並簽章 APK、檢查簽章與 16 KB 對齊，然後發布 Beta Release。
