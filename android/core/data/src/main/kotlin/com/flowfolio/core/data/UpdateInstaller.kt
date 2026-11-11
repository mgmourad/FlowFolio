package com.flowfolio.core.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Installs a downloaded update through a [PackageInstaller] session (owner, 2026-11-11: "Install updates
 * automatically").
 *
 * With [silent], Android 12+ is asked not to show its confirmation. It honours that only when FlowFolio is updating
 * itself and is allowed to install apps. The first time, or when the phone says no, Android shows its own prompt
 * ([UpdateInstallReceiver] opens it), so the reader is never left with nothing. Without [silent] the prompt always
 * shows: the reader pressed Install.
 */
object UpdateInstaller {
    /** Starts the install; false when the session could not even be written (the caller falls back). */
    fun install(context: Context, apk: File, silent: Boolean): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(
                    if (silent) {
                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    } else {
                        PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                    }
                )
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("flowfolio.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val answer = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(PendingIntent.getBroadcast(context, id, answer, flags).intentSender)
        }
        true
    }.onFailure { Log.w(TAG, "update session failed", it) }.getOrDefault(false)

    internal const val ACTION_RESULT = "com.flowfolio.update.INSTALL_RESULT"
    internal const val TAG = "FlowFolioUpdate"
}

/**
 * Android's answer to an install session. When it wants the reader's say (the first self-update, or silent install
 * refused) it hands back its own prompt, which is opened here. Success needs nothing: the app restarts as the new
 * build, and the "what's new" popup and banner greet the reader on the next opening.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_RESULT) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val prompt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                prompt?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.i(UpdateInstaller.TAG, "update installed")

            else -> Log.w(
                UpdateInstaller.TAG,
                "update not installed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
            )
        }
    }
}
