package com.search.browser

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTION = "section"
        const val SEC_SECURITY = "security"
        const val SEC_ADBLOCK = "adblock"
        const val SEC_ACCESSIBILITY = "accessibility"
        const val SEC_CUSTOMIZE = "customize"
        const val SEC_SITE = "site"
    }

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_section)
        content = findViewById(R.id.sectionContent)
        findViewById<android.widget.ImageButton>(R.id.sectionBack)
            .setOnClickListener { finish() }

        val section = intent.getStringExtra(EXTRA_SECTION) ?: SEC_SECURITY
        val title = findViewById<TextView>(R.id.sectionTitle)

        when (section) {
            SEC_SECURITY -> { title.text = "Search Security"; buildSecurity() }
            SEC_ADBLOCK -> { title.text = "Ad blocking"; buildAdblock() }
            else -> { title.text = "Coming soon"; addNote("This section is coming soon.") }
        }
    }

    // ---- Security section ----

    private fun buildSecurity() {
        addToggle(
            "HTTPS-only mode",
            "Always try to connect securely and warn on insecure sites.",
            Settings.SEC_HTTPS_ONLY, true
        )
        addToggle(
            "Safe Browsing",
            "Warn about dangerous sites and downloads.",
            Settings.SEC_SAFE_BROWSING, true
        )
        addToggle(
            "Block pop-ups",
            "Stop sites from opening unwanted pop-up windows.",
            Settings.SEC_BLOCK_POPUPS, true
        )
        addToggle(
            "Block third-party cookies",
            "Prevent cross-site tracking cookies.",
            Settings.SEC_BLOCK_3P_COOKIES, false
        )
        addToggle(
            "Confirm every download",
            "Ask before any file downloads, so nothing saves without your OK.",
            Settings.SEC_CONFIRM_DOWNLOADS, true
        )
    }

    private fun buildAdblock() {
        addToggle(
            "Block ads and trackers",
            "Blocks common ad networks and trackers for faster, cleaner browsing.",
            Settings.ADBLOCK_ENABLED, false
        )
        addNote("Reload open pages after changing this for it to take full effect.")
    }

    // ---- UI builders ----

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun addToggle(title: String, desc: String, key: String, default: Boolean) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(20), dp(14), dp(20), dp(14))
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )

        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(resolveTextColor())

        val d = TextView(this)
        d.text = desc
        d.textSize = 13f
        d.setTextColor(0xFF8A8A8F.toInt())
        d.setPadding(0, dp(2), 0, 0)

        textCol.addView(t)
        textCol.addView(d)

        val sw = SwitchCompat(this)
        sw.isChecked = Settings.getBool(this, key, default)
        sw.setOnCheckedChangeListener { _, checked ->
            Settings.setBool(this, key, checked)
        }

        row.addView(textCol)
        row.addView(sw)
        content.addView(row)

        // divider
        val div = TextView(this)
        div.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        )
        div.setBackgroundColor(0x22808080)
        content.addView(div)
    }

    private fun addNote(text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setTextColor(0xFF8A8A8F.toInt())
        t.setPadding(dp(20), dp(16), dp(20), dp(16))
        content.addView(t)
    }

    private fun resolveTextColor(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        return tv.data
    }
}
