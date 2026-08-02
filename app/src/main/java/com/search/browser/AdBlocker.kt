package com.search.browser

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Lightweight ad/tracker blocker using domain matching against a bundled list.
 * Not as thorough as uBlock (no cosmetic filtering), but blocks the common
 * ad networks and trackers by intercepting their requests.
 */
object AdBlocker {

    // Common ad/tracker host fragments. Matched as substring of the request host.
    private val BLOCKED = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "adnxs.com", "adsystem.com", "amazon-adsystem.com",
        "facebook.net", "connect.facebook.net", "fbcdn.net/ads",
        "scorecardresearch.com", "quantserve.com", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "chartboost.com", "inmobi.com", "mopub.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "adform.net", "smartadserver.com", "moatads.com", "3lift.com",
        "bidswitch.net", "sharethrough.com", "yieldmo.com", "teads.tv",
        "propellerads.com", "popads.net", "popcash.net", "adsterra.com",
        "exoclick.com", "juicyads.com", "hilltopads.net", "trafficjunky.net",
        "onesignal.com", "pushcrew.com", "mgid.com", "revcontent.com",
        "zedo.com", "adroll.com", "hotjar.com", "mixpanel.com",
        "segment.com", "amplitude.com", "branch.io", "appsflyer.com",
        "adjust.com", "kochava.com", "flurry.com", "crashlytics.com",
        "bugsnag.com", "newrelic.com", "optimizely.com", "yandex.ru/ads"
    )

    // A blocked request must get its own WebResourceResponse each time: a
    // response's input stream is consumed once and read from WebView threads,
    // so a single shared instance across concurrent blocked requests is unsafe.
    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
    )

    fun isEnabled(c: Context): Boolean =
        Settings.getBool(c, Settings.ADBLOCK_ENABLED, false)

    private fun isBlocked(url: String): Boolean {
        val u = url.lowercase()
        return BLOCKED.any { u.contains(it) }
    }

    /** Returns an empty response if the request is an ad/tracker, else null. */
    fun check(c: Context, request: WebResourceRequest?): WebResourceResponse? {
        if (!isEnabled(c)) return null
        val url = request?.url?.toString() ?: return null
        return if (isBlocked(url)) emptyResponse() else null
    }

    /** JS that injects CSS to hide common ad slots/containers. */
    fun hideCss(): String {
        val css = listOf(
            "[id*='ad-']", "[id*='-ad']", "[class*='ad-']", "[class*='-ad']",
            "[class*='ads']", "[id*='ads']", "[class*='banner']", "[id*='banner']",
            "[class*='sponsor']", "[class*='popup']", "[id*='popup']",
            "iframe[src*='ad']", "ins.adsbygoogle", "[class*='adsbox']",
            "[aria-label*='advert']", "[class*='promoted']"
        ).joinToString(",")
        val js = "(function(){try{var s=document.createElement('style');" +
            "s.innerHTML='" + css + "{display:none !important;visibility:hidden !important;}';" +
            "document.head.appendChild(s);}catch(e){}})();"
        return js
    }
}