package com.boujuan.tvagent.phone.ui.search

import com.boujuan.tvagent.phone.data.HelpCommand
import com.boujuan.tvagent.phone.data.HelpResponse
import com.boujuan.tvagent.phone.data.HelpSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Search's quick commands, from the agent's help: one of each kind, the two languages in turn. */
class SuggestionsTest {
    private fun help(lang: String) = HelpResponse(lang, sections = listOf(
        HelpSection("films", listOf(
            HelpCommand(if (lang == "es") "pon ‹título›" else "play ‹title›", type = "play_title", example = "x"),
            HelpCommand("ep", type = "play_title", by = "llm", example = if (lang == "es") "pon el capítulo de ‹título› en el que se besan" else "play the episode of ‹title› where they kiss"),
            HelpCommand("next", type = "next_episode", example = if (lang == "es") "siguiente capítulo" else "next episode"))),
        HelpSection("tracks", listOf(
            HelpCommand("subtitles on/off", type = "tracks", example = "subtitles on"),
            HelpCommand("‹language› subtitles", type = "tracks", example = "spanish subtitles"),
            HelpCommand("audio in ‹language›", type = "tracks", example = if (lang == "es") "ponlo en inglés" else "audio in Spanish"),
            HelpCommand("from now on, audio in ‹language›", type = "tracks",
                example = if (lang == "es") "a partir de ahora, audio en inglés" else "from now on, audio in Spanish"))),
        HelpSection("chains", listOf(
            HelpCommand("play ‹title› and go to ‹time›", type = "sequence",
                example = if (lang == "es") "pon ‹título› y ve al minuto tres" else "play ‹title› and go to twenty minutes"),
            HelpCommand("cancel", type = "cancel", example = if (lang == "es") "cancela" else "cancel"))),
    ))

    @Test
    fun `one of each kind, in turn in each language`() {
        val got = Suggestions.mixed(help("es"), help("en")).map { it.example }
        assertEquals(listOf("ponlo en inglés", "from now on, audio in Spanish", "pon ‹título› y ve al minuto tres",
            "play the episode of ‹title› where they kiss", "siguiente capítulo", "cancel"), got)
    }

    @Test
    fun `one language only, or none`() {
        assertEquals(6, Suggestions.mixed(help("en"), null).size)
        assertEquals(emptyList<HelpCommand>(), Suggestions.mixed(null, null))
    }

    @Test
    fun `the title to type is selected, a whole command is sent`() {
        assertEquals(4..11, Suggestions.placeholder("pon ‹título› y ve al minuto tres"))
        assertNull(Suggestions.placeholder("siguiente capítulo"))
    }
}
