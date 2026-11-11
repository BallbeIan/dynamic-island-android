package com.ian.island.island

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import com.ian.island.IslandAccessibilityService
import com.ian.island.IslandService
import com.ian.island.activities.MediaProvider
import com.ian.island.gestures.ActionRunner
import com.ian.island.gestures.Gesture
import com.ian.island.settings.Prefs
import com.ian.island.settings.ScreenPrefs
import java.util.Calendar

/**
 * The single shared brain. The notification listener and the accessibility service both feed it;
 * providers post [IslandActivity]s; it decides which one is primary/secondary and drives the overlay.
 */
object IslandController : SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val acts = LinkedHashMap<String, IslandActivity>()
    private val snoozed = HashSet<String>()
    private val expiry = HashMap<String, Runnable>()
    private var pinned: String? = null
    private var announceId: String? = null
    private var expandedId: String? = null

    private var overlay: IslandOverlay? = null
    private var overlayHost: Any? = null

    var a11y: IslandAccessibilityService? = null
        private set
    var listener: IslandService? = null
        private set
    var media: MediaProvider? = null

    private val modules = ArrayList<IslandModule>()
    private var started = false

    // rule state
    private var foregroundPkg: String? = null
    private var screenOn = true
    private val idle = IslandActivity("idle", ActivityType.IDLE)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; panelOpen = false; overlay?.setPaused(true) }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; overlay?.setPaused(false); a11y?.refreshWindowState() }
            }
            recompute()
        }
    }

    // ================= Lifecycle =================

    private fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        prefs = Prefs.get(app)
    }

    fun attachAccessibility(svc: IslandAccessibilityService) {
        init(svc)
        a11y = svc
        ensureStarted()
        recompute()
    }

    fun detachAccessibility(svc: IslandAccessibilityService) {
        if (a11y !== svc) return
        a11y = null
        foregroundPkg = null
        fullscreen = false
        recompute()
        stopIfIdle()
    }

    fun attachListener(svc: IslandService) {
        init(svc)
        listener = svc
        ensureStarted()
        recompute()
    }

    fun detachListener(svc: IslandService) {
        if (listener !== svc) return
        listener = null
        media = null
        removeWhere { it.id == "media" || it.id.startsWith("n:") }
        recompute()
        stopIfIdle()
    }

    private fun ensureStarted() {
        if (started) return
        started = true
        prefs.registerOnSharedPreferenceChangeListener(this)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        Intents.register(app, screenReceiver, f)
        screenOn = app.getSystemService(PowerManager::class.java).isInteractive
        for (m in IslandModules.create(app)) { modules.add(m); m.start() }
    }

    private fun stopIfIdle() {
        if (!started || a11y != null || listener != null) return
        started = false
        for (m in modules) try { m.stop() } catch (_: Exception) {}
        modules.clear()
        try { app.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        overlay?.destroy(); overlay = null; overlayHost = null
        acts.clear(); expiry.values.forEach { handler.removeCallbacks(it) }; expiry.clear()
    }

    // ================= Activities =================

    fun post(a: IslandActivity, announce: Boolean = false) {
        if (!::app.isInitialized) return
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { post(a, announce) }; return }
        val existing = acts[a.id]
        a.postedAt = if (existing != null && !announce) existing.postedAt else System.currentTimeMillis()
        acts[a.id] = a
        if (announce) { snoozed.remove(a.id); announceId = a.id }
        scheduleExpiry(a)
        recompute()
    }

    fun remove(id: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { remove(id) }; return }
        if (acts.remove(id) == null) return
        snoozed.remove(id)
        if (pinned == id) pinned = null
        expiry.remove(id)?.let { handler.removeCallbacks(it) }
        recompute()
    }

    fun get(id: String): IslandActivity? = acts[id]

    fun removeWhere(pred: (IslandActivity) -> Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { removeWhere(pred) }; return }
        val ids = acts.values.filter(pred).map { it.id }
        ids.forEach { acts.remove(it); snoozed.remove(it); expiry.remove(it)?.let { r -> handler.removeCallbacks(r) } }
        if (ids.isNotEmpty()) recompute()
    }

    fun unsnooze(id: String) { if (snoozed.remove(id)) recompute() }

    private fun scheduleExpiry(a: IslandActivity) {
        expiry.remove(a.id)?.let { handler.removeCallbacks(it) }
        if (!a.isTransient) return
        val r = object : Runnable {
            override fun run() {
                if (expandedId == a.id) { handler.postDelayed(this, 3000); return }
                expiry.remove(a.id)
                remove(a.id)
            }
        }
        expiry[a.id] = r
        handler.postDelayed(r, a.transientMs)
    }

    /** The user is interacting with a transient (e.g. dragging the volume slider): keep it alive. */
    fun touchTransient() {
        val p = primaryId?.let { acts[it] } ?: return
        if (p.isTransient) scheduleExpiry(p)
    }

    /** Swaps the two pills as if the small one had been tapped (used by the debug hook). */
    fun swapSecondary() { secondaryId?.let { swapTo(it) } }

    fun swapTo(id: String) {
        pinned = id
        overlay?.noteSwap()
        recompute()
    }

    /** Collapse if expanded; else dismiss a transient; else hide the current activity until it is re-announced. */
    fun dismissCurrent() {
        val o = overlay ?: return
        val id = primaryId ?: return
        when {
            o.mode == IslandOverlay.Mode.EXPANDED -> o.collapse()
            acts[id]?.isTransient == true -> remove(id)
            o.mode == IslandOverlay.Mode.BANNER -> o.collapseBanner()
            else -> { snoozed.add(id); recompute() }
        }
    }

    fun toggleExpand() { overlay?.toggleExpand() }

    /**
     * Opens the activity's app from the island (header tap, long press, tapping a peek) and puts the pill away:
     * a peek or alert is dismissed, an expanded or banner pill goes back to the small pill.
     */
    fun open(a: IslandActivity) {
        a.onOpen?.invoke()
        val o = overlay ?: return
        when {
            a.isTransient -> remove(a.id)
            primaryId != a.id -> {}
            o.mode == IslandOverlay.Mode.EXPANDED -> o.collapse()
            o.mode == IslandOverlay.Mode.BANNER -> o.collapseBanner()
        }
    }

    /** Only a user-initiated expansion keeps a transient alive; auto-expanded alerts still time out. */
    fun onOverlayExpanded(id: String, expanded: Boolean, byUser: Boolean) {
        expandedId = if (expanded && byUser) id else if (expandedId == id) null else expandedId
    }

    // ================= Decisions =================

    private var primaryId: String? = null
    private var secondaryId: String? = null

    private val enabled get() = prefs.getBoolean(Prefs.ENABLED, true)

    private fun ordered(): List<IslandActivity> {
        val list = acts.values.filter { it.id !in snoozed }
            .sortedWith(compareByDescending<IslandActivity> { it.priority }.thenByDescending { it.postedAt })
            .toMutableList()
        val pin = pinned
        if (pin != null) {
            if (list.size >= 2 && list[1].id == pin) { val t = list[0]; list[0] = list[1]; list[1] = t }
            else if (list.none { it.id == pin }) pinned = null
        }
        return list
    }

    fun recompute() {
        if (!::app.isInitialized) return
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { recompute() }; return }
        handler.removeCallbacks(recomputeRunnable)
        val host = chooseHost()
        if (host == null || !enabled) {
            overlay?.destroy()
            overlay = null; overlayHost = null
            primaryId = null
            return
        }
        if (overlay == null || overlayHost != host.key) {
            overlay?.destroy()
            overlay = IslandOverlay(host.ctx, host.type)
            overlayHost = host.key
            overlay?.setPaused(!screenOn)
        }
        val o = overlay!!

        val all = ordered()
        val (inds, main) = all.partition { it.type == ActivityType.INDICATOR }
        val slots = main + inds
        var p = slots.getOrNull(0)
        val s = slots.getOrNull(1)
        if (p == null && prefs.getString(Prefs.SHOW_MODE, "activity") == "always") p = idle
        primaryId = p?.id
        secondaryId = s?.id
        val dots = inds.filter { it.id != p?.id && it.id != s?.id }.map { (it.trailing as? Trailing.Dot)?.color ?: it.accent }

        o.setSuppressed(!visibleNow())
        val announce = p != null && announceId == p.id
        if (announce) announceId = null
        o.render(p, s, dots, announce)
        scheduleBoundary()
    }

    private val recomputeRunnable = Runnable { recompute() }

    private class Host(val ctx: Context, val type: Int, val key: Any)

    private fun chooseHost(): Host? {
        a11y?.let { return Host(it, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, it) }
        listener?.let { return Host(it, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, it) }
        return null
    }

    // ================= Visibility rules =================

    /** Whether the island may be shown right now (every hiding rule in one place). */
    private fun visibleNow(): Boolean {
        if (!enabled || !screenOn || panelOpen) return false
        if (fullscreen && !prefs.getBoolean(Prefs.SHOW_IN_FULLSCREEN, false)) return false
        if (prefs.getBoolean(Prefs.SCHEDULE_ON, false) && !inSchedule()) return false
        if (prefs.getBoolean(Prefs.HIDE_LOCK, false) &&
            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val fg = foregroundPkg
        if (fg != null && prefs.getStringSet(Prefs.HIDE_APPS, emptySet())?.contains(fg) == true) return false
        if (prefs.getBoolean(Prefs.HIDE_LANDSCAPE, false) &&
            app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return false
        return true
    }

    private fun minutesNow(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun inSchedule(): Boolean {
        val s = prefs.getInt(Prefs.SCHEDULE_START, 7 * 60)
        val e = prefs.getInt(Prefs.SCHEDULE_END, 23 * 60)
        val n = minutesNow()
        return if (s <= e) n in s until e else n >= s || n < e
    }

    /** One wake-up at the next schedule edge; no polling. */
    private fun scheduleBoundary() {
        if (!prefs.getBoolean(Prefs.SCHEDULE_ON, false)) return
        val s = prefs.getInt(Prefs.SCHEDULE_START, 7 * 60)
        val e = prefs.getInt(Prefs.SCHEDULE_END, 23 * 60)
        val n = minutesNow()
        val next = listOf(s, e).map { (it - n + 1440) % 1440 }.filter { it > 0 }.minOrNull() ?: return
        handler.postDelayed(recomputeRunnable, next * 60_000L)
    }

    // ---- notification shade / Quick Settings: the island hides while they are open ----
    private var panelOpen = false
    private var fullscreen = false
    private var panelOpenedAt = 0L
    private val panelTimeout = Runnable { setPanel(false) }

    /** [open] true from the systemui panel window event; false when another app window announces itself. */
    fun onSystemPanel(open: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (open) {
            panelOpenedAt = now
            handler.removeCallbacks(panelTimeout)
            handler.postDelayed(panelTimeout, 20_000)   // safety net if no closing event ever arrives
            setPanel(true)
        } else if (panelOpen && now - panelOpenedAt > 500) {
            handler.removeCallbacks(panelTimeout)
            setPanel(false)
        }
    }

    /** Authoritative state from the accessibility window list: no timeout needed. */
    fun setSystemPanel(open: Boolean) {
        handler.removeCallbacks(panelTimeout)
        setPanel(open)
    }

    /** From the accessibility window list: an app is currently full screen. */
    fun setFullscreen(v: Boolean) {
        if (fullscreen == v) return
        fullscreen = v
        recompute()
    }

    private fun setPanel(v: Boolean) {
        if (panelOpen == v) return
        panelOpen = v
        recompute()
    }

    fun onForegroundApp(pkg: String?) {
        if (foregroundPkg == pkg) return
        foregroundPkg = pkg
        recompute()
    }

    fun onConfigChanged() {
        overlay?.refreshAppearance()
        recompute()
    }

    // ================= Gestures & media pass-through =================

    fun onGesture(g: Gesture) {
        val primary = primaryId?.let { acts[it] }
        // a tap on the always-on idle pill is a tap on the camera: nothing is showing
        val gesture = if (g == Gesture.TAP && primaryId == "idle") Gesture.CAMERA_TAP else g
        ActionRunner.run(app, gesture.action(app), gesture, primary)
    }

    fun mediaPlayPause() = media?.playPause() ?: MediaProvider.fallbackPlayPause(app)
    fun mediaNext() { media?.next() }
    fun mediaPrevious() { media?.previous() }
    fun seekMedia(ms: Long) { media?.seekTo(ms) }

    // ================= Preferences =================

    private val appearanceKeys = setOf(
        Prefs.PILL_HEIGHT, Prefs.CORNER, Prefs.BG_COLOR, Prefs.BG_ALPHA, Prefs.OUTLINE, Prefs.GLOW,
        Prefs.WALLPAPER_ACCENT, Prefs.DOUBLE_SIDE, Prefs.DOUBLE_GAP, Prefs.DOUBLE_PILL, Prefs.WIDTH,
    )

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        for (m in modules) try { m.onPrefChanged(key) } catch (_: Exception) {}
        media?.onPrefChanged(key)
        when (ScreenPrefs.baseKey(key)) {
            Prefs.VOLUME_REPLACE -> a11y?.syncKeyFilter()
            Prefs.OFFSET, Prefs.XOFFSET, Prefs.AUTO_CAMERA -> overlay?.updateOffset()
            in appearanceKeys -> overlay?.refreshAppearance()
        }
        recompute()
    }
}
