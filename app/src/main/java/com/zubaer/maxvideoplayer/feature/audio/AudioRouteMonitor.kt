package com.zubaer.maxvideoplayer.feature.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioRouteMonitor(
    context: Context,
    private val onRoute: (AudioRouteInfo) -> Unit,
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = publish()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        publish()
    }

    fun stop() = runCatching { audioManager.unregisterAudioDeviceCallback(callback) }.getOrNull()

    private fun publish() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val preferred = outputs.maxByOrNull { priority(it.type) }
        onRoute(preferred?.toRoute() ?: AudioRouteInfo())
    }

    private fun AudioDeviceInfo.toRoute(): AudioRouteInfo {
        val type = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRouteType.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioRouteType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioRouteType.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioRouteType.BLUETOOTH_A2DP
            26, 27 -> AudioRouteType.BLUETOOTH_LE // BLE headset/speaker constants on newer APIs.
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioRouteType.USB
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> AudioRouteType.HDMI
            else -> AudioRouteType.UNKNOWN
        }
        val product = productName?.toString()?.takeIf { it.isNotBlank() }
        val fallback = when (type) {
            AudioRouteType.SPEAKER -> "Built-in speaker"
            AudioRouteType.WIRED_HEADSET -> "Wired headset"
            AudioRouteType.WIRED_HEADPHONES -> "Wired headphones"
            AudioRouteType.BLUETOOTH_A2DP -> "Bluetooth audio"
            AudioRouteType.BLUETOOTH_LE -> "Bluetooth LE audio"
            AudioRouteType.USB -> "USB audio"
            AudioRouteType.HDMI -> "HDMI audio"
            AudioRouteType.UNKNOWN -> "Unknown output"
        }
        return AudioRouteInfo(type, product ?: fallback)
    }

    private fun priority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27 -> 70
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 60
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 50
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> 40
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 10
        else -> 0
    }
}
