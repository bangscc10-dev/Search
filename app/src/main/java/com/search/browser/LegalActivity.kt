package com.search.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Minimal viewer for the bundled legal pages (Privacy / Terms).
 * A plain WebView in its own activity so the back stack stays natural:
 * MainActivity -> Settings -> LegalActivity, and back walks straight home.
 */
class LegalActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: "file:///android_asset/privacy.html"

        val container = FrameLayout(this)
        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.loadUrl(url)
        container.addView(web)
        setContentView(container)

        // Edge-to-edge: pad by system-bar insets so content clears status/nav bars.
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
