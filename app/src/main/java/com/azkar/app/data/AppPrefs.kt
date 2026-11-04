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
    private const val K_SLIDER = "reader_slider"
    private const val K_COMPLETE = "completion_screen"
    private const val K_SOURCES = "sources_on"
    private const val K_SRC_OFF_FOLDERS = "sources_off_folders"   // folder ids whose sources are hidden
    private const val K_SRC_OFF_ZIKRS = "sources_off_zikrs"       // zikr ids whose source is hidden
    private const val K_POS = "pos_"      // pos_<folderId> = last zikr id
    private const val K_POSC = "posc_"    // posc_<folderId> = its count
    private const val K_POST = "post_"    // post_<folderId> = when it was saved (ms)
    private const val K_LAT = "loc_lat"
    private const val K_LNG = "loc_lng"
    private const val K_LOC_ASKED = "loc_asked"
    private const val K_LOC_MANUAL = "loc_manual"     // true: the user set the location; the phone's is ignored
    private const val K_LOC_CITY = "loc_city"         // Cities id of a hand-picked city; absent for typed coordinates
    private const val K_DELAY = "session_delay_"   // session_delay_<scheduleId> = minutes after Fajr / Asr

    const val SESSION_DELAY_DEFAULT = 10
    const val SLEEP_DELAY_DEFAULT = 120      // before sleep ("s"): minutes after Isha
    const val SESSION_DELAY_MAX = 300
    const val NUDGE_LEAD_DEFAULT = 45        // "not finished yet": minutes before Dhuhr / Maghrib
    private const val K_NUDGE = "nudge_on"
    private const val K_NUDGE_LEAD = "nudge_lead"
    private const val K_CALC = "calc_method"
    private const val K_RESET = "reset_"          // reset_<folderId> = TimeRule text, or "NEVER"
    private const val K_REM_RULE = "rem_rule_"    // rem_rule_<scheduleId> = TimeRule text: the reminder follows a prayer
    private const val NEVER = "NEVER"
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
    /** The reader's progress bar has a handle to drag to another zikr (off: the plain segment bar). */
    fun readerSlider() = sp.getBoolean(K_SLIDER, true)
    fun setReaderSlider(v: Boolean) = sp.edit().putBoolean(K_SLIDER, v).apply()
    /** Show the "collection complete" screen after the last zikr (off: straight back to Home). */
    fun completionScreen() = sp.getBoolean(K_COMPLETE, false)
    fun setCompletionScreen(v: Boolean) = sp.edit().putBoolean(K_COMPLETE, v).apply()

    // ---------- Sources: hidden for the whole app, a folder, or one zikr ----------
    /** The reader's source button, for the whole app (on by default). */
    fun sourcesOn() = sp.getBoolean(K_SOURCES, true)
    fun setSourcesOn(v: Boolean) = sp.edit().putBoolean(K_SOURCES, v).apply()
    fun folderSourcesHidden(folderId: String) = folderId in sp.getStringSet(K_SRC_OFF_FOLDERS, emptySet())!!
    fun setFolderSourcesHidden(folderId: String, hidden: Boolean) = setMember(K_SRC_OFF_FOLDERS, folderId, hidden)
    fun zikrSourceHidden(zikrId: String) = zikrId in sp.getStringSet(K_SRC_OFF_ZIKRS, emptySet())!!
    fun setZikrSourceHidden(zikrId: String, hidden: Boolean) = setMember(K_SRC_OFF_ZIKRS, zikrId, hidden)
    /** Whether the reader offers the source of zikr [zikrId] in folder [folderId] (null: not in a folder). */
    fun showsSource(zikrId: String, folderId: String?) =
        sourcesOn() && (folderId == null || !folderSourcesHidden(folderId)) && !zikrSourceHidden(zikrId)
    private fun setMember(key: String, id: String, add: Boolean) {
        val now = sp.getStringSet(key, emptySet())!!.toMutableSet()
        if (if (add) now.add(id) else now.remove(id)) sp.edit().putStringSet(key, now).apply()
    }

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

    /** True while the location was set by hand (a city or typed coordinates). */
    fun locationManual(): Boolean = ::sp.isInitialized && sp.getBoolean(K_LOC_MANUAL, false)

    /** The hand-picked city's id, or null for typed coordinates or an automatic location. */
    fun locationCity(): String? = if (locationManual()) sp.getString(K_LOC_CITY, null) else null

    fun setManualLocation(lat: Double, lng: Double, cityId: String?) {
        sp.edit().putString(K_LAT, lat.toString()).putString(K_LNG, lng.toString())
            .putBoolean(K_LOC_MANUAL, true).putString(K_LOC_CITY, cityId).apply()
    }

    /** Back to the phone's location: the hand-set one is forgotten, so nothing stale is used until a new fix. */
    fun useAutomaticLocation() {
        sp.edit().putBoolean(K_LOC_MANUAL, false).remove(K_LOC_CITY).remove(K_LAT).remove(K_LNG).apply()
    }

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

    /**
     * When a folder that remembers its position starts over from the first zikr; null = never (only when finished).
     * Morning azkar start over at Dhuhr and evening azkar at Maghrib unless the user chose otherwise.
     */
    fun resetRule(folderId: String): com.azkar.app.reminders.TimeRule? {
        val raw = if (::sp.isInitialized) sp.getString(K_RESET + folderId, null) else null
        if (raw == NEVER) return null
        return com.azkar.app.reminders.TimeRule.decode(raw) ?: when (folderId) {
            DefaultFolders.MORNING -> com.azkar.app.reminders.TimeRule(com.azkar.app.reminders.Moment.DHUHR)
            DefaultFolders.EVENING -> com.azkar.app.reminders.TimeRule(com.azkar.app.reminders.Moment.MAGHRIB)
            else -> null
        }
    }

    /** Forgets a deleted folder's choice. */
    fun clearResetRule(folderId: String) = sp.edit().remove(K_RESET + folderId).apply()

    fun setResetRule(folderId: String, rule: com.azkar.app.reminders.TimeRule?) =
        sp.edit().putString(K_RESET + folderId, rule?.encode() ?: NEVER).apply()

    /** A reminder that comes before / after a prayer instead of at a clock time; null = at its clock time. */
    fun reminderRule(scheduleId: String): com.azkar.app.reminders.TimeRule? =
        if (::sp.isInitialized) com.azkar.app.reminders.TimeRule.decode(sp.getString(K_REM_RULE + scheduleId, null)) else null

    fun setReminderRule(scheduleId: String, rule: com.azkar.app.reminders.TimeRule?) {
        if (rule == null) sp.edit().remove(K_REM_RULE + scheduleId).apply()
        else sp.edit().putString(K_REM_RULE + scheduleId, rule.encode()).apply()
    }

    /** All prayer-based reminder rules, for the backup file. */
    fun reminderRules(): Map<String, String> = sp.all.mapNotNull { (k, v) ->
        if (k.startsWith(K_REM_RULE) && v is String) k.removePrefix(K_REM_RULE) to v else null
    }.toMap()

    /** All folder start-over choices the user made, for the backup file. */
    fun resetRules(): Map<String, String> = sp.all.mapNotNull { (k, v) ->
        if (k.startsWith(K_RESET) && v is String) k.removePrefix(K_RESET) to v else null
    }.toMap()

    fun setResetRuleRaw(folderId: String, raw: String) {
        if (raw == NEVER || com.azkar.app.reminders.TimeRule.decode(raw) != null) sp.edit().putString(K_RESET + folderId, raw).apply()
    }

    /** True once the app has asked for location permission (it asks only once). */
    fun locationAsked() = sp.getBoolean(K_LOC_ASKED, false)
    fun setLocationAsked() = sp.edit().putBoolean(K_LOC_ASKED, true).apply()
}
