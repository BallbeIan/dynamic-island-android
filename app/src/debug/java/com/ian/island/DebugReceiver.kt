package com.ian.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import com.ian.island.island.ActivityType
import com.ian.island.island.Icon
import com.ian.island.island.IconDrawable
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.island.Trailing

/** Debug-only test hook (not part of release builds). */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            "com.ian.island.DEBUG_TWO" -> {
                IslandController.post(IslandActivity("dbg:timer", ActivityType.TIMER).apply {
                    title = "Timer"; accent = Color.rgb(255, 159, 10)
                    icon = { IconDrawable(Icon.ALARM, Color.rgb(255, 159, 10)) }
                    trailing = Trailing.Timer(System.currentTimeMillis() - 65_000, false)
                })
                IslandController.post(IslandActivity("dbg:music", ActivityType.MEDIA).apply {
                    title = "Song"; accent = Color.rgb(48, 209, 88)
                    icon = { IconDrawable(Icon.MUSIC, Color.rgb(48, 209, 88)) }
                    trailing = Trailing.Label("Playing now")
                })
            }
            "com.ian.island.DEBUG_CLOCK_CANCEL" -> {
                // fire the "Cancel" button of any running Clock timer / stopwatch notification
                val svc = IslandService.instance ?: return
                for (sbn in svc.activeNotifications.filter { it.packageName == "com.sec.android.app.clockpackage" }) {
                    sbn.notification.actions?.filter { it.title?.toString() == "Cancel" }?.forEach { it.actionIntent?.send() }
                }
            }
            "com.ian.island.DEBUG_WINDOWS" -> {
                // window types / bounds only, never content
                val svc = IslandAccessibilityService.instance ?: return
                val h = svc.resources.displayMetrics.heightPixels
                val line = svc.windows.joinToString(" | ") { w ->
                    val r = android.graphics.Rect(); w.getBoundsInScreen(r)
                    "t${w.type} L${w.layer} a=${if (w.isActive) 1 else 0} f=${if (w.isFocused) 1 else 0} ${r.left},${r.top}-${r.right},${r.bottom}"
                }
                android.util.Log.d("IslandWin", "screenH=$h :: $line")
            }
            "com.ian.island.DEBUG_PREF" ->
                com.ian.island.settings.Prefs.get(ctx).edit().putString(i.getStringExtra("key"), i.getStringExtra("value")).apply()
            "com.ian.island.DEBUG_AUDIO" -> {
                val a = ctx.getSystemService(android.media.AudioManager::class.java)
                val cc = com.ian.island.activities.CallControls
                when (val what = i.getStringExtra("do")) {
                    "mute" -> cc.toggleMute(ctx)
                    null -> {}
                    else -> cc.routes(ctx).firstOrNull { it.kind.name.equals(what, true) }?.let { cc.setRoute(ctx, it) }
                }
                android.util.Log.d("IslandAudio", "mode=${a.mode} micMuted=${a.isMicrophoneMute} route=${cc.currentKind(ctx)} " +
                    "available=${cc.routes(ctx).map { it.kind.name }}")
            }
            "com.ian.island.DEBUG_MEDIA" -> {
                val bmp = android.graphics.Bitmap.createBitmap(240, 240, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bmp).drawPaint(android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(0f, 0f, 240f, 240f, Color.rgb(20, 40, 110), Color.rgb(200, 90, 150), android.graphics.Shader.TileMode.CLAMP)
                })
                val playing = i.getBooleanExtra("playing", true)
                val info = com.ian.island.island.MediaInfo("x", "Music Sounds Better With You", "Stardust, Benjamin Diamond", bmp, playing,
                    403_000, 79_000, android.os.SystemClock.elapsedRealtime(), 1f, true, true)
                IslandController.post(IslandActivity("media", ActivityType.MEDIA).apply {
                    kind = com.ian.island.island.Kind.MEDIA; media = info; title = info.title; subtitle = info.artist
                    trailing = Trailing.Wave; accent = Color.rgb(130, 150, 255)
                    icon = { android.graphics.drawable.BitmapDrawable(ctx.resources, bmp) }
                })
            }
            "com.ian.island.DEBUG_TAP" -> IslandController.toggleExpand()
            "com.ian.island.DEBUG_SWAP" -> IslandController.swapSecondary()
            "com.ian.island.DEBUG_CLEAR" -> { IslandController.remove("dbg:timer"); IslandController.remove("dbg:music") }
        }
    }
}
