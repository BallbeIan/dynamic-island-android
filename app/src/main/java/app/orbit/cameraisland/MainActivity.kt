package app.orbit.cameraisland

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.widget.LinearLayout
import app.orbit.cameraisland.island.IconDrawable
import app.orbit.cameraisland.island.IslandController
import app.orbit.cameraisland.settings.Prefs
import app.orbit.cameraisland.settings.SectionActivity
import app.orbit.cameraisland.settings.SetupChecklist
import app.orbit.cameraisland.settings.Ui
import app.orbit.cameraisland.settings.UiKit

/** Home screen: header, setup checklist, master switch, and one row per settings section. */
class MainActivity : Activity() {

    private lateinit var checklist: SetupChecklist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = UiKit(this)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dpi(6), ui.dpi(20), ui.dpi(6), ui.dpi(4))
            addView(Ui.text(this@MainActivity, 36f, Ui.TEXT, medium = true).apply { text = "Orbit" })
            addView(Ui.text(this@MainActivity, 15f, Ui.TEXT2).apply {
                text = "A camera island for your Android"; setPadding(0, ui.dpi(2), 0, 0)
            })
        }
        ui.raw(hero, 0)

        ui.section("Setup")
        checklist = SetupChecklist(ui)
        checklist.build()

        ui.section("Orbit")
        ui.toggle("Orbit enabled", Prefs.ENABLED, true, "Show the island on screen")

        ui.section("Customise")
        for (s in SectionActivity.Section.values()) {
            ui.navRow(s.title, s.subtitle, IconDrawable(s.icon, Color.WHITE), s.color) {
                startActivity(Intent(this, SectionActivity::class.java).putExtra("section", s.name))
            }
        }

        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        ui.raw(Ui.text(this, 12f, Ui.TEXT2).apply {
            text = "Orbit $version"
            gravity = android.view.Gravity.CENTER
            setPadding(0, ui.dpi(8), 0, 0)
        }, 20)

        setContentView(ui.root)
    }

    override fun onResume() {
        super.onResume()
        checklist.refresh()
        if (SetupChecklist.listenerEnabled(this)) {
            val svc = IslandService.instance
            if (svc != null) svc.refresh()
            else NotificationListenerService.requestRebind(ComponentName(this, IslandService::class.java))
        }
        IslandController.recompute()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checklist.refresh()
    }
}
