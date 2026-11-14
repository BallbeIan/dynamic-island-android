package app.orbit.cameraisland.activities

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import app.orbit.cameraisland.island.ActionButton
import app.orbit.cameraisland.island.ActivityType
import app.orbit.cameraisland.island.AnimStyle
import app.orbit.cameraisland.island.BLUE
import app.orbit.cameraisland.island.GREEN
import app.orbit.cameraisland.island.Intents
import app.orbit.cameraisland.island.IslandActivity
import app.orbit.cameraisland.island.IslandController
import app.orbit.cameraisland.island.ORANGE
import app.orbit.cameraisland.island.Presentation
import app.orbit.cameraisland.island.RED
import app.orbit.cameraisland.island.ReplyAction
import app.orbit.cameraisland.island.Trailing
import app.orbit.cameraisland.island.YELLOW

/** Builds the island activity for each kind of notification. */
class LiveBuilders(private val ctx: Context) {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun id(d: NotifData) = "n:${d.key}"

    private fun open(d: NotifData): () -> Unit = {
        val pi = d.n.contentIntent
        if (pi == null || !Intents.send(ctx, pi)) Intents.openPackage(ctx, d.pkg)
    }

    /** Notification actions -> island buttons (the inline-reply action becomes a text field instead). */
    private fun buttons(d: NotifData, max: Int = 4): List<ActionButton> =
        d.actions.filter { it.actionIntent != null && !isReply(it) }.take(max).map { act ->
            val label = act.title?.toString().orEmpty()
            ActionButton(label, colorFor(label)) { act.actionIntent?.let { Intents.send(ctx, it) } }
        }

    private fun isReply(a: Notification.Action) = a.remoteInputs?.any { it.allowFreeFormInput } == true

    private val dialerPackages = setOf(
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.incallui", "com.android.server.telecom", "com.android.phone", "com.oneplus.dialer", "com.miui.voip",
    )

    private val positiveRe = Regex("answer|accept|pick up")
    private val negativeRe = Regex("decline|reject|hang|end call|dismiss call|ignore|stop|cancel|delete")

    private fun colorFor(label: String): Int {
        val l = label.lowercase()
        return when {
            positiveRe.containsMatchIn(l) -> GREEN
            negativeRe.containsMatchIn(l) -> RED
            else -> 0
        }
    }

    fun reply(d: NotifData): ReplyAction? {
        val act = d.actions.firstOrNull { isReply(it) && it.actionIntent != null } ?: return null
        val ri = act.remoteInputs.first { it.allowFreeFormInput }
        return ReplyAction(ri.label?.toString()?.ifEmpty { null } ?: "Reply") { text ->
            val fill = Intent()
            val b = Bundle().apply { putCharSequence(ri.resultKey, text) }
            RemoteInput.addResultsToIntent(act.remoteInputs, fill, b)
            Intents.send(ctx, act.actionIntent, fill)
        }
    }

    // ---- Calls ----

    @Suppress("DEPRECATION")   // TelecomManager.acceptRingingCall / endCall: still the only way without a button
    fun call(d: NotifData): IslandActivity {
        val incoming = d.callType == 1 || (d.callType == 0 && d.n.fullScreenIntent != null && !d.chronometer)
        val a = IslandActivity(id(d), ActivityType.CALL)
        a.title = d.title.ifEmpty { d.appLabel }
        a.accent = GREEN
        a.icon = d.factory { d.callPersonIcon() ?: d.largeIcon() ?: d.appIcon() }
        a.onOpen = open(d)
        var btns = buttons(d)
        val telecom = ctx.getSystemService(TelecomManager::class.java)
        val canTelecom = ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        if (incoming) {
            a.priority = 200
            a.subtitle = d.text.ifEmpty { "Incoming call" }
            a.presentation = Presentation.EXPANDED
            a.trailing = Trailing.Dot(GREEN)
            // Phone-app calls are answered through Telecom. Calls from other apps (WhatsApp, Telegram, Signal...) are
            // not Telecom calls: acceptRingingCall does nothing for them, so their own Answer action must be sent, and
            // if that can't start, open the call screen (full-screen intent, then the notification itself).
            val systemCall = d.pkg in dialerPackages
            if (canTelecom && systemCall) btns = btns.map { b ->
                if (b.color != GREEN) b
                else ActionButton(b.label, b.color, b.icon) {
                    try { telecom.acceptRingingCall() } catch (_: Exception) { b.run() }
                }
            }
            if (!systemCall) btns = btns.map { b ->
                if (b.color != GREEN) b
                else ActionButton(b.label, b.color, b.icon) {
                    val act = d.actions.firstOrNull { it.title?.toString() == b.label.toString() && it.actionIntent != null }
                    val ok = act?.actionIntent?.let { Intents.send(ctx, it) } ?: false
                    if (!ok) {
                        val fs = d.n.fullScreenIntent
                        if (fs == null || !Intents.send(ctx, fs)) open(d)()
                    }
                }
            }
            if (btns.none { it.color == GREEN } && canTelecom && systemCall)
                btns = btns + ActionButton("Answer", GREEN) { try { telecom.acceptRingingCall() } catch (_: Exception) {} }
            if (btns.none { it.color == GREEN } && !systemCall)
                btns = btns + ActionButton("Answer", GREEN) {
                    val fs = d.n.fullScreenIntent
                    if (fs == null || !Intents.send(ctx, fs)) open(d)()
                }
            if (btns.none { it.color == RED } && canTelecom && android.os.Build.VERSION.SDK_INT >= 28)
                btns = listOf(ActionButton("Decline", RED) { try { telecom.endCall() } catch (_: Exception) {} }) + btns
            // order: red (decline) first, green (answer) last, like a phone UI
            btns = btns.sortedBy { if (it.color == RED) 0 else if (it.color == GREEN) 2 else 1 }
        } else {
            a.subtitle = d.text.ifEmpty { "On call" }
            a.trailing = if (d.isTimer) Trailing.Timer(d.whenMs, false) else Trailing.Dot(GREEN)
            if (btns.none { it.color == RED } && canTelecom && android.os.Build.VERSION.SDK_INT >= 28)
                btns = btns + ActionButton("Hang up", RED) { try { telecom.endCall() } catch (_: Exception) {} }
            // The call notification normally only offers Hang up, so add Mute and Speaker (unless it has its own).
            fun has(word: String) = btns.any { it.label.toString().contains(word, ignoreCase = true) }
            val refresh = { IslandController.post(call(d)) }
            val controls = ArrayList<ActionButton>()
            if (!has("mute")) {
                val on = CallControls.muted(ctx)
                controls += ActionButton(if (on) "Unmute" else "Mute", if (on) BLUE else 0) { CallControls.toggleMute(ctx); refresh() }
            }
            // Audio output: a menu of the outputs the phone has (earpiece, speaker, headphones, Bluetooth).
            if (!has("speaker")) {
                val kind = CallControls.currentKind(ctx)
                controls += ActionButton(kind.label + " \u25BE", if (kind != CallControls.Kind.EARPIECE) BLUE else 0) {
                    routeMenu.add(d.key); refresh()
                }
            }
            btns = controls + btns
            if (d.key in routeMenu) btns = routeButtons(d, a, refresh)
        }
        a.buttons = btns
        return a
    }

    /** Calls whose audio-output menu is open (it replaces the button row until a choice is made). */
    private val routeMenu = HashSet<String>()

    private fun routeButtons(d: NotifData, a: IslandActivity, refresh: () -> Unit): List<ActionButton> {
        val current = CallControls.currentKind(ctx)
        val options = CallControls.routes(ctx).map { r ->
            ActionButton(r.label, if (r.kind == current) BLUE else 0) {
                routeMenu.remove(d.key)
                CallControls.setRoute(ctx, r)
                handler.postDelayed({
                    if (CallControls.currentKind(ctx) == r.kind) refresh()
                    else {
                        // Android only lets the phone app change a call's audio route, so say so and open it
                        refresh()
                        android.widget.Toast.makeText(ctx, "This phone only lets the call screen change the audio output", android.widget.Toast.LENGTH_LONG).show()
                        IslandController.open(a)
                    }
                }, 400)     // the audio route takes a moment to change
            }
        }
        return options + ActionButton("Back") { routeMenu.remove(d.key); refresh() }
    }

    // ---- Alarm ringing ----

    fun alarm(d: NotifData): IslandActivity {
        val a = IslandActivity(id(d), ActivityType.ALARM)
        a.priority = 190
        a.title = d.title.ifEmpty { "Alarm" }
        a.subtitle = d.text
        a.accent = YELLOW
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        a.trailing = Trailing.Dot(YELLOW)
        a.buttons = buttons(d)
        a.presentation = Presentation.EXPANDED
        a.anim = AnimStyle.PULSE
        a.onOpen = open(d)
        return a
    }

    // ---- Timer / stopwatch ----

    fun timer(d: NotifData): IslandActivity {
        val a = IslandActivity(id(d), ActivityType.TIMER)
        a.title = d.title.ifEmpty { d.appLabel }
        a.subtitle = d.text
        a.accent = ORANGE
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        a.trailing = Trailing.Timer(d.whenMs, d.countDown)
        a.buttons = buttons(d)
        a.presentation = Presentation.BANNER
        a.onOpen = open(d)
        return a
    }

    // ---- Samsung Clock timer / stopwatch ----

    /**
     * Samsung Clock has no standard chronometer fields, so build the activity from its custom view.
     * Running: a live count (down for the timer, up for the stopwatch). Paused: the frozen time.
     */
    fun samsungClock(d: NotifData): IslandActivity? {
        val isTimer = d.channelId?.contains("timer") == true
        val chrono = d.samsungChronometer()
        val paused = d.actions.any { it.title?.toString()?.equals("Resume", ignoreCase = true) == true }
        val a = IslandActivity(id(d), ActivityType.TIMER)
        a.title = d.title.ifEmpty { if (isTimer) "Timer" else "Stopwatch" }
        a.subtitle = d.samsungSecondary.ifEmpty { if (paused) "Paused" else "" }
        a.accent = ORANGE
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        a.trailing = when {
            paused -> Trailing.Label((chrono?.text ?: d.text).ifEmpty { "Paused" })
            chrono != null -> Trailing.Timer(chrono.wallBaseMs, chrono.countDown)
            d.text.isNotEmpty() -> Trailing.Label(d.text)
            else -> return null
        }
        a.buttons = buttons(d)
        a.presentation = Presentation.BANNER
        a.onOpen = open(d)
        return a
    }

    // ---- Navigation ----

    fun navigation(d: NotifData): IslandActivity {
        val a = IslandActivity(id(d), ActivityType.NAVIGATION)
        a.title = d.title.ifEmpty { d.appLabel }
        a.subtitle = listOf(d.bigText.ifEmpty { d.text }, d.subText).filter { it.isNotEmpty() }.joinToString("\n")
        a.accent = GREEN
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        a.trailing = shortDistance(d)?.let { Trailing.Label(it) } ?: Trailing.None
        a.buttons = buttons(d)
        a.presentation = Presentation.BANNER
        a.onOpen = open(d)
        return a
    }

    private val distanceRe = Regex("\\d+(?:[.,]\\d+)?\\s?(?:km|mi|ft|yd|m)\\b", RegexOption.IGNORE_CASE)

    /** The collapsed pill only has room for a short value: the distance to the next turn, if we can find one. */
    private fun shortDistance(d: NotifData): String? {
        for (s in listOf(d.title, d.text, d.subText)) distanceRe.find(s)?.let { return it.value }
        return d.title.takeIf { it.isNotEmpty() && it.length <= 8 }
    }

    // ---- Progress / Live Updates / delivery / ride ----

    fun progress(d: NotifData): IslandActivity {
        val a = IslandActivity(id(d), ActivityType.LIVE)
        a.title = d.title.ifEmpty { d.appLabel }
        a.subtitle = listOf(d.bigText.ifEmpty { d.text }, d.subText).filter { it.isNotEmpty() }.joinToString(" · ")
        a.accent = BLUE
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        val f = d.progressFraction
        a.trailing = when {
            d.criticalText.isNotEmpty() -> Trailing.Label(d.criticalText)
            d.hasProgress -> Trailing.Ring(f)
            d.isTimer -> Trailing.Timer(d.whenMs, d.countDown)
            else -> Trailing.None
        }
        a.progress = f
        a.buttons = buttons(d)
        a.reply = reply(d)
        a.presentation = Presentation.BANNER
        a.onOpen = open(d)
        return a
    }

    // ---- Anything else the user wants live, or a normal peek ----

    /** [live] true: persistent while ongoing / long-lived banner; false: short peek. */
    fun generic(d: NotifData, live: Boolean): IslandActivity {
        val type = if (live && d.ongoing) ActivityType.LIVE else ActivityType.NOTIFICATION
        val a = IslandActivity(id(d), type)
        a.title = d.title.ifEmpty { d.appLabel }
        a.subtitle = d.subtitle
        a.icon = d.factory { d.appIcon() ?: d.largeIcon() }
        a.buttons = buttons(d)
        a.reply = reply(d)
        a.onOpen = open(d)
        a.expandable = a.buttons.isNotEmpty() || a.reply != null
        a.presentation = Presentation.BANNER
        if (type == ActivityType.NOTIFICATION) a.transientMs = if (live) 8000 else 4000
        return a
    }
}
