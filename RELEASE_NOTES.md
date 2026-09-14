# LCPatch 1.2.0-beta.1

這是 LCPatch 1.2.0 系列的第一個公開測試版本。

## 主要更新

- 版本策略改為 SemVer prerelease，後續同系列測試版將使用 `beta.2`、`beta.3`，穩定前候選版使用 `rc.x`。
- App 內更新現在會精準匹配與版本一致的 APK 與 SHA-256 資產。
- 更新下載加入 SHA-256 完整性驗證。
- 更新下載支援伺服器允許時的 Range 續傳；不支援時會安全重新下載。

## 漢化與相容性

- 本次尚未啟用實驗性的 runtime path redirection；既有相容／快取交換模式維持不變。
- Native guard 與目前已驗證的套用方式不在本次可靠性修改中改動。

## 修正與改善

- 修正 prerelease 版本比較，能正確處理 `beta.2`、`beta.10`、`rc.1` 與正式版的先後順序。
- 取消或失敗的更新下載會清理未完成的 `.part` 檔案。
- CI 與 Release workflow 分離：一般 PR／main 只負責測試、Lint 與建置；版本 tag 才執行簽名與 GitHub Release 發布。
- Release workflow 在建置前檢查 tag 與 Gradle `versionName` 是否完全一致。

## 已知問題 / Beta 提醒

- UI 導航重整、日誌提升為底欄主頁、獨立更新頁與可選 Liquid Glass 尚未在此批提交中完成，因此不列為本版本已實裝功能。
- `1.2.0-beta.1` 為測試版本，正式使用前仍建議保留可回復方案並回報相容性問題。
