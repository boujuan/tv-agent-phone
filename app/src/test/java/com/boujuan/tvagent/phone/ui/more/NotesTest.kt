package com.boujuan.tvagent.phone.ui.more

import org.junit.Assert.assertEquals
import org.junit.Test

/** A release's notes (the CHANGELOG section) as the About screen shows them. */
class NotesTest {
    @Test
    fun `wrapped lines are joined, headings and bullets read as text`() {
        val md = """
            Works with any server.

            ### Added
            - **More › About**: versions, the
              licence and *links*.
            - `X-App-Version` on every call.
        """.trimIndent()
        assertEquals(
            "Works with any server.\n\nAdded\n• More › About: versions, the licence and links.\n• X-App-Version on every call.",
            notes(md).text,
        )
    }
}
