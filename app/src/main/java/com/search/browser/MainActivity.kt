package com.search.browser
import androidx.activity.enableEdgeToEdge



import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.search.browser.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ---- Native search-page mode ----
    private var searchMode = false
    private var lastFailedUrl: String? = null

    // Tracks the site-settings signature last applied, so we only reload when it changed.
    private var lastSiteSig: String = ""

    private fun siteSettingsSignature(): String {
        return listOf(
            Settings.getBool(this, Settings.SITE_JAVASCRIPT, true),
            Settings.getBool(this, Settings.SITE_BLOCK_IMAGES, false),
            Settings.getBool(this, Settings.SITE_BLOCK_AUTOPLAY, true),
            Settings.getTextScale(this)
        ).joinToString("|")
    }
    private var suggestAdapter: SuggestAdapter? = null
    private var suggestSeq = 0

    private fun setupSuggestOverlay() {
        suggestAdapter = SuggestAdapter(emptyList()) { item ->
            val kind = item.optString("kind")
            val title = item.optString("title")
            val url = item.optString("url")
            exitSearchMode()
            if (kind == "web" || url.isBlank()) go(title) else activeWeb()?.loadUrl(url)
        }
        binding.suggestOverlay.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.suggestOverlay.adapter = suggestAdapter
    }

    private fun fetchSuggests(query: String) {
        val id = ++suggestSeq
        Thread {
            val items = buildSuggestions(query.trim())
            runOnUiThread { if (id == suggestSeq && searchMode) suggestAdapter?.submit(items) }
        }.start()
    }

    private fun enterSearchMode() {
        if (searchMode) return
        searchMode = true
        binding.homeBtn.visibility = View.GONE
        binding.reloadBtn.visibility = View.GONE
        binding.tabCountBtn.visibility = View.GONE
        binding.settingsBtn.visibility = View.GONE
        binding.starBtn.visibility = View.GONE
        binding.urlBarContainer.visibility = View.VISIBLE
        val current = tabs.activeTab?.url
        val onHome = (current == null || current == homePage)
        if (onHome) binding.urlBar.setText("") else {
            binding.urlBar.setText(current); binding.urlBar.selectAll()
        }
        binding.urlBar.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.urlBar, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        binding.suggestOverlay.visibility = View.VISIBLE
        fetchSuggests(binding.urlBar.text.toString())
    }

    private fun exitSearchMode() {
        if (!searchMode) return
        searchMode = false
        binding.suggestOverlay.visibility = View.GONE
        suggestAdapter?.submit(emptyList())
        binding.homeBtn.visibility = View.VISIBLE
        binding.reloadBtn.visibility = View.VISIBLE
        binding.tabCountBtn.visibility = View.VISIBLE
        binding.settingsBtn.visibility = View.VISIBLE
        binding.starBtn.visibility = View.VISIBLE
        binding.urlBar.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
        val current = tabs.activeTab?.url
        val onHome = (current == null || current == homePage)
        if (onHome) {
            binding.urlBarContainer.visibility = View.INVISIBLE
            binding.urlBar.setText("")
        } else {
            binding.urlBarContainer.visibility = View.VISIBLE
            binding.urlBar.setText(displayUrl(current))
        }
    }


    // Night Owl (private browsing) mode state.
    private var nightOwl = false

    override fun onResume() {
        super.onResume()
        val sig = siteSettingsSignature()
        if (sig == lastSiteSig) return  // nothing changed -> don't touch anything
        lastSiteSig = sig

        val zoom = Settings.getTextScale(this)
        val js = Settings.getBool(this, Settings.SITE_JAVASCRIPT, true)
        val blockImg = Settings.getBool(this, Settings.SITE_BLOCK_IMAGES, false)
        val blockAutoplay = Settings.getBool(this, Settings.SITE_BLOCK_AUTOPLAY, true)
        tabs.tabs.forEach { tab ->
            tab.webView?.settings?.apply {
                textZoom = zoom
                javaScriptEnabled = js
                blockNetworkImage = blockImg
                mediaPlaybackRequiresUserGesture = blockAutoplay
            }
        }
        // Settings are applied live to the WebView above; we do NOT reload,
        // because reloading wipes the tab's back/forward history. Changes take
        // full effect on the next navigation.
    }

    private lateinit var binding: ActivityMainBinding
    private val homePage = "file:///android_asset/home.html"
    private val tabs = TabManager(maxLiveTabs = 3)
    private lateinit var tabAdapter: TabAdapter

    private val thumbWidthPx = 400
    private var deckVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Launched with the splash theme so the system splash shows the owl;
        // switch to the real app theme before inflating the browser UI.
        setTheme(R.style.Theme_Search)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Apply the saved theme preference before inflating.
        when (Settings.getTheme(this)) {
            Settings.THEME_LIGHT -> androidx.appcompat.app.AppCompatDelegate
                .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            Settings.THEME_DARK -> androidx.appcompat.app.AppCompatDelegate
                .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            else -> androidx.appcompat.app.AppCompatDelegate
                .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Edge-to-edge (mandatory on Android 16 / SDK 36): pad the root by the
        // system-bar insets so the top bar sits below the status bar and content
        // stays above the navigation bar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setupSuggestOverlay()
        lastSiteSig = siteSettingsSignature()

        onBackPressedDispatcher.addCallback(this, object :
            androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val web = activeWeb()
                when {
                    searchMode -> exitSearchMode()
                    binding.menuScrim.visibility == View.VISIBLE -> closeMenu()
                    binding.tabDeck.visibility == View.VISIBLE -> closeDeck()
                    web?.canGoBack() == true -> web.goBack()
                    else -> finish()
                }
            }
        })

        tabs.onNeedFreeze = { tab -> freezeTab(tab) }

        setupUrlBar()
        setupToolbar()
        setupDeck()

        // Only create the initial home tab on a genuine fresh start.
        // Prevents losing your open tab if the activity is recreated (e.g. returning from Settings).
        if (tabs.count() == 0) {
            val first = tabs.createTab(homePage)
            openTab(first, homePage)
        } else {
            // Re-attach the existing active tab's view.
            tabs.activeTab?.let { openTab(it) }
        }
        updateTabCount()

        // Handle a page requested from Settings (Privacy / Terms).
        intent?.getStringExtra("open_url")?.let { url ->
            addNewTab(url)
            intent.removeExtra("open_url")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_url")?.let { url ->
            addNewTab(url)
            intent.removeExtra("open_url")
        }
    }

    // ---------- JS bridge ----------

    inner class SearchAppBridge {
        @JavascriptInterface
        fun submit(query: String) { runOnUiThread { go(query) } }
        @JavascriptInterface
        fun open(url: String) { runOnUiThread { activeWeb()?.loadUrl(url) } }
        @JavascriptInterface
        fun focusSearch() { runOnUiThread { enterSearchMode() } }

        @JavascriptInterface
        fun retry() {
            runOnUiThread {
                val target = lastFailedUrl
                if (target != null) activeWeb()?.loadUrl(target)
                else activeWeb()?.reload()
            }
        }
        @JavascriptInterface
        fun cacheFavicon(domain: String, dataUrl: String) {
            if (domain.isBlank() || dataUrl.isBlank()) return
            getSharedPreferences("favicon_cache", Context.MODE_PRIVATE)
                .edit().putString(domain, dataUrl).apply()
        }

        @JavascriptInterface
        fun getCachedFavicon(domain: String): String {
            return getSharedPreferences("favicon_cache", Context.MODE_PRIVATE)
                .getString(domain, "") ?: ""
        }

        @JavascriptInterface
        fun getRecentSites(): String {
            // Return up to 8 most-recent unique domains from history as JSON.
            val entries = History.load(this@MainActivity)
            val seen = LinkedHashSet<String>()
            val out = StringBuilder("[")
            var count = 0
            for (e in entries) {
                if (count >= 8) break
                val host = try {
                    android.net.Uri.parse(e.url).host ?: continue
                } catch (ex: Exception) { continue }
                val domain = host.removePrefix("www.")
                if (domain.isBlank() || !seen.add(domain)) continue
                if (count > 0) out.append(",")
                val safeUrl = e.url.replace("\\", "\\\\").replace("\"", "\\\"")
                out.append("{\"domain\":\"").append(domain).append("\",")
                out.append("\"url\":\"").append(safeUrl).append("\"}")
                count++
            }
            out.append("]")
            return out.toString()
        }

        @JavascriptInterface
        fun getConfig(): String {
            val bg = Settings.getHomeBackground(this@MainActivity)
            val accent = Settings.getHomeAccent(this@MainActivity)
            val tiles = Settings.getBool(this@MainActivity, Settings.HOME_SHOW_TILES, true)
            return "{\"background\":\"$bg\",\"accent\":\"$accent\",\"tiles\":$tiles}"
        }
        @JavascriptInterface
        fun suggest(query: String, requestId: Int) {
            Thread {
                val items = buildSuggestions(query.trim())
                runOnUiThread { pushSuggestions(requestId, items) }
            }.start()
        }
    }

    private fun localSuggestionMatches(query: String, limit: Int): List<Triple<String, String, String>> {
        val marks = Bookmarks.load(this).filter { matchesQuery(it.title, it.url, query) }
            .map { Triple("bookmark", it.title, it.url) }
        val hist = History.load(this).filter { matchesQuery(it.title, it.url, query) }
            .map { Triple("history", it.title, it.url) }
        return (marks + hist).distinctBy { it.third }.take(limit)
    }

    private fun matchesQuery(title: String, url: String, query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return title.lowercase().contains(q) || url.lowercase().contains(q)
    }

    private fun fetchWebSuggestions(query: String): List<String> {
        if (query.isEmpty()) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val endpoint = when (Settings.getEngineName(this)) {
            "DuckDuckGo" -> "https://duckduckgo.com/ac/?type=list&q=$q"
            "Bing" -> "https://api.bing.com/osjson.aspx?query=$q"
            else -> "https://www.google.com/complete/search?client=chrome&q=$q"
        }
        return try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val cleaned = body.trim().removePrefix(")]}'").trim()
            val list = JSONArray(cleaned).optJSONArray(1) ?: return emptyList()
            (0 until list.length()).mapNotNull { i -> list.optString(i, null) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildSuggestions(query: String): List<JSONObject> {
        val local = localSuggestionMatches(query, if (query.isEmpty()) 5 else 3)
        val web = if (query.isEmpty()) emptyList() else fetchWebSuggestions(query)
        val seen = local.map { it.third.lowercase() }.toMutableSet()
        val out = mutableListOf<JSONObject>()
        local.forEach { (kind, title, url) ->
            out += JSONObject().put("kind", kind).put("title", title).put("url", url)
        }
        web.forEach { text ->
            val key = text.lowercase()
            if (out.size < 7 && key !in seen) {
                seen += key
                out += JSONObject().put("kind", "web").put("title", text)
            }
        }
        return out
    }

    private fun pushSuggestions(requestId: Int, items: List<JSONObject>) {
        val web = activeWeb() ?: return
        val payload = JSONArray(items).toString()
        val js = "window.__onSuggest && window.__onSuggest(" + requestId + ", JSON.parse(" +
            JSONObject.quote(payload) + "));"
        web.evaluateJavascript(js, null)
    }

    // ---------- WebView creation / lifecycle ----------

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun newWebView(): WebView {
        val web = WebView(this)
        web.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        web.settings.apply {
            javaScriptEnabled = Settings.getBool(this@MainActivity, Settings.SITE_JAVASCRIPT, true)
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture =
                Settings.getBool(this@MainActivity, Settings.SITE_BLOCK_AUTOPLAY, true)

            // Data saver: block images when enabled.
            blockNetworkImage = Settings.getBool(this@MainActivity, Settings.SITE_BLOCK_IMAGES, false)

            userAgentString = userAgentString.replace("; wv", "")

            // --- Desktop mode ---
            if (Settings.getBool(this@MainActivity, Settings.DESKTOP_MODE, false)) {
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
            }

            // --- Accessibility: text size ---
            textZoom = Settings.getTextScale(this@MainActivity)

            // --- Security toggles ---
            // Block pop-ups: disallow auto-opening windows when enabled.
            val blockPopups = Settings.getBool(
                this@MainActivity, Settings.SEC_BLOCK_POPUPS, true)
            javaScriptCanOpenWindowsAutomatically = !blockPopups
            setSupportMultipleWindows(!blockPopups)

            // Safe Browsing (WebView built-in), where supported.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = Settings.getBool(
                    this@MainActivity, Settings.SEC_SAFE_BROWSING, true)
            }
        }

        // Third-party cookie policy.
        val block3p = Settings.getBool(this, Settings.SEC_BLOCK_3P_COOKIES, false)
        android.webkit.CookieManager.getInstance()
            .setAcceptThirdPartyCookies(web, !block3p)

        web.addJavascriptInterface(SearchAppBridge(), "SearchApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                // Ad/tracker blocking (when enabled in settings).
                return AdBlocker.check(this@MainActivity, request)
                    ?: super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only replace the main-frame failure (not sub-resources like images/ads).
                if (request?.isForMainFrame == true) {
                    lastFailedUrl = request.url?.toString()
                    view?.loadUrl("file:///android_asset/offline.html")
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (searchMode && url != null && url != homePage) exitSearchMode()
                super.onPageStarted(view, url, favicon)
                if (view == tabs.activeTab?.webView) {
                    if (!binding.urlBar.hasFocus()) binding.urlBar.setText(displayUrl(url))
                    updateNavButtons()
                    refreshOmniboxVisibility(url)
                }
                url?.let { tabs.activeTab?.url = it }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Cosmetic ad-hiding: hide common ad containers when blocking is on.
                if (AdBlocker.isEnabled(this@MainActivity)) {
                    view?.evaluateJavascript(AdBlocker.hideCss(), null)
                }
                // Desktop mode: force a desktop-width viewport so responsive
                // sites render their desktop layout.
                if (Settings.getBool(this@MainActivity, Settings.DESKTOP_MODE, false)) {
                    val js = "(function(){var v=document.querySelector('meta[name=viewport]');" +
                        "if(!v){v=document.createElement('meta');v.name='viewport';" +
                        "document.head.appendChild(v);}" +
                        "v.setAttribute('content','width=980');})();"
                    view?.evaluateJavascript(js, null)
                    val sw = resources.displayMetrics.widthPixels
                    val scale = (sw.toFloat() / 980f * 100f).toInt().coerceIn(20, 100)
                    view?.setInitialScale(scale)
                }
                tabs.activeTab?.let { t ->
                    t.title = view?.title ?: t.title
                    t.url = url ?: t.url
                }
                // Record the visited page in history (never in Night Owl mode).
                if (url != null && !nightOwl) {
                    History.add(this@MainActivity, view?.title ?: "", url)
                }
                if (view == tabs.activeTab?.webView) {
                    updateNavButtons(); refreshStar(); refreshOmniboxVisibility(url)
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // Location: honor the Site setting.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                val allow = Settings.getBool(this@MainActivity, Settings.SITE_LOCATION, true)
                callback?.invoke(origin, allow, false)
            }

            // Camera & microphone: honor the Site setting.
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                val allow = Settings.getBool(this@MainActivity, Settings.SITE_CAMERA_MIC, true)
                runOnUiThread {
                    if (allow) request?.grant(request.resources) else request?.deny()
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view == tabs.activeTab?.webView) {
                    binding.progressBar.progress = newProgress
                    binding.progressBar.visibility =
                        if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
        }

        // Handle file downloads via Android's DownloadManager.
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        return web
    }

    private fun displayUrl(url: String?): String =
        if (url == null || url == homePage) "" else url

    private fun captureThumbnail(tab: Tab, onDone: (() -> Unit)? = null) {
        val web = tab.webView
        if (deckVisible || web == null || web.width <= 0 || web.height <= 0 ||
            !web.isAttachedToWindow
        ) { onDone?.invoke(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val source = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.RGB_565)
                val location = IntArray(2)
                web.getLocationInWindow(location)
                val rect = android.graphics.Rect(
                    location[0], location[1],
                    location[0] + web.width, location[1] + web.height
                )
                PixelCopy.request(
                    window, rect, source,
                    { result ->
                        if (result == PixelCopy.SUCCESS && !deckVisible) storeScaled(tab, source)
                        else source.recycle()
                        onDone?.invoke()
                    },
                    Handler(Looper.getMainLooper())
                )
                return
            } catch (e: Exception) { /* fall through */ }
        }
        onDone?.invoke()
    }

    private fun softwareCapture(tab: Tab) {
        if (deckVisible) return
        val web = tab.webView ?: return
        if (web.width <= 0 || web.height <= 0) return
        try {
            val full = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.RGB_565)
            web.draw(Canvas(full))
            storeScaled(tab, full)
        } catch (e: Exception) { /* skip */ }
    }

    private fun storeScaled(tab: Tab, full: Bitmap) {
        try {
            val ratio = full.height.toFloat() / full.width.toFloat()
            val targetH = (thumbWidthPx * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(full, thumbWidthPx, targetH, true)
            if (scaled != full) full.recycle()
            tab.thumbnail?.recycle()
            tab.thumbnail = scaled
        } catch (e: Exception) { if (!full.isRecycled) full.recycle() }
    }

    private fun openTab(tab: Tab, loadUrl: String? = null) {
        binding.webContainer.removeAllViews()
        if (tab.webView == null) {
            val web = newWebView()
            tab.webView = web
            val restored = tab.savedState?.let { web.restoreState(it) != null } ?: false
            if (!restored) web.loadUrl(loadUrl ?: tab.url)
        } else if (loadUrl != null) {
            tab.webView!!.loadUrl(loadUrl)
        }
        binding.webContainer.addView(tab.webView)
        tabs.setActive(tab)
        tabs.markLive(tab)
        binding.urlBar.setText(displayUrl(tab.url))
        updateNavButtons()
        updateTabCount()
        refreshStar()
        refreshOmniboxVisibility(tab.url)
    }

    /**
     * The home/new-tab page has its own search field under the wordmark,
     * so the native address bar (and the star button riding along with it)
     * is hidden while it's showing — that top-bar space just sits empty,
     * the same way it does on any other page before you start typing.
     * Everywhere else, the native bar behaves exactly as it always has.
     */
    private fun refreshOmniboxVisibility(url: String?) {
        val isHome = url == null || url == homePage
        binding.urlBarContainer.visibility = if (isHome) View.INVISIBLE else View.VISIBLE
    }

    private fun freezeTab(tab: Tab) {
        val web = tab.webView ?: return
        softwareCapture(tab)
        val state = Bundle()
        web.saveState(state)
        tab.savedState = state
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        tab.webView = null
    }

    private fun applyDesktopMode(on: Boolean) {
        val web = tabs.activeTab?.webView ?: return
        val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        web.settings.apply {
            if (on) {
                userAgentString = desktopUA
                useWideViewPort = true
                loadWithOverviewMode = true
                // Force a desktop-width layout so responsive sites render desktop.
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            } else {
                userAgentString = null
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
        // Scale the 980px desktop layout to fit the screen width (like Chrome).
        if (on) {
            val screenWidthDp = resources.displayMetrics.widthPixels
            val scale = (screenWidthDp.toFloat() / 980f * 100f).toInt().coerceIn(20, 100)
            web.setInitialScale(scale)
        } else {
            web.setInitialScale(0)
        }
        web.reload()
    }

    private fun enterNightOwl() {
        nightOwl = true
        // Isolate the private session: no disk cache, no form/password saving.
        activeWeb()?.settings?.apply {
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            saveFormData = false
        }
        // Don't persist cookies created during Night Owl.
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        // Visual indicator.
        binding.nightOwlBadge.visibility = View.VISIBLE
        applyNightOwlChrome(true)
        // Fresh private tab.
        addNewTab(homePage)
        android.widget.Toast.makeText(this,
            "Night Owl on — private browsing", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun exitNightOwl() {
        nightOwl = false
        // Wipe session data created during Night Owl.
        android.webkit.CookieManager.getInstance().removeSessionCookies(null)
        android.webkit.WebStorage.getInstance().deleteAllData()
        activeWeb()?.clearCache(true)
        binding.nightOwlBadge.visibility = View.GONE
        applyNightOwlChrome(false)
        android.widget.Toast.makeText(this,
            "Night Owl off", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun applyNightOwlChrome(on: Boolean) {
        val topBar = binding.homeBtn.parent as? View
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)

        // Detect dark mode.
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (on) {
            // Subtle purple wash matched to the theme.
            val tint = if (isDark) "#231A3A" else "#ECE7F5"
            topBar?.setBackgroundColor(android.graphics.Color.parseColor(tint))
            binding.rootView.setBackgroundColor(android.graphics.Color.parseColor(tint))
            // Icons: light icons on dark tint, dark icons on light tint.
            controller.isAppearanceLightStatusBars = !isDark
        } else {
            topBar?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val tv = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
            binding.rootView.setBackgroundColor(tv.data)
            controller.isAppearanceLightStatusBars = !isDark
        }
    }

    private fun openDownloads() {
        try {
            val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this,
                "No downloads app available", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMenu() {
        // Reflect current Night Owl state in the menu label.
        (binding.menuNightOwl.getChildAt(1) as? android.widget.TextView)?.text =
            if (nightOwl) "Exit Night Owl" else "Night Owl"
        binding.menuScrim.visibility = View.VISIBLE
    }

    private fun closeMenu() {
        binding.menuScrim.visibility = View.GONE
    }

    private fun addNewTab(loadUrl: String = homePage) {
        val tab = tabs.createTab(loadUrl)
        openTab(tab, loadUrl)
    }

    // ---------- Tab deck ----------

    private fun setupDeck() {
        tabAdapter = TabAdapter(
            tabs = tabs.tabs,
            onSelect = { tab -> closeDeck(); openTab(tab) },
            onClose = { tab -> closeTabFromDeck(tab) }
        )
        binding.tabList.layoutManager = GridLayoutManager(this, 2)
        binding.tabList.adapter = tabAdapter

        binding.deckClose.setOnClickListener { closeDeck() }
        binding.deckNewTab.setOnClickListener { closeDeck(); addNewTab(homePage) }


        binding.deckSearch.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (enter) {
                val q = binding.deckSearch.text.toString()
                if (q.isNotBlank()) {
                    closeDeck()
                    addNewTab(UrlHelper.toUrlOrSearch(q, Settings.getEngineUrl(this)))
                    binding.deckSearch.setText("")
                }
                true
            } else false
        }
    }

    private fun openDeck() {
        // Always open on the tab view; reset any lingering history/bookmark view.
        historyOpen = false
        bookmarksOpen = false
        binding.historyList.visibility = View.GONE
        binding.bookmarkList.visibility = View.GONE
        binding.tabList.visibility = View.VISIBLE
        val active = tabs.activeTab
        if (active?.webView != null && !deckVisible) captureThumbnail(active) { showDeckNow() }
        else showDeckNow()
    }

    private fun showDeckNow() {
        deckVisible = true
        tabAdapter.notifyDataSetChanged()
        binding.tabDeck.visibility = View.VISIBLE
    }

    private fun closeDeck() {
        hideKeyboard()
        binding.tabDeck.visibility = View.GONE
        deckVisible = false
    }

    private fun closeTabFromDeck(tab: Tab) {
        val wasActive = tab == tabs.activeTab
        tab.webView?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
            tab.webView = null
        }
        tab.thumbnail?.recycle()
        tab.thumbnail = null
        tabs.removeTab(tab)
        if (tabs.count() == 0) { closeDeck(); addNewTab(homePage) }
        else if (wasActive) tabs.tabs.lastOrNull()?.let { tabs.setActive(it) }
        tabAdapter.notifyDataSetChanged()
        updateTabCount()
    }

    // ---------- History ----------

    private var historyOpen = false

    private fun toggleHistory() {
        if (historyOpen) hideHistory() else showHistory()
    }

    private fun showHistory() {
        val entries = History.load(this)
        val adapter = HistoryAdapter(
            entries = entries,
            onSelect = { entry ->
                closeDeck()
                addNewTab(entry.url)
            },
            onDelete = { entry ->
                History.delete(this, entry.url)
                showHistory()  // rebuild the list without the deleted entry
            }
        )
        binding.historyList.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.historyList.adapter = adapter
        binding.historyList.visibility = View.VISIBLE
        binding.tabList.visibility = View.GONE
        binding.bookmarkList.visibility = View.GONE
        historyOpen = true
        bookmarksOpen = false
    }

    private var bookmarksOpen = false

    private fun toggleBookmarks() {
        if (bookmarksOpen) hideBookmarks() else showBookmarks()
    }

    private fun showBookmarks() {
        val entries = Bookmarks.load(this).map { History.Entry(it.title, it.url, it.time) }
        val adapter = HistoryAdapter(
            entries = entries,
            onSelect = { entry -> closeDeck(); addNewTab(entry.url) },
            onDelete = { entry -> Bookmarks.remove(this, entry.url); showBookmarks() }
        )
        binding.bookmarkList.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.bookmarkList.adapter = adapter
        binding.bookmarkList.visibility = View.VISIBLE
        binding.tabList.visibility = View.GONE
        binding.historyList.visibility = View.GONE
        bookmarksOpen = true
        historyOpen = false
    }

    private fun hideBookmarks() {
        binding.bookmarkList.visibility = View.GONE
        binding.tabList.visibility = View.VISIBLE
        bookmarksOpen = false
    }

    private fun hideHistory() {
        binding.historyList.visibility = View.GONE
        binding.tabList.visibility = View.VISIBLE
        historyOpen = false
    }

    // ---------- Bookmarks ----------

    private fun toggleBookmark() {
        val tab = tabs.activeTab ?: return
        val url = tab.url
        if (url.isBlank() || url.startsWith("file:///android_asset/")) return
        if (Bookmarks.isBookmarked(this, url)) {
            Bookmarks.remove(this, url)
            android.widget.Toast.makeText(this, "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            Bookmarks.add(this, tab.title, url)
            android.widget.Toast.makeText(this, "Bookmarked", android.widget.Toast.LENGTH_SHORT).show()
        }
        refreshStar()
    }

    private fun refreshStar() {
        val url = tabs.activeTab?.url ?: ""
        val marked = url.isNotBlank() && Bookmarks.isBookmarked(this, url)
        binding.starBtn.setImageResource(
            if (marked) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    // ---------- Downloads ----------

    /**
     * Security gate: every download — whether the user tapped it or a site
     * triggered it silently — must be confirmed here before it proceeds.
     * This stops websites from secretly downloading files to the device.
     */
    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        // If the user disabled download confirmation, download straight away.
        if (!Settings.getBool(this, Settings.SEC_CONFIRM_DOWNLOADS, true)) {
            performDownload(url, userAgent, contentDisposition, mimeType)
            return
        }
        val fileName = try {
            android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        } catch (e: Exception) { "file" }

        val view = layoutInflater.inflate(R.layout.dialog_download, null)
        view.findViewById<android.widget.TextView>(R.id.dlFileName).text = fileName

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        // Transparent window so our rounded layout shows cleanly.
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        view.findViewById<android.widget.TextView>(R.id.dlConfirm).setOnClickListener {
            dialog.dismiss()
            performDownload(url, userAgent, contentDisposition, mimeType)
        }
        view.findViewById<android.widget.TextView>(R.id.dlCancel).setOnClickListener {
            dialog.dismiss()
            android.widget.Toast.makeText(
                this, "Download cancelled", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        dialog.show()
        // Constrain the dialog to a compact card width.
        dialog.window?.let { w ->
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.86f).toInt()
            w.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun performDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            request.setMimeType(mimeType)
            userAgent?.let { request.addRequestHeader("User-Agent", it) }
            request.setTitle(fileName)
            request.setDescription("Downloading…")
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS, fileName
            )
            val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            android.widget.Toast.makeText(
                this, "Downloading $fileName", android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this, "Download failed", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------- UI wiring ----------

    private fun setupUrlBar() {
        binding.urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !searchMode) enterSearchMode()
        }
        binding.urlBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(cs: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(cs: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                if (searchMode) fetchSuggests(e?.toString() ?: "")
            }
        })

        binding.urlBar.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (enter) { go(binding.urlBar.text.toString()); true } else false
        }
    }

    private fun setupToolbar() {
        binding.reloadBtn.setOnClickListener { activeWeb()?.reload() }
        binding.homeBtn.setOnClickListener { activeWeb()?.loadUrl(homePage) }
        binding.tabCountBtn.setOnClickListener { openDeck() }
        binding.settingsBtn.setOnClickListener { openMenu() }

        // Menu scrim tap closes the menu
        binding.menuScrim.setOnClickListener { closeMenu() }

        // Menu items
        binding.menuNewTab.setOnClickListener {
            closeMenu(); addNewTab(homePage)
        }
        binding.menuNightOwl.setOnClickListener {
            closeMenu()
            if (nightOwl) exitNightOwl() else enterNightOwl()
        }
        binding.menuDesktop.setOnClickListener {
            val on = !Settings.getBool(this, Settings.DESKTOP_MODE, false)
            Settings.setBool(this, Settings.DESKTOP_MODE, on)
            closeMenu()
            applyDesktopMode(on)
            android.widget.Toast.makeText(this,
                if (on) "Desktop site on" else "Desktop site off",
                android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.menuHistory.setOnClickListener {
            closeMenu(); openDeck(); showHistory()
        }
        binding.menuBookmarks.setOnClickListener {
            closeMenu(); openDeck(); showBookmarks()
        }
        binding.menuSettings.setOnClickListener {
            closeMenu()
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        binding.menuDownloads.setOnClickListener {
            closeMenu()
            openDownloads()
        }
        binding.menuGames.setOnClickListener {
            closeMenu()
            android.widget.Toast.makeText(this, "Play games — coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.starBtn.setOnClickListener { toggleBookmark() }
    }

    private fun go(input: String) {
        if (searchMode) exitSearchMode()
        var url = UrlHelper.toUrlOrSearch(input, Settings.getEngineUrl(this))
        // HTTPS-only mode: upgrade insecure http links.
        if (Settings.getBool(this, Settings.SEC_HTTPS_ONLY, true) &&
            url.startsWith("http://")) {
            url = "https://" + url.removePrefix("http://")
        }
        activeWeb()?.loadUrl(url)
        hideKeyboard()
        activeWeb()?.requestFocus()
    }

    private fun activeWeb(): WebView? = tabs.activeTab?.webView

    private fun updateNavButtons() { /* system back handles page-back */ }

    private fun updateTabCount() { binding.tabCountBtn.text = tabs.count().toString() }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
    }


}
