package com.flyre.lyrics.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlyrePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var opacity: Float
        get() = prefs.getFloat(KEY_OPACITY, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_OPACITY, value).apply()
            _opacityFlow.value = value
        }

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 16f)
        set(value) {
            prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()
            _fontSizeFlow.value = value
        }

    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_TRANSPARENT) ?: THEME_TRANSPARENT
        set(value) {
            prefs.edit().putString(KEY_THEME, value).apply()
            _themeFlow.value = value
        }

    var isTouchLocked: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_LOCKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOUCH_LOCKED, value).apply()
            _touchLockedFlow.value = value
        }

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, 100)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, 200)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    var overlayWidthDp: Int
        get() = prefs.getInt(KEY_OVERLAY_WIDTH, 320)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_WIDTH, value).apply()

    var overlayHeightDp: Int
        get() = prefs.getInt(KEY_OVERLAY_HEIGHT, 380)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_HEIGHT, value).apply()

    var isMinimized: Boolean
        get() = prefs.getBoolean(KEY_IS_MINIMIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MINIMIZED, value).apply()

    private val _opacityFlow = MutableStateFlow(opacity)
    val opacityFlow: StateFlow<Float> = _opacityFlow.asStateFlow()

    private val _fontSizeFlow = MutableStateFlow(fontSizeSp)
    val fontSizeFlow: StateFlow<Float> = _fontSizeFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(theme)
    val themeFlow: StateFlow<String> = _themeFlow.asStateFlow()

    private val _touchLockedFlow = MutableStateFlow(isTouchLocked)
    val touchLockedFlow: StateFlow<Boolean> = _touchLockedFlow.asStateFlow()

    companion object {
        private const val PREF_NAME = "flyre_prefs"
        private const val KEY_OPACITY = "pref_opacity"
        private const val KEY_FONT_SIZE = "pref_font_size"
        private const val KEY_THEME = "pref_theme"
        private const val KEY_TOUCH_LOCKED = "pref_touch_locked"
        private const val KEY_OVERLAY_X = "pref_overlay_x"
        private const val KEY_OVERLAY_Y = "pref_overlay_y"
        private const val KEY_OVERLAY_WIDTH = "pref_overlay_width"
        private const val KEY_OVERLAY_HEIGHT = "pref_overlay_height"
        private const val KEY_IS_MINIMIZED = "pref_is_minimized"

        const val THEME_TRANSPARENT = "transparent"
        const val THEME_DARK = "dark"
        const val THEME_AMOLED = "amoled"
        const val THEME_SLATE = "slate"
    }
}
