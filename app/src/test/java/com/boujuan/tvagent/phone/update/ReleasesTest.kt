package com.boujuan.tvagent.phone.update

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** The GitHub release check and download, against a local server answering as the API does. */
class ReleasesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.close()

    private val apk = ByteArray(200_000) { (it % 253).toByte() }
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    private fun release(digest: String) = """{"tag_name": "v0.2.0", "name": "0.2.0", "body": "### Added\n- About",
        "html_url": "https://github.com/boujuan/tv-agent-phone/releases/tag/v0.2.0", "draft": false, "prerelease": false,
        "assets": [{"name": "SHA256SUMS", "browser_download_url": "${server.url("/sums")}", "size": 90, "digest": null},
                   {"name": "tv-agent-phone-0.2.0.apk", "browser_download_url": "${server.url("/apk")}",
                    "size": ${apk.size}, "digest": "$digest"}]}"""

    @Test
    fun `newer means a later version only, never the same or an older one`() {
        assertTrue(Versions.newer("0.2.0", "0.1.0"))
        assertTrue(Versions.newer("v0.10.0", "0.9.9"))
        assertTrue(Versions.newer("1.0", "0.9.9"))
        assertTrue(Versions.newer("0.2.0", "0.1.9-test"))
        assertFalse(Versions.newer("0.1.0", "0.1.0"))
        assertFalse(Versions.newer("v0.1.0", "0.1"))
        assertFalse(Versions.newer("0.1.0", "0.2.0"))
        assertFalse(Versions.newer("0.2.0-rc1", "0.2.0"))
    }

    @Test
    fun `the latest release and its APK`() = runBlocking {
        server.enqueue(MockResponse(200, headersOf("Content-Type", "application/json"), release("sha256:$apkSha")))
        val r = Releases(http, server.url("/latest").toString()).latest()
        assertEquals("0.2.0", r.version)
        assertEquals("tv-agent-phone-0.2.0.apk", r.apk?.name)
        assertEquals(apkSha, r.apk?.sha256)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.headers["Accept"])
    }

    @Test
    fun `a download that matches the digest is kept`() = runBlocking {
        server.enqueue(MockResponse(200, headersOf("Content-Type", "application/json"), release("sha256:$apkSha")))
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apk)).build())
        val releases = Releases(http, server.url("/latest").toString())
        val to = tmp.root.resolve("updates/app.apk")
        var last = 0f
        releases.download(releases.latest().apk!!, to) { last = it }
        assertTrue(apk.contentEquals(to.readBytes()))
        assertEquals(1f, last)
    }

    @Test
    fun `a download that doesn't match is deleted and refused`() = runBlocking {
        server.enqueue(MockResponse(200, headersOf("Content-Type", "application/json"), release("sha256:" + "0".repeat(64))))
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(apk)).build())
        val releases = Releases(http, server.url("/latest").toString())
        val to = tmp.root.resolve("updates/app.apk")
        try {
            releases.download(releases.latest().apk!!, to)
            fail("a wrong SHA-256 was accepted")
        } catch (e: UpdateError) {
            assertFalse(to.exists())
        }
    }

    @Test
    fun `a release without a digest is never downloaded`() = runBlocking {
        server.enqueue(MockResponse(200, headersOf("Content-Type", "application/json"),
            release("sha256:$apkSha").replace("\"sha256:$apkSha\"", "null")))
        val releases = Releases(http, server.url("/latest").toString())
        val asset = releases.latest().apk!!
        assertNull(asset.sha256)
        try {
            releases.download(asset, tmp.root.resolve("app.apk"))
            fail("no digest, still downloaded")
        } catch (e: UpdateError) {
            assertEquals(1, server.requestCount)          // only the API call: the APK wasn't fetched
        }
    }
}
