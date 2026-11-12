package app.orbit.cameraisland.island

/** A self-contained source of activities (battery, system alerts, ...) started/stopped by the controller. */
interface IslandModule {
    fun start()
    fun stop()
    /** A preference changed; re-evaluate anything derived from it. */
    fun onPrefChanged(key: String?) {}
}
