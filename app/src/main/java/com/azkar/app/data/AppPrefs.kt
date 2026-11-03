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
    private const val K_COMPLETE = "completion_screen"
    private const val K_POS = "pos_"      // pos_<folderId> = last zikr id
    private const val K_POSC = "posc_"    // posc_<folderId> = its count
    private const val K_POST = "post_"    // post_<folderId> = when it was saved (ms)
    private const val K_LAT = "loc_lat"
    private const val K_LNG = "loc_lng"
    private const val K_LOC_ASKED = "loc_asked"
    private const val K_DELAY = "session_delay_"   // session_delay_<scheduleId> = minutes after Fajr / Asr

    const val SESSION_DELAY_DEFAULT = 10
    const val SLEEP_DELAY_DEFAULT = 120      // before sleep ("s"): minutes after Isha
    const val SESSION_DELAY_MAX = 300
    const val NUDGE_LEAD_DEFAULT = 45        // "not finished yet": minutes before Dhuhr / Maghrib
    private const val K_NUDGE = "nudge_on"
    private const val K_NUDGE_LEAD = "nudge_lead"
    private const val K_CALC = "calc_method"
    private const val K_ASR_HANAFI = "asr_hanafi"
    /** Prayer-time method used before it became a setting; stays the default so existing reminders don't move. */
    const val CALC_METHOD_DEFAULT = "EGYPTIAN"

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
    /** Show the "collection complete" screen after the last zikr (off: straight back to Home). */
    fun completionScreen() = sp.getBoolean(K_COMPLETE, false)
    fun setCompletionScreen(v: Boolean) = sp.edit().putBoolean(K_COMPLETE, v).apply()

    /** 0 = teal theme, 1 = OLED black (default). */
    fun theme() = sp.getInt(K_THEME, 1)
    fun setTheme(v: Int) = sp.edit().putInt(K_THEME, v).apply()

    // ---------- Per-folder reading position ----------
    fun savePosition(folderId: String, zikrId: String, count: Int) {
        sp.edit().putString(K_POS + folderId, zikrId).putInt(K_POSC + folderId, count)
            .putLong(K_POST + folderId, System.currentTimeMillis()).apply()
    }

    /** When [savePosition] last ran for [folderId]; 0 for positions saved before this was recorded. */
    fun savedPositionTime(folderId: String): Long = sp.getLong(K_POST + folderId, 0L)

    fun savedPosition(folderId: String): Pair<String, Int>? {
        val id = sp.getString(K_POS + folderId, null) ?: return null
        return id to sp.getInt(K_POSC + folderId, 0)
    }

    fun clearPosition(folderId: String) {
        sp.edit().remove(K_POS + folderId).remove(K_POSC + folderId).remove(K_POST + folderId).apply()
    }

    // ---------- Notifications ----------
    fun reminderOn(key: String, default: Boolean) = sp.getBoolean(K_REM + key, default)
    fun notificationsOn() = sp.getBoolean(K_NOTIF, true)
    fun setNotificationsOn(v: Boolean) = sp.edit().putBoolean(K_NOTIF, v).apply()

    // ---------- Location (for prayer times) ----------
    /** Last known (latitude, longitude), or null before the first location fix. */
    fun location(): Pair<Double, Double>? {
        val lat = sp.getString(K_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = sp.getString(K_LNG, null)?.toDoubleOrNull() ?: return null
        return lat to lng
    }

    fun setLocation(lat: Double, lng: Double) =
        sp.edit().putString(K_LAT, lat.toString()).putString(K_LNG, lng.toString()).apply()

    /**
     * Minutes after Fajr (morning, "m") or Asr (evening, "e") when that session's azkar start.
     * Falls back to the default before [attach], so pure unit tests can call into PrayerSessions.
     */
    fun sessionDelayMinutes(scheduleId: String): Int {
        val default = if (scheduleId == "s") SLEEP_DELAY_DEFAULT else SESSION_DELAY_DEFAULT
        return if (::sp.isInitialized) sp.getInt(K_DELAY + scheduleId, default).coerceIn(0, SESSION_DELAY_MAX) else default
    }

    fun setSessionDelayMinutes(scheduleId: String, minutes: Int) =
        sp.edit().putInt(K_DELAY + scheduleId, minutes.coerceIn(0, SESSION_DELAY_MAX)).apply()

    /** "Not finished yet" reminder for tracked morning / evening azkar (on unless turned off). */
    fun nudgeOn() = sp.getBoolean(K_NUDGE, true)
    fun setNudgeOn(v: Boolean) = sp.edit().putBoolean(K_NUDGE, v).apply()
    fun nudgeLeadMinutes() = sp.getInt(K_NUDGE_LEAD, NUDGE_LEAD_DEFAULT)
    fun setNudgeLeadMinutes(v: Int) = sp.edit().putInt(K_NUDGE_LEAD, v).apply()

    /**
     * Prayer-time calculation method, as an adhan CalculationMethod name. Like [sessionDelayMinutes], falls back to
     * the default before [attach] so pure unit tests can call into PrayerSessions.
     */
    fun calcMethod(): String = if (::sp.isInitialized) sp.getString(K_CALC, null) ?: CALC_METHOD_DEFAULT else CALC_METHOD_DEFAULT
    fun setCalcMethod(v: String) = sp.edit().putString(K_CALC, v).apply()

    /** Asr by the Hanafi shadow length (twice the object) instead of the majority's (once). */
    fun asrHanafi(): Boolean = ::sp.isInitialized && sp.getBoolean(K_ASR_HANAFI, false)
    fun setAsrHanafi(v: Boolean) = sp.edit().putBoolean(K_ASR_HANAFI, v).apply()

    /** True once the app has asked for location permission (it asks only once). */
    fun locationAsked() = sp.getBoolean(K_LOC_ASKED, false)
    fun setLocationAsked() = sp.edit().putBoolean(K_LOC_ASKED, true).apply()
}
