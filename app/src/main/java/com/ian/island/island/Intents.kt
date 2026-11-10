package com.ian.island.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object Intents {
    /** registerReceiver with the not-exported flag where the platform requires one (system broadcasts still arrive). */
    fun register(ctx: Context, r: android.content.BroadcastReceiver, f: android.content.IntentFilter): Intent? =
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(r, f)

    /** Sends a PendingIntent, allowing it to start an activity from the background. */
    fun send(ctx: Context, pi: PendingIntent, fill: Intent? = null): Boolean = try {
        val opts = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null
        pi.send(ctx, 0, fill, null, null, null, opts)
        true
    } catch (_: Exception) {
        false
    }

    fun openPackage(ctx: Context, pkg: String?): Boolean {
        if (pkg == null) return false
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(launch); true } catch (_: Exception) { false }
    }

    fun start(ctx: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (_: Exception) { false }
    }
}
