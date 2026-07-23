package com.dylphiiee.pieboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/**
 * Plays short audio clips through the MEDIA stream so playback behaves like a
 * regular music app. It also forces routing to the device's built-in speaker
 * (even if a headset or Bluetooth device is connected), so the sound is
 * acoustically audible and can be picked up by Roblox's microphone during
 * voice chat.
 *
 * Two layers of enforcement are used together, since neither is 100% reliable
 * on its own across every device/OEM:
 *  1. [MediaPlayer.setPreferredDevice] targeting the built-in speaker — the
 *     precise, per-player routing override. Works consistently for wired
 *     headsets and Bluetooth SCO (call-profile) devices, and on most modern
 *     Android versions/devices for Bluetooth A2DP (media-profile) too.
 *  2. [AudioManager.isSpeakerphoneOn] — the same low-level flag phone apps use
 *     to force speaker during calls. On some OEM audio stacks this is honored
 *     even for plain media playback and helps override stubborn A2DP routing
 *     where (1) alone doesn't take effect. This is restored to its previous
 *     value as soon as playback ends, so it never lingers and affects other
 *     apps/audio afterwards.
 *
 * Neither touches the phone's Bluetooth connection itself, so headsets keep
 * working normally for calls, other apps, and Roblox's own voice chat output —
 * only this app's own sound-effect playback is forced to the speaker.
 *
 * Known edge case: a small number of heavily OEM-customized Android builds
 * still keep media routed to an active Bluetooth A2DP device regardless of
 * what any app requests. If that happens, the guaranteed fix is on the phone
 * itself: Bluetooth settings → the device's gear icon → turn off "Media audio"
 * (keep "Phone calls" on if still needed for calls).
 */
class SoundPlayer(context: Context, private val prefs: Prefs) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var currentPlayer: MediaPlayer? = null
    private var savedSpeakerphoneOn = false
    private var speakerphoneOverrideActive = false

    fun play(filePath: String, loop: Boolean = false) {
        release()
        try {
            forceSpeakerRouting()
            val speaker = findSpeakerDevice()
            val volume = (prefs.masterVolume.coerceIn(0, 100)) / 100f

            currentPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (speaker != null) setPreferredDevice(speaker)
                setDataSource(filePath)
                setVolume(volume, volume)
                isLooping = loop
                if (!loop) {
                    setOnCompletionListener { release() }
                }
                setOnErrorListener { _, _, _ -> release(); true }
                prepare()
                // Re-assert after prepare(): on some devices the internal audio track
                // is (re)created during prepare(), which can drop the preferred device
                // set earlier unless it's reapplied right before playback starts.
                if (speaker != null) setPreferredDevice(speaker)
                start()
            }
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Gagal memutar sound: $filePath", e)
            release()
        }
    }

    private fun findSpeakerDevice(): AudioDeviceInfo? {
        return try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Gagal mencari speaker device", e)
            null
        }
    }

    /** Layer 1 + 2 of speaker enforcement — see class doc for details. */
    private fun forceSpeakerRouting() {
        val am = audioManager ?: return
        try {
            if (am.mode != AudioManager.MODE_NORMAL) {
                am.mode = AudioManager.MODE_NORMAL
            }
            if (am.isBluetoothScoOn) {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
            }
            if (!speakerphoneOverrideActive) {
                savedSpeakerphoneOn = am.isSpeakerphoneOn
                speakerphoneOverrideActive = true
            }
            am.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Gagal memaksa routing ke speaker", e)
        }
    }

    /** Restores the speakerphone flag to whatever it was before we touched it. */
    private fun restoreSpeakerRouting() {
        val am = audioManager ?: return
        if (speakerphoneOverrideActive) {
            try {
                am.isSpeakerphoneOn = savedSpeakerphoneOn
            } catch (_: Exception) {
            }
            speakerphoneOverrideActive = false
        }
    }

    /** Applies a new looping state to whatever is currently playing, if anything. */
    fun setLooping(loop: Boolean) {
        currentPlayer?.isLooping = loop
    }

    fun isPlaying(): Boolean = currentPlayer?.isPlaying == true

    /** Stops playback immediately. Alias of [release] kept for call-site clarity. */
    fun stop() = release()

    fun release() {
        currentPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        currentPlayer = null
        restoreSpeakerRouting()
    }
}
