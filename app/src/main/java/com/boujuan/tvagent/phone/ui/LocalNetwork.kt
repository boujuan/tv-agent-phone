package com.boujuan.tvagent.phone.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.boujuan.tvagent.phone.R

/** Android 17 (API 37) asks the user before an app reaches devices on the local network, such as the agent's computer. */
const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

private fun granted(context: Context) = Build.VERSION.SDK_INT < 37 ||
    ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Asks for local-network access once; while it isn't granted, a card says why the app needs it, with a button to
 * ask again (or, when Android won't ask any more, to open the app's settings). Nothing on older Android.
 */
@Composable
fun LocalNetworkNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ok by rememberSaveable { mutableStateOf(granted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it; asked = true }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { ok = granted(context) }   // back from Settings
    }
    LaunchedEffect(Unit) { if (!ok && !asked) ask.launch(LOCAL_NETWORK) }
    if (ok) return
    val canAsk = !asked || context.activity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, LOCAL_NETWORK) } == true
    Card(modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.local_network_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.local_network_why), style = MaterialTheme.typography.bodyMedium)
            Row {
                if (canAsk) {
                    TextButton(onClick = { ask.launch(LOCAL_NETWORK) }) { Text(stringResource(R.string.allow)) }
                } else {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)))
                    }) { Text(stringResource(R.string.open_settings)) }
                }
            }
        }
    }
}
