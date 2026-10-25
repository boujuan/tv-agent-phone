package com.boujuan.tvagent.phone.ui.search

import com.boujuan.tvagent.phone.data.HelpCommand
import com.boujuan.tvagent.phone.data.HelpResponse

/**
 * The quick commands Search offers, taken from what the agent says it understands (GET /v1/help), so they follow
 * it: one command of each kind people reach for (a language now and from now on, a chain, an episode described, a
 * scene, skips, the next episode, a number from the TV's list, cancel…), alternating the two languages.
 */
object Suggestions {
    /** Which commands, by what they do: (type, which of that type's commands that have a ‹placeholder›, from the end). */
    private val KINDS = listOf(
        Pick("tracks", placeholder = true, fromEnd = 1),        // audio in ‹language›
        Pick("tracks", placeholder = true, fromEnd = 0),        // from now on, audio in ‹language›
        Pick("sequence"),
        Pick("play_title", described = true),                   // the episode of ‹title› where ‹moment›
        Pick("find_scene"),
        Pick("skip"),
        Pick("next_episode"),
        Pick("pick"),
        Pick("replay"),
        Pick("recommend"),
        Pick("cancel"),
    )

    /** described: a placeholder besides the title and numbers (‹moment›): what's in it is described, not named. */
    private data class Pick(val type: String, val described: Boolean = false, val placeholder: Boolean = false, val fromEnd: Int? = null)

    private val NAMED = setOf("title", "título", "n")
    private fun placeholders(say: String) = Regex("‹([^›]+)›").findAll(say).map { it.groupValues[1] }.toList()

    /** (section, command) of each kind in [help], in KINDS' order; a kind the agent doesn't have is left out. */
    fun indices(help: HelpResponse): List<Pair<Int, Int>> {
        val all = help.sections.flatMapIndexed { s, section -> section.commands.mapIndexed { c, cmd -> Triple(s, c, cmd) } }
        val out = mutableListOf<Pair<Int, Int>>()
        for (k in KINDS) {
            val matching = all.filter { (_, _, cmd) ->
                cmd.type == k.type && (!k.placeholder || '‹' in cmd.say) &&
                    (!k.described || placeholders(cmd.say).any { it !in NAMED })
            }
            val hit = if (k.fromEnd != null) matching.getOrNull(matching.size - 1 - k.fromEnd) else matching.firstOrNull()
            hit?.let { (s, c, _) -> (s to c).takeIf { it !in out }?.let(out::add) }
        }
        return out
    }

    /** The commands to offer, in [first] then [other] in turn (the same command in each, where both are there). */
    fun mixed(first: HelpResponse?, other: HelpResponse?): List<HelpCommand> {
        val main = first ?: other ?: return emptyList()
        return indices(main).mapIndexed { i, (s, c) ->
            val from = if (i % 2 == 1 && other != null && first != null) other else main
            from.sections.getOrNull(s)?.commands?.getOrNull(c) ?: main.sections[s].commands[c]
        }
    }

    /** Where to put the cursor in an example: over its first ‹placeholder› (the title to type), or null to send it. */
    fun placeholder(example: String): IntRange? {
        val start = example.indexOf('‹')
        val end = example.indexOf('›', start + 1)
        return if (start >= 0 && end > start) start..end else null
    }
}
