package app.orbit.cameraisland.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.orbit.cameraisland.island.dp
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * One app picker for three jobs:
 *  - MODES:  per-app notification behaviour (auto / peek / live / off)  [key unused]
 *  - MULTI:  a set of apps stored under [key] (e.g. hide island in these apps)
 *  - SINGLE: one app stored under [key] (e.g. "launch app" gesture)
 */
class AppListActivity : Activity() {

    private class App(val pkg: String, val label: String, val icon: Drawable)

    private val all = ArrayList<App>()
    private val shown = ArrayList<App>()
    private lateinit var kind: String
    private lateinit var key: String
    private val prefs by lazy { Prefs.get(this) }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(i: Int) = shown[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
            val row = (convert as? LinearLayout) ?: makeRow()
            val app = shown[i]
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            val texts = row.getChildAt(1) as LinearLayout
            (texts.getChildAt(0) as TextView).text = app.label
            (texts.getChildAt(1) as TextView).text = app.pkg
            (row.getChildAt(2) as TextView).text = statusOf(app.pkg)
            return row
        }
    }

        private fun dpi(v: Int) = dp(v.toFloat()).roundToInt()

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        background = Ui.ripple(null)
        addView(ImageView(this@AppListActivity), LinearLayout.LayoutParams(dpi(42), dpi(42)))
        val t = LinearLayout(this@AppListActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(this@AppListActivity, 16f, Ui.TEXT).apply { isSingleLine = true })
            addView(Ui.text(this@AppListActivity, 11f, Ui.TEXT2).apply { isSingleLine = true })
        }
        addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(14); marginEnd = dpi(8) })
        addView(Ui.text(this@AppListActivity, 13f, Ui.ACCENT, medium = true))
    }

    private fun multiSet() = prefs.getStringSet(key, emptySet()) ?: emptySet()

    private fun statusOf(pkg: String): String = when (kind) {
        "modes" -> when (prefs.getString(Prefs.APP_MODE_PREFIX + pkg, "auto")) {
            "peek" -> "Peek"; "live" -> "Live"; "off" -> "Off"; else -> "Auto"
        }
        "multi" -> if (pkg in multiSet()) "Hidden" else ""
        else -> if (prefs.getString(key, null) == pkg) "✓" else ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = intent.getStringExtra("kind") ?: "modes"
        key = intent.getStringExtra("key") ?: ""
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        col.addView(Ui.topBar(this, intent.getStringExtra("title") ?: "Apps"))
        intent.getStringExtra("hint")?.let {
            col.addView(Ui.text(this, 13f, Ui.TEXT2).apply { text = it; setLineSpacing(0f, 1.15f); setPadding(dpi(20), 0, dpi(20), dpi(10)) })
        }
        val search = EditText(this).apply {
            hint = "Search apps"; setHintTextColor(Ui.TEXT2); setTextColor(Ui.TEXT); isSingleLine = true
            textSize = 15f
            background = Ui.round(this@AppListActivity, Ui.SURFACE, 14f)
            setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
        }
        col.addView(search, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(16), 0, dpi(16), dpi(8)) })
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Ui.DIVIDER)
            dividerHeight = 1
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnItemClickListener { _, _, i, _ -> onPick(shown[i]) }
        }
        col.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padForInsets(this, col)
        setContentView(col)
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { all.addAll(apps); filter(search.text.toString()) }
        }
    }

    private fun filter(q: String) {
        shown.clear()
        shown.addAll(all.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) })
        adapter.notifyDataSetChanged()
    }

    private fun onPick(app: App) {
        when (kind) {
            "modes" -> {
                val opts = listOf("auto" to "Auto (smart)", "peek" to "Peek only", "live" to "Live activity", "off" to "Off")
                val cur = prefs.getString(Prefs.APP_MODE_PREFIX + app.pkg, "auto")
                AlertDialog.Builder(this).setTitle(app.label)
                    .setSingleChoiceItems(opts.map { it.second }.toTypedArray(), opts.indexOfFirst { it.first == cur }) { d, i ->
                        val e = prefs.edit()
                        if (opts[i].first == "auto") e.remove(Prefs.APP_MODE_PREFIX + app.pkg) else e.putString(Prefs.APP_MODE_PREFIX + app.pkg, opts[i].first)
                        e.apply(); adapter.notifyDataSetChanged(); d.dismiss()
                    }.show()
            }
            "multi" -> {
                val s = HashSet(multiSet())
                if (!s.add(app.pkg)) s.remove(app.pkg)
                prefs.edit().putStringSet(key, s).apply()
                adapter.notifyDataSetChanged()
            }
            else -> {
                prefs.edit().putString(key, app.pkg).apply()
                finish()
            }
        }
    }

    companion object {
        fun open(from: Activity, kind: String, key: String, title: String, hint: String? = null) {
            from.startActivity(Intent(from, AppListActivity::class.java)
                .putExtra("kind", kind).putExtra("key", key).putExtra("title", title).putExtra("hint", hint))
        }
    }
}
