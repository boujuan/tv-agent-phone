package com.boujuan.tvagent.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** GET /v1/settings as the agent answers it now (TV Agent Player, the episode check, its updates mode). */
class SettingsTest {
    @Test
    fun `the agent's settings and choices`() {
        val r = json.decodeFromString(SettingsResponse.serializer(), """{"settings": {"player": "tvplayer",
            "max_height": 2160, "max_mbps": 0.0, "probe_streams": true, "binge": true, "auto_skip": true,
            "audio_language": "eng", "subtitle_languages": ["eng", "spa"], "native_language": "spa",
            "twitch_favorites": [], "log_transcripts": true, "updates": "auto", "check_episodes": false},
            "choices": {"player": ["tvplayer", "kodi", "just"], "max_height": [2160, 1440, 1080, 720],
            "audio": [{"code": "eng", "label": "English"}], "subtitles": [{"code": "spa", "label": "Spanish"}]}}""")
        assertEquals("tvplayer", r.settings.player)
        assertEquals("auto", r.settings.updates)
        assertFalse(r.settings.checkEpisodes)
        assertEquals(listOf("tvplayer", "kodi", "just"), r.choices.player)
    }

    @Test
    fun `an older agent without them`() {
        val r = json.decodeFromString(SettingsResponse.serializer(), """{"settings": {"player": "kodi"}, "choices": {}}""")
        assertEquals("notify", r.settings.updates)
        assertEquals(true, r.settings.checkEpisodes)
    }
}
