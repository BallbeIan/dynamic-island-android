package app.orbit.cameraisland.island

import android.graphics.Bitmap
import android.os.SystemClock

data class MediaInfo(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val duration: Long,
    val position: Long,
    val positionTime: Long,
    val speed: Float,
    /** false = the player's session is gone; we're showing the last known track. */
    val live: Boolean,
    val canSeek: Boolean = true,
) {
    fun currentPosition(): Long {
        val p = if (playing && positionTime > 0) {
            position + ((SystemClock.elapsedRealtime() - positionTime) * speed).toLong()
        } else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }
}
