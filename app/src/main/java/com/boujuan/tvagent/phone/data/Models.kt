package com.boujuan.tvagent.phone.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The agent's answers (tv-agent's server.py, remote.py and clients.py); fields the app doesn't use are ignored.

@Serializable
data class PairRequest(val code: String, val name: String)

@Serializable
data class PairResponse(
    val token: String,
    val device: DeviceInfo,
    val agent: AgentInfo,
    @SerialName("stream_port") val streamPort: Int,
)

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val paired: Double = 0.0,
    @SerialName("last_seen") val lastSeen: Double? = null,
    @SerialName("last_ip") val lastIp: String = "",
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class AgentInfo(val name: String = "", val version: String = "")

@Serializable
data class Me(
    val device: DeviceInfo,
    val agent: AgentInfo,
    @SerialName("stream_port") val streamPort: Int,
    /** The agent's phone addresses as it listens on them now, in the order to try; null from an older agent. */
    val addresses: List<String>? = null,
)

@Serializable
data class Outcome(val ok: Boolean = false, val say: String = "")

/** /v1/text, /v1/audio and /v1/decide: what was heard, the action and how it was decided, and what happened. */
@Serializable
data class CommandResult(
    val heard: String = "",
    val action: JsonObject? = null,
    val via: String = "",
    val rule: String = "",
    val confidence: Double = 0.0,
    val describe: String = "",
    val trace: JsonArray? = null,
    val ok: Boolean = false,
    val say: String = "",
)

@Serializable
data class NowPlayingResponse(
    @SerialName("now_playing") val nowPlaying: NowPlaying? = null,
    val player: PlayerState? = null,
    val tracks: Tracks? = null,
    val segments: List<Segment> = emptyList(),
)

@Serializable
data class NowPlaying(
    @SerialName("imdb_id") val imdbId: String = "",
    val kind: String = "",
    @SerialName("video_id") val videoId: String = "",
    val name: String = "",
    val player: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null,
    val background: String? = null,
    @SerialName("episode_name") val episodeName: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class PlayerState(
    val app: String = "",
    @SerialName("position_s") val positionS: Long = 0,
    @SerialName("duration_s") val durationS: Long = 0,
    val playing: Boolean = false,
)

@Serializable
data class Track(
    val index: Int,
    val language: String = "",
    val label: String = "",
    val name: String = "",
    val codec: String = "",
    val channels: Int? = null,
    val forced: Boolean = false,
)

@Serializable
data class Tracks(
    val audio: List<Track> = emptyList(),
    @SerialName("audio_current") val audioCurrent: Int? = null,
    val subtitles: List<Track> = emptyList(),
    @SerialName("subtitle_current") val subtitleCurrent: Int? = null,
    @SerialName("subtitles_on") val subtitlesOn: Boolean = false,
)

@Serializable
data class Segment(val what: String, @SerialName("start_s") val startS: Long, @SerialName("end_s") val endS: Long)

@Serializable
data class VolumeLevel(val level: Int? = null)

@Serializable
data class AppEntry(val key: String, val label: String)

@Serializable
data class AppsResponse(val apps: List<AppEntry> = emptyList())

@Serializable
data class ListItem(
    val number: Int,
    val imdb: String,
    val kind: String = "",
    val name: String = "",
    val label: String = "",
    val detail: String = "",
    val poster: String = "",
    val video: String? = null,
)

@Serializable
data class ListResponse(val list: String, val title: String = "", val items: List<ListItem> = emptyList())

@Serializable
data class ActivityEntry(
    val t: Double,
    val source: String = "",
    val heard: String = "",
    val type: String = "",
    val via: String = "",
    val rule: String = "",
    val confidence: Double? = null,
    val action: JsonObject? = null,
    val ok: Boolean? = null,
    val say: String = "",
    val ms: Double? = null,
    @SerialName("decide_ms") val decideMs: Double? = null,
    val cost: Double? = null,
    val stt: String? = null,
    @SerialName("stt_ms") val sttMs: Double? = null,
    @SerialName("stt_cost") val sttCost: Double? = null,
    @SerialName("after_speech_ms") val afterSpeechMs: Double? = null,
    @SerialName("audio_s") val audioS: Double? = null,
    val recording: String? = null,
    val wrong: Boolean? = null,
    val taught: String? = null,
)

@Serializable
data class ActivityPage(val entries: List<ActivityEntry> = emptyList(), val more: Boolean = false, val enabled: Boolean = true)

@Serializable
data class MarkResult(val ok: Boolean = false, val entry: ActivityEntry? = null)

@Serializable
data class Learned(val say: String, val `do`: JsonObject)

@Serializable
data class LearnedList(val learned: List<Learned> = emptyList())

@Serializable
data class Keys(val liquid: Boolean = false, val openrouter: Boolean = false, @SerialName("hosted_d1") val hostedD1: Boolean = false)

@Serializable
data class Pipeline(
    val steps: List<String> = emptyList(),
    val off: List<String> = emptyList(),
    @SerialName("d1_where") val d1Where: String = "",
    @SerialName("hosted_d1_model") val hostedD1Model: String = "",
    @SerialName("llm_model") val llmModel: String = "",
    @SerialName("llm_reasoning") val llmReasoning: String = "",
    @SerialName("llm_private") val llmPrivate: Boolean = false,
    val fallback: String? = null,
    val keys: Keys = Keys(),
    val counts: Map<String, Int> = emptyMap(),
    val learned: Int = 0,
    @SerialName("stt_where") val sttWhere: String = "",
    @SerialName("stt_hosted_model") val sttHostedModel: String = "",
    @SerialName("stt_model") val sttModel: String = "",
    @SerialName("stt_loaded") val sttLoaded: Boolean = false,
    @SerialName("keep_recordings") val keepRecordings: Boolean = false,
    @SerialName("d1_model") val d1Model: String = "",
    @SerialName("stt_language") val sttLanguage: String = "native",      // "native", "" (the model guesses) or "es", "en"…
    @SerialName("stt_language_now") val sttLanguageNow: String = "",     // what "native" means now
    @SerialName("stt_vocabulary") val sttVocabulary: List<String> = emptyList(),
    @SerialName("stt_vocabulary_library") val sttVocabularyLibrary: Boolean = false,
    @SerialName("stt_vocabulary_count") val sttVocabularyCount: Int = 0,
    /** Every name sent with a command (yours, then Twitch favourites and library titles); null from an older agent. */
    @SerialName("stt_vocabulary_sent") val sttVocabularySent: List<String>? = null,
)

@Serializable
data class SettingsValues(
    val player: String = "tvplayer",
    @SerialName("max_height") val maxHeight: Int = 2160,
    @SerialName("max_mbps") val maxMbps: Double = 60.0,
    @SerialName("probe_streams") val probeStreams: Boolean = true,
    val binge: Boolean = true,
    @SerialName("auto_skip") val autoSkip: Boolean = false,
    @SerialName("audio_language") val audioLanguage: String = "eng",     // a language, or "original"
    @SerialName("subtitle_languages") val subtitleLanguages: List<String> = listOf("eng"),
    @SerialName("native_language") val nativeLanguage: String = "spa",
    @SerialName("twitch_favorites") val twitchFavorites: List<String> = emptyList(),
    @SerialName("log_transcripts") val logTranscripts: Boolean = true,
    /** The agent's own updates: "auto" (installs them by themselves), "notify" or "off". */
    val updates: String = "notify",
    @SerialName("check_episodes") val checkEpisodes: Boolean = true,
    /** When subtitles show: "foreign" (only when the audio isn't the native language), "always", "off". */
    val subtitles: String = "foreign",
)

@Serializable
data class LangChoice(val code: String, val label: String)

@Serializable
data class SettingsChoices(
    val player: List<String> = listOf("tvplayer", "kodi", "just"),
    @SerialName("max_height") val maxHeight: List<Int> = listOf(2160, 1440, 1080, 720),
    val audio: List<LangChoice> = emptyList(),                  // the languages (native_language's too)
    val subtitles: List<LangChoice> = emptyList(),
    /** audio_language's: "original" first, then the languages; empty from an older agent (then `audio`). */
    @SerialName("audio_language") val audioLanguage: List<LangChoice> = emptyList(),
    /** The `subtitles` setting's: foreign, always, off; empty from an older agent (no such setting). */
    @SerialName("subtitles_when") val subtitlesWhen: List<LangChoice> = emptyList(),
)

@Serializable
data class SettingsResponse(val settings: SettingsValues = SettingsValues(), val choices: SettingsChoices = SettingsChoices())

@Serializable
data class About(
    val version: String = "",
    val python: String = "",
    val started: Double = 0.0,
    @SerialName("stt_model") val sttModel: String = "",
    @SerialName("stt_where") val sttWhere: String = "",
    @SerialName("stt_hosted_model") val sttHostedModel: String = "",
    @SerialName("d1_where") val d1Where: String = "",
    @SerialName("llm_model") val llmModel: String = "",
    val player: String = "",
)

@Serializable
data class StageStats(val n: Int = 0, val p50: Double = 0.0, val p95: Double = 0.0)

@Serializable
data class Wifi(
    val ssid: String? = null,
    val rssi: String? = null,
    @SerialName("link_mbps") val linkMbps: String? = null,
    val mhz: String? = null,
)

@Serializable
data class Memory(@SerialName("total_mb") val totalMb: Int = 0, @SerialName("used_mb") val usedMb: Int = 0)

@Serializable
data class TvApp(val installed: Boolean = false, val version: String? = null)

@Serializable
data class TvStatus(
    val model: String = "",
    val android: String = "",
    val address: String? = null,
    val app: TvApp = TvApp(),
    val wifi: Wifi? = null,
    val link: String? = null,
    val cpu: Double? = null,
    val memory: Memory? = null,
    val uptime: String? = null,
    val foreground: String? = null,
    @SerialName("foreground_label") val foregroundLabel: String? = null,
    val screen: String? = null,
    @SerialName("ping_ms") val pingMs: Double? = null,
    val error: String? = null,
)

@Serializable
data class DiagnoseStep(val step: String, val ok: Boolean? = null, val detail: String = "", val hint: String = "")

@Serializable
data class Diagnosis(val steps: List<DiagnoseStep> = emptyList())

@Serializable
data class Reconnect(val ok: Boolean = false, val host: String? = null, val error: String? = null)

/** GET /v1/updates: what the agent found of newer releases (server, panel, TV app, this app). */
@Serializable
data class UpdatesInfo(
    val checked: Double? = null,
    val error: String? = null,
    val mode: String? = null,
    val components: Map<String, ComponentUpdate> = emptyMap(),
)

@Serializable
data class ComponentUpdate(
    val current: String? = null,
    val latest: String? = null,
    val newer: Boolean? = null,
    val url: String? = null,
)

@Serializable
data class Speed(val mbps: Double? = null, val error: String? = null)
