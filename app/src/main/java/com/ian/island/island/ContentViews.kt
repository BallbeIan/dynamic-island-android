package com.ian.island.island

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView

private fun iconOf(a: IslandActivity): android.graphics.drawable.Drawable =
    a.icon?.invoke() ?: ColorDrawable(Color.DKGRAY)

/** Small 6dp coloured dots (privacy / recording indicators) drawn inside the collapsed pill. */
class DotsView(ctx: Context) : View(ctx) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var colors: List<Int> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val d = context.dpi(7)
        setMeasuredDimension(if (colors.isEmpty()) 0 else colors.size * d + (colors.size - 1) * context.dpi(3), d)
    }

    override fun onDraw(c: android.graphics.Canvas) {
        val d = context.dp(7f)
        colors.forEachIndexed { i, col ->
            paint.color = col
            c.drawCircle(d / 2 + i * (d + context.dp(3f)), height / 2f, d / 2, paint)
        }
    }
}

/** The small pill: [icon] ........ [trailing]. */
class CollapsedView(ctx: Context) : LinearLayout(ctx) {
    val lead = ImageView(ctx)
    val wave = WaveView(ctx)
    val ring = RingView(ctx)
    val timer = TextView(ctx)
    val label = TextView(ctx)
    val dot = View(ctx)
    val dots = DotsView(ctx)
    private val trailingHost = FrameLayout(ctx)
    private var timerSpec: Trailing.Timer? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        lead.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(lead, 0f, oval = true)
        addView(lead, LayoutParams(ctx.dpi(24), ctx.dpi(24)))
        addView(View(ctx), LayoutParams(0, 1, 1f))
        styleText(timer, 13f, Color.WHITE, bold = true)
        timer.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        styleText(label, 12f, Color.WHITE, bold = true)
        label.maxWidth = ctx.dpi(64)
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        trailingHost.addView(wave, FrameLayout.LayoutParams(ctx.dpi(20), ctx.dpi(14), Gravity.CENTER))
        trailingHost.addView(ring, FrameLayout.LayoutParams(ctx.dpi(20), ctx.dpi(20), Gravity.CENTER))
        trailingHost.addView(timer, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        trailingHost.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        trailingHost.addView(dot, FrameLayout.LayoutParams(ctx.dpi(10), ctx.dpi(10), Gravity.CENTER))
        addView(dots, LayoutParams(-2, -2).apply { marginEnd = ctx.dpi(6) })
        addView(trailingHost, LayoutParams(-2, -2))
    }

    fun bind(a: IslandActivity, accent: Int, indicatorDots: List<Int>) {
        lead.setImageDrawable(iconOf(a))
        lead.visibility = if (a.type == ActivityType.IDLE) INVISIBLE else VISIBLE
        for (v in listOf<View>(wave, ring, timer, label, dot)) v.visibility = GONE
        timerSpec = null
        when (val t = a.trailing) {
            Trailing.None -> {}
            Trailing.Wave -> { wave.visibility = VISIBLE; wave.barColor = accent; wave.playing = a.media?.playing == true }
            is Trailing.Timer -> { timer.visibility = VISIBLE; timerSpec = t; timer.text = timerText(t) }
            is Trailing.Ring -> { ring.visibility = VISIBLE; ring.color = accent; ring.fraction = t.fraction }
            is Trailing.Label -> { label.visibility = VISIBLE; label.text = t.text }
            is Trailing.Dot -> {
                dot.visibility = VISIBLE
                (dot.background as GradientDrawable).setColor(t.color)
            }
        }
        dots.colors = indicatorDots
    }

    val hasTimer get() = timerSpec != null
    fun tick() { timerSpec?.let { timer.text = timerText(it) } }

    /** Natural width of everything right of the centre, so the pill can keep the camera cutout clear. */
    fun trailingWidthPx(): Int {
        val spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        trailingHost.measure(spec, spec)
        var w = trailingHost.measuredWidth
        if (dots.colors.isNotEmpty()) { dots.measure(spec, spec); w += dots.measuredWidth + context.dpi(6) }
        return w
    }
}

/** Banner: [icon] title / subtitle, plus an inline slider for volume. */
class BannerView(ctx: Context, private val requestFocus: () -> Unit) : LinearLayout(ctx) {
    val icon = ImageView(ctx)
    private val title = TextView(ctx)
    private val sub = TextView(ctx)
    private val value = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val seekRow = LinearLayout(ctx)
    private var volume: VolumeInfo? = null

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(16), ctx.dpi(11), ctx.dpi(18), ctx.dpi(11))
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(icon, 0f, oval = true)
        row.addView(icon, LayoutParams(ctx.dpi(40), ctx.dpi(40)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        styleText(title, 14f, Color.WHITE, bold = true)
        styleText(sub, 13f, GRAY_TEXT)
        texts.addView(title); texts.addView(sub)
        row.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(12) })
        styleText(value, 14f, Color.WHITE, bold = true)
        row.addView(value, LayoutParams(-2, -2).apply { marginStart = ctx.dpi(8) })
        addView(row)

        seekRow.orientation = HORIZONTAL
        seekRow.gravity = Gravity.CENTER_VERTICAL
        seek.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(70, 255, 255, 255))
        seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                volume?.setLevel(p)
                value.text = pct(p, seek.max)
                IslandController.touchTransient()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seekRow.addView(seek, LayoutParams(0, -2, 1f))
        addView(seekRow, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(6) })
    }

    /** Pushes content below the camera cutout. */
    fun setTopInset(px: Int) = setPadding(context.dpi(16), context.dpi(11) + px, context.dpi(18), context.dpi(11))

    private fun pct(p: Int, max: Int) = if (max <= 0) "" else "${(p * 100f / max).toInt()}%"

    fun bind(a: IslandActivity, accent: Int) {
        icon.setImageDrawable(iconOf(a))
        title.text = a.title
        sub.text = a.subtitle
        sub.visibility = if (a.subtitle.isEmpty()) GONE else VISIBLE
        val v = a.volume
        volume = v
        if (a.kind == Kind.VOLUME && v != null) {
            seekRow.visibility = VISIBLE
            seek.max = v.max
            seek.progress = v.level
            seek.progressTintList = ColorStateList.valueOf(accent)
            value.visibility = VISIBLE
            value.text = if (v.muted) "Muted" else pct(v.level, v.max)
        } else {
            seekRow.visibility = GONE
            value.visibility = GONE
        }
    }
}

/** Expanded player: art, title/artist, draggable progress, transport controls. */
class MediaExpandedView(ctx: Context) : LinearLayout(ctx) {
    val header = LinearLayout(ctx)
    private val art = ImageView(ctx)
    private val title = TextView(ctx)
    private val artist = TextView(ctx)
    private val wave = WaveView(ctx)
    private val elapsed = TextView(ctx)
    private val duration = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val progressRow = LinearLayout(ctx)
    private val prev = ImageButton(ctx)
    private val play = ImageButton(ctx)
    private val next = ImageButton(ctx)
    private var info: MediaInfo? = null
    private var dragging = false

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(18), ctx.dpi(16), ctx.dpi(18), ctx.dpi(8))
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(art, ctx.dp(12f))
        header.addView(art, LayoutParams(ctx.dpi(56), ctx.dpi(56)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        styleText(title, 15f, Color.WHITE, bold = true)
        title.ellipsize = TextUtils.TruncateAt.MARQUEE
        title.marqueeRepeatLimit = -1
        title.isSelected = true
        styleText(artist, 13f, GRAY_TEXT)
        texts.addView(title); texts.addView(artist)
        header.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(14); marginEnd = ctx.dpi(10) })
        header.addView(wave, LayoutParams(ctx.dpi(24), ctx.dpi(18)))
        addView(header)

        progressRow.orientation = HORIZONTAL
        progressRow.gravity = Gravity.CENTER_VERTICAL
        styleText(elapsed, 11f, GRAY_TEXT)
        styleText(duration, 11f, GRAY_TEXT)
        seek.max = 1000
        seek.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(60, 255, 255, 255))
        seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        seek.splitTrack = false
        seek.setPadding(ctx.dpi(8), 0, ctx.dpi(8), 0)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val m = info ?: return
                if (fromUser) elapsed.text = fmtClock(m.duration * p / 1000)
            }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                dragging = false
                val m = info ?: return
                IslandController.seekMedia(m.duration * (s?.progress ?: 0) / 1000)
            }
        })
        progressRow.addView(elapsed)
        progressRow.addView(seek, LayoutParams(0, -2, 1f))
        progressRow.addView(duration)
        addView(progressRow, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(10) })

        val controls = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        prev.setImageResource(android.R.drawable.ic_media_previous)
        next.setImageResource(android.R.drawable.ic_media_next)
        play.setImageResource(android.R.drawable.ic_media_play)
        controls.addView(prev, control(prev, 46) { IslandController.mediaPrevious() })
        controls.addView(play, control(play, 56) { IslandController.mediaPlayPause() })
        controls.addView(next, control(next, 46) { IslandController.mediaNext() })
        addView(controls, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(2) })
    }

    fun setTopInset(px: Int) = setPadding(context.dpi(18), context.dpi(16) + px, context.dpi(18), context.dpi(8))

    private fun control(b: ImageButton, sizeDp: Int, onClick: () -> Unit): LayoutParams {
        b.background = null
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.setColorFilter(Color.WHITE)
        val pad = context.dpi(sizeDp / 5)
        b.setPadding(pad, pad, pad, pad)
        b.setOnClickListener { onClick() }
        return LayoutParams(context.dpi(sizeDp), context.dpi(sizeDp)).apply {
            marginStart = context.dpi(14); marginEnd = context.dpi(14)
        }
    }

    fun bind(a: IslandActivity, accent: Int) {
        val m = a.media ?: return
        info = m
        art.setImageDrawable(iconOf(a))
        wave.barColor = accent
        wave.playing = m.playing
        seek.progressTintList = ColorStateList.valueOf(accent)
        if (title.text.toString() != m.title) title.text = m.title
        artist.text = m.artist
        artist.visibility = if (m.artist.isEmpty()) GONE else VISIBLE
        play.setImageResource(if (m.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val a2 = if (m.live) 1f else 0.35f
        prev.alpha = a2; next.alpha = a2
        prev.isEnabled = m.live; next.isEnabled = m.live
        seek.isEnabled = m.live && m.canSeek
        tick()
    }

    fun tick() {
        val m = info ?: return
        if (m.duration <= 0) { progressRow.visibility = INVISIBLE; return }
        progressRow.visibility = VISIBLE
        if (dragging) return
        val pos = m.currentPosition()
        seek.progress = ((pos * 1000) / m.duration).toInt()
        elapsed.text = fmtClock(pos)
        duration.text = fmtClock(m.duration)
    }
}

/** Expanded view for everything else: header, optional progress, action buttons, inline reply. */
class GenericExpandedView(ctx: Context, private val requestFocus: () -> Unit, private val releaseFocus: () -> Unit) : LinearLayout(ctx) {
    val header = LinearLayout(ctx)
    private val icon = ImageView(ctx)
    private val title = TextView(ctx)
    private val sub = TextView(ctx)
    private val timer = TextView(ctx)
    private val ring = RingView(ctx)
    private val label = TextView(ctx)
    private val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val buttons = LinearLayout(ctx)
    private val replyRow = LinearLayout(ctx)
    private val replyField = EditText(ctx)
    private val replySend = TextView(ctx)
    private var timerSpec: Trailing.Timer? = null
    private var reply: ReplyAction? = null

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(16), ctx.dpi(14), ctx.dpi(16), ctx.dpi(12))
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.isClickable = true
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(icon, 0f, oval = true)
        header.addView(icon, LayoutParams(ctx.dpi(48), ctx.dpi(48)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        styleText(title, 16f, Color.WHITE, bold = true)
        styleText(sub, 13f, GRAY_TEXT)
        sub.isSingleLine = false
        sub.maxLines = 3
        sub.ellipsize = TextUtils.TruncateAt.END
        texts.addView(title); texts.addView(sub)
        header.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(12); marginEnd = ctx.dpi(8) })
        styleText(timer, 22f, Color.WHITE, bold = true)
        styleText(label, 14f, Color.WHITE, bold = true)
        label.maxWidth = ctx.dpi(100)
        header.addView(timer); header.addView(label)
        header.addView(ring, LayoutParams(ctx.dpi(34), ctx.dpi(34)))
        addView(header, LayoutParams(-1, -2))

        bar.max = 1000
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(60, 255, 255, 255))
        addView(bar, LayoutParams(-1, ctx.dpi(6)).apply { topMargin = ctx.dpi(10) })

        buttons.orientation = HORIZONTAL
        buttons.gravity = Gravity.CENTER
        addView(buttons, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(12) })

        replyRow.orientation = HORIZONTAL
        replyRow.gravity = Gravity.CENTER_VERTICAL
        replyField.setTextColor(Color.WHITE)
        replyField.setHintTextColor(GRAY_TEXT)
        replyField.textSize = 14f
        replyField.isSingleLine = true
        replyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyField.imeOptions = EditorInfo.IME_ACTION_SEND
        replyField.setPadding(ctx.dpi(14), 0, ctx.dpi(14), 0)
        replyField.background = GradientDrawable().apply { cornerRadius = ctx.dp(20f); setColor(Color.argb(40, 255, 255, 255)) }
        // Overlay windows are not focusable by default; ask for focus the moment the field is touched.
        replyField.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) { requestFocus(); v.requestFocus() }
            false
        }
        replyField.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendReply(); true } else false }
        styleText(replySend, 14f, Color.WHITE, bold = true)
        replySend.text = "Send"
        replySend.gravity = Gravity.CENTER
        replySend.background = GradientDrawable().apply { cornerRadius = ctx.dp(20f); setColor(Color.rgb(10, 132, 255)) }
        replySend.setOnClickListener { sendReply() }
        replyRow.addView(replyField, LayoutParams(0, ctx.dpi(40), 1f))
        replyRow.addView(replySend, LayoutParams(ctx.dpi(64), ctx.dpi(40)).apply { marginStart = ctx.dpi(8) })
        addView(replyRow, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(10) })
    }

    fun setTopInset(px: Int) = setPadding(context.dpi(16), context.dpi(14) + px, context.dpi(16), context.dpi(12))

    private fun sendReply() {
        val text = replyField.text.toString().trim()
        if (text.isEmpty()) return
        reply?.send?.invoke(text)
        replyField.setText("")
        (context.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
            ?.hideSoftInputFromWindow(replyField.windowToken, 0)
        releaseFocus()
    }

    fun bind(a: IslandActivity, accent: Int) {
        icon.setImageDrawable(iconOf(a))
        title.text = a.title
        sub.text = a.subtitle
        sub.visibility = if (a.subtitle.isEmpty()) GONE else VISIBLE
        header.setOnClickListener { a.onOpen?.invoke() }
        for (v in listOf<View>(timer, ring, label)) v.visibility = GONE
        timerSpec = null
        when (val t = a.trailing) {
            is Trailing.Timer -> { timer.visibility = VISIBLE; timerSpec = t; timer.text = timerText(t) }
            is Trailing.Ring -> { ring.visibility = VISIBLE; ring.color = accent; ring.fraction = t.fraction }
            is Trailing.Label -> { label.visibility = VISIBLE; label.text = t.text }
            else -> {}
        }
        if (a.progress >= 0f) {
            bar.visibility = VISIBLE
            bar.progress = (a.progress * 1000).toInt()
            bar.progressTintList = ColorStateList.valueOf(accent)
        } else bar.visibility = GONE

        buttons.removeAllViews()
        buttons.visibility = if (a.buttons.isEmpty()) GONE else VISIBLE
        val compact = a.buttons.size > 3
        a.buttons.forEachIndexed { i, b ->
            buttons.addView(makeButton(b, accent, compact), LayoutParams(0, context.dpi(if (compact) 38 else 44), 1f).apply {
                if (i > 0) marginStart = context.dpi(8)
            })
        }
        reply = a.reply
        replyRow.visibility = if (a.reply == null) GONE else VISIBLE
        replyField.hint = a.reply?.hint ?: ""
    }

    private fun makeButton(b: ActionButton, accent: Int, compact: Boolean): TextView {
        val bg = when (b.color) {
            0 -> Color.argb(46, 255, 255, 255)
            else -> b.color
        }
        return TextView(context).apply {
            text = b.label
            styleText(this, if (compact) 12f else 14f, Color.WHITE, bold = true)
            gravity = Gravity.CENTER
            setPadding(context.dpi(8), 0, context.dpi(8), 0)
            background = GradientDrawable().apply { cornerRadius = context.dp(22f); setColor(bg) }
            b.icon?.invoke()?.let {
                val s = context.dpi(18)
                it.setBounds(0, 0, s, s)
                it.setTint(Color.WHITE)
                setCompoundDrawables(it, null, null, null)
                compoundDrawablePadding = context.dpi(6)
            }
            isClickable = true
            setOnClickListener { b.run() }
        }
    }

    val hasTimer get() = timerSpec != null
    fun tick() { timerSpec?.let { timer.text = timerText(it) } }
    fun clearReply() { replyField.setText(""); }
}
