# 無 Root 網路資源替換：可行性與驗證門檻

狀態：**設計研究；未實作、未宣稱可用**。本文件不修改 Root/LSPosed 核心，也不要求 Android 系統 Root。

## 目標

在 Limbus Company 下載語言資源的時候，提供與遊戲版本相符的本地化資源，避免要求使用者自行判斷下載完成的時機或在標題畫面反覆切換 App。

參考：
- https://github.com/ghcruise/LimbusCompany-IOS-Localization ：iOS HTTPS 資源重導，會同時提供匹配的 localize_jp.zip / LocalizePatchInfo.json。
- https://github.com/gongfuture/LimbusCompany-Nginx-Localization ：Android 範例目前需要 Root，**不可當作無 Root 方案**。

## 無 Root Android 的實際權限邊界

1. Android 的 `VpnService` 可在使用者明確同意後建立 VPN 隧道，並限制至遊戲套件。它本身只能路由流量，無法替第三方遊戲修改 HTTPS 信任設定。
2. Android 7 起，目標為 API 24 以上的應用預設不信任使用者安裝的 CA。VPN、Shizuku 的一般 shell 權限都不能改變這點；Unity 自定憑證驗證也可能更嚴格。
3. 所以在沒證實遊戲接受使用者 CA 前，不應把 HTTPS 解密／資源替換做成可按下的正式選項。也不以關閉 TLS 驗證、安裝共用 CA 私鑰、修改遊戲安全保護作為替代方案。
4. HTTPS 有效且資源版本匹配，是繼續開發這條路的必要條件。若遊戲拒絕信任，應立即停止 VPN 替換並提供清楚錯誤，而不是造成遊戲連不上伺服器。

## 開發檢查點

- [ ] 非 Root 裝置上僅為遊戲建立使用者授權的 VPN 連線，其他 App 不受影響；先只觀察來源主機與連線結果，不擷取使用者登入內容。
- [ ] 在得到明確授權的測試環境，驗證客戶端是否支援使用者安裝的憑證；不推測已經成功。
- [ ] 若相容，才支援從可信 GitHub Release 同時選取同版的語言檔和資源資訊，驗證 ZIP 結構、版本與檔案大小。
- [ ] 所有不匹配、不可信、解密／驗證失敗的情況都不修改遊戲內容，並讓 VPN 恢復正常轉送或停止。
- [ ] 實機驗證下載、退出、下次啟動、資源更新的完整生命週期。

## 與 Shizuku 背景覆蓋

Shizuku 模式目前是獨立的「使用者在遊戲退背景後覆蓋 JSON」流程。測試版先修正已有備份時重複套用的錯誤，**不**將 VPN 可行性驗證當作已完成的網路替換模式，不會使用 Root 後端。
