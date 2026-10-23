# 無 root 文件載入與 App 檢查

這次新增的無 root 模式不修改遊戲 APK、不注入字型，直接覆蓋遊戲既有的日語／韓語 JSON，保留漢化來源完成的相近漢字替換。

## 載入契約與來源

- 載入目錄：`Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/jp` 或 `kr`。
- 對應檔名：`JP_*.json`／`KR_*.json`，包含原有子目錄。
- 無 root 不能沿用 Root 模式的 `cache/Localize/cn`、PUA 或自訂字型。
- 格式依據：[pzwboy 的發布流程](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid/blob/main/.github/workflows/update-084.yml) 與 [使用說明](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid#readme)。
- Shizuku 整合依據：[官方 API／UserService 文件](https://github.com/RikkaApps/Shizuku-API#userservice)。
- SAF 限制依據：[Android 文件存取說明](https://developer.android.com/training/data-storage/shared/documents-files#document-tree-access-restrictions)。Android 11 以上標準選擇器不允許授權 `Android/data`；本實作沒有繞過此限制。

Shizuku 使用 shell 權限的 UserService，只提供套用／還原與備份狀態，透過檔案描述元接收 ZIP。服務先關閉遊戲，在同一 Localize 目錄備份整個原語言目錄，於暫存目錄合併 JSON 後交換目錄；發生錯誤會嘗試還原。更換漢化包與語言前先回到原始備份，避免上個漢化新增的文件殘留。完成後解除服務連線。

SAF 僅寫入使用者明確授權的遊戲 Localize 目錄，先備份所有同名文件並原子儲存還原清單，再寫入及核對 SHA-256。中斷後仍保留還原資料；還原不會刪除未列入清單的文件。SAF 的備份保存在 LCPatch 私有空間，解除安裝或清除 App 資料會遺失這份備份，因此應先停用漢化。

## 已修正的問題

| 優先度 | 問題 | 修改 |
| --- | --- | --- |
| 高 | 下載、匯入、啟動診斷及清除日誌都可能呼叫 su | 無 root 模式改存 App 私有目錄，所有 Root 入口依模式隔離；新安裝預設無 root，已套用 Root 的舊使用者保留原模式 |
| 高 | 原生文件覆蓋與 hook 重導路徑混用 | 使用獨立原生載入路徑，禁止無 root PUA、繁簡轉換、字型注入及不符合語言的包 |
| 高 | 覆蓋資料沒有無 root 還原機制 | Shizuku 目錄備份／交換、SAF 還原清單；保留新增及原有文件的差異 |
| 高 | 切換模式或下載時能同時修改資源 | 模式、字型、匯入、語言與套用控制依工作狀態停用；已啟用漢化須先停用才可換模式或載入方式 |
| 高 | 停用 Root 漢化後再套用不寫入新資源，且驗證必敗 | 將新內容寫入停用目錄，依啟用狀態驗證；維持停用直到使用者開啟 |
| 中 | 環境卡只憑作用域宣告健康 | 同時納入遊戲安裝與 Root 狀態 |
| 中 | 權限詳情開啟後顯示舊狀態 | 即時同步權限結果與狀態文案 |
| 中 | 卡片使用系統深色設定，忽略 App 自訂主題 | 以目前 Miuix 主題表面色判斷 |
| 中 | 固定高度及水平資訊列可能截斷大字、長裝置資訊 | 卡片依內容高度排列，資訊改為上下排列；詳情含標題可整體捲動 |
| 中 | 錯誤短暫消失且重試動作沒有介面 | 錯誤保留到關閉，提供重試與螢幕閱讀器狀態語意，長內容可捲動 |
| 中 | 切換模式／語言期間舊下載清單可能蓋掉新清單 | 比對請求模式與語言，丟棄過期結果並重新取得 |
| 中 | 遊戲安裝資訊只在初次開啟時查詢 | 返回 App 後重新讀取 |
| 中 | shader cache 未在入口保護 Android 13 API | 加入明確 SDK 檢查；既有呼叫端保留舊裝置降級效果 |
| 低 | 權限 overlay 使用未使用 constraints 的 BoxWithConstraints，完整 lint 失敗 | 改為 Box，移除多餘子組合 |
| 高 | 漢化名稱可為 .／.. 或隱藏交易目錄名稱 | 限制檔名並加入回歸測試 |

## 驗證與限制

- 最終驗證（2026-10-24）：`testDebugUnitTest lintDebug lintVitalRelease assembleDebug assembleRelease` 全數成功。38 項測試，37 通過、1 項既有 fixture 條件略過；完整 Debug lint 為 0 錯誤、55 項警告。
- 本機 Debug APK 採開發測試簽章，Release APK 未簽署；兩者都包含從原始碼建置的 ARM64 native 核心，ELF LOAD 對齊為 16 KB。本機驗證未使用正式金鑰；正式版本由 GitHub Actions 的發布流程建置及簽署。
- 單元測試包含原生目錄路徑、語言包拒絕、保留日文字形、ZIP 匯出、下載替換失敗保留舊檔、原生套用／還原、惡意 ZIP 路徑回復、更換語言與漢化包不留殘檔。
- 真實來源 `v1.117.1/jp.zip`：2,420 個 JSON，全部位於原生 JP 目錄，沒有 PUA 字元；SHA-256：`3982701c39e434f98511914816018d4997de6eb68b7dda50c4e0cdf64f71ebaa`。檢查腳本：`validation/check_native_release.py`。測試包不隨儲存庫散布。
- native 核心使用 NDK 27.2.12479018 從原始碼成功建置。
- 原有 ScannerRuleTest 依賴未隨儲存庫提供的遊戲 libunity fixture，因此依既有條件略過。
- 沒有連線 Android 裝置：尚未實測 Shizuku 的 ROM 權限差異、遊戲實際載入、375px／橫向／平板、大字體、減少動態效果與 TalkBack。編譯及單元測試不能代替這些驗證。
- 遊戲更新可能重新下載並覆蓋原生語言文件。應先停用漢化，再更新遊戲／語言資源，最後重新套用；不會自動監控或阻擋遊戲更新。
- 原有 root native 核心的儲存路徑仍以主使用者為基準，多使用者裝置需另行驗證。
- lint 的套件版本更新建議、固定 root 路徑、舊啟動圖示密度、ContentProvider 匯出提示等保留在報告中；LogProvider 已有呼叫 UID 驗證，沒有為消除提示而放寬存取。
