# Search

A native Android web browser built in Kotlin on top of the system WebView. Fast, lightweight (~1.8 MB release), privacy-minded, and branded around an owl mascot. Package: `com.devbangs.search`.

---

## Overview

Search wraps Android's `WebView` in a full browser experience: multi-tab browsing with a visual tab switcher, a native search page with live suggestions, a sectioned settings system, ad blocking, a private mode, and a set of on-brand touches (animated splash, branded download gate, offline page). It targets low-end devices (developed/tested on a Tecno KL5, HiOS/Transsion, low RAM), so memory discipline and graceful degradation are recurring themes in the design.

- **Language:** Kotlin
- **UI:** Android Views + ViewBinding (no Compose)
- **minSdk:** 24 · **compile/target SDK:** 34 · **JDK:** 17
- **Rendering:** system `WebView` (+ `androidx.webkit` for newer settings)
- **Build:** Gradle (Kotlin DSL), R8 minification + resource shrinking on release

---

## Architecture

The app is deliberately simple — a handful of activities and small helper classes, no DI framework, no reactive stack. State lives in plain objects; the WebView does the heavy lifting.

### Entry & lifecycle
- **`SplashActivity`** — animated owl splash (HTML-based, `splash.html`). Handles the Transsion black-strip cutout bug via `SHORT_EDGES` display cutout mode.
- **`MainActivity`** — the core of the app (~1000+ lines). Owns the top bar, WebView container, tab lifecycle, the slide-in menu, search mode, downloads, Night Owl, and desktop mode. Declares `configChanges` (orientation/screenSize/uiMode/etc.) so it is NOT destroyed on config changes — important because a recreation would otherwise wipe the active tab.

### Tabs
- **`Tab`** — holds `webView: WebView?`, `savedState: Bundle?`, and a `thumbnail` bitmap.
- **`TabManager`** — `maxLiveTabs = 3`. Keeps at most 3 live WebViews; older tabs are frozen via an `onNeedFreeze` callback. `createTab`, `setActive`, `markLive`, `activeTab`, `count()`.
- **Freeze/restore:** `freezeTab()` calls `web.saveState(bundle)` *before* `web.destroy()`, storing navigation state so a frozen tab restores its history on reopen. `openTab()` recreates the WebView and calls `restoreState()`; if that fails it loads the tab's URL fresh.
- **Tab deck:** a card-grid switcher (`TabAdapter`, `item_tab.xml`) showing live thumbnails, a "+New" tile, and "Done". History/Bookmarks were intentionally moved OUT of the deck into the menu to declutter.

### Navigation input
- **Omnibox** (`urlBar`, an `EditText` with `imeOptions=actionGo`) is the single real text input. `UrlHelper.toUrlOrSearch(raw, engineUrl)` decides URL-vs-search.
- **`go(input)`** loads in the *current* tab (`activeWeb().loadUrl(...)`), upgrading `http→https` when HTTPS-only is on.
- On the home page the omnibox container is `INVISIBLE`; the home HTML shows a dumb search box instead (see Search page).

### Back handling
Exactly ONE back handler: an `OnBackPressedDispatcher` callback registered in `onCreate`. Priority order: exit search mode → close menu → close deck → `web.goBack()` → `finish()`. The legacy `onKeyDown` back handler was removed — having both caused a double-fire bug (half-exit + jump-home). This is the single most important lesson in the codebase: **one back handler only.**

---

## Key features & how they work

### Home page (`assets/home.html`)
- Wordmark + a read-only search box + a 4-tile quick-access grid.
- **Tiles** show recently-visited sites (newest pushing in from the left, defaults — Google/YouTube/Wikipedia/GitHub — falling off the right), always 4, deduped. Data comes from the `getRecentSites()` bridge (top unique domains from history). Favicons load from Google's s2 service when online; a grey alphabetic letter is the offline/failed fallback.
- The search box is a **dumb button**: tapping it calls `SearchApp.focusSearch()` and hands off to the native search system. It contains NO suggestion logic of its own — this separation is deliberate (see below).

### Native search page (search mode)
This is the most heavily-iterated feature and the design that finally worked is important:

- **One search system, native only.** The native omnibox is the sole input; a native RecyclerView overlay (`suggestOverlay` + `SuggestAdapter` + `item_suggestion.xml`) is the sole suggestion surface. The home HTML has zero search/suggestion code so the two can never fight (an earlier HTML-vs-native hybrid produced a "ghost state" bug).
- **`enterSearchMode()`** hides the nav icons (owl/reload/tabs/settings/star → GONE, so the omnibox auto-expands via `layout_weight=1`), shows + focuses the omnibox, opens the keyboard, shows the overlay, and fetches suggestions. On a loaded page it pre-fills the URL and `selectAll()`s it (Chrome-style edit); on home it clears.
- **`exitSearchMode()`** is bulletproof — hides the overlay first, restores every icon, restores the omnibox to the current page URL (or clears on home). Called from back, from `go()`, and from `onPageStarted` when a real page begins loading.
- **Suggestions** reuse `buildSuggestions(query)` (local history/bookmark matches + `fetchWebSuggestions`) on a background thread, returning `List<JSONObject>` with `kind`/`title`/`url`. The overlay sits below the 54dp top bar (`layout_marginTop=54dp`) so the omnibox stays visible above it.

### Settings (sectioned)
- **`SettingsActivity`** — the main settings menu: search engine picker, theme (System/Light/Dark via `AppCompatDelegate`), Clear browsing data, About + Privacy Policy + Terms of Use rows, and section rows that launch `SectionActivity`.
- **`SectionActivity`** — a reusable per-section screen keyed by `EXTRA_SECTION`. Sections:
  - **Search Security** — HTTPS-only, Safe Browsing, block pop-ups, block 3rd-party cookies, confirm downloads.
  - **Ad blocking** — `AdBlocker.kt`: a domain blocklist via `shouldInterceptRequest` + cosmetic CSS hiding injected in `onPageFinished`. Default OFF. Honest ceiling: first-party ads leak.
  - **Accessibility** — text size (Small→Extra-large) with live preview, applied via `textZoom`.
  - **Customize** — accent color swatches (tint the home wordmark) + show-tiles toggle.
  - **Site settings** — JavaScript, Location, Camera & mic, Block autoplay, Data saver (block images). All functional against real WebView APIs. Changes re-apply live in `onResume` via a `siteSettingsSignature()` diff (only re-applies when something actually changed; no reload, to preserve back-history).
- **In-app legal:** `privacy.html` and `terms.html` are bundled assets, opened in a browser tab via an `open_url` intent extra that `MainActivity` handles in `onCreate`/`onNewIntent`.

### Slide-in menu
A compact rounded card (top-right, from the sliders button): New tab, Night Owl, Desktop mode, History, Bookmarks, **Downloads**, **Play games**, Settings.
- **Downloads** → opens the system Downloads UI (`ACTION_VIEW_DOWNLOADS`).
- **Play games** → stub (toast) — intentionally left to wire later.

### Night Owl (private mode)
`enterNightOwl()`/`exitNightOwl()`: no history/cache/form-save, a fresh private tab, session cookie/storage cleanup on exit, a theme-aware chrome tint (`applyNightOwlChrome`: dark `#231A3A` / light `#ECE7F5`), and a "Night Owl" badge. History recording is guarded by `!nightOwl`.

### Desktop mode
`applyDesktopMode(on)` sets a desktop user-agent, injects a `width=980` viewport in `onPageFinished`, and computes an initial scale so the page fits. Honest limit: UA + viewport can't force desktop layout on every responsive site.

### Downloads with a security gate
Every download (unless "confirm downloads" is off) shows a branded, non-cancelable confirmation dialog (`dialog_download.xml`, owl + "Yes it was me"). `startDownload()` gates → `performDownload()` uses `DownloadManager` to the Downloads folder.

### Offline error page
`onReceivedError` (main-frame only) loads a branded `offline.html` (owl drawn in SVG so it renders with no network) with a "Try again" button that calls the `retry()` bridge to reload `lastFailedUrl`.

---

## The JS ↔ native bridge

Exposed as `SearchApp` (`@JavascriptInterface`, inner class in `MainActivity`). R8 keep-rules preserve these — see ProGuard notes.

| Method | Purpose |
|--------|---------|
| `submit(query)` | run a search / load (→ `go`) |
| `open(url)` | load a URL in the active tab |
| `getConfig()` | returns `{accent, tiles}` JSON for the home page |
| `getRecentSites()` | top unique domains from history, as JSON, for tiles |
| `focusSearch()` | enter native search mode (home box handoff) |
| `retry()` | reload the last failed URL (offline page) |
| `suggest(query, id)` | legacy HTML suggestion path (native overlay is primary now) |

---

## Project layout

```
app/src/main/
├── java/com/search/browser/
│   ├── MainActivity.kt        # core: tabs, top bar, search mode, menu, downloads, night owl
│   ├── SplashActivity.kt      # animated owl splash
│   ├── SettingsActivity.kt    # main settings menu + About/Privacy/Terms
│   ├── SectionActivity.kt     # reusable settings section screen (EXTRA_SECTION)
│   ├── Tab.kt / TabManager.kt  # tab model + lifecycle (freeze/restore, 3 live max)
│   ├── TabAdapter.kt          # tab deck grid
│   ├── SuggestAdapter.kt      # native suggestion list
│   ├── History.kt / HistoryAdapter.kt
│   ├── Bookmarks.kt
│   ├── Settings.kt            # all preference keys + getters/setters
│   ├── AdBlocker.kt           # blocklist + cosmetic hiding
│   └── UrlHelper.kt           # URL-vs-search resolution
├── assets/
│   ├── home.html              # home page (wordmark, dumb search box, tiles)
│   ├── splash.html            # animated splash
│   ├── offline.html           # branded offline error page (SVG owl)
│   ├── privacy.html           # in-app privacy policy
│   └── terms.html             # in-app terms of use
└── res/
    ├── layout/                # activity_main, activity_settings, activity_section,
    │                          # item_tab, item_history, item_suggestion, dialog_download
    ├── values/                # colors, themes, strings, settings_styles
    └── drawable/              # owl assets, menu icons, suggestion icons, backgrounds
```

`Settings.kt` centralises all preference keys: `ENGINES`, `THEME_*`, `SEC_*` (HTTPS_ONLY, SAFE_BROWSING, BLOCK_POPUPS, BLOCK_3P_COOKIES, CONFIRM_DOWNLOADS), `ADBLOCK_ENABLED`, `A11Y_TEXT_SCALE`, `DESKTOP_MODE`, `HOME_ACCENT`, `HOME_SHOW_TILES`, `SITE_*` (JAVASCRIPT, LOCATION, CAMERA_MIC, BLOCK_AUTOPLAY, BLOCK_IMAGES).

---

## Build

```bash
# Debug
./gradlew assembleDebug

# Release (signed AAB for Play)
./gradlew bundleRelease

# Release APK (signed, for direct install)
./gradlew assembleRelease
```

### Release signing
`app/build.gradle.kts` loads credentials from a local, git-ignored `keystore.properties`:

```
storeFile=search-release.keystore
storePassword=...
keyAlias=search
keyPassword=...
```

The keystore (`*.keystore`), `keystore.properties`, and `*.jks` are all git-ignored — never committed. **The release keystore must be backed up permanently; losing it means the app can never be updated on Play.**

### Low-memory build environments
R8 minification is memory-heavy. On constrained machines (e.g. Codespaces) set in `gradle.properties`:

```
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m
org.gradle.daemon=false
org.gradle.workers.max=1
```

and build one artifact at a time (`bundleRelease` and `assembleRelease` separately) to avoid the daemon being OOM-killed. `lint { abortOnError = false; checkReleaseBuilds = false }` keeps advisory lint from blocking release builds.

### ProGuard / R8
`app/proguard-rules.pro` keeps the JS bridge (critical — R8 would otherwise strip `@JavascriptInterface` methods and break the home page):

```
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class com.search.browser.** { *; }
-keep class com.search.browser.databinding.** { *; }
```

Note: `applicationId` is `com.devbangs.search` (the public Play identity) while the internal `namespace` remains `com.search.browser` (so the generated `R`/`BuildConfig` and all `package` declarations stay put). They are allowed to differ.

---

## Honest limitations (WebView reality)

These were tried and either constrained or dropped — documented so no one re-attempts them blindly:

- **Adaptive chrome** (tinting the toolbar to the page's theme-color / background) — WebView can't reliably read a page's true color; results mismatched (dark bar on white pages). Reverted.
- **Force-dark on websites** — `androidx.webkit` algorithmic darkening is inconsistent across sites/devices; the app's own dark theme is better. Removed.
- **Offline favicon caching** via canvas `toDataURL` — CORS/canvas-taint makes it unreliable; `crossOrigin` breaks even online loads. Grey-letter fallback is accepted instead.
- **Ad blocking** leaks first-party ads by design (domain + cosmetic layers only).
- **Desktop mode** can't force desktop layout on all responsive sites.

---

## Privacy

No data leaves the device. History, bookmarks, cookies, settings, and downloads are stored locally only; there is no account, no analytics, no server. Play Data Safety = "no data collected / no data shared." The app contains no ads (ad-blocking is a feature).
