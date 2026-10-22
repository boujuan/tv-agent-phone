package com.boujuan.tvagent.phone.data

import java.net.URI
import java.net.URLDecoder

/**
 * What the panel's QR code carries (tv-agent's clients.py):
 * `tvagent://pair?u=<https address>[&u=<another>]&c=<code>&s=<certificate SHA-256>&n=<computer name>`.
 */
data class PairingLink(val urls: List<String>, val code: String, val pin: String, val name: String)

object Pairing {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   // Crockford base32, as the agent makes them
    private val PIN = Regex("^[0-9a-f]{64}$")

    /** The link from a scanned QR code, or null if it isn't one of the panel's. */
    fun parse(text: String): PairingLink? {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "tvagent" || !uri.rawSchemeSpecificPart.startsWith("//pair?")) return null
        val query = uri.rawSchemeSpecificPart.substringAfter('?')
        val params = query.split('&').filter { '=' in it }.map {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8.name())
        }
        val urls = params.filter { it.first == "u" }.mapNotNull { normalizeUrl(it.second) }
        val code = normalizeCode(params.firstOrNull { it.first == "c" }?.second ?: return null)
        val pin = (params.firstOrNull { it.first == "s" }?.second ?: return null).lowercase()
        if (urls.isEmpty() || !isCode(code) || !PIN.matches(pin)) return null
        return PairingLink(urls, code, pin, params.firstOrNull { it.first == "n" }?.second.orEmpty())
    }

    /** As typed: any case, with or without the dash or spaces; O for 0 and I or L for 1 are forgiven. */
    fun normalizeCode(code: String): String =
        code.uppercase().filter { it.isLetterOrDigit() }.map {
            when (it) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> it
            }
        }.joinToString("")

    fun isCode(code: String): Boolean = code.length == 8 && code.all { it in ALPHABET }

    /**
     * An address as typed ("192.168.1.51", "192.168.1.51:8768", "https://laptop.local:8768/"): https, the phone port
     * unless given, no path. Null if it isn't one.
     */
    fun normalizeUrl(text: String, defaultPort: Int = 8768): String? {
        val t = text.trim().removeSuffix("/")
        if (t.isEmpty()) return null
        val withScheme = if ("://" in t) t else "https://$t"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrEmpty() || !(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
            return null
        }
        val host = if (':' in uri.host && !uri.host.startsWith("[")) "[${uri.host}]" else uri.host
        return "https://$host:${if (uri.port > 0) uri.port else defaultPort}"
    }

    /** The fingerprint as the panel shows it to compare: the first 16 hex digits, in fours. */
    fun grouped(pin: String): String = pin.take(16).uppercase().chunked(4).joinToString(" ")
}
