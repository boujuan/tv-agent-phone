package com.boujuan.tvagent.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.ApiException
import com.boujuan.tvagent.phone.data.ShownItem
import com.boujuan.tvagent.phone.data.ShownList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The numbered list the TV shows ("number 2" or "sí" picks: suggestions, a title to make sure of, the episodes or
 * moments found, "go there?"), here too while the app is open, a tap picking as saying the number does.
 */
class ShownViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val list = MutableStateFlow<ShownList?>(null)
    private var unsupported = false              // an agent without GET /v1/shown: not asked again
    private var closed: Long? = null             // the list closed or picked here: not shown again

    init {
        viewModelScope.launch {
            repo.current.map { it?.agent?.id }.distinctUntilChanged().collect { unsupported = false; list.value = null }
        }
        viewModelScope.launch { repo.changed.collect { delay(1_500); refresh() } }     // a command may have shown one
    }

    fun refresh() = viewModelScope.launch {
        val api = api ?: return@launch
        if (unsupported) return@launch
        try {
            list.value = api.shown()?.takeIf { it.id != closed && it.items.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.code == 404) unsupported = true
        } catch (_: Exception) {
            // not reachable now: the screens say so
        }
    }

    fun close() {
        closed = list.value?.id
        list.value = null
    }

    fun pick(item: ShownItem) = call(touch = true) { api ->
        val shown = list.value ?: return@call
        close()
        try {
            tell(api.pickShown(item.number, shown.id).say)
        } catch (e: ApiException) {
            if (e.code != 404 && e.code != 409) throw e
            tell(R.string.shown_gone)                // the TV moved on (another list, or it played)
            closed = null
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShownSheet() {
    val vm = appViewModel { ShownViewModel(it.repo) }
    Messages(vm)
    WhileVisible(3_000) { vm.refresh() }
    val shown by vm.list.collectAsStateWithLifecycle()
    val tap = rememberTap()
    val list = shown ?: return
    ModalBottomSheet(onDismissRequest = { vm.close() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(list.title.ifEmpty { stringResource(R.string.shown_title) }, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.shown_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(list.items, key = { it.number }) { item -> ShownRow(item) { tap(); vm.pick(item) } }
            }
        }
    }
}

@Composable
private fun ShownRow(item: ShownItem, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(item.number.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        }
        if (!item.poster.isNullOrEmpty()) {
            RemoteImage(item.poster, Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(6.dp)))
        }
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.detail.isNotEmpty()) {
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
