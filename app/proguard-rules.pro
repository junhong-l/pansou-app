# 保留 WebView 的 JS 接口（如后续需要）
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
