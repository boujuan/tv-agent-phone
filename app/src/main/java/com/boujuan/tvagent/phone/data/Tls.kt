package com.boujuan.tvagent.phone.data

import android.annotation.SuppressLint
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The agent's listeners use its own self-signed certificate. The phone trusts exactly that certificate, by the
 * SHA-256 the QR code carried (or the user confirmed), and nothing else: on another network with the same addresses
 * the token is never sent, because the handshake fails first. Host names aren't checked, since the pin already
 * names the one certificate (and the laptop's address can change).
 */
@SuppressLint("CustomX509TrustManager")     // it narrows trust to one certificate; it never widens it
class PinnedTrust(private val pin: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        val seen = sha256(leaf.encoded)
        if (!MessageDigest.isEqual(seen.toByteArray(), pin.lowercase().toByteArray())) {
            throw PinMismatch(seen)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** A certificate other than the pinned one answered: not this agent (or it made a new certificate). */
class PinMismatch(val seen: String) : CertificateException("a different certificate ($seen)")

object Tls {
    val anyHost = HostnameVerifier { _, _ -> true }

    fun context(trust: X509TrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }

    /** A TLS socket to the agent's audio stream, pinned like the HTTPS calls. */
    fun socket(host: String, port: Int, pin: String, timeoutMs: Int = 4000): SSLSocket {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            val ssl = context(PinnedTrust(pin)).socketFactory.createSocket(raw, host, port, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()
            return ssl
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }

    /**
     * The certificate an address presents, without trusting it: for pairing with a typed code, where the user
     * compares this fingerprint with the panel's before anything is sent.
     */
    fun fingerprintOf(host: String, port: Int, timeoutMs: Int = 4000): String {
        var seen: String? = null
        // Trusts anything, but only for this handshake, which sends nothing: the user compares what it saw.
        @SuppressLint("CustomX509TrustManager")
        val capture = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                seen = chain?.firstOrNull()?.let { sha256(it.encoded) }
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                throw CertificateException("not a server")

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        Socket().use { raw ->
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            (context(capture).socketFactory.createSocket(raw, host, port, true) as SSLSocket).use {
                it.soTimeout = timeoutMs
                it.startHandshake()
            }
        }
        return seen ?: throw CertificateException("no certificate")
    }
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
