package com.lcpatch;

/** Registry of font-hook builds that have passed device verification. */
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

    public static String status(long versionCode) {
        Profile profile = find(versionCode);
        return profile == null ? "未驗證遊戲版本" : "已驗證 · 文字與字型核心";
    }
}
