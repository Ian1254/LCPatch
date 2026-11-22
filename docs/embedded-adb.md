# 內建無線 ADB

本分支將無 Root 寫檔後端改為 LCPatch 內建 ADB 用戶端，不再連接 Stellar／Shizuku。不修改、重簽或重打包遊戲 APK。

## 使用方式

1. Android 11 以上，連上 Wi-Fi，開啟開發人員選項 → 無線偵錯。
2. LCPatch 概觀 → 無線 ADB 與權限 → 設定／連線無線 ADB。
3. 首次使用，以分割畫面保留系統「使用配對碼配對裝置」視窗，輸入配對連接埠及六位數配對碼。關閉系統視窗會讓配對資訊失效。
4. 配對成功後，輸入無線偵錯首頁的連線連接埠並連線；它通常不是配對連接埠。
5. 下載、匯入及套用維持原有流程。遊戲啟動完成，到標題畫面後，點擊待套用通知再次套用。
6. App 程序結束、無線偵錯服務重啟或裝置重啟後，重新輸入目前連線連接埠。身分金鑰保存在 App 的 noBackup 私有目錄；未儲存配對碼。
7. 可在 LCPatch 中斷連線；要撤銷配對，請在系統無線偵錯頁移除 LCPatch。清除 App 資料或重新安裝後需重新配對。

Android 9／10 不提供本機無線配對入口，Root／LSPosed 支援不變。未實作 mDNS 自動探索，避免連到區網其他裝置；所有連線固定為 127.0.0.1。

## 文件服務

- LibADB Android 3.1.1 負責 ADB 協定；Conscrypt 提供 TLS 1.3。Bouncy Castle 產生本 App 專用的 RSA 身分憑證，並不修改遊戲的 HTTPS 憑證信任。
- `TimedPairingConnection.java` 衍生自上游同版本的 `PairingConnectionCtx.java`，加入 15 秒 socket 連線／讀取／TLS 握手逾時，以及失敗時的 null-safe socket 清理。
- 連線後先核對 shell UID 2000。每次套用，以已安裝 LCPatch APK 為 CLASSPATH，透過 ADB exec 啟動 `com.lcpatch.AdbFileService`；不開放網路監聽埠或匯出的 Binder 服務。
- 傳輸為 8-byte big-endian 長度加 ZIP 二進位內容，上限 512 MiB。先收完整份 ZIP 到 shell 私有權限的暫存檔，再呼叫既有 `NativeFileInstaller`，避免傳輸未完成就開始覆蓋。
- 目標固定為目前 Android 使用者的 Limbus Company `Localize/jp` 或 `kr`，只接受符合語言的 JSON。沿用 ZIP 路徑、檔案數、解壓容量及逐檔原子替換檢查。
- 進度及成功／失敗以獨立行協定傳回；錯誤以 Base64 編碼，避免換行混入協定。只有明確收到成功訊息才更新套用狀態。
- 傳輸／套用 180 秒後關閉 ADB stream；shell 工作程序最遲 185 秒退出。正常完成或失敗會清除傳輸暫存檔；硬性逾時或程序被殺可能留下暫存檔。
- 安裝器依舊是逐檔覆蓋，沒有整包回滾。寫入中斷後應重新套用；本次未加入遊戲退出監看或原文還原。

## 啟動文件校驗

內建 ADB 解決寫檔權限及外部管理器依賴，不改變遊戲驗證行為。本分支未修改 `LocalizePatchInfo.json`、校驗結果或遊戲程序；仍需先讓遊戲完成原始資源驗證，再套用漢化。

後續若研究本地清單同步更新，必須先取得對應遊戲版本的本地清單位置、遠端更新時機及簽章／雜湊驗證關係，並驗證下次啟動與資源更新。現有原始碼不構成這條路可用的證據。

## 驗證界線

單元測試涵蓋二進位 frame 邊界、截斷傳輸、大小上限、錯誤訊息與 shell 路徑編碼；現有安裝器測試涵蓋語言覆蓋及非法路徑。建構通過不能證明實機配對、ROM 對 shell 存取 Android/data 的限制或遊戲載入效果。

交付前應在非 Root ARM64 裝置驗證：首次配對、錯誤／過期配對碼、錯誤連線埠、撤銷配對、關閉偵錯、重啟後重連、大小包傳輸、中斷重試，以及標題畫面後套用與下次啟動行為。

## 上游

- https://github.com/MuntashirAkon/libadb-android/tree/3.1.1
- https://github.com/MuntashirAkon/spake2-java/tree/2.2.1
- https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi

Spake2 是 LGPL-3.0 相依項目，來源固定在上述 2.2.1 標籤。可從上游 checkout 該標籤，依其 `android/build.gradle` 建置 Android AAR，將本專案的對應 Gradle 相依替換成本機 AAR，再以 `./gradlew assembleDebug` 重建；本分支不修改 Spake2。相關授權全文隨 APK 的 assets 提供。
