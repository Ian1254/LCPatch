# libxposed reads this entry class from META-INF/xposed/java_init.list.
-keep class com.lcpatch.ModernModule { *; }

# Android creates these components from the manifest.
-keep class com.lcpatch.ModernApp { *; }
-keep class com.lcpatch.MainActivity { *; }
-keep class com.lcpatch.LogProvider { *; }

# opencc4j discovers and creates converter implementations through reflection.
# R8 cannot infer these calls and otherwise turns some implementations abstract.
-keep class com.github.houbb.opencc4j.support.segment.impl.FastForwardSegment { *; }
-keep class com.github.houbb.opencc4j.support.datamap.impl.DataMapDefault { *; }
-keep class com.github.houbb.opencc4j.support.data.impl.** { *; }

# app_process loads this entry by its fully qualified name.
-keep class com.lcpatch.AdbFileService { *; }
# LibADB discovers the TLS provider by class name.
-keep class org.conscrypt.OpenSSLProvider { public <init>(); }
-keep class org.conscrypt.Conscrypt { public static *** exportKeyingMaterial(...); }
# Conscrypt's adapters for Android versions below this app's minSdk (28).
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
