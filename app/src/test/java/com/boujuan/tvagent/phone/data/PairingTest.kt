package com.boujuan.tvagent.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val pin = "b3495cede9c2660420887086c5ea55056b8d933b81254aefe9c8e3e9b4d8d3d7"

    @Test
    fun `the panel's link is read with all its addresses`() {
        // As clients.pairing_link makes it (urlencode of the query).
        val link = Pairing.parse(
            "tvagent://pair?u=https%3A%2F%2F192.168.1.51%3A8768&u=https%3A%2F%2F100.125.125.38%3A8768" +
                "&c=AN3977VW&s=$pin&n=boucatchyOS"
        )!!
        assertEquals(listOf("https://192.168.1.51:8768", "https://100.125.125.38:8768"), link.urls)
        assertEquals("AN3977VW", link.code)
        assertEquals(pin, link.pin)
        assertEquals("boucatchyOS", link.name)
    }

    @Test
    fun `anything else is not a pairing link`() {
        val good = "u=https%3A%2F%2F192.168.1.51%3A8768&c=AN3977VW&s=$pin&n=x"
        assertNull(Pairing.parse("https://example.com/pair?$good"))
        assertNull(Pairing.parse("tvagent://other?$good"))
        assertNull(Pairing.parse("tvagent://pair?u=https%3A%2F%2F192.168.1.51%3A8768&c=AN3977VW&s=abc"))      // short pin
        assertNull(Pairing.parse("tvagent://pair?u=https%3A%2F%2F192.168.1.51%3A8768&c=AN39&s=$pin"))         // short code
        assertNull(Pairing.parse("tvagent://pair?u=http%3A%2F%2F192.168.1.51%3A8768&c=AN3977VW&s=$pin"))      // not https
        assertNull(Pairing.parse("tvagent://pair?c=AN3977VW&s=$pin"))                                         // no address
        assertNull(Pairing.parse("not a link at all"))
    }

    @Test
    fun `codes are forgiving as typed`() {
        assertEquals("AN3977VW", Pairing.normalizeCode("an39-77vw"))
        assertEquals("01100000", Pairing.normalizeCode("OIL0 0000"))
        assertTrue(Pairing.isCode("AN3977VW"))
        assertFalse(Pairing.isCode("AN3977V"))
        assertFalse(Pairing.isCode("AN3977VU"))        // U isn't in the agent's alphabet
    }

    @Test
    fun `typed addresses become the phone listener's https address`() {
        assertEquals("https://192.168.1.51:8768", Pairing.normalizeUrl("192.168.1.51"))
        assertEquals("https://192.168.1.51:9000", Pairing.normalizeUrl(" 192.168.1.51:9000 "))
        assertEquals("https://laptop.local:8768", Pairing.normalizeUrl("https://laptop.local:8768/"))
        assertEquals("https://[fd7a:115c::1]:8768", Pairing.normalizeUrl("[fd7a:115c::1]"))
        assertNull(Pairing.normalizeUrl("http://192.168.1.51"))
        assertNull(Pairing.normalizeUrl("https://192.168.1.51/v1/pair"))
        assertNull(Pairing.normalizeUrl(""))
    }

    @Test
    fun `the fingerprint is shown like the panel shows it`() {
        assertEquals("B349 5CED E9C2 6604", Pairing.grouped(pin))
    }
}
