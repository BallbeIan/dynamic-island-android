package com.ian.island.island

import android.graphics.Color
import android.graphics.drawable.Drawable

enum class ActivityType(val priority: Int) {
    // persistent
    CALL(100), ALARM(95), TIMER(60), NAVIGATION(55), LIVE(50), CHARGING(45), MEDIA(40), INDICATOR(30), IDLE(0),
    // transient (override persistent ones while they last)
    NOTIFICATION(110), ALERT(120), VOLUME(125), BATTERY_ALERT(130),
}

/** How an activity enters the island the first time it is announced. */
enum class Presentation { COLLAPSED, BANNER, EXPANDED }

enum class AnimStyle { POP, SLIDE, PULSE;
    companion object { fun from(s: String?) = values().firstOrNull { it.name == s } ?: POP }
}

enum class Kind { GENERIC, MEDIA, VOLUME }

/** What sits at the right edge of the collapsed pill (and in the expanded header). */
sealed class Trailing {
    object None : Trailing()
    object Wave : Trailing()
    /** Wall-clock based chronometer. */
    data class Timer(val baseMs: Long, val countDown: Boolean) : Trailing()
    /** fraction < 0 = indeterminate. */
    data class Ring(val fraction: Float) : Trailing()
    data class Label(val text: String) : Trailing()
    data class Dot(val color: Int) : Trailing()
}

class ActionButton(
    val label: CharSequence,
    val color: Int = 0,               // 0 = neutral
    val icon: (() -> Drawable?)? = null,
    val run: () -> Unit,
)

class ReplyAction(val hint: CharSequence, val send: (String) -> Unit)

class VolumeInfo(val label: String, val level: Int, val max: Int, val muted: Boolean, val setLevel: (Int) -> Unit)

/**
 * One thing the island can show. Providers create/update these and hand them to
 * [IslandController]; the overlay renders whichever ones the controller picks.
 */
class IslandActivity(val id: String, val type: ActivityType) {
    var priority: Int = type.priority
    var kind: Kind = Kind.GENERIC

    /** A factory because several views display the icon at once and drawables can't be shared. */
    var icon: (() -> Drawable?)? = null
    var accent: Int = Color.WHITE
    var title: CharSequence = ""
    var subtitle: CharSequence = ""
    var trailing: Trailing = Trailing.None

    /** 0..1 progress bar in the expanded view; < 0 hides it. */
    var progress: Float = -1f

    var buttons: List<ActionButton> = emptyList()
    var reply: ReplyAction? = null
    var onOpen: (() -> Unit)? = null

    /** > 0: removed automatically after this long (and shown as a banner meanwhile). */
    var transientMs: Long = 0
    var presentation: Presentation = Presentation.COLLAPSED
    /** How long a banner stays before shrinking to the collapsed pill (persistent activities only). */
    var bannerMs: Long = 3500
    var anim: AnimStyle = AnimStyle.POP
    /** false = tapping goes straight to [onOpen] instead of expanding. */
    var expandable: Boolean = true

    var media: MediaInfo? = null
    var volume: VolumeInfo? = null

    var postedAt: Long = 0

    val isTransient get() = transientMs > 0
}
