package com.boujuan.tvagent.phone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.PairingLink
import com.boujuan.tvagent.phone.ui.activity.ActivityScreen
import com.boujuan.tvagent.phone.ui.agents.AgentsScreen
import com.boujuan.tvagent.phone.ui.agents.AppPrefsScreen
import com.boujuan.tvagent.phone.ui.agents.PairScreen
import com.boujuan.tvagent.phone.ui.decisions.DecisionsScreen
import com.boujuan.tvagent.phone.ui.decisions.VocabularyScreen
import com.boujuan.tvagent.phone.ui.more.AboutScreen
import com.boujuan.tvagent.phone.ui.more.MoreScreen
import com.boujuan.tvagent.phone.ui.overview.OverviewScreen
import com.boujuan.tvagent.phone.ui.playing.NowPlayingScreen
import com.boujuan.tvagent.phone.ui.remote.RemoteScreen
import com.boujuan.tvagent.phone.ui.search.SearchScreen
import com.boujuan.tvagent.phone.ui.settings.SettingsScreen
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import com.boujuan.tvagent.phone.ui.voice.VoiceButton
import com.boujuan.tvagent.phone.ui.voice.VoicePanel
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("remote", R.string.tab_remote, Icons.Filled.SettingsRemote),
    Tab("playing", R.string.tab_playing, Icons.Filled.PlayCircle),
    Tab("search", R.string.tab_search, Icons.Filled.Search),
    Tab("activity", R.string.tab_activity, Icons.Filled.History),
    Tab("more", R.string.tab_more, Icons.Filled.Menu),
)
private val SUB = mapOf(
    "overview" to R.string.overview, "decisions" to R.string.decisions, "settings" to R.string.agent_settings,
    "agents" to R.string.agents, "app" to R.string.app_settings, "pair" to R.string.pair_title,
    "vocabulary" to R.string.vocabulary_title, "about" to R.string.about,
)

@Composable
fun AppRoot(pairingLink: PairingLink?, onLinkUsed: () -> Unit, open: String? = null, onOpened: () -> Unit = {}) {
    val repo = LocalContext.current.app.repo
    val agents by repo.agents.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        when {
            agents == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            agents!!.isEmpty() || pairingLink != null -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
                    LocalNetworkNotice()
                    PairScreen(link = pairingLink, onDone = onLinkUsed, onCancel = if (agents!!.isEmpty()) null else onLinkUsed)
                }
            }
            else -> Main(snackbar, open, onOpened)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(snackbar: SnackbarHostState, open: String?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(open) { if (open != null) { nav.navigate(open); onOpened() } }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "remote"
    val isTab = TABS.any { it.route == route }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (isTab) AgentSwitcher(nav) else Text(stringResource(SUB[route] ?: R.string.app_name)) },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                val tabNow = if (isTab) route else "more"             // the screens under More belong to it
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = tabNow == tab.route,
                        onClick = {
                            if (tab.route == tabNow) {
                                nav.popBackStack(tab.route, inclusive = false)   // again: back to the tab's own screen
                            } else {
                                nav.navigate(tab.route) {
                                    popUpTo("remote") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                }
            }
        },
        floatingActionButton = { if (route in setOf("remote", "playing", "search")) VoiceButton() },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {   // fields stay above the keyboard
            NavHost(nav, startDestination = "remote") {
                composable("remote") { RemoteScreen() }
                composable("playing") { NowPlayingScreen() }
                composable("search") { SearchScreen() }
                composable("activity") { ActivityScreen() }
                composable("more") { MoreScreen(open = { nav.navigate(it) }) }
                composable("overview") { OverviewScreen() }
                composable("decisions") { DecisionsScreen(onVocabulary = { nav.navigate("vocabulary") }) }
                composable("vocabulary") { VocabularyScreen() }
                composable("settings") { SettingsScreen() }
                composable("agents") { AgentsScreen(onAdd = { nav.navigate("pair") }) }
                composable("app") { AppPrefsScreen() }
                composable("about") { AboutScreen() }
                composable("pair") { PairScreen(link = null, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() }) }
            }
            LocalNetworkNotice(Modifier.align(Alignment.BottomCenter))
            VoicePanel(Modifier.align(Alignment.TopCenter))       // the mic button floats at the bottom
        }
    }
}

/** The agent in use (a dot: reachable or not) and a menu to switch to another or add one. */
@Composable
private fun AgentSwitcher(nav: NavHostController) {
    val repo = LocalContext.current.app.repo
    val agents by repo.agents.collectAsStateWithLifecycle()
    val conn by repo.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    WhileVisible(15_000) {
        reachable = conn?.api?.let { api -> runCatching { api.me() }.isSuccess }
    }
    LaunchedEffect(conn) { reachable = null }
    Box {
        TextButton(onClick = { open = true }) {
            Box(Modifier.size(10.dp).background(
                when (reachable) { true -> Green; false -> Red; null -> MaterialTheme.colorScheme.outline }, CircleShape))
            Text("  " + (conn?.agent?.name ?: stringResource(R.string.app_name)), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.switch_agent))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            agents.orEmpty().forEach { a ->
                DropdownMenuItem(
                    text = { Column { Text(a.name); Text(a.urls.first(), style = MaterialTheme.typography.bodySmall) } },
                    leadingIcon = { if (a.id == conn?.agent?.id) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; scope.launch { repo.select(a) } },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.add_agent)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { open = false; nav.navigate("pair") })
            DropdownMenuItem(text = { Text(stringResource(R.string.agents)) }, onClick = { open = false; nav.navigate("agents") })
        }
    }
}
