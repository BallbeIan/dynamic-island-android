package com.ian.island.activities

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Mute and speaker for the call card. A call's notification usually only offers "Hang up", so these talk to the
 * audio system directly. Reading the state back after a change (see [speakerOn]) tells whether it took effect.
 */
object CallControls {
    private fun am(ctx: Context) = ctx.getSystemService(AudioManager::class.java)

    fun muted(ctx: Context) = am(ctx).isMicrophoneMute

    fun toggleMute(ctx: Context) { am(ctx).isMicrophoneMute = !muted(ctx) }

    @Suppress("DEPRECATION")   // isSpeakerphoneOn: the only API before Android 12
    fun speakerOn(ctx: Context): Boolean {
        val a = am(ctx)
        return if (Build.VERSION.SDK_INT >= 31) a.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        else a.isSpeakerphoneOn
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker(ctx: Context) {
        val a = am(ctx)
        if (Build.VERSION.SDK_INT >= 31) {
            if (speakerOn(ctx)) a.clearCommunicationDevice()
            else a.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { a.setCommunicationDevice(it) }
        } else a.isSpeakerphoneOn = !a.isSpeakerphoneOn
    }
}
