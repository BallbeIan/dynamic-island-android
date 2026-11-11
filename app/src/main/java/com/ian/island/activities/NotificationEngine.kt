package com.ian.island.activities

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ian.island.alerts.ScreenRecordTracker
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.settings.Prefs

/**
 * Turns notifications into island activities.
 *
 * Per-app mode (settings): auto | peek | live | off.
 *  - auto: calls, alarms, timers, navigation and progress become live activities; the rest peek.
 *  - peek: everything from the app is a short peek.   - live: like auto, and other notifications stay interactive longer.
 *  - off: ignored.
 */
class NotificationEngine(private val ctx: Context) {

    private val prefs = Prefs.get(ctx)
    private val builders = LiveBuilders(ctx)
    private val lastSig = HashMap<String, String>()
    private val liveKeys = HashSet<String>()

    fun restored(sbn: StatusBarNotification) = handle(sbn, null, initial = true)

    private val handler = Handler(Looper.getMainLooper())
    private val lastAt = HashMap<String, Long>()
    private val pending = HashMap<String, Pair<StatusBarNotification, NotificationListenerService.RankingMap?>>()

    /**
     * Live notifications (navigation, timers, progress) update every second or so. Rebuilding icons and the
     * island for each one janks the UI, so repeated updates of the same key are coalesced to ~2 per second.
     */
    fun posted(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap?) {
        val k = sbn.key
        val now = SystemClock.uptimeMillis()
        val last = lastAt[k] ?: 0L
        if (k in liveKeys && now - last < THROTTLE_MS) {
            if (pending.put(k, sbn to rankingMap) == null) {
                handler.postDelayed({
                    pending.remove(k)?.let { lastAt[k] = SystemClock.uptimeMillis(); handle(it.first, it.second, initial = false) }
                }, THROTTLE_MS - (now - last))
            }
            return
        }
        lastAt[k] = now
        handle(sbn, rankingMap, initial = false)
    }

    fun removed(sbn: StatusBarNotification) {
        lastSig.remove(sbn.key)
        pending.remove(sbn.key)
        lastAt.remove(sbn.key)
        ScreenRecordTracker.removed(ctx, sbn.key)
        if (liveKeys.remove(sbn.key)) IslandController.remove("n:${sbn.key}")
    }

    private companion object {
        const val THROTTLE_MS = 500L
        const val SAMSUNG_CLOCK = "com.sec.android.app.clockpackage"
    }

    private fun appMode(pkg: String) = prefs.getString(Prefs.APP_MODE_PREFIX + pkg, "auto") ?: "auto"

    private fun handle(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?, initial: Boolean) {
        try { handleSafe(sbn, ranking, initial) } catch (e: Throwable) {
            Log.w("IslandNotif", "ignored notification from ${sbn.packageName}: $e")
        }
    }

    private fun handleSafe(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?, initial: Boolean) {
        if (!prefs.getBoolean(Prefs.ENABLED, true)) return
        val n = sbn.notification ?: return
        if (sbn.packageName == ctx.packageName) return
        val mode = appMode(sbn.packageName)
        if (mode == "off") return

        var importance = NotificationManager.IMPORTANCE_DEFAULT
        if (ranking != null) {
            val r = NotificationListenerService.Ranking()
            if (ranking.getRanking(sbn.key, r)) importance = r.importance
        }
        val d = NotifData(ctx, sbn, importance)
        // A group summary only repeats its children, so it is skipped, except for calls: WhatsApp (and others) flag
        // the incoming-call notification itself as a group summary.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && !d.isCall) return
        if (ScreenRecordTracker.posted(ctx, d)) return
        if (d.isMedia) return                                  // media sessions are handled by MediaProvider

        val live = if (mode == "peek") null else classifyLive(d, mode == "live")
        if (live != null) {
            val key = d.key
            val first = liveKeys.add(key)
            IslandController.post(live, announce = first && !initial)
            return
        }
        if (liveKeys.remove(d.key)) IslandController.remove("n:${d.key}")   // no longer qualifies
        if (!initial) peek(d, mode)
    }

    /** The live activity for this notification, or null if it should be a plain peek / ignored. */
    private fun classifyLive(d: NotifData, forceLive: Boolean): IslandActivity? {
        if (d.isCall) {
            return if (prefs.getBoolean(Prefs.LIVE_CALLS, true)) builders.call(d) else null
        }
        // Samsung Clock timers / stopwatch use their own channels and a custom chronometer view
        if (d.pkg == SAMSUNG_CLOCK && d.ongoing && prefs.getBoolean(Prefs.LIVE_TIMERS, true) &&
            (d.channelId == "notification_channel_timer" || d.channelId == "notification_channel_stopwatch"))
            builders.samsungClock(d)?.let { return it }
        if (d.isRingingAlarm && prefs.getBoolean(Prefs.LIVE_ALARM, true)) return builders.alarm(d)
        if (d.isNavigation && d.ongoing && prefs.getBoolean(Prefs.LIVE_NAV, true)) return builders.navigation(d)
        if (d.isTimer && (d.ongoing || d.category == Notification.CATEGORY_ALARM) && prefs.getBoolean(Prefs.LIVE_TIMERS, true))
            return builders.timer(d)
        if (prefs.getBoolean(Prefs.LIVE_PROGRESS, true) &&
            d.ongoing && (d.promoted || (d.hasProgress && !d.progressIndeterminate)))
            return builders.progress(d)
        if (forceLive && d.ongoing && d.title.isNotEmpty() && prefs.getBoolean(Prefs.LIVE_OTHER, true))
            return builders.generic(d, live = true)
        return null
    }

    private fun peek(d: NotifData, mode: String) {
        if (!prefs.getBoolean(Prefs.PEEK, true)) return
        if (mode != "peek" && mode != "live") {
            if (d.ongoing || !d.sbn.isClearable) return
            when (d.category) {
                Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SYSTEM -> return
            }
        }
        if (d.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        if (d.title.isEmpty()) return

        // Skip silent re-posts of the same content.
        val sig = "${d.title}\u0000${d.text}"
        if (lastSig[d.key] == sig) return
        lastSig[d.key] = sig
        if (lastSig.size > 200) lastSig.clear()

        IslandController.post(builders.generic(d, live = mode == "live"), announce = true)
    }
}
