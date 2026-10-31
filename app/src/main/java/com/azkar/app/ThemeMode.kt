package com.azkar.app

import androidx.appcompat.app.AppCompatDelegate

/** The two in-app themes. Never follows the phone's system dark mode. */
object ThemeMode {
    const val CURRENT = 0
    const val OLED = 1

    /** OLED = night resources (values-night); Current = day resources. Recreates the activity if the mode changed. */
    fun apply(choice: Int) = AppCompatDelegate.setDefaultNightMode(
        if (choice == OLED) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    )
}