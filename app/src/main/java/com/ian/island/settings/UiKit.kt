package com.ian.island.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.ian.island.island.dpi

/**
 * Programmatic settings UI: a scrolling column of grouped cards. [section] starts a labelled card;
 * every row added after it lands in that card, separated by hairline dividers.
 * Pass a [title] for a sub-screen with a back button, or null to build a custom header (home screen).
 */
class UiKit(val act: Activity, title: String? = null) {
    val prefs: SharedPreferences = Prefs.get(act)
    /** Per-screen values for the position / look settings (separate on foldables). */
    private val scopeKey: String? by lazy { ScreenProfile.scopeOrNull(act) }
    private val sp = ScreenPrefs(prefs) { scopeKey }

    /** Put this in setContentView. */
    val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
    val col = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16), dpi(4), dpi(16), dpi(32))
    }
    val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(col) }

    private var card: LinearLayout? = null
    private val refreshers = ArrayList<() -> Unit>()

    init {
        if (title != null) root.addView(Ui.topBar(act, title))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padForInsets(act, root)
    }

    fun dpi(v: Int) = act.dpi(v)

    /** Re-reads values shown in value rows (call from onResume). */
    fun refresh() = refreshers.forEach { it() }

    // ---------------- structure ----------------

    private fun hostCard(): LinearLayout = card ?: LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(act, Ui.SURFACE, 18f)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        col.addView(this, LinearLayout.LayoutParams(-1, -2))
        card = this
    }

    /** Adds a row to the current card (starting one if needed). */
    fun addRow(v: View, divider: Boolean = true) {
        val c = hostCard()
        if (divider && c.childCount > 0) {
            c.addView(View(act).apply { setBackgroundColor(Ui.DIVIDER) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = dpi(16) })
        }
        c.addView(v, LinearLayout.LayoutParams(-1, -2))
    }

    /** Adds a view outside any card (e.g. the live preview). */
    fun raw(v: View, topDp: Int = 8) {
        card = null
        col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(topDp) })
    }

    fun add(v: View) = raw(v, 0)

    fun section(title: String) {
        card = null
        col.addView(Ui.text(act, 12f, Ui.TEXT2, medium = true).apply {
            text = title.uppercase()
            letterSpacing = 0.08f
            setPadding(dpi(6), dpi(24), 0, dpi(8))
        })
    }

    fun title(text: String) = raw(Ui.text(act, 28f, Ui.TEXT, medium = true).apply { this.text = text }, 0)

    /** Footnote: inside the current card, or free-standing before the first section. */
    fun note(text: String, color: Int = Ui.TEXT2) {
        val t = Ui.text(act, 13f, color).apply {
            this.text = text
            setLineSpacing(0f, 1.15f)
        }
        if (card == null) {
            t.setPadding(dpi(6), dpi(8), dpi(6), dpi(4))
            col.addView(t, LinearLayout.LayoutParams(-1, -2))
        } else {
            t.setPadding(dpi(16), dpi(12), dpi(16), dpi(14))
            addRow(t, divider = false)
        }
    }

    fun spacer(dp: Int = 12) = col.addView(View(act), LinearLayout.LayoutParams(1, dpi(dp)))

    // ---------------- row building blocks ----------------

    private fun rowBase(clickable: Boolean) = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(56)
        setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        if (clickable) { isClickable = true; background = Ui.ripple(null) }
    }

    private fun textBlock(title: String, desc: String?, titleColor: Int = Ui.TEXT): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(act, 16f, titleColor).apply { text = title })
            if (desc != null) addView(Ui.text(act, 13f, Ui.TEXT2).apply {
                text = desc; setPadding(0, dpi(2), 0, 0); setLineSpacing(0f, 1.1f)
            })
        }

    private fun valueText(text: String) = Ui.text(act, 15f, Ui.ACCENT).apply { this.text = text; maxLines = 1 }

    // ---------------- rows ----------------

    fun toggle(label: String, key: String, def: Boolean, desc: String? = null, onChange: ((Boolean) -> Unit)? = null): Switch {
        val sw = Switch(act).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(states, intArrayOf(Ui.ACCENT, Color.rgb(58, 62, 74)))
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(182, 186, 196)))
            isChecked = sp.bool(key, def)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(sp.key(key), c).apply(); onChange?.invoke(c) }
        }
        val row = rowBase(true).apply {
            addView(textBlock(label, desc), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(12) })
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
        addRow(row)
        return sw
    }

    fun slider(label: String, key: String, def: Int, min: Int, max: Int, unit: String = "dp") {
        val value = sp.int(key, def)
        val v = valueText("$value$unit")
        val bar = SeekBar(act).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(Ui.ACCENT)
            thumbTintList = ColorStateList.valueOf(Ui.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(64, 68, 80))
            splitTrack = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val n = p + min
                    v.text = "$n$unit"
                    if (fromUser) prefs.edit().putInt(sp.key(key), n).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(act, 16f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(v)
        }
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(14), dpi(16), dpi(8))
            addView(top)
            addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4); marginStart = -dpi(8); marginEnd = -dpi(8) })
        }
        addRow(row)
    }

    /** Text option stored under [key]. */
    fun choice(label: String, key: String, def: String, options: List<Pair<String, String>>, onChange: ((String) -> Unit)? = null) =
        chooser(label, def, options, { sp.str(key, def) ?: def }, { prefs.edit().putString(sp.key(key), it).apply(); onChange?.invoke(it) })

    /** Number option stored under [key] (e.g. a timeout). */
    fun longChoice(label: String, key: String, def: Long, options: List<Pair<Long, String>>) =
        chooser(label, def, options, { prefs.getLong(key, def) }, { prefs.edit().putLong(key, it).apply() })

    /** A row showing the current option on the right; tapping opens a single-choice dialog. */
    private fun <T> chooser(label: String, def: T, options: List<Pair<T, String>>, get: () -> T, set: (T) -> Unit) {
        fun current() = (options.firstOrNull { it.first == get() } ?: options.first { it.first == def }).second
        val value = valueText(current())
        val row = rowBase(true).apply {
            addView(Ui.text(act, 16f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(12) })
            addView(value)
            addView(Ui.chevron(act), LinearLayout.LayoutParams(dpi(20), dpi(20)).apply { marginStart = dpi(4) })
            setOnClickListener {
                AlertDialog.Builder(act).setTitle(label)
                    .setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == get() }) { d, i ->
                        set(options[i].first); value.text = current(); d.dismiss()
                    }.show()
            }
        }
        addRow(row)
    }

    /** Row of colour swatches. */
    fun colors(label: String, key: String, def: Int, palette: List<Int>) {
        val swatches = ArrayList<View>()
        fun mark() {
            val cur = sp.int(key, def)
            swatches.forEachIndexed { i, v ->
                (v.background as GradientDrawable).setStroke(dpi(if (palette[i] == cur) 3 else 1), if (palette[i] == cur) Color.WHITE else Ui.SURFACE2)
            }
        }
        val strip = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        palette.forEach { c ->
            val v = View(act).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                setOnClickListener { prefs.edit().putInt(sp.key(key), c).apply(); mark() }
            }
            swatches.add(v)
            strip.addView(v, LinearLayout.LayoutParams(dpi(34), dpi(34)).apply { marginEnd = dpi(10) })
        }
        mark()
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
            addView(Ui.text(act, 16f, Ui.TEXT).apply { text = label })
            addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(strip) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10) })
        }
        addRow(row)
    }

    /** Time of day stored as minutes since midnight. */
    fun time(label: String, key: String, def: Int) {
        fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val value = valueText(fmt(sp.int(key, def)))
        val row = rowBase(true).apply {
            addView(Ui.text(act, 16f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
            setOnClickListener {
                val cur = sp.int(key, def)
                android.app.TimePickerDialog(act, { _, h, m -> prefs.edit().putInt(sp.key(key), h * 60 + m).apply(); value.text = fmt(h * 60 + m) },
                    cur / 60, cur % 60, true).show()
            }
        }
        addRow(row)
    }

    /** A navigation row with a live value on the right (refreshed on resume). */
    fun valueRow(label: String, desc: String? = null, value: () -> String, onClick: () -> Unit) {
        val v = valueText(value())
        refreshers += { v.text = value() }
        val row = rowBase(true).apply {
            addView(textBlock(label, desc), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(12) })
            addView(v)
            addView(Ui.chevron(act), LinearLayout.LayoutParams(dpi(20), dpi(20)).apply { marginStart = dpi(4) })
            setOnClickListener { onClick() }
        }
        addRow(row)
    }

    /** An action row (accent-coloured label). */
    fun button(label: String, onClick: () -> Unit): View {
        val row = rowBase(true).apply {
            addView(Ui.text(act, 16f, Ui.ACCENT, medium = true).apply { text = label })
            setOnClickListener { onClick() }
        }
        addRow(row)
        return row
    }

    /** Home-screen navigation row: coloured icon badge, title, subtitle, chevron. */
    fun navRow(title: String, subtitle: String, icon: Drawable, badge: Int, onClick: () -> Unit) {
        val badgeView = ImageView(act).apply {
            background = Ui.round(act, badge, 10f)
            setImageDrawable(icon)
            val p = dpi(7)
            setPadding(p, p, p, p)
        }
        val row = rowBase(true).apply {
            minimumHeight = dpi(64)
            addView(badgeView, LinearLayout.LayoutParams(dpi(38), dpi(38)))
            addView(textBlock(title, subtitle), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(14); marginEnd = dpi(8) })
            addView(Ui.chevron(act), LinearLayout.LayoutParams(dpi(20), dpi(20)))
            setOnClickListener { onClick() }
        }
        addRow(row)
    }
}

object Palette {
    val BG = listOf(Color.BLACK, Color.rgb(28, 28, 30), Color.rgb(10, 24, 56), Color.rgb(40, 12, 48), Color.rgb(8, 40, 28), Color.rgb(60, 14, 20), Color.rgb(70, 70, 76))
    val ACCENT = listOf(
        Color.rgb(48, 209, 88), Color.rgb(10, 132, 255), Color.rgb(255, 69, 58), Color.rgb(255, 159, 10),
        Color.rgb(255, 214, 10), Color.rgb(191, 90, 242), Color.rgb(100, 210, 255), Color.rgb(255, 255, 255),
    )
}
