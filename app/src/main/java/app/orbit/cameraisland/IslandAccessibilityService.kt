package app.orbit.cameraisland

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import app.orbit.cameraisland.island.IslandController
import app.orbit.cameraisland.settings.Prefs

/**
 * Hosts the overlay as TYPE_ACCESSIBILITY_OVERLAY (draws over the status bar / cutout / lock screen),
 * performs global gestures (home, recents, screenshot...), and reports the foreground app.
 * It only looks at which windows exist (type, bounds, owning app), never at the content of other apps' windows.
 * (canRetrieveWindowContent is declared because Android requires it to list windows at all.)
 */
class IslandAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: IslandAccessibilityService? = null
            private set
    }

    private var imePackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        imePackages = try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        IslandController.attachAccessibility(this)
        syncKeyFilter()
        refreshWindowState()
    }

    /** Only intercept key events while "replace volume panel" is on. */
    fun syncKeyFilter() {
        val info = serviceInfo ?: return
        val want = Prefs.get(this).getBoolean(Prefs.VOLUME_REPLACE, false)
        val has = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (want == has) return
        info.flags = if (want) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        serviceInfo = info
    }

    /** Volume keys change the volume without the system panel; the island's slider shows the result. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.get(this).getBoolean(Prefs.VOLUME_REPLACE, false)) return false
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            getSystemService(AudioManager::class.java)
                .adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
        }
        return true
    }

    /**
     * Re-reads everything derived from the window list (also called on screen on): whether the shade / Quick
     * Settings (or another full-screen system surface such as the power menu) is the focused window, and whether an
     * app is full screen (it covers the screen and the status bar's own window is gone). Never looks at window
     * content. Returns whether the panel is open, or null when the window list isn't available.
     */
    fun refreshWindowState(): Boolean? {
        val list = try { windows } catch (_: Exception) { return null }
        if (list.isEmpty()) return null
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val dm = resources.displayMetrics
        val panel = !locked && systemPanelOpen(list, dm.heightPixels)
        IslandController.setSystemPanel(panel)
        IslandController.setFullscreen(!locked && fullScreenApp(list, dm.widthPixels, dm.heightPixels))
        return panel
    }

    private fun fullScreenApp(list: List<AccessibilityWindowInfo>, screenW: Int, screenH: Int): Boolean {
        val r = Rect()
        val app = list.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) } ?: return false
        app.getBoundsInScreen(r)
        if (r.width() < screenW * 0.95f || r.height() < screenH * 0.95f) return false   // split screen / floating window
        val statusBarListed = list.any { w ->
            w.getBoundsInScreen(r)
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && r.top <= 0 && r.height() < screenH * 0.25f && r.width() >= screenW * 0.7f
        }
        return !statusBarListed
    }

    private fun systemPanelOpen(list: List<AccessibilityWindowInfo>, screenH: Int): Boolean {
        val r = Rect()
        return list.any { w ->
            w.getBoundsInScreen(r)
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && (w.isActive || w.isFocused) && r.top <= 0 && r.height() >= screenH / 2
        }
    }

    /** The shade / Quick Settings announce themselves as a systemui window with their whole content as text. */
    private fun looksLikePanel(text: List<CharSequence>): Boolean {
        val t = text.joinToString(" ").lowercase()
        return t.contains("quick settings") || t.contains("notification shade") ||
            t.contains("notification panel") || text.size >= 4
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            refreshWindowState()
            return
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        val byWindows = refreshWindowState()
        if (pkg == "com.android.systemui") {
            if (byWindows == null && looksLikePanel(event.text)) IslandController.onSystemPanel(true)
            return
        }
        // ignore our own overlay, keyboards and popup windows
        if (pkg == packageName || pkg == "android" || pkg in imePackages) return
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.") || cls.startsWith("android.inputmethodservice.")) {
            // popups say nothing about the panel closing, but an app window announcing itself does
            if (cls != "android.widget.FrameLayout") return
        }
        if (byWindows == null) IslandController.onSystemPanel(false)
        IslandController.onForegroundApp(pkg)
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandController.onConfigChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        IslandController.detachAccessibility(this)
    }
}
