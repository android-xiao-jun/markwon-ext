# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in $ANDROID_HOME/tools/proguard/proguard-android.txt

# Markwon 使用 commonmark-java 解析 Markdown，通过反射/接口实现类需保留
-keep class org.commonmark.** { *; }
-keep class io.noties.markwon.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
