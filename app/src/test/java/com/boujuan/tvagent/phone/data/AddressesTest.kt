package com.boujuan.tvagent.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The saved addresses kept current from GET /v1/clients/me's "addresses". */
class AddressesTest {
    private val lan = "https://192.168.1.52:8768"
    private val tailscale = "https://100.125.125.38:8768"

    private fun agent(urls: List<String>, manual: List<String> = emptyList()) = SavedAgent(
        id = "a", name = "laptop", urls = urls, pin = "p".repeat(64), streamPort = 8769, deviceId = "d",
        deviceName = "Pixel", sealedToken = "", manualUrls = manual,
    )

    @Test
    fun `only https addresses, normalized, few`() {
        assertEquals(listOf(lan, tailscale), Addresses.offered(listOf("$lan/", " $tailscale ", lan)))
        assertEquals(listOf("https://[fd7a::1]:8768"), Addresses.offered(listOf("http://192.168.1.52:8768",
            "https://[fd7a::1]:8768", "192.168.1.9:8768", "https://evil/path", "nonsense")))
        assertEquals(Addresses.MAX, Addresses.offered((1..20).map { "https://10.0.0.$it:8768" })?.size)
    }

    @Test
    fun `an agent that sends none changes nothing`() {
        assertNull(Addresses.offered(null))
        assertNull(Addresses.offered(emptyList()))
        assertNull(Addresses.offered(listOf("http://192.168.1.52:8768")))
    }

    @Test
    fun `the new LAN address replaces the old one, the one that answered first`() {
        val saved = agent(listOf("https://192.168.1.51:8768", tailscale))
        // reached over Tailscale after the laptop moved from .51 to .52
        assertEquals(listOf(tailscale, lan), Addresses.merge(saved, listOf(lan, tailscale), tailscale))
        // at home again, over the LAN
        assertEquals(listOf(lan, tailscale), Addresses.merge(saved, listOf(lan, tailscale), lan))
    }

    @Test
    fun `addresses typed by hand stay, after the agent's`() {
        val typed = "https://laptop.example.ts.net:8768"
        val saved = Addresses.edited(agent(listOf("https://192.168.1.51:8768")), listOf("https://192.168.1.51:8768", typed))
        assertEquals(listOf(typed), saved.manualUrls)
        assertEquals(listOf(lan, tailscale, typed), Addresses.merge(saved, listOf(lan, tailscale), lan))
        // removed by hand: no longer kept
        val removed = Addresses.edited(saved, listOf("https://192.168.1.51:8768"))
        assertEquals(emptyList<String>(), removed.manualUrls)
        assertEquals(listOf(lan, tailscale), Addresses.merge(removed, listOf(lan, tailscale), lan))
    }
}
