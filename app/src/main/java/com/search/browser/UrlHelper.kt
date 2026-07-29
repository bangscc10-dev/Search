package com.search.browser

import android.net.Uri
import android.util.Patterns

object UrlHelper {

    private val PASSTHROUGH_SCHEMES = listOf(
        "http://", "https://", "about:", "file:", "data:",
        "javascript:", "chrome:", "content:", "ftp://"
    )

    // A pragmatic set of real TLDs. If the suffix isn't here, we search
    // (so "node.js", "index.php", "main.py" correctly go to search).
    private val KNOWN_TLDS = setOf(
        // generic
        "com", "org", "net", "io", "dev", "app", "co", "edu", "gov", "mil", "int",
        "info", "biz", "me", "xyz", "tech", "ai", "cc", "tv", "online", "store",
        "site", "blog", "shop", "cloud", "page", "wiki", "news", "email", "live",
        "pro", "name", "mobi", "asia", "space", "fun", "life", "world", "today",
        // country codes (selection incl. West Africa)
        "sl", "ng", "gh", "gm", "ci", "sn", "lr", "gn",
        "uk", "us", "ca", "au", "de", "fr", "es", "it", "nl", "se", "no", "fi",
        "ru", "cn", "jp", "kr", "in", "br", "za", "ke", "eg", "ma", "pt", "ie",
        "ch", "at", "be", "dk", "pl", "gr", "tr", "ua", "cz", "ro", "hu", "sk",
        "sg", "hk", "tw", "my", "id", "th", "ph", "vn", "ae", "sa", "il", "mx",
        "ar", "cl", "pe", "nz"
    )

    private const val GOOGLE_SEARCH = "https://www.google.com/search?q="

    fun toUrlOrSearch(raw: String, searchPrefix: String = GOOGLE_SEARCH): String {
        val text = raw.trim()
        if (text.isEmpty()) return GOOGLE_SEARCH

        val lower = text.lowercase()

        // 1. Known scheme -> load as-is
        if (PASSTHROUGH_SCHEMES.any { lower.startsWith(it) }) return text

        // 2. localhost (+ optional port/path)
        if (lower == "localhost" || lower.startsWith("localhost:") || lower.startsWith("localhost/")) {
            return "http://$text"
        }

        // 3. Raw IP (+ optional port/path)
        val hostForIp = text.substringBefore("/").substringBefore(":")
        if (Patterns.IP_ADDRESS.matcher(hostForIp).matches()) return "http://$text"

        // 4. Spaces -> search
        if (text.contains(" ")) return search(text, searchPrefix)

        // 5. Domain with a *known* TLD -> load
        if (looksLikeDomain(text)) return "https://$text"

        // 6. Fallback -> search
        return search(text, searchPrefix)
    }

    private fun looksLikeDomain(text: String): Boolean {
        // Inspect only the host portion (strip path, query, port)
        val host = text.substringBefore("/").substringBefore("?").substringBefore(":")
        if (!host.contains(".")) return false
        if (host.startsWith(".") || host.endsWith(".")) return false

        val labels = host.split(".")
        if (labels.size < 2) return false
        if (labels.any { it.isEmpty() }) return false

        val tld = labels.last().lowercase()
        // Strict: only a recognized TLD counts as a domain.
        return tld in KNOWN_TLDS
    }

    private fun search(query: String, prefix: String): String = prefix + Uri.encode(query)
}
