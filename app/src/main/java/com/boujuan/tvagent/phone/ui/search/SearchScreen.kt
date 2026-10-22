package com.boujuan.tvagent.phone.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.CommandResult
import com.boujuan.tvagent.phone.data.ListItem
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.RemoteImage
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.rememberTap
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val result = MutableStateFlow<CommandResult?>(null)
    val running = MutableStateFlow(false)
    val lists = MutableStateFlow<Map<String, List<ListItem>>>(emptyMap())
    val loading = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch { repo.current.collect { lists.value = emptyMap(); result.value = null } }
    }

    /** Decide and do it, as if said: "the bear season 3", "play dune", "what's new?". */
    fun send(text: String) = call(touch = true) {
        running.value = true
        try {
            result.value = it.text(text)
        } finally {
            running.value = false
        }
    }

    fun load(list: String, again: Boolean = false) {
        if (!again && list in lists.value || list in loading.value) return
        call {
            loading.value += list
            try {
                lists.value += list to it.list(list).items
            } finally {
                loading.value -= list
            }
        }
    }

    fun pick(list: String, item: ListItem) = call(touch = true) { tell(it.pick(list, item).say) }
}

private val LISTS = listOf("recent" to R.string.list_recent, "new" to R.string.list_new, "movies" to R.string.list_movies)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val vm = appViewModel { SearchViewModel(it.repo) }
    Messages(vm)
    val result by vm.result.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val tap = rememberTap()
    var text by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    val list = LISTS[tab].first
    LaunchedEffect(list) { vm.load(list) }
    val send = { if (text.isNotBlank()) { tap(); vm.send(text.trim()) } }

    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_label)) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { send() }),
                trailingIcon = {
                    if (running) CircularProgressIndicator(Modifier.width(24.dp), strokeWidth = 2.dp)
                    else FilledIconButton(onClick = send) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
                },
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in listOf(R.string.suggest_continue, R.string.suggest_next, R.string.suggest_whats_new,
                    R.string.suggest_new_films, R.string.suggest_skip_intro)) {
                    val phrase = stringResource(s)
                    SuggestionChip(onClick = { text = phrase; tap(); vm.send(phrase) }, label = { Text(phrase) })
                }
            }
        }
        result?.let { r -> item { ResultCard(r) } }
        item {
            SecondaryTabRow(selectedTabIndex = tab) {
                LISTS.forEachIndexed { i, (_, label) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
        }
        val items = lists[list]
        when {
            list in loading && items == null -> item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
            items.isNullOrEmpty() -> item {
                Text(stringResource(R.string.list_empty), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(items, key = { it.imdb + (it.video ?: "") }) { item ->
                ListRow(item) { tap(); vm.pick(list, item) }
            }
        }
    }
}

@Composable
private fun ResultCard(r: CommandResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.describe.ifEmpty { r.heard }, style = MaterialTheme.typography.titleMedium)
            Text(r.say, color = if (r.ok) Green else Red, fontWeight = FontWeight.Medium)
            if (r.via.isNotEmpty()) {
                Text(stringResource(R.string.decided_by, r.via, r.rule), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ListRow(item: ListItem, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(item.poster, Modifier.width(56.dp).aspectRatio(2f / 3).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f)) {
                Text(item.label.ifEmpty { item.name }, style = MaterialTheme.typography.titleMedium)
                if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${item.number}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
