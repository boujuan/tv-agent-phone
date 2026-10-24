package com.boujuan.tvagent.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** GET /v1/pipeline's vocabulary: the language hint's own list when it has one (as the agent sends it), else the no-hint list. */
class PipelineTest {
    private fun pipeline(extra: String) = json.decodeFromString(Pipeline.serializer(),
        """{"stt_vocabulary": ["Severance", "Ibai"], $extra}""")

    @Test
    fun `the hint in use has its own list`() {
        val p = pipeline(""""stt_vocabularies": {"es": ["La casa de papel"]}, "stt_vocabulary_language": "es"""")
        assertEquals(listOf("La casa de papel"), p.ownVocabulary)
    }

    @Test
    fun `a hint without its own list, no hint, or an older agent use the no-hint list`() {
        assertEquals(listOf("Severance", "Ibai"), pipeline(""""stt_vocabularies": {"es": ["X"]}, "stt_vocabulary_language": "en"""").ownVocabulary)
        assertEquals(listOf("Severance", "Ibai"), pipeline(""""stt_vocabularies": {"es": ["X"]}, "stt_vocabulary_language": """"").ownVocabulary)
        assertEquals(listOf("Severance", "Ibai"), pipeline(""""stt_language": "native"""").ownVocabulary)
    }
}
