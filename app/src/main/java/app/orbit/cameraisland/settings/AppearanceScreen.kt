package app.orbit.cameraisland.settings

object AppearanceScreen {
    fun build(ui: UiKit) {
        ui.raw(PillPreview(ui.act), 8)

        if (ScreenProfile.foldable(ui.act)) {
            ui.section("Screen")
            ui.note("Editing the ${ScreenProfile.label(ui.act)}. On a foldable these settings are saved separately for each screen: fold or unfold the phone and open Island there to change the other screen.")
        }

        ui.section("Shape")
        ui.slider("Pill height", Prefs.PILL_HEIGHT, 36, 28, 56)
        ui.slider("Pill width", Prefs.WIDTH, Prefs.DEFAULT_WIDTH, 100, 320)
        ui.slider("Corner roundness", Prefs.CORNER, 100, 0, 100, "%")
        ui.section("Colour")
        ui.colors("Background", Prefs.BG_COLOR, android.graphics.Color.BLACK, Palette.BG)
        ui.slider("Background opacity", Prefs.BG_ALPHA, 100, 30, 100, "%")
        ui.choice("Outline", Prefs.OUTLINE, "none", listOf("none" to "None", "accent" to "Accent colour"))
        ui.toggle("Glow in the accent colour", Prefs.GLOW, false)
        ui.toggle("Match accent colours to the wallpaper", Prefs.WALLPAPER_ACCENT, false,
            "Uses your wallpaper's main colour instead of the album-art / activity colour (Android 8.1+).")
        ui.section("Position")
        ui.toggle("Align to the camera automatically", Prefs.AUTO_CAMERA, true,
            "Finds where the front camera is on this screen (punch hole, notch or under-display camera) and centres the island on it.")
        ui.slider("Distance from top", Prefs.OFFSET, Prefs.DEFAULT_OFFSET, 0, 120)
        ui.slider("Fine-tune sideways", Prefs.XOFFSET, 0, -150, 150)
        ui.section("Double pill")
        ui.toggle("Split into two pills", Prefs.DOUBLE_PILL, true,
            "When two things are active (e.g. music + a timer) a second small pill splits off. Tap it to make it the main one.")
        ui.choice("Second pill side", Prefs.DOUBLE_SIDE, "right", listOf("right" to "Right", "left" to "Left"))
        ui.slider("Gap between pills", Prefs.DOUBLE_GAP, 6, 0, 24)
    }
}
