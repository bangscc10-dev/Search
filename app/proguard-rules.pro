# ---- Search browser ProGuard/R8 rules ----

# Keep all @JavascriptInterface methods (the home.html <-> app bridge).
# Without this, R8 strips/renames them and the home page's calls break.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the WebView JS interface class itself.
-keep class com.search.browser.** { *; }

# WebView with JS enabled — standard keep rules.
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

# Keep view binding generated classes.
-keep class com.search.browser.databinding.** { *; }

# General Android safe-keeps.
-dontwarn android.webkit.**
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# ---- GMS Google Code Scanner / ML Kit ----
# The code scanner loads classes dynamically; keep ML Kit + GMS vision
# classes so R8 doesn't strip what the scanner needs at runtime.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---- Media session / notification ----
# MediaButtonReceiver is referenced from the manifest; keep it and the
# media support classes so R8 doesn't strip/rename what the session needs.
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class com.search.browser.MediaService { *; }
-dontwarn androidx.media.**
