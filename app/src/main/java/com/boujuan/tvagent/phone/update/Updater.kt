package com.boujuan.tvagent.phone.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boujuan.tvagent.phone.BuildConfig
import com.boujuan.tvagent.phone.MainActivity
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The project's release key (RELEASING.md): an APK signed with any other is never installed. */
const val RELEASE_CERT_SHA256 = "bd934e07d6784766bbfb3948bf3467ef9b2cb0c9c1146fdf9528dc76c8eb1e08"

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: Release) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    /** Android's "Install unknown apps" isn't allowed for this app yet: the user has to switch it on once. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates from GitHub: checks the latest release, downloads its APK (SHA-256 against the asset's digest), checks it's
 * this app, newer, and signed with the release key, then installs it with a PackageInstaller session that asks for no
 * user action: on Android 12+ an app updating itself (with UPDATE_PACKAGES_WITHOUT_USER_ACTION) installs silently once
 * "Install unknown apps" is allowed. Otherwise Android asks, and the user confirms.
 */
class Updater(private val context: Context, http: OkHttpClient) {
    private val releases = Releases(http)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    val current: String = BuildConfig.VERSION_NAME

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The latest release, and whether it's newer than this app (never an older or equal one). */
    suspend fun check(): Release? {
        _state.value = UpdateState.Checking
        return try {
            val r = releases.latest()
            val newer = !r.draft && !r.prerelease && r.apk != null && Versions.newer(r.version, current)
            _state.value = if (newer) UpdateState.Available(r) else UpdateState.UpToDate(r)
            r.takeIf { newer }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            null
        }
    }

    /** Download, check and hand to the installer. False when it can't start (no permission yet, or it failed). */
    suspend fun install(release: Release): Boolean {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(release)
            return false
        }
        val asset = release.apk ?: run { _state.value = UpdateState.Failed("no APK in that release"); return false }
        return try {
            val dir = File(context.cacheDir, "updates")
            withContext(Dispatchers.IO) { dir.deleteRecursively() }          // an earlier download
            val file = File(dir, asset.name)
            _state.value = UpdateState.Downloading(release, 0f)
            releases.download(asset, file) { _state.value = UpdateState.Downloading(release, it) }
            _state.value = UpdateState.Installing(release)
            withContext(Dispatchers.IO) {
                verify(file)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(UPDATING_TO, release.version) }
                commit(file)
            }
            true
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    fun failed(message: String) {
        _state.value = UpdateState.Failed(message)
    }

    /** This app's package, a higher version code, and signed with the release key only. */
    private fun verify(file: File) {
        val pm = context.packageManager
        val info: PackageInfo = (if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }) ?: throw UpdateError("not an APK Android can read")
        if (info.packageName != context.packageName) throw UpdateError("that APK is another app (${info.packageName})")
        val installed = pm.getPackageInfo(context.packageName, 0).longVersionCode
        if (info.longVersionCode <= installed) throw UpdateError("that APK isn't newer than this one")
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        if (signers.isEmpty() || signers.any { sha256(it.toByteArray()) != RELEASE_CERT_SHA256 }) {
            throw UpdateError("that APK isn't signed with the release key")
        }
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)    // the installer adds its status
            session.commit(result.intentSender)
        }
    }

    companion object {
        const val PREFS = "updates"
        const val UPDATING_TO = "updating_to"
    }
}

/** The installer's answer: asks the user when Android wants confirmation, says when it failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = context.app.updater
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm ?: return
                if (context.app.inFront) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    Notices.post(context, Notices.NEEDS_YOU, context.getString(R.string.update_needs_you),
                        PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}     // this process is replaced; PackageReplacedReceiver says so
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                updater.failed(message)
                if (!context.app.inFront) Notices.post(context, Notices.FAILED, context.getString(R.string.update_failed, message))
            }
        }
    }
}

/** After an update this app started: a notification that it's now on the new version. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(Updater.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(Updater.UPDATING_TO, null) == null) return
        prefs.edit { remove(Updater.UPDATING_TO) }
        Notices.post(context, Notices.UPDATED, context.getString(R.string.update_done, BuildConfig.VERSION_NAME))
        UpdateWork.schedule(context, true)            // a replaced app's periodic work carries on, but make sure
    }
}

/** The periodic check (every 12 h with a network): installs a newer release by itself, or says one is waiting. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.repo.store.prefs.first().autoUpdate) return Result.success()
        val release = app.updater.check() ?: return Result.success()
        if (!app.updater.canInstall()) {
            Notices.post(applicationContext, Notices.AVAILABLE,
                applicationContext.getString(R.string.update_available_notice, release.version))
            return Result.success()
        }
        if (!app.updater.install(release)) {
            val state = app.updater.state.value
            if (state is UpdateState.Failed) {
                Notices.post(applicationContext, Notices.FAILED, applicationContext.getString(R.string.update_failed, state.message))
            }
        }
        return Result.success()
    }
}

object UpdateWork {
    private const val NAME = "tv-agent-phone-update"

    fun schedule(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Update notifications (one channel); nothing without the notification permission. */
object Notices {
    const val UPDATED = 1
    const val NEEDS_YOU = 2
    const val FAILED = 3
    const val AVAILABLE = 4
    private const val CHANNEL = "updates"

    fun post(context: Context, id: Int, text: String, tap: PendingIntent? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = tap ?: PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN, "about"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(id, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}
