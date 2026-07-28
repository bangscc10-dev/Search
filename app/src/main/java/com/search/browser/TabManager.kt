package com.search.browser

/**
 * Owns the list of tabs and tracks which is active.
 * Enforces a cap on how many tabs stay live (hold a WebView) at once,
 * freezing the least-recently-used tab when the cap is exceeded.
 *
 * This class holds only data/bookkeeping. Actual WebView creation,
 * freezing (saveState + destroy) and restoring is done by the Activity,
 * which has the Context and view hierarchy. TabManager tells it what to do
 * via the callbacks.
 */
class TabManager(
    private val maxLiveTabs: Int = 3
) {
    private var nextId = 1L
    val tabs = mutableListOf<Tab>()
    var activeTab: Tab? = null
        private set

    // Recently-used order of LIVE tab ids (most recent last).
    private val liveOrder = mutableListOf<Long>()

    // Callbacks the Activity provides:
    // onNeedFreeze: freeze this tab (saveState + destroy its WebView)
    var onNeedFreeze: ((Tab) -> Unit)? = null

    fun createTab(url: String? = null): Tab {
        val tab = Tab(id = nextId++)
        url?.let { tab.url = it }
        tabs.add(tab)
        return tab
    }

    fun count(): Int = tabs.size

    fun setActive(tab: Tab) {
        activeTab = tab
        touchLive(tab.id)
    }

    /** Call when a tab becomes live (gets a WebView). Enforces the cap. */
    fun markLive(tab: Tab) {
        touchLive(tab.id)
        enforceCap()
    }

    private fun touchLive(id: Long) {
        liveOrder.remove(id)
        liveOrder.add(id)
    }

    private fun enforceCap() {
        while (liveOrder.size > maxLiveTabs) {
            val lruId = liveOrder.first()
            // Never freeze the active tab.
            if (lruId == activeTab?.id) {
                // Move active to the back and try the next candidate.
                liveOrder.remove(lruId)
                liveOrder.add(lruId)
                // If active is the only live tab over cap, stop.
                if (liveOrder.first() == activeTab?.id) break
                continue
            }
            val lruTab = tabs.find { it.id == lruId }
            liveOrder.remove(lruId)
            if (lruTab != null && lruTab.isLive) {
                onNeedFreeze?.invoke(lruTab)
            }
        }
    }

    fun removeTab(tab: Tab) {
        tabs.remove(tab)
        liveOrder.remove(tab.id)
        if (activeTab == tab) {
            activeTab = tabs.lastOrNull()
        }
    }
}
