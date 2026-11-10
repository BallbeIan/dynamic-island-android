package com.ian.island.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.WindowManager
import com.ian.island.island.ActivityType
import com.ian.island.island.Icon
import com.ian.island.island.IconDrawable
import com.ian.island.island.Intents
import com.ian.island.island.IslandActivity
import com.ian.island.island.IslandController
import com.ian.island.island.IslandModule
import com.ian.island.island.Kind
import com.ian.island.island.Presentation
import com.ian.island.island.VolumeInfo
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Registers every system callback / broadcast (no polling) and turns changes into island alerts.
 * Initial states are ignored for a couple of seconds after start so we don't announce what was already true.
 */
class SystemAlerts(private val ctx: Context) : IslandModule {

    private val handler = Handler(Looper.getMainLooper())
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val camera = ctx.getSystemService(CameraManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var armed = false
    private val netCallbacks = ArrayList<ConnectivityManager.NetworkCallback>()
    private var torchCb: CameraManager.TorchCallback? = null
    private var cameraCb: CameraManager.AvailabilityCallback? = null
    private var recCb: AudioManager.AudioRecordingCallback? = null
    private var screenRecCb: Any? = null
    private val receivers = ArrayList<BroadcastReceiver>()

    private var camActive = false
    private var camPackage: String? = null
    private var micActive = false
    private var micApp: String? = null
    private var screenRecording = false
    private var recStart = 0L

    override fun start() {
        armed = false
        handler.postDelayed({ armed = true }, 2500)
        safe { registerNetwork() }
        safe { registerBroadcasts() }
        safe { registerTorch() }
        safe { registerCamera() }
        safe { registerMic() }
        safe { registerScreenRecording() }
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        netCallbacks.forEach { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        netCallbacks.clear()
        receivers.forEach { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        receivers.clear()
        torchCb?.let { try { camera.unregisterTorchCallback(it) } catch (_: Exception) {} }
        cameraCb?.let { try { camera.unregisterAvailabilityCallback(it) } catch (_: Exception) {} }
        recCb?.let { try { am.unregisterAudioRecordingCallback(it) } catch (_: Exception) {} }
        if (Build.VERSION.SDK_INT >= 35) {
            (screenRecCb as? java.util.function.Consumer<Int>)?.let {
                try { ctx.getSystemService(WindowManager::class.java).removeScreenRecordingCallback(it) } catch (_: Exception) {}
            }
        }
        IslandController.removeWhere { it.id.startsWith("alert:") || it.id.startsWith("ind:") || it.id == "volume" }
    }

    override fun onPrefChanged(key: String?) {
        if (key == null || !key.startsWith("alert_")) return
        micIndicator(); cameraIndicator(); screenRecIndicator()
    }

    private inline fun safe(block: () -> Unit) { try { block() } catch (_: Throwable) {} }

    private fun receiver(vararg actions: String, onReceive: (Intent) -> Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = onReceive(i) }
        val f = IntentFilter().apply { actions.forEach { addAction(it) } }
        Intents.register(ctx, r, f)
        receivers += r
    }

    // ================= Network: Wi-Fi, mobile data, VPN =================

    private fun registerNetwork() {
        val wifiState = WifiState()
        val wifiCb: ConnectivityManager.NetworkCallback =
            if (Build.VERSION.SDK_INT >= 31) WifiCallback31(wifiState) else WifiCallback(wifiState)
        cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCb, handler)
        netCallbacks += wifiCb

        var cell: Network? = null
        val cellCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { cell = network; if (armed) Alerts.flash(ctx, AlertType.MOBILE_DATA, "Mobile data connected") }
            override fun onLost(network: Network) { if (network === cell) { cell = null; if (armed) Alerts.flash(ctx, AlertType.MOBILE_DATA, "Mobile data lost") } }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(), cellCb, handler)
        netCallbacks += cellCb

        var vpn: Network? = null
        val vpnCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { vpn = network; if (armed) Alerts.flash(ctx, AlertType.VPN, "VPN connected") }
            override fun onLost(network: Network) { if (network === vpn) { vpn = null; if (armed) Alerts.flash(ctx, AlertType.VPN, "VPN disconnected") } }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), vpnCb, handler)
        netCallbacks += vpnCb
    }

    /** Tracks the current Wi-Fi network and its name; shared by the API-specific callbacks below. */
    private inner class WifiState {
        var net: Network? = null
        var name: String? = null

        fun available(network: Network) {
            net = network
            if (!armed) return
            handler.postDelayed({ if (net === network) Alerts.flash(ctx, AlertType.WIFI, "Wi-Fi connected", name ?: "") }, 700)
        }

        fun capabilities(caps: NetworkCapabilities) {
            if (Build.VERSION.SDK_INT < 29) return
            val s = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')
            if (s != null && s != WifiManager.UNKNOWN_SSID && s != "<unknown ssid>") name = s
        }

        fun lost(network: Network) {
            if (network !== net) return
            net = null
            if (armed) Alerts.flash(ctx, AlertType.WIFI, "Wi-Fi disconnected", name ?: "")
            name = null
        }
    }

    private inner class WifiCallback(private val st: WifiState) : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = st.available(network)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = st.capabilities(caps)
        override fun onLost(network: Network) = st.lost(network)
    }

    /** API 31+: ask for location-bearing WifiInfo so the SSID is available when location permission is granted. */
    @android.annotation.SuppressLint("NewApi") // only instantiated on API 31+
    private inner class WifiCallback31(private val st: WifiState) : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onAvailable(network: Network) = st.available(network)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = st.capabilities(caps)
        override fun onLost(network: Network) = st.lost(network)
    }

    // ================= Broadcast-driven alerts =================

    private fun registerBroadcasts() {
        // Bluetooth
        val lastBt = HashMap<String, Long>()
        fun bt(i: Intent, connected: Boolean) {
            val dev = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            dev ?: return
            val addr = dev.address ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - (lastBt[addr + connected] ?: 0L) < 4000) return
            lastBt[addr + connected] = now
            if (!armed) return
            // profiles (and the battery level) report a moment after the ACL link comes up
            handler.postDelayed({ announceBluetooth(dev, connected) }, if (connected) 1500 else 0)
        }
        receiver(BluetoothDevice.ACTION_ACL_CONNECTED) { bt(it, true) }
        receiver(BluetoothDevice.ACTION_ACL_DISCONNECTED) { bt(it, false) }

        // Hotspot (hidden but long-stable broadcast)
        receiver("android.net.wifi.WIFI_AP_STATE_CHANGED") {
            when (it.getIntExtra("wifi_state", -1)) {
                13 -> if (armed) Alerts.flash(ctx, AlertType.HOTSPOT, "Hotspot on")
                11 -> if (armed) Alerts.flash(ctx, AlertType.HOTSPOT, "Hotspot off")
            }
        }

        // Next alarm
        receiver(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
            val next = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock
            if (next == null) Alerts.flash(ctx, AlertType.ALARM_SET, "Alarm cleared")
            else {
                val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(ctx)) "EEE Hm" else "EEE hm")
                Alerts.flash(ctx, AlertType.ALARM_SET, "Alarm set", java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(Date(next.triggerTime)))
            }
        }

        // Volume (stream type / level come from the hidden but widely-used AudioService broadcast)
        receiver("android.media.VOLUME_CHANGED_ACTION", "android.media.STREAM_MUTE_CHANGED_ACTION") {
            val stream = it.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream in 0..6) showVolume(stream)
        }

        // Ringer mode
        receiver(AudioManager.RINGER_MODE_CHANGED_ACTION) {
            if (!armed) return@receiver
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> Alerts.flash(ctx, AlertType.RINGER, "Ring", "Sound on")
                AudioManager.RINGER_MODE_VIBRATE -> Alerts.flash(ctx, AlertType.RINGER, "Vibrate", "Sound off")
                AudioManager.RINGER_MODE_SILENT -> Alerts.flash(ctx, AlertType.RINGER, "Silent", "Sound and vibration off")
            }
        }

        // Do Not Disturb
        receiver(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            if (!armed) return@receiver
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> Alerts.flash(ctx, AlertType.DND, "Do Not Disturb off")
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> {}
                else -> Alerts.flash(ctx, AlertType.DND, "Do Not Disturb on")
            }
        }

        // Airplane / NFC
        receiver(Intent.ACTION_AIRPLANE_MODE_CHANGED) {
            if (armed) Alerts.flash(ctx, AlertType.AIRPLANE, if (it.getBooleanExtra("state", false)) "Airplane mode on" else "Airplane mode off")
        }
        receiver("android.nfc.action.ADAPTER_STATE_CHANGED") {
            if (!armed) return@receiver
            when (it.getIntExtra("android.nfc.extra.ADAPTER_STATE", -1)) {
                3 -> Alerts.flash(ctx, AlertType.NFC, "NFC on")
                1 -> Alerts.flash(ctx, AlertType.NFC, "NFC off")
            }
        }
    }

    private fun announceBluetooth(dev: BluetoothDevice, connected: Boolean) {
        val name = try { dev.name } catch (_: SecurityException) { null } ?: "Bluetooth device"
        val audio = try { dev.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO } catch (_: SecurityException) { false }
        val battery = try {
            (dev.javaClass.getMethod("getBatteryLevel").invoke(dev) as? Int)?.takeIf { it in 0..100 }
        } catch (_: Throwable) { null }
        val sub = if (connected) (battery?.let { "$it% battery" } ?: "Connected") else "Disconnected"
        Alerts.flash(ctx, AlertType.BLUETOOTH, name, sub, smartIcon = if (audio) Icon.HEADPHONES else Icon.BLUETOOTH)
    }

    // ================= Volume slider =================

    private fun streamLabel(s: Int) = when (s) {
        AudioManager.STREAM_MUSIC -> "Media"
        AudioManager.STREAM_RING -> "Ring"
        AudioManager.STREAM_ALARM -> "Alarm"
        AudioManager.STREAM_VOICE_CALL -> "Call"
        AudioManager.STREAM_NOTIFICATION -> "Notification"
        AudioManager.STREAM_SYSTEM -> "System"
        else -> "Bluetooth call"
    }

    private fun showVolume(stream: Int) {
        if (!Alerts.enabled(ctx, AlertType.VOLUME)) return
        val max = am.getStreamMaxVolume(stream)
        val level = am.getStreamVolume(stream)
        val muted = am.isStreamMute(stream) || level == 0
        val col = Alerts.color(ctx, AlertType.VOLUME)
        val ic = Alerts.icon(ctx, AlertType.VOLUME)
        val a = IslandActivity("volume", ActivityType.VOLUME)
        a.kind = Kind.VOLUME
        a.title = streamLabel(stream)
        a.accent = col
        a.icon = { IconDrawable(if (muted && stream == AudioManager.STREAM_RING) Icon.BELL else ic, col) }
        a.volume = VolumeInfo(a.title.toString(), level, max, muted) { v -> am.setStreamVolume(stream, v, 0) }
        a.transientMs = 2200
        a.presentation = Presentation.BANNER
        a.anim = Alerts.anim(ctx, AlertType.VOLUME)
        a.expandable = false
        IslandController.post(a, announce = IslandController.get("volume") == null)
    }

    // ================= Flashlight / camera / mic / screen recording =================

    private fun registerTorch() {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (armed) Alerts.flash(ctx, AlertType.FLASHLIGHT, if (enabled) "Flashlight on" else "Flashlight off")
            }
        }
        camera.registerTorchCallback(cb, handler)
        torchCb = cb
    }

    private fun registerCamera() {
        val busy = HashSet<String>()
        val cb = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) { busy.add(cameraId); camActive = true; cameraIndicator() }
            override fun onCameraAvailable(cameraId: String) { busy.remove(cameraId); camActive = busy.isNotEmpty(); if (!camActive) camPackage = null; cameraIndicator() }
            /** Not in the public SDK stubs on every version; the system calls it by signature when present. */
            @Suppress("unused")
            fun onCameraOpened(cameraId: String, packageId: String) { camPackage = packageId; cameraIndicator() }
        }
        camera.registerAvailabilityCallback(cb, handler)
        cameraCb = cb
    }

    private fun appLabel(pkg: String?): String? = pkg?.let {
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(it, 0)).toString() } catch (_: Exception) { null }
    }

    private fun cameraIndicator() {
        val pkg = camPackage
        Alerts.indicator(ctx, AlertType.CAMERA, camActive && pkg != ctx.packageName, "Camera in use", appLabel(pkg) ?: "")
    }

    private fun registerMic() {
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>?) {
                val live = (configs ?: emptyList()).filter { Build.VERSION.SDK_INT < 29 || !it.isClientSilenced }
                micActive = live.isNotEmpty()
                micApp = live.firstNotNullOfOrNull { cfg ->
                    try {
                        // hidden getters: best effort, falls back to "unknown app"
                        (cfg.javaClass.getMethod("getClientPackageName").invoke(cfg) as? String)?.takeIf { it.isNotEmpty() }
                    } catch (_: Throwable) { null }
                }
                micIndicator()
            }
        }
        am.registerAudioRecordingCallback(cb, handler)
        recCb = cb
    }

    private fun micIndicator() {
        Alerts.indicator(ctx, AlertType.MIC, micActive && micApp != ctx.packageName, "Microphone in use", appLabel(micApp) ?: "")
    }

    /** Android 15+: tells us when this app's windows are being recorded; older: system notification (see ScreenRecordTracker). */
    private fun registerScreenRecording() {
        if (Build.VERSION.SDK_INT < 35) return
        val consumer = java.util.function.Consumer<Int> { state ->
            setRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
        }
        screenRecCb = consumer
        val exec = Executor { handler.post(it) }
        val state = ctx.getSystemService(WindowManager::class.java).addScreenRecordingCallback(exec, consumer)
        setRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
    }

    fun setRecording(on: Boolean) {
        if (on == screenRecording) return
        screenRecording = on
        if (on) recStart = System.currentTimeMillis()
        screenRecIndicator()
    }

    private fun screenRecIndicator() {
        Alerts.indicator(ctx, AlertType.SCREEN_REC, screenRecording || ScreenRecordTracker.active, "Recording screen", "", timerBase = if (ScreenRecordTracker.active) ScreenRecordTracker.start else recStart)
    }

    /** Settings "preview" button. */
    fun preview(t: AlertType) = preview(ctx, t)

    companion object {
        fun preview(ctx: Context, t: AlertType) = Alerts.flash(ctx, t, t.label.substringBefore(" ("), "Preview", force = true)
    }
}
