package com.lcpatch;

/** Game versions with known verification status; runtime also checks Unity signatures. */
public final class FontProfiles {
    public static final class Profile {
        public final long versionCode;
        public final String gameVersion;
        public final String libraryName;

        private Profile(long versionCode, String gameVersion, String libraryName) {
            this.versionCode = versionCode;
            this.gameVersion = gameVersion;
            this.libraryName = libraryName;
        }
    }

    private static final Profile[] VERIFIED = {
            new Profile(468L, "1.113.1", "liblcpatch_core.so"),
            new Profile(469L, "1.114.0", "liblcpatch_core.so")
    };

    private FontProfiles() {}

    public static Profile find(long versionCode) {
        for (Profile profile : VERIFIED) {
            if (profile.versionCode == versionCode) return profile;
        }
        return null;
    }

    public static String status(long versionCode, String gameVersion) {
        Profile profile = find(versionCode);
        if (versionCode < 0) return "無法確認遊戲版本";
        if (profile != null) return "已實機驗證 · 執行時仍檢查 Unity";
        if (gameVersion.startsWith("1.115.0 (")) return "已收錄 Unity 特徵 · 待實機驗證";
        return "未實機驗證 · 啟動時檢查 Unity";
    }
}
