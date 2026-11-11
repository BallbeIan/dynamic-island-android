package com.ian.island.activities

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Mute and audio output for the call card. A call's notification usually only offers "Hang up", so these talk to the
 * audio system directly. Reading the state back after a change ([currentRoute]) tells whether it took effect.
 */
object CallControls {
    private fun am(ctx: Context) = ctx.getSystemService(AudioManager::class.java)

    fun muted(ctx: Context) = am(ctx).isMicrophoneMute

    fun toggleMute(ctx: Context) { am(ctx).isMicrophoneMute = !muted(ctx) }

    // ---- audio output ----

    enum class Kind(val label: String) { EARPIECE("Earpiece"), SPEAKER("Speaker"), HEADPHONES("Headphones"), BLUETOOTH("Bluetooth") }

    /** One choice in the audio menu. [device] is null on Android 11 and older, where only speaker on/off exists. */
    class Route(val kind: Kind, val label: String, val device: AudioDeviceInfo?)

    private fun kindOf(type: Int): Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Kind.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> Kind.HEADPHONES
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_BLE_BROADCAST -> Kind.BLUETOOTH
        else -> null
    }

    /** The outputs the phone offers right now, in a sensible order (earpiece, speaker, headphones, Bluetooth...). */
    fun routes(ctx: Context): List<Route> {
        if (Build.VERSION.SDK_INT < 31) return listOf(Route(Kind.EARPIECE, "Earpiece", null), Route(Kind.SPEAKER, "Speaker", null))
        return am(ctx).availableCommunicationDevices
            .mapNotNull { d -> kindOf(d.type)?.let { k ->
                Route(k, if (k == Kind.BLUETOOTH) d.productName?.toString()?.takeIf { it.isNotBlank() } ?: k.label else k.label, d)
            } }
            .distinctBy { if (it.kind == Kind.BLUETOOTH) it.label else it.kind.name }
            .sortedBy { it.kind.ordinal }
    }

    @Suppress("DEPRECATION")   // isSpeakerphoneOn: the only API before Android 12
    fun currentKind(ctx: Context): Kind {
        val a = am(ctx)
        return if (Build.VERSION.SDK_INT >= 31) a.communicationDevice?.let { kindOf(it.type) } ?: Kind.EARPIECE
        else if (a.isSpeakerphoneOn) Kind.SPEAKER else Kind.EARPIECE
    }

    @Suppress("DEPRECATION")
    fun setRoute(ctx: Context, r: Route) {
        val a = am(ctx)
        if (Build.VERSION.SDK_INT >= 31) {
            if (r.device != null) a.setCommunicationDevice(r.device) else a.clearCommunicationDevice()
        } else a.isSpeakerphoneOn = r.kind == Kind.SPEAKER
    }
}
