package com.boujuan.tvagent.phone.data

/**
 * A paired agent's addresses kept current from what it says it listens on now (GET /v1/clients/me's "addresses"),
 * so a new LAN address, or Tailscale switched on, needs no pairing again and no address typed by hand. The list comes
 * over the pinned, authenticated connection, and every later connection to any of its addresses is pinned too: one
 * that answers with another certificate is never sent the token.
 */
object Addresses {
    const val MAX = 8

    /** The agent's https addresses, normalized; null when it sent none usable (an older agent: nothing changes). */
    fun offered(list: List<String>?): List<String>? =
        list?.filter { it.trim().startsWith("https://") }?.mapNotNull { Pairing.normalizeUrl(it) }?.distinct()
            ?.take(MAX)?.ifEmpty { null }

    /**
     * The new list: the address that just answered first (as the app tries them), then the agent's in its order, then
     * those typed in by hand. Addresses that only came from an earlier pairing or answer, and that the agent no longer
     * listens on, are dropped.
     */
    fun merge(agent: SavedAgent, offered: List<String>, answered: String?): List<String> {
        val manual = agent.manualUrls.filter { it in agent.urls }
        return (listOfNotNull(answered?.let { Pairing.normalizeUrl(it) }) + offered + manual).distinct()
    }

    /** Addresses edited by hand (Agents › Addresses…): those added there are kept from then on, after the agent's. */
    fun edited(agent: SavedAgent, urls: List<String>): SavedAgent =
        agent.copy(urls = urls, manualUrls = urls.filter { it in agent.manualUrls || it !in agent.urls })
}
