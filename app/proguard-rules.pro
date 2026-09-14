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
