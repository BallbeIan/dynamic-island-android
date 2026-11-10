package com.ian.island

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.KeyEvent
import com.ian.island.settings.Prefs
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.ian.island.island.IslandController

/**
 * Hosts the overlay as TYPE_ACCESSIBILITY_OVERLAY (draws over the status bar / cutout / lock screen),
 * performs global gestures (home, recents, screenshot...), and reports the foreground app.
 * It never reads screen content (canRetrieveWindowContent = false).
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
     * True while an app is full screen (video, game, immersive): the focused app window covers the whole screen and
     * the status bar's own window is gone from the window list. Null when the list isn't available.
     */
    private fun fullScreenApp(): Boolean? {
        val list = try { windows } catch (_: Exception) { return null }
        if (list.isEmpty()) return null
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
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

    /** Re-reads everything derived from the window list (also called on screen on). */
    fun refreshWindowState() {
        systemPanelOpen()?.let { IslandController.setSystemPanel(it) }
        fullScreenApp()?.let { IslandController.setFullscreen(it) }
    }

    /** The shade / Quick Settings announce themselves as a systemui window with their whole content as text. */
    private fun looksLikePanel(text: List<CharSequence>): Boolean {
        val t = text.joinToString(" ").lowercase()
        return t.contains("quick settings") || t.contains("notification shade") ||
            t.contains("notification panel") || text.size >= 4
    }

    /**
     * True while the notification shade / Quick Settings (or another full-screen system surface such as the
     * power menu) is the focused window: a full-screen TYPE_SYSTEM window with no app window in front of it.
     * Returns null when the window list isn't available. Never looks at window content.
     */
    private fun systemPanelOpen(): Boolean? {
        val list = try { windows } catch (_: Exception) { return null }
        if (list.isEmpty()) return null
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val h = resources.displayMetrics.heightPixels
        val r = Rect()
        return list.any { w ->
            w.getBoundsInScreen(r)
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && (w.isActive || w.isFocused) && r.top <= 0 && r.height() >= h / 2
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            refreshWindowState()
            return
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        val byWindows = systemPanelOpen()
        if (byWindows != null) IslandController.setSystemPanel(byWindows)
        fullScreenApp()?.let { IslandController.setFullscreen(it) }
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
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
