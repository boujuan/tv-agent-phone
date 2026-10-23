package com.boujuan.tvagent.phone.ui.decisions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.CommandResult
import com.boujuan.tvagent.phone.data.Learned
import com.boujuan.tvagent.phone.data.Pipeline
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Choice
import com.boujuan.tvagent.phone.ui.ModelPicker
import com.boujuan.tvagent.phone.ui.Toggle
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.LabeledValue
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.theme.Amber
import com.boujuan.tvagent.phone.ui.theme.Green
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DecisionsViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val pipeline = MutableStateFlow<Pipeline?>(null)
    val learned = MutableStateFlow<List<Learned>>(emptyList())
    val tried = MutableStateFlow<CommandResult?>(null)

    init {
        viewModelScope.launch { repo.current.collect { pipeline.value = null; load() } }
    }

    fun load() = call {
        pipeline.value = it.pipeline()
        learned.value = it.learned()
    }

    /** One change to the decision steps or speech-to-text; the agent checks it, saves it and uses it at once. */
    fun change(body: JsonObject) = call { pipeline.value = it.setPipeline(body); tell(R.string.saved) }

    fun move(step: String, by: Int) {
        val steps = pipeline.value?.steps?.toMutableList() ?: return
        val i = steps.indexOf(step)
        val j = i + by
        if (i < 0 || j !in steps.indices) return
        steps[i] = steps[j].also { steps[j] = step }
        change(buildJsonObject { put("steps", buildJsonArray { steps.forEach { add(JsonPrimitive(it)) } }) })
    }

    fun toggle(step: String, on: Boolean) {
        val off = pipeline.value?.off?.toMutableSet() ?: return
        if (on) off -= step else off += step
        change(buildJsonObject { put("off", buildJsonArray { off.forEach { add(JsonPrimitive(it)) } }) })
    }

    fun unlearn(l: Learned) = call { it.unlearn(l.say); learned.value = it.learned() }
    fun tryOut(text: String) = call { tried.value = it.decide(text) }
}

private val WHERE = listOf("local" to R.string.where_local, "hosted" to R.string.where_hosted,
    "local-first" to R.string.where_local_first, "hosted-first" to R.string.where_hosted_first)
private val REASONING = listOf("none", "minimal", "low", "medium", "high")

@Composable
private fun stepName(step: String) = stringResource(
    when (step) {
        "phrases" -> R.string.step_phrases
        "patterns" -> R.string.step_patterns
        "llm" -> R.string.step_llm
        else -> R.string.step_d1
    }
)

@Composable
fun DecisionsScreen(onVocabulary: () -> Unit) {
    val vm = appViewModel { DecisionsViewModel(it.repo) }
    Messages(vm)
    val p by vm.pipeline.collectAsStateWithLifecycle()
    val learned by vm.learned.collectAsStateWithLifecycle()
    val tried by vm.tried.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val pipe = p ?: run { Waiting(vm) { vm.load() }; return@Column }
        SectionCard(stringResource(R.string.steps)) {
            Text(stringResource(R.string.steps_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            pipe.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${stepName(step)}", Modifier.weight(1f))
                    IconButton(onClick = { vm.move(step, -1) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = { vm.move(step, 1) }, enabled = i < pipe.steps.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                    }
                    Switch(checked = step !in pipe.off, onCheckedChange = { vm.toggle(step, it) })
                }
            }
            LabeledValue(stringResource(R.string.rules_counts), pipe.counts.entries.joinToString(", ") { "${it.value} ${it.key}" })
        }
        SectionCard(stringResource(R.string.step_llm)) {
            KeyState(pipe.keys.openrouter, "OpenRouter")
            ModelPicker(stringResource(R.string.model), pipe.llmModel, LLM_MODELS) { vm.change(buildJsonObject { put("llm_model", it) }) }
            Choice(stringResource(R.string.reasoning), REASONING.map { it to it }, pipe.llmReasoning.ifEmpty { "none" }) {
                vm.change(buildJsonObject { put("llm_reasoning", it) })
            }
            Toggle(stringResource(R.string.llm_private), pipe.llmPrivate) { vm.change(buildJsonObject { put("llm_private", it) }) }
        }
        SectionCard(stringResource(R.string.step_d1)) {
            KeyState(pipe.keys.liquid || pipe.keys.hostedD1, "Liquid / OpenRouter")
            Choice(stringResource(R.string.where_runs), WHERE.map { it.first to stringResource(it.second) }, pipe.d1Where) {
                vm.change(buildJsonObject { put("d1_where", it) })
            }
            ModelPicker(stringResource(R.string.hosted_model), pipe.hostedD1Model, DECISION_MODELS) {
                vm.change(buildJsonObject { put("hosted_d1_model", it) })
            }
            LabeledValue(stringResource(R.string.local_model), pipe.d1Model.substringAfterLast('/'))
        }
        SectionCard(stringResource(R.string.speech_to_text)) {
            Choice(stringResource(R.string.where_runs), WHERE.map { it.first to stringResource(it.second) }, pipe.sttWhere) {
                vm.change(buildJsonObject { put("stt_where", it) })
            }
            ModelPicker(stringResource(R.string.hosted_model), pipe.sttHostedModel, STT_MODELS) {
                vm.change(buildJsonObject { put("stt_hosted_model", it) })
            }
            LabeledValue(stringResource(R.string.local_model), pipe.sttModel)     // a laptop path: the agent refuses it from phones
            Toggle(stringResource(R.string.stt_loaded), pipe.sttLoaded) { vm.change(buildJsonObject { put("stt_loaded", it) }) }
            Toggle(stringResource(R.string.keep_recordings), pipe.keepRecordings) { vm.change(buildJsonObject { put("keep_recordings", it) }) }
            val native = stringResource(R.string.stt_language_native, pipe.sttLanguageNow)
            Choice(stringResource(R.string.stt_language), listOf("native" to native, "" to stringResource(R.string.stt_language_guess),
                "es" to "Español (es)", "en" to "English (en)"), pipe.sttLanguage) {
                vm.change(buildJsonObject { put("stt_language", it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stt_vocabulary))
                    Text(pipe.sttVocabulary.take(4).joinToString(", ") + if (pipe.sttVocabulary.size > 4) "…" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                OutlinedButton(onClick = onVocabulary) { Text(stringResource(R.string.vocabulary_edit, pipe.sttVocabulary.size)) }
            }
            Toggle(stringResource(R.string.stt_vocabulary_library), pipe.sttVocabularyLibrary) {
                vm.change(buildJsonObject { put("stt_vocabulary_library", it) })
            }
            Text(stringResource(R.string.stt_vocabulary_hint, pipe.sttVocabularyCount), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.laptop_only), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.try_sentence)) {
            var text by rememberSaveable { mutableStateOf("") }
            val keyboard = LocalSoftwareKeyboardController.current     // the answer shows under the field
            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sentence)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),     // the button is under the keyboard
                keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) { keyboard?.hide(); vm.tryOut(text) } }))
            OutlinedButton(onClick = { vm.tryOut(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.decide_only)) }
            tried?.let { t ->
                Text(t.describe, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.decided_by, t.via, t.rule) + " · %.2f".format(t.confidence),
                    style = MaterialTheme.typography.bodySmall)
                t.action?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        SectionCard(stringResource(R.string.taught_sentences, learned.size)) {
            if (learned.isEmpty()) Text(stringResource(R.string.taught_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            learned.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("“${l.say}”")
                        Text(l.`do`.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.unlearn(l) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
                }
            }
        }
    }
}

@Composable
private fun KeyState(set: Boolean, name: String) {
    Text(if (set) stringResource(R.string.key_set, name) else stringResource(R.string.key_missing, name),
        color = if (set) Green else Amber, style = MaterialTheme.typography.bodySmall)
}
