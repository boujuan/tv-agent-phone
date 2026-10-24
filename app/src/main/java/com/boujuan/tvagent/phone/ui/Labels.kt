package com.boujuan.tvagent.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boujuan.tvagent.phone.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Names for what the agent sends as codes (its action types, how a command was decided, the latency stages, the
 * players, the languages), in the app's language and as the panel names them. A code the app doesn't know yet (a
 * newer agent) shows as itself.
 */

private val ACTIONS = mapOf(
    "play_title" to R.string.act_play_title, "continue_watching" to R.string.continue_watching,
    "next_episode" to R.string.next_episode, "open_in_stremio" to R.string.act_stremio,
    "open_app" to R.string.act_open_app, "home" to R.string.key_home, "transport" to R.string.act_transport,
    "volume" to R.string.volume, "binge" to R.string.act_binge, "timer" to R.string.timer,
    "tracks" to R.string.tracks, "replay" to R.string.replay, "show_list" to R.string.act_show_list,
    "recommend" to R.string.act_recommend, "pick" to R.string.act_pick, "library" to R.string.act_library,
    "skip" to R.string.skip, "auto_skip" to R.string.act_auto_skip, "help" to R.string.act_help,
    "find_scene" to R.string.act_find_scene, "info" to R.string.act_info, "wrong" to R.string.act_wrong,
    "cancel" to R.string.cancel, "none" to R.string.act_none,
)

private fun str(o: JsonObject?, key: String) = runCatching { o?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()

/** What an action does, in a few words ("Find an episode", "A few in one go: Play a title → Playback"). */
@Composable
fun actionLabel(action: JsonObject?, type: String = ""): String {
    val t = str(action, "type") ?: type
    return when {
        t == "play_title" && !str(action, "episode_name").isNullOrEmpty() -> stringResource(R.string.act_find_episode)
        t == "next_episode" && (runCatching { action?.get("offset")?.jsonPrimitive?.intOrNull }.getOrNull() ?: 1) < 0 ->
            stringResource(R.string.previous_episode)
        t == "tracks" && runCatching { action?.get("keep")?.jsonPrimitive?.booleanOrNull }.getOrNull() == true ->
            stringResource(R.string.act_language_settings)
        t == "youtube" -> "YouTube"
        t == "twitch" -> "Twitch"
        t == "sequence" -> {
            val steps = (action?.get("steps") as? JsonArray).orEmpty().map { actionLabel(it as? JsonObject) }
            stringResource(R.string.act_sequence, steps.joinToString(" → "))
        }
        else -> ACTIONS[t]?.let { stringResource(it) } ?: t
    }
}

private val VIA = mapOf(
    "phrase" to R.string.via_phrase, "pattern" to R.string.via_pattern, "llm" to R.string.via_llm,
    "d1" to R.string.step_d1, "chain" to R.string.via_chain, "remote" to R.string.via_remote, "none" to R.string.via_none,
)

/** How a command was decided: a fixed phrase, a pattern rule, the LLM, the decision model… */
@Composable
fun viaLabel(via: String): String = VIA[via]?.let { stringResource(it) } ?: via

/** "Decided by: Pattern rule · continue" (the rule or model after it, when there's one). */
@Composable
fun decidedBy(via: String, rule: String): String =
    if (rule.isEmpty()) stringResource(R.string.decided_by_only, viaLabel(via)) else stringResource(R.string.decided_by, viaLabel(via), rule)

/** A latency row's stage: "stt", "endpoint", "decide:llm", "execute:play_title", "start:first_frame"… */
@Composable
fun stageLabel(key: String): String {
    val (head, rest) = key.substringBefore(':') to key.substringAfter(':', "")
    return when (head) {
        "stt" -> stringResource(R.string.speech_to_text)
        "endpoint" -> stringResource(R.string.stage_endpoint)
        "decide" -> stringResource(R.string.stage_decide, viaLabel(rest))
        "execute" -> stringResource(R.string.stage_execute, actionLabel(null, rest))
        "start" -> when (rest) {
            "title" -> stringResource(R.string.stage_start_title)
            "first_frame" -> stringResource(R.string.stage_start_first_frame)
            else -> stringResource(R.string.stage_start, rest)
        }
        else -> key
    }
}

/** The player's name: TV Agent Player, Kodi, Just Player. */
@Composable
fun playerName(app: String): String = when (app) {
    "tvplayer" -> stringResource(R.string.tv_player)
    "kodi" -> "Kodi"
    "just" -> "Just Player"
    else -> app
}

private val LANGUAGES = mapOf(
    "eng" to R.string.lang_eng, "spa" to R.string.lang_spa, "fre" to R.string.lang_fre, "ita" to R.string.lang_ita,
    "ger" to R.string.lang_ger, "por" to R.string.lang_por, "jpn" to R.string.lang_jpn, "kor" to R.string.lang_kor,
    "chi" to R.string.lang_chi, "chs" to R.string.lang_chs, "cht" to R.string.lang_cht,
)

/** A language the agent names by its code, in the app's language ("Español"); else the agent's own label. */
@Composable
fun languageName(code: String, agentLabel: String = code): String = LANGUAGES[code]?.let { stringResource(it) } ?: agentLabel

private val METHODS = mapOf(
    "rule" to R.string.method_rule, "stt" to R.string.method_stt, "addon" to R.string.method_addon,
    "cache" to R.string.method_cache, "player" to R.string.method_player,
)

/** How a step of a command's trace was done (tracing.py's method kinds): rule, LLM, d1, speech, add-on… */
@Composable
fun methodLabel(kind: String): String = METHODS[kind]?.let { stringResource(it) } ?: kind.uppercase().takeIf { kind == "llm" } ?: kind
