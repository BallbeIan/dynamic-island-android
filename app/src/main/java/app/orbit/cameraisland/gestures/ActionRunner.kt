package app.orbit.cameraisland.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import app.orbit.cameraisland.island.Intents
import app.orbit.cameraisland.island.IslandActivity
import app.orbit.cameraisland.island.IslandController
import app.orbit.cameraisland.settings.Prefs

/** Executes a configured gesture action. */
object ActionRunner {

    fun run(ctx: Context, action: GestureAction, gesture: Gesture, primary: IslandActivity?) {
        val a11y = IslandController.a11y
        when (action) {
            GestureAction.NONE -> {}
            GestureAction.EXPAND -> IslandController.toggleExpand()
            GestureAction.DISMISS -> IslandController.dismissCurrent()
            GestureAction.OPEN_APP -> primary?.let { IslandController.open(it) }
            GestureAction.PLAY_PAUSE -> if (primary?.media != null || IslandController.media != null) IslandController.mediaPlayPause()
            GestureAction.NEXT -> IslandController.mediaNext()
            GestureAction.PREVIOUS -> IslandController.mediaPrevious()
            GestureAction.HOME -> global(ctx, a11y, AccessibilityService.GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> global(ctx, a11y, AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.BACK -> global(ctx, a11y, AccessibilityService.GLOBAL_ACTION_BACK)
            GestureAction.SCREENSHOT -> global(ctx, a11y, 9 /* GLOBAL_ACTION_TAKE_SCREENSHOT, API 28 */)
            GestureAction.LOCK -> global(ctx, a11y, 8 /* GLOBAL_ACTION_LOCK_SCREEN, API 28 */)
            GestureAction.NOTIFICATIONS ->
                if (a11y != null) a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                else statusBar(ctx, "expandNotificationsPanel")
            GestureAction.QUICK_SETTINGS ->
                if (a11y != null) a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                else statusBar(ctx, "expandSettingsPanel")
            GestureAction.TORCH -> Torch.toggle(ctx)
            GestureAction.LAUNCH_APP -> {
                val pkg = Prefs.get(ctx).getString(Prefs.gestureAppKey(gesture.key), null)
                if (!Intents.openPackage(ctx, pkg)) toast(ctx, "No app chosen for this gesture")
            }
        }
    }

    private fun global(ctx: Context, svc: AccessibilityService?, action: Int) {
        if (svc == null) { toast(ctx, "Enable the Orbit accessibility service for this gesture"); return }
        // TAKE_SCREENSHOT/LOCK_SCREEN need API 28; older devices just ignore the unknown id.
        svc.performGlobalAction(action)
    }

    /** Fallback without accessibility: EXPAND_STATUS_BAR is a normal permission; the call is hidden API, best effort. */
    private fun statusBar(ctx: Context, method: String) {
        try {
            val sbm = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sbm)
        } catch (_: Throwable) {
            toast(ctx, "Enable the Orbit accessibility service to open the shade")
        }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

/** Flashlight toggle with state tracking (no permission needed). */
object Torch {
    private var on = false
    private var registered = false
    private var cameraId: String? = null

    fun toggle(ctx: Context) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        if (!registered) {
            registered = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) { if (id == cameraId) on = enabled }
            }, null)
        }
        val id = cameraId ?: cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }?.also { cameraId = it } ?: return
        try { cm.setTorchMode(id, !on); on = !on } catch (_: Exception) {}
    }
}
