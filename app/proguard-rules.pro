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
