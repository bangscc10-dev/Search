package com.search.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashDuration = 2200L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge-to-edge and hide system bars in immersive-sticky mode.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Let the window draw into the status-bar / cutout region so the
        // gradient fills the very top instead of leaving a black band.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Paint the purple gradient at the WINDOW level so no black shows anywhere.
        val windowGrad = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2A1E48"), Color.parseColor("#140E28"))
        )
        window.setBackgroundDrawable(windowGrad)

        val container = FrameLayout(this)
        container.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2A1E48"), Color.parseColor("#140E28"))
        )

        val web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#140E28"))
        web.settings.javaScriptEnabled = true
        web.loadUrl("file:///android_asset/splash.html")
        container.addView(web)
        setContentView(container)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, container)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, splashDuration)
    }
}
