package app.orbit.cameraisland.activities

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** A notification, parsed once into the fields the island cares about. */
class NotifData(private val ctx: Context, val sbn: StatusBarNotification, val importance: Int) {
    val n: Notification = sbn.notification
    private val ex: Bundle = n.extras ?: Bundle()

    val pkg: String = sbn.packageName
    val key: String = sbn.key
    val title: String = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val text: String = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    val bigText: String = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
    val subText: String = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
    val template: String = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty()
    val category: String? = n.category
    val ongoing: Boolean = sbn.isOngoing
    val channelId: String? = n.channelId
    val actions: List<Notification.Action> = n.actions?.toList() ?: emptyList()

    val chronometer: Boolean = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
    val countDown: Boolean = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
    val whenMs: Long = n.`when`

    val progressMax: Int = ex.getInt(Notification.EXTRA_PROGRESS_MAX)
    val progressNow: Int = ex.getInt(Notification.EXTRA_PROGRESS)
    val progressIndeterminate: Boolean = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
    val hasProgress: Boolean get() = progressMax > 0 || progressIndeterminate
    val progressFraction: Float get() =
        if (progressIndeterminate || progressMax <= 0) -1f else (progressNow.toFloat() / progressMax).coerceIn(0f, 1f)

    /** Android 16 "Live Updates" / promoted ongoing notifications. */
    val promoted: Boolean = ex.getBoolean("android.requestPromotedOngoing") || template.endsWith("ProgressStyle")
    val criticalText: String = ex.getCharSequence("android.shortCriticalText")?.toString()?.trim().orEmpty()

    val isMedia: Boolean = category == Notification.CATEGORY_TRANSPORT ||
        template.contains("MediaStyle") || template.contains("DecoratedMediaCustomViewStyle")
    val isCall: Boolean = category == Notification.CATEGORY_CALL || template.endsWith("CallStyle")
    /** 1 incoming, 2 ongoing, 3 screening (CallStyle); 0 unknown. */
    val callType: Int = ex.getInt("android.callType", 0)
    val isRingingAlarm: Boolean = category == Notification.CATEGORY_ALARM &&
        (n.fullScreenIntent != null || n.flags and Notification.FLAG_INSISTENT != 0)
    val isNavigation: Boolean = category == "navigation"
    val isTimer: Boolean = chronometer && whenMs > 0

    val appLabel: String by lazy {
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
    }

    val subtitle: String get() = (bigText.ifEmpty { text }).ifEmpty { subText }

    fun appIcon(): Drawable? = try { ctx.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }

    /** Large icon / contact photo as a drawable, if the app set one. */
    fun largeIcon(): Drawable? = try { n.getLargeIcon()?.loadDrawable(ctx) } catch (_: Throwable) { null }

    /** Person photo for calls (CallStyle), if present. */
    fun callPersonIcon(): Drawable? = if (android.os.Build.VERSION.SDK_INT < 28) null else try {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) ex.getParcelable("android.callPerson", android.app.Person::class.java)
        else @Suppress("DEPRECATION") ex.getParcelable<android.app.Person>("android.callPerson")
        p?.icon?.loadDrawable(ctx)
    } catch (_: Throwable) { null }

    /** Loads once, then hands out fresh copies (a drawable can only live in one view). */
    fun factory(load: () -> Drawable?): () -> Drawable? {
        val d = load()
        return {
            (d?.constantState?.newDrawable(ctx.resources)?.mutate()) ?: d ?: ColorDrawable(Color.DKGRAY)
        }
    }

    /** A chronometer found inside a custom RemoteViews: wall-clock base, direction, and the text it currently shows. */
    class ChronoInfo(val wallBaseMs: Long, val countDown: Boolean, val text: String)

    private fun remoteViewExtra(key: String): android.widget.RemoteViews? = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) ex.getParcelable(key, android.widget.RemoteViews::class.java)
        else @Suppress("DEPRECATION") ex.getParcelable<android.widget.RemoteViews>(key)
    } catch (_: Throwable) { null }

    /**
     * Samsung's Clock draws its timer / stopwatch with a Chronometer inside a custom RemoteViews instead of the
     * standard chronometer fields. Inflating that view with the clock's own resources gives us the real base time.
     */
    @Suppress("DEPRECATION")   // Notification.contentView
    fun samsungChronometer(): ChronoInfo? {
        val rv = remoteViewExtra("android.ongoingActivityNoti.chronometerRemoteView")
            ?: remoteViewExtra("capsule_layout") ?: n.contentView ?: return null
        return try {
            val view = rv.apply(ctx.createPackageContext(pkg, 0), android.widget.FrameLayout(ctx))
            var found: android.widget.Chronometer? = null
            fun walk(v: android.view.View) {
                if (found == null && v is android.widget.Chronometer) found = v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(view)
            val c = found ?: return null
            // Chronometer.base is on the elapsedRealtime clock; convert to wall-clock so it survives ticking.
            val wall = System.currentTimeMillis() + (c.base - android.os.SystemClock.elapsedRealtime())
            ChronoInfo(wall, if (android.os.Build.VERSION.SDK_INT >= 24) c.isCountDown else false, c.text?.toString().orEmpty())
        } catch (_: Throwable) { null }
    }

    /** The extra Samsung uses for a short description, e.g. "5 m / 10:05 PM". */
    val samsungSecondary: String get() = ex.getString("android.ongoingActivityNoti.secondaryInfo").orEmpty()
}
