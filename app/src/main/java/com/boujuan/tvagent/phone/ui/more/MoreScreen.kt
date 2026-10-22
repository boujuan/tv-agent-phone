package com.boujuan.tvagent.phone.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.boujuan.tvagent.phone.BuildConfig
import com.boujuan.tvagent.phone.R

private data class Entry(val route: String, val title: Int, val detail: Int, val icon: ImageVector)

private val ENTRIES = listOf(
    Entry("overview", R.string.overview, R.string.overview_detail, Icons.Filled.Dashboard),
    Entry("decisions", R.string.decisions, R.string.decisions_detail, Icons.Filled.Psychology),
    Entry("settings", R.string.agent_settings, R.string.agent_settings_detail, Icons.Filled.Tune),
    Entry("agents", R.string.agents, R.string.agents_detail, Icons.Filled.Computer),
    Entry("app", R.string.app_settings, R.string.app_settings_detail, Icons.Filled.Palette),
)

@Composable
fun MoreScreen(open: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ENTRIES.forEach { e ->
            ListItem(
                headlineContent = { Text(stringResource(e.title)) },
                supportingContent = { Text(stringResource(e.detail)) },
                leadingContent = { Icon(e.icon, contentDescription = null) },
                modifier = Modifier.clickable { open(e.route) },
            )
            HorizontalDivider()
        }
        Text(stringResource(R.string.about_line, BuildConfig.VERSION_NAME), Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
