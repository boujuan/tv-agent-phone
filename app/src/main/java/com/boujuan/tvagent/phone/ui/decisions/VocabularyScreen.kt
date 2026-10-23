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
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** The names hosted speech-to-text should hear as written (stt_vocabulary): each add or remove is saved at once. */
class VocabularyViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val words = MutableStateFlow<List<String>?>(null)
    val library = MutableStateFlow(false)
    val total = MutableStateFlow(0)

    init {
        viewModelScope.launch { repo.current.collect { words.value = null; load() } }
    }

    fun load() = call { show(it.pipeline()) }

    private fun show(p: com.boujuan.tvagent.phone.data.Pipeline) {
        words.value = p.sttVocabulary
        library.value = p.sttVocabularyLibrary
        total.value = p.sttVocabularyCount
    }

    /** The whole list, as the agent keeps it (it trims, drops repeats and keeps up to 500). */
    private fun save(list: List<String>) = call {
        show(it.setPipeline(buildJsonObject { put("stt_vocabulary", buildJsonArray { list.forEach { w -> add(JsonPrimitive(w)) } }) }))
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
fun VocabularyScreen() {
    val vm = appViewModel { VocabularyViewModel(it.repo) }
    Messages(vm)
    val words by vm.words.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
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
            Text(stringResource(R.string.vocabulary_count, words?.size ?: 0, total) +
                (if (library) " " + stringResource(R.string.vocabulary_plus_library) else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        val list = words
        if (list == null) {
            Column(Modifier.padding(16.dp)) { Waiting(vm) { vm.load() } }
            return@Column
        }
        // Typing filters the list too, so a name already there shows up before it's added twice.
        val shown = if (text.isBlank()) list else list.filter { it.contains(text.trim(), ignoreCase = true) }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
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
        }
    }
}
