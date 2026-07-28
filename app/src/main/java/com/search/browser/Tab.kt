package com.search.browser

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView

/**
 * Represents a single browser tab.
 * A tab is "live" when it holds a WebView, or "frozen" when only its
 * saved state (url, title, WebView.saveState bundle) is kept to save RAM.
 * The thumbnail is captured when the tab goes to the background, so it
 * survives freezing and can be shown in the tab deck.
 */
class Tab(
    val id: Long,
    var url: String = "about:blank",
    var title: String = "New Tab"
) {
    var webView: WebView? = null
    var savedState: Bundle? = null
    var thumbnail: Bitmap? = null

    val isLive: Boolean get() = webView != null
}
