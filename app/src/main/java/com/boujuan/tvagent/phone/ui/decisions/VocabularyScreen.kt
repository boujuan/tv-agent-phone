package com.boujuan.tvagent.phone.ui.decisions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.Toggle
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.boujuan.tvagent.phone.data.ownVocabulary
import androidx.compose.runtime.remember

/**
 * The names hosted speech-to-text should hear as written: the list of the language hint in use (stt_vocabularies,
 * as the panel saves it), else the no-hint one (stt_vocabulary). Each add or remove is saved at once.
 */
class VocabularyViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val words = MutableStateFlow<List<String>?>(null)
    val library = MutableStateFlow(false)
    val total = MutableStateFlow(0)
    /** What else goes with each command: Twitch favourites and library titles (null: an agent too old to say). */
    val alsoSent = MutableStateFlow<List<String>?>(null)
    /** The hint whose list this is ("es"; "" for none), and whether it has a list of its own yet. */
    val hint = MutableStateFlow("")
    val hintHasOwn = MutableStateFlow(false)

    init {
        viewModelScope.launch { repo.current.collect { words.value = null; load() } }
    }

    fun load() = call { show(it.pipeline()) }

    private fun show(p: com.boujuan.tvagent.phone.data.Pipeline) {
        val own = p.ownVocabulary
        words.value = own
        hint.value = p.sttVocabularyLanguage.orEmpty()
        hintHasOwn.value = hint.value.isNotEmpty() && hint.value in p.sttVocabularies
        library.value = p.sttVocabularyLibrary
        total.value = p.sttVocabularyCount
        val yours = own.map { it.lowercase() }.toSet()
        alsoSent.value = p.sttVocabularySent?.filter { it.lowercase() !in yours }
    }

    /** The library's titles (and Twitch favourites) with each command, or not. Switched on, the agent reads the
     * library in the background, so the list is asked for again a moment later. */
    fun setLibrary(on: Boolean) = call {
        show(it.setPipeline(buildJsonObject { put("stt_vocabulary_library", on) }))
        if (on) {
            delay(3000)
            show(it.pipeline())
        }
    }

    /** The whole list, as the agent keeps it (it trims, drops repeats and keeps up to 500): the hint's own list when a
     * hint is in use (made from the no-hint names on the first change, as the panel does), else the no-hint one. */
    private fun save(list: List<String>) = call {
        val names = buildJsonArray { list.forEach { w -> add(JsonPrimitive(w)) } }
        val lang = hint.value
        show(it.setPipeline(buildJsonObject {
            if (lang.isEmpty()) put("stt_vocabulary", names) else put("stt_vocabularies", buildJsonObject { put(lang, names) })
        }))
    }

    fun add(word: String) {
        val w = word.trim()
        val now = words.value ?: return
        if (w.isEmpty() || now.any { it.equals(w, ignoreCase = true) }) return
        save(listOf(w) + now)
    }

    fun remove(word: String) = words.value?.let { save(it - word) }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun VocabularyScreen() {
    val vm = appViewModel { VocabularyViewModel(it.repo) }
    Messages(vm)
    val words by vm.words.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val alsoSent by vm.alsoSent.collectAsStateWithLifecycle()
    val hint by vm.hint.collectAsStateWithLifecycle()
    val hintHasOwn by vm.hintHasOwn.collectAsStateWithLifecycle()
    val hintName = remember(hint) { java.util.Locale.forLanguageTag(hint).getDisplayLanguage(java.util.Locale.getDefault()) }
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var added by rememberSaveable { mutableStateOf<String?>(null) }
    val add = { added = text.trim(); vm.add(text); text = "" }
    // A new name goes first: scroll up to it once the agent has saved it (the list otherwise keeps its place)
    LaunchedEffect(words) { if (added != null && words?.firstOrNull() == added) { listState.animateScrollToItem(0); added = null } }
    Column {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vocabulary_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.vocabulary_add)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }))
                FilledIconButton(onClick = add, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
            }
            Toggle(stringResource(R.string.stt_vocabulary_library), library) { vm.setLibrary(it) }
            Text(stringResource(R.string.vocabulary_count, words?.size ?: 0, total),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        val list = words
        if (list == null) {
            Column(Modifier.padding(16.dp)) { Waiting(vm) { vm.load() } }
            return@Column
        }
        // Typing filters the lists too, so a name already there shows up before it's added twice.
        fun matching(names: List<String>) = if (text.isBlank()) names else names.filter { it.contains(text.trim(), ignoreCase = true) }
        val shown = matching(list)
        val more = alsoSent?.let { matching(it) }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SectionHeader(when {
                    hint.isEmpty() -> stringResource(R.string.vocabulary_no_hint, list.size)
                    else -> stringResource(R.string.vocabulary_for_hint, hintName, list.size)
                })
                if (hint.isNotEmpty() && !hintHasOwn) {
                    Text(stringResource(R.string.vocabulary_hint_shared, hintName), Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(shown, key = { it }) { w ->
                ListItem(
                    headlineContent = { Text(w) },
                    trailingContent = {
                        IconButton(onClick = { vm.remove(w) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
                    },
                )
                HorizontalDivider()
            }
            if (shown.isEmpty()) item {
                Text(stringResource(if (list.isEmpty()) R.string.vocabulary_empty else R.string.vocabulary_none_match),
                    Modifier.padding(16.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The rest of what's sent: read-only here (Twitch favourites are in Settings, titles come from the library)
            if (library) {
                item { SectionHeader(stringResource(R.string.vocabulary_also, alsoSent?.size ?: 0)) }
                when {
                    more == null -> item {
                        Text(stringResource(R.string.vocabulary_also_unknown), Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    more.isEmpty() -> item {
                        Text(stringResource(R.string.vocabulary_also_none), Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> items(more, key = { "also-$it" }) { w ->
                        ListItem(headlineContent = { Text(w, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
