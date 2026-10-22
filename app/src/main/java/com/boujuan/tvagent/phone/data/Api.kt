package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The agent answered with an error (its "detail" when it gave one). */
class ApiException(val code: Int, message: String) : IOException(message)

/** None of the agent's addresses could be reached (or a different computer answered there). */
class Unreachable(val tried: List<String>, cause: Throwable?) : IOException(cause?.message ?: "unreachable", cause)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_TYPE = "application/json".toMediaType()
private val AUDIO_TYPE = "application/octet-stream".toMediaType()

/**
 * The agent's API as a paired phone sees it: HTTPS to its phone listener, pinned to its certificate, with this
 * phone's token. Its addresses are tried in order (the LAN, then e.g. Tailscale) until one connects; the one that
 * worked is tried first next time. A request is only sent to another address when the first couldn't connect, so a
 * command never runs twice.
 */
class AgentApi(
    urls: List<String>,
    private val pin: String,
    private val token: String?,
    base: OkHttpClient = OkHttpClient(),
) {
    private val urls = urls.ifEmpty { throw IllegalArgumentException("no address") }
    @Volatile var preferred = 0
        private set
    val activeUrl: String get() = urls[preferred]
    /** The addresses in the order they're tried: the one that last answered first. */
    val addresses: List<String> get() = urls.indices.map { urls[(preferred + it) % urls.size] }

    private val http: OkHttpClient = run {
        val trust = PinnedTrust(pin)
        base.newBuilder()
            .sslSocketFactory(Tls.context(trust).socketFactory, trust)
            .hostnameVerifier(Tls.anyHost)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val slow: OkHttpClient = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()

    // ---- the calls ----

    suspend fun pair(code: String, name: String): PairResponse =
        post("/v1/pair", json.encodeToString(PairRequest.serializer(), PairRequest(code, name)), PairResponse.serializer())

    suspend fun me(): Me = get("/v1/clients/me", Me.serializer())
    suspend fun forget(): Outcome = post("/v1/clients/me/forget", "{}", Outcome.serializer())

    suspend fun nowPlaying(): NowPlayingResponse = get("/v1/now-playing", NowPlayingResponse.serializer())
    suspend fun setAudioTrack(index: Int): Outcome =
        post("/v1/now-playing/track", buildJsonObject { put("audio", index) }, Outcome.serializer())
    suspend fun setSubtitleTrack(index: Int?): Outcome =
        post("/v1/now-playing/track", buildJsonObject { put("subtitle", index) }, Outcome.serializer())

    /** One typed action (tv-agent's actions.py), e.g. {"type": "transport", "op": "pause"}. */
    suspend fun action(action: JsonObject): Outcome = post("/v1/action", action, Outcome.serializer(), slow = true)

    suspend fun key(key: String, long: Boolean = false): Outcome =
        post("/v1/remote/key", buildJsonObject { put("key", key); put("long", long) }, Outcome.serializer())
    suspend fun type(text: String): Outcome =
        post("/v1/remote/text", buildJsonObject { put("text", text) }, Outcome.serializer())
    suspend fun volume(): VolumeLevel = get("/v1/tv/volume", VolumeLevel.serializer())
    suspend fun apps(): List<AppEntry> = get("/v1/apps", AppsResponse.serializer()).apps

    /** Decide and do it, as if said ("play the bear season 3"). */
    suspend fun text(text: String): CommandResult =
        post("/v1/text", buildJsonObject { put("text", text); put("source", "phone") }, CommandResult.serializer(), slow = true)
    /** A whole recording (16 kHz mono PCM16), when the stream isn't available. */
    suspend fun audio(pcm: ByteArray): CommandResult =
        send("/v1/audio", pcm.toRequestBody(AUDIO_TYPE), CommandResult.serializer(), slow = true)
    /** Decide only (teaching: what would this sentence do?). */
    suspend fun decide(text: String): CommandResult =
        post("/v1/decide", buildJsonObject { put("text", text) }, CommandResult.serializer())

    suspend fun list(name: String): ListResponse = get("/v1/lists?list=$name", ListResponse.serializer(), slow = true)
    suspend fun pick(list: String, item: ListItem): Outcome = post("/v1/lists/pick", buildJsonObject {
        put("list", list); put("imdb", item.imdb); put("video", item.video)
    }, Outcome.serializer(), slow = true)

    suspend fun activity(limit: Int = 50, before: Double? = null): ActivityPage =
        get("/v1/activity?limit=$limit" + (before?.let { "&before=$it" } ?: ""), ActivityPage.serializer())
    suspend fun mark(entry: ActivityEntry, wrong: Boolean): MarkResult = post("/v1/activity/mark", buildJsonObject {
        put("t", entry.t); put("wrong", wrong); put("heard", entry.heard)
    }, MarkResult.serializer())
    /** Teach a sentence its action; t marks that history entry as taught. */
    suspend fun learn(say: String, action: JsonObject, entry: ActivityEntry?): Outcome =
        post("/v1/learn", buildJsonObject {
            put("say", say); put("do", action)
            if (entry != null) { put("t", entry.t); put("heard", entry.heard) }
        }, Outcome.serializer())
    suspend fun learned(): List<Learned> = get("/v1/learned", LearnedList.serializer()).learned
    suspend fun unlearn(say: String): Outcome =
        post("/v1/learned/remove", buildJsonObject { put("say", say) }, Outcome.serializer())

    suspend fun pipeline(): Pipeline = get("/v1/pipeline", Pipeline.serializer())
    suspend fun setPipeline(change: JsonObject): Pipeline = post("/v1/pipeline", change, Pipeline.serializer(), slow = true)
    suspend fun settings(): SettingsResponse = get("/v1/settings", SettingsResponse.serializer())
    suspend fun setSettings(change: JsonObject): SettingsResponse = post("/v1/settings", change, SettingsResponse.serializer())

    suspend fun about(): About = get("/v1/about", About.serializer())
    suspend fun stats(): Map<String, StageStats> = get("/v1/stats", MapSerializer(String.serializer(), StageStats.serializer()))
    suspend fun tvStatus(): TvStatus = get("/v1/tv/status", TvStatus.serializer(), slow = true, okCodes = setOf(503))
    suspend fun diagnose(): Diagnosis = post("/v1/tv/diagnose", "{}", Diagnosis.serializer(), slow = true)
    suspend fun reconnect(): Reconnect = post("/v1/tv/reconnect", "{}", Reconnect.serializer(), slow = true)
    suspend fun speedtest(): Speed = post("/v1/tv/speedtest", "{}", Speed.serializer(), slow = true, okCodes = setOf(503))
    suspend fun screenshot(): ByteArray = bytes(Request.Builder().get(), "/v1/tv/screenshot")
    suspend fun recording(name: String): ByteArray = bytes(Request.Builder().get(), "/v1/recording?name=$name")
    suspend fun playLink(url: String): Outcome =
        post("/v1/tv/play-link", buildJsonObject { put("url", url) }, Outcome.serializer(), slow = true)

    // ---- plumbing ----

    private suspend fun <T> get(path: String, de: KSerializer<T>, slow: Boolean = false, okCodes: Set<Int> = emptySet()): T =
        call(Request.Builder().get(), path, slow, okCodes) { json.decodeFromString(de, it) }

    private suspend fun <T> post(path: String, body: JsonElement, de: KSerializer<T>, slow: Boolean = false, okCodes: Set<Int> = emptySet()): T =
        post(path, body.toString(), de, slow, okCodes)

    private suspend fun <T> post(path: String, body: String, de: KSerializer<T>, slow: Boolean = false, okCodes: Set<Int> = emptySet()): T =
        send(path, body.toRequestBody(JSON_TYPE), de, slow, okCodes)

    private suspend fun <T> send(path: String, body: RequestBody, de: KSerializer<T>, slow: Boolean = false, okCodes: Set<Int> = emptySet()): T =
        call(Request.Builder().post(body), path, slow, okCodes) { json.decodeFromString(de, it) }

    private suspend fun bytes(builder: Request.Builder, path: String): ByteArray =
        execute(builder, path, slow = true, okCodes = emptySet()).use { it.body!!.bytes() }

    private suspend fun <T> call(builder: Request.Builder, path: String, slow: Boolean, okCodes: Set<Int>, parse: (String) -> T): T =
        execute(builder, path, slow, okCodes).use { parse(it.body!!.string()) }

    private suspend fun execute(builder: Request.Builder, path: String, slow: Boolean, okCodes: Set<Int>): Response {
        if (token != null) builder.header("Authorization", "Bearer $token")
        var last: Throwable? = null
        val order = urls.indices.map { (preferred + it) % urls.size }
        for (i in order) {
            val request = builder.url((urls[i] + path).toHttpUrl()).build()
            val response = try {
                (if (slow) this.slow else http).newCall(request).await()
            } catch (e: IOException) {
                if (!couldNotConnect(e)) throw e
                last = e
                continue
            }
            preferred = i
            if (!response.isSuccessful && response.code !in okCodes) {
                val detail = response.use { errorDetail(it) }
                throw ApiException(response.code, detail)
            }
            return response
        }
        throw Unreachable(urls, last)
    }

    private fun errorDetail(r: Response): String {
        val text = r.body?.string().orEmpty()
        val detail = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            (o["detail"] ?: o["error"])?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
        }.getOrNull()
        return detail ?: "HTTP ${r.code}"
    }

    companion object {
        /** Failed before the request could reach the agent, so trying another address can't run it twice. */
        fun couldNotConnect(e: Throwable): Boolean = when (e) {
            is ConnectException, is NoRouteToHostException, is UnknownHostException, is PortUnreachableException -> true
            is SocketTimeoutException -> e.message.orEmpty().contains("connect", ignoreCase = true)
            is SSLException -> true                 // the handshake: another computer (pin mismatch) or none
            else -> e.cause?.let { it !== e && couldNotConnect(it) } ?: false
        }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

/** Typed actions (tv-agent's actions.py) the screens send. */
object Actions {
    fun transport(op: String, seconds: Int? = null, percent: Int? = null) = buildJsonObject {
        put("type", "transport"); put("op", op)
        if (seconds != null) put("seconds", seconds)
        if (percent != null) put("percent", percent)
    }
    fun volume(op: String, amount: Int? = null) = buildJsonObject {
        put("type", "volume"); put("op", op); if (amount != null) put("amount", amount)
    }
    fun openApp(app: String) = buildJsonObject { put("type", "open_app"); put("app", app) }
    fun home() = buildJsonObject { put("type", "home") }
    fun next(offset: Int = 1) = buildJsonObject { put("type", "next_episode"); put("offset", offset) }
    fun skip(what: String) = buildJsonObject { put("type", "skip"); put("what", what) }
    fun subtitles(value: String) = buildJsonObject { put("type", "tracks"); put("subtitles", value) }
    fun binge(on: Boolean) = buildJsonObject { put("type", "binge"); put("on", on) }
    fun play(title: String) = buildJsonObject { put("type", "play_title"); put("title", title) }
    fun continueWatching() = buildJsonObject { put("type", "continue_watching") }
    fun showList(list: String) = buildJsonObject { put("type", "show_list"); put("list", list) }
}
