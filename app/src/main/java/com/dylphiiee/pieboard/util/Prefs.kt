package com.dylphiiee.pieboard.util

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 0..100, persisted master volume for playback. */
    var masterVolume: Int
        get() = sp.getInt(KEY_VOLUME, 80)
        set(value) = sp.edit().putInt(KEY_VOLUME, value).apply()

    var floatingEnabled: Boolean
        get() = sp.getBoolean(KEY_FLOATING_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_FLOATING_ENABLED, value).apply()

    var floatingButtonX: Int
        get() = sp.getInt(KEY_BUTTON_X, 0)
        set(value) = sp.edit().putInt(KEY_BUTTON_X, value).apply()

    var floatingButtonY: Int
        get() = sp.getInt(KEY_BUTTON_Y, 300)
        set(value) = sp.edit().putInt(KEY_BUTTON_Y, value).apply()

    /** Whether sounds played from the floating panel should loop until stopped. */
    var loopEnabled: Boolean
        get() = sp.getBoolean(KEY_LOOP_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_LOOP_ENABLED, value).apply()

    companion object {
        private const val NAME = "pieboard_prefs"
        private const val KEY_VOLUME = "master_volume"
        private const val KEY_FLOATING_ENABLED = "floating_enabled"
        private const val KEY_BUTTON_X = "button_x"
        private const val KEY_BUTTON_Y = "button_y"
        private const val KEY_LOOP_ENABLED = "loop_enabled"
    }
}
