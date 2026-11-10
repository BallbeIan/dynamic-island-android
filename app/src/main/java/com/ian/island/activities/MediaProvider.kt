package com.ian.island.activities

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.ian.island.island.ActivityType
import com.ian.island.island.Intents
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.island.Kind
import com.ian.island.island.MediaInfo
import com.ian.island.island.Trailing
import com.ian.island.island.vivid
import com.ian.island.settings.Prefs
import kotlin.math.max

/**
 * Tracks media sessions and publishes the "media" activity.
 *
 * Pause handling: when playback stops, the island keeps the track (even if the player drops its
 * session) for [Prefs.PAUSE_TIMEOUT], then animates away. Playing again brings it back.
 */
class MediaProvider(
    private val ctx: Context,
    private val component: ComponentName,
) : MediaSessionManager.OnActiveSessionsChangedListener {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = Prefs.get(ctx)
    private var msm: MediaSessionManager? = null

    private val tracked = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var controller: MediaController? = null
    private var lastInfo: MediaInfo? = null

    private var pausedSince = 0L
    /** Starts true: a stale paused session must not pop up just because the service (re)started. */
    private var timedOut = true
    private var wasPlaying = false

    private var accentFor: Bitmap? = null
    private var accent = Color.WHITE

    private val timeoutRunnable = Runnable {
        timedOut = true
        IslandController.remove(ID)
    }

    fun start() {
        val m = ctx.getSystemService(MediaSessionManager::class.java)
        msm = m
        try {
            m.addOnActiveSessionsChangedListener(this, component, handler)
            onActiveSessionsChanged(m.getActiveSessions(component))
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        handler.removeCallbacks(timeoutRunnable)
        try { msm?.removeOnActiveSessionsChangedListener(this) } catch (_: Exception) {}
        for ((c, cb) in tracked.values) try { c.unregisterCallback(cb) } catch (_: Exception) {}
        tracked.clear()
        controller = null
        IslandController.remove(ID)
    }

    fun refresh() {
        val m = msm ?: return
        try { onActiveSessionsChanged(m.getActiveSessions(component)) } catch (_: SecurityException) {}
    }

    fun onPrefChanged(key: String?) {
        if (key == Prefs.PAUSE_TIMEOUT) rescheduleTimeout()
    }

    // ================= Sessions =================

    override fun onActiveSessionsChanged(list: MutableList<MediaController>?) {
        val sessions = list ?: emptyList<MediaController>()
        val tokens = sessions.map { it.sessionToken }.toSet()
        val it = tracked.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in tokens) {
                try { e.value.first.unregisterCallback(e.value.second) } catch (_: Exception) {}
                it.remove()
            }
        }
        for (c in sessions) {
            if (c.sessionToken in tracked) continue
            val cb = makeCallback(c.sessionToken)
            c.registerCallback(cb, handler)
            tracked[c.sessionToken] = c to cb
        }
        evaluate()
    }

    private fun makeCallback(token: MediaSession.Token) = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
        override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
        override fun onSessionDestroyed() {
            tracked.remove(token)?.let { (c, cb) -> try { c.unregisterCallback(cb) } catch (_: Exception) {} }
            evaluate()
        }
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun evaluate() {
        val ctrls = tracked.values.map { it.first }
        val playing = ctrls.firstOrNull { isPlaying(it) }
        val current = controller?.let { cur -> ctrls.firstOrNull { it.sessionToken == cur.sessionToken } }
        controller = playing ?: current ?: ctrls.firstOrNull { it.metadata != null }
        publish()
    }

    private fun publish() {
        val info = controller?.let { toInfo(it) }
        if (info != null) lastInfo = info

        if (info != null && info.playing) {
            // playing: cancel any pending dismissal
            handler.removeCallbacks(timeoutRunnable)
            pausedSince = 0
            timedOut = false
            if (!wasPlaying) IslandController.unsnooze(ID)
            wasPlaying = true
            show(info)
            return
        }

        // not playing (paused, or the player dropped its session)
        if (wasPlaying && pausedSince == 0L) {
            pausedSince = SystemClock.elapsedRealtime()
            rescheduleTimeout()
        }
        wasPlaying = false
        if (timedOut) {
            IslandController.remove(ID)
            return
        }
        val shown = info ?: lastInfo?.copy(playing = false, live = false, position = 0, duration = 0) ?: run {
            IslandController.remove(ID); return
        }
        show(shown)
    }

    private fun rescheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable)
        if (pausedSince == 0L || timedOut) return
        val limit = prefs.getLong(Prefs.PAUSE_TIMEOUT, Prefs.DEFAULT_PAUSE_TIMEOUT)
        if (limit < 0) return
        val left = limit - (SystemClock.elapsedRealtime() - pausedSince)
        if (left <= 0) timeoutRunnable.run() else handler.postDelayed(timeoutRunnable, left)
    }

    private fun toInfo(c: MediaController): MediaInfo? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank()) return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val st = c.playbackState
        return MediaInfo(
            pkg = c.packageName,
            title = title,
            artist = artist,
            art = art,
            playing = isPlaying(c),
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = st?.position ?: 0L,
            positionTime = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            live = true,
            canSeek = st != null && (st.actions and PlaybackState.ACTION_SEEK_TO) != 0L,
        )
    }

    private fun show(info: MediaInfo) {
        val a = IslandActivity(ID, ActivityType.MEDIA)
        a.kind = Kind.MEDIA
        a.media = info
        a.title = info.title
        a.subtitle = info.artist
        a.trailing = Trailing.Wave
        a.accent = accentOf(info.art)
        a.icon = iconFactory(info)
        a.onOpen = { openApp() }
        IslandController.post(a, announce = false)
    }

    private fun iconFactory(info: MediaInfo): () -> Drawable? {
        val art = info.art
        val pkg = info.pkg
        return {
            if (art != null) BitmapDrawable(ctx.resources, art)
            else try { ctx.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { ColorDrawable(Color.DKGRAY) }
        }
    }

    private fun accentOf(b: Bitmap?): Int {
        if (b == null) return Color.WHITE
        if (b === accentFor) return accent
        accent = try {
            val src = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
            vivid(Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0))
        } catch (_: Throwable) {
            Color.WHITE
        }
        accentFor = b
        return accent
    }

    // ================= Controls =================

    fun playPause() {
        val c = controller
        if (c != null) {
            if (isPlaying(c)) c.transportControls.pause() else c.transportControls.play()
        } else {
            fallbackPlayPause(ctx)
            // If nothing picked it up within a second, open the app itself.
            handler.postDelayed({ if (controller == null) openApp() }, 1200)
        }
    }

    fun next() { controller?.transportControls?.skipToNext() }

    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(max(0, ms)) }

    fun openApp() {
        controller?.sessionActivity?.let { if (Intents.send(ctx, it)) return }
        Intents.openPackage(ctx, controller?.packageName ?: lastInfo?.pkg)
    }

    companion object {
        const val ID = "media"

        /** No live session: poke the system so the last media app resumes. */
        fun fallbackPlayPause(ctx: Context) {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }
}
