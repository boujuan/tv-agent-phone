package com.boujuan.tvagent.phone.update

import com.boujuan.tvagent.phone.data.await
import com.boujuan.tvagent.phone.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A GitHub release, as the public API answers (no token: the repository is public). */
@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val published: String? = null,
    val assets: List<Asset> = emptyList(),
) {
    val version: String get() = tag.removePrefix("v")

    /** The app's APK among the assets (tv-agent-phone-<version>.apk), with the digest GitHub keeps for it. */
    val apk: Asset? get() = assets.firstOrNull { it.name.startsWith("tv-agent-phone-") && it.name.endsWith(".apk") }
}

@Serializable
data class Asset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    val digest: String? = null,      // "sha256:…"
) {
    val sha256: String? get() = digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
}

class UpdateError(message: String) : IOException(message)

object Versions {
    /** "v0.2.0", "0.2.0-test" → [0, 2, 0]; anything after the numbers is ignored. */
    fun parts(version: String): List<Int> =
        version.removePrefix("v").substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }

    /** Whether [latest] is a later release than [current]: equal or older never is (no downgrades). */
    fun newer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * The phone app's releases on GitHub: the latest one, and its APK downloaded with its SHA-256 checked against the
 * digest GitHub records for the asset (a file that doesn't match is deleted, never installed).
 */
class Releases(
    private val http: OkHttpClient,
    private val latestUrl: String = "https://api.github.com/repos/boujuan/tv-agent-phone/releases/latest",
) {
    suspend fun latest(): Release {
        val request = Request.Builder().url(latestUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        return http.newCall(request).await().use { r ->
            if (!r.isSuccessful) throw UpdateError("GitHub answered ${r.code}")
            val text = withContext(Dispatchers.IO) { r.body.string() }
            json.decodeFromString(Release.serializer(), text)
        }
    }

    /** The asset into [to], checked against its SHA-256; [progress] gets 0..1 as it arrives. */
    suspend fun download(asset: Asset, to: File, progress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val expected = asset.sha256 ?: throw UpdateError("the release doesn't say the APK's SHA-256")
        val digest = MessageDigest.getInstance("SHA-256")
        to.parentFile?.mkdirs()
        http.newCall(Request.Builder().url(asset.url).build()).await().use { r ->
            if (!r.isSuccessful) throw UpdateError("download answered ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: asset.size
            var done = 0L
            r.body.byteStream().use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        val seen = digest.digest().joinToString("") { "%02x".format(it) }
        if (!MessageDigest.isEqual(seen.toByteArray(), expected.toByteArray())) {
            to.delete()
            throw UpdateError("the download's SHA-256 doesn't match the release's")
        }
        to
    }
}
