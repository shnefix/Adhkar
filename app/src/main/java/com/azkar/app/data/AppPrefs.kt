package com.azkar.app.data

import android.content.SharedPreferences

/** Reader, theme and notification preferences (SharedPreferences only; no Room content). */
object AppPrefs {
    private lateinit var sp: SharedPreferences

    private const val K_SIZE = "arabic_size"
    private const val K_THEME = "theme_mode"
    private const val K_REM = "reminder_"
    private const val K_NOTIF = "notif_on"
    private const val K_SP = "arabic_sp"
    private const val K_TAP = "tap_advance"
    private const val K_AWAKE = "keep_awake"
    private const val K_POS = "pos_"      // pos_<folderId> = last zikr id
    private const val K_POSC = "posc_"    // posc_<folderId> = its count

    /** Called by [Store.init]; shares the "azkar" preferences file so existing values carry over. */
    internal fun attach(prefs: SharedPreferences) {
        sp = prefs
    }

    /** 0 = small, 1 = medium (default), 2 = large. */
    fun arabicSize() = sp.getInt(K_SIZE, 1)
    fun setArabicSize(v: Int) = sp.edit().putInt(K_SIZE, v).apply()

    /** Arabic size in sp. Existing Small/Medium/Large choices carry over as 20/26/32. */
    fun arabicSp(): Int = ReaderPrefs.clampSp(sp.getInt(K_SP, ReaderPrefs.spFromOldIndex(arabicSize())))
    fun setArabicSp(v: Int) = sp.edit().putInt(K_SP, ReaderPrefs.clampSp(v)).apply()
    fun tapAdvance() = sp.getBoolean(K_TAP, true)
    fun setTapAdvance(v: Boolean) = sp.edit().putBoolean(K_TAP, v).apply()
    fun keepScreenOn() = sp.getBoolean(K_AWAKE, true)
    fun setKeepScreenOn(v: Boolean) = sp.edit().putBoolean(K_AWAKE, v).apply()

    /** 0 = teal theme, 1 = OLED black (default). */
    fun theme() = sp.getInt(K_THEME, 1)
    fun setTheme(v: Int) = sp.edit().putInt(K_THEME, v).apply()

    // ---------- Per-folder reading position ----------
    fun savePosition(folderId: String, zikrId: String, count: Int) {
        sp.edit().putString(K_POS + folderId, zikrId).putInt(K_POSC + folderId, count).apply()
    }

    fun savedPosition(folderId: String): Pair<String, Int>? {
        val id = sp.getString(K_POS + folderId, null) ?: return null
        return id to sp.getInt(K_POSC + folderId, 0)
    }

    fun clearPosition(folderId: String) {
        sp.edit().remove(K_POS + folderId).remove(K_POSC + folderId).apply()
    }

    // ---------- Notifications ----------
    fun reminderOn(key: String, default: Boolean) = sp.getBoolean(K_REM + key, default)
    fun notificationsOn() = sp.getBoolean(K_NOTIF, true)
    fun setNotificationsOn(v: Boolean) = sp.edit().putBoolean(K_NOTIF, v).apply()
}
