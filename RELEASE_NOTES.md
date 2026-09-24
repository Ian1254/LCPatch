# LCPatch 1.2.2-beta.2

本測試版加入 Limbus Company 1.115.0 的 Unity 字型 hook 驗證設定，並為未知 Unity build 加入嚴格的動態驗證。未改動 Localize 讀取重導、漢化下載與套用、PUA、OpenCC 或 UI。

- 已知 build 以 GNU Build ID、可執行區段、固定 offset 和 entry／accessor 指令特徵共同驗證；保留舊版 profile。
- 未知 build 必須找到唯一 accessor、唯一合法 caller、唯一函式入口，並再次核對入口結構，才能安裝字型 hook。僅允許入口 ADRP 的頁面位址 immediate 隨連結位址改變。
- 任一驗證不符即停用字型 hook，並於 `core.unity.unsupported` 記錄原因；成功後可從 `core.unity.verified` 或 `core.unity.dynamic_verified`、`core.font.hooked`、`core.font.swap` 追查。

已以 1.115.0 ARM64 APK 核對 Build ID、兩個指令特徵與唯一 caller／入口；字型實際交換仍須實機確認。未知版本只有在實作特徵維持一致時才可能自動相容，並不保證所有未來版本。
