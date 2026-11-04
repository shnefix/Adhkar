package com.azkar.app.reminders

import com.azkar.app.data.AppPrefs
import com.azkar.app.data.DefaultFolders
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Morning and evening azkar follow the prayer times (the method and Asr school chosen in Settings, Egyptian by default):
 * the reminder comes the user's chosen delay (default [DELAY_MS]) after Fajr / Asr, and the session
 * ends at Dhuhr / Maghrib. Everything returns null without a location, so callers fall back to the
 * fixed reminder time.
 */
object PrayerSessions {
    const val DELAY_MS = AppPrefs.SESSION_DELAY_DEFAULT * 60_000L

    enum class Session(val scheduleId: String, val folderId: String) {
        MORNING("m", DefaultFolders.MORNING),
        EVENING("e", DefaultFolders.EVENING)
    }

    fun forSchedule(id: String): Session? = Session.values().firstOrNull { it.scheduleId == id }
    fun forFolder(id: String): Session? = Session.values().firstOrNull { it.folderId == id }

    /** How long after Fajr / Asr this session's azkar start, as set in the reminder. */
    fun delayMs(s: Session): Long = AppPrefs.sessionDelayMinutes(s.scheduleId) * 60_000L

    /** Start (prayer + delay) and end (Dhuhr / Maghrib) of [s] on the local day containing [day]; null without a location. */
    fun window(
        s: Session, day: Long, loc: Pair<Double, Double>? = AppPrefs.location(),
        zone: TimeZone = TimeZone.getDefault(), delayMs: Long = delayMs(s)
    ): Pair<Long, Long>? {
        val (lat, lng) = loc ?: return null
        val t = timesOn(day, lat, lng, zone)
        return (start(s, t).time + delayMs) to end(s, t).time
    }

    /** Calculation parameters from the user's method and Asr school; an unknown saved method falls back to Egyptian. */
    fun parameters(
        method: String = AppPrefs.calcMethod(), hanafi: Boolean = AppPrefs.asrHanafi()
    ): CalculationParameters {
        val m = CalculationMethod.values().firstOrNull { it.name == method } ?: CalculationMethod.EGYPTIAN
        return m.parameters.also { it.madhab = if (hanafi) Madhab.HANAFI else Madhab.SHAFI }
    }

    /** Prayer times for the local day containing [at]. */
    fun timesOn(
        at: Long, lat: Double, lng: Double, zone: TimeZone = TimeZone.getDefault(),
        params: CalculationParameters = parameters()
    ): PrayerTimes {
        val day = Calendar.getInstance(zone).apply { timeInMillis = at }
        val date = DateComponents(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH))
        return PrayerTimes(Coordinates(lat, lng), date, params)
    }

    /** Adhan's times are whole minutes plus milliseconds left over from the clock; drop those so results are stable. */
    fun minute(d: Date): Long = d.time / 60_000L * 60_000L

    private fun start(s: Session, t: PrayerTimes) = Date(minute(if (s == Session.MORNING) t.fajr else t.asr))
    private fun end(s: Session, t: PrayerTimes) = Date(minute(if (s == Session.MORNING) t.dhuhr else t.maghrib))

    private fun dayShift(at: Long, days: Int, zone: TimeZone) =
        Calendar.getInstance(zone).apply { timeInMillis = at; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    /** Next reminder time (start + delay) strictly after [now]: today's if still ahead, else tomorrow's. */
    fun nextReminder(
        s: Session, now: Long, loc: Pair<Double, Double>? = AppPrefs.location(),
        zone: TimeZone = TimeZone.getDefault(), delayMs: Long = delayMs(s)
    ): Long? {
        val (lat, lng) = loc ?: return null
        for (d in 0..1) {
            val at = start(s, timesOn(dayShift(now, d, zone), lat, lng, zone)).time + delayMs
            if (at > now) return at
        }
        return null
    }

    /** The first session end (Dhuhr / Maghrib) after [time]. */
    fun endAfter(
        s: Session, time: Long, loc: Pair<Double, Double>? = AppPrefs.location(),
        zone: TimeZone = TimeZone.getDefault()
    ): Long? {
        val (lat, lng) = loc ?: return null
        for (d in 0..1) {
            val e = end(s, timesOn(dayShift(time, d, zone), lat, lng, zone)).time
            if (e > time) return e
        }
        return null
    }

    /** True while today's session is running (Fajr..Dhuhr / Asr..Maghrib); null without a location. */
    fun inSession(
        s: Session, now: Long, loc: Pair<Double, Double>? = AppPrefs.location(),
        zone: TimeZone = TimeZone.getDefault()
    ): Boolean? {
        val (lat, lng) = loc ?: return null
        val t = timesOn(now, lat, lng, zone)
        return now >= start(s, t).time && now < end(s, t).time
    }

    /**
     * "Not finished yet" reminder for [s]: [leadMs] before Dhuhr / Maghrib (never before the session starts),
     * the next one strictly after [now]; null without a location.
     */
    fun nextNudge(
        s: Session, now: Long, leadMs: Long, loc: Pair<Double, Double>? = AppPrefs.location(),
        zone: TimeZone = TimeZone.getDefault(), delayMs: Long = delayMs(s)
    ): Long? {
        val (lat, lng) = loc ?: return null
        for (d in 0..1) {
            val t = timesOn(dayShift(now, d, zone), lat, lng, zone)
            val at = maxOf(end(s, t).time - leadMs, start(s, t).time + delayMs)
            if (at > now) return at
        }
        return null
    }

    // ---------- Before sleep: follows Isha ----------
    const val SLEEP_SCHEDULE = "s"
    const val SLEEP_FOLDER = DefaultFolders.SLEEP

    /** Next before-sleep reminder: the chosen time after Isha, strictly after [now]; null without a location. */
    fun nextAfterIsha(
        now: Long, loc: Pair<Double, Double>? = AppPrefs.location(), zone: TimeZone = TimeZone.getDefault(),
        delayMs: Long = AppPrefs.sessionDelayMinutes(SLEEP_SCHEDULE) * 60_000L
    ): Long? {
        val (lat, lng) = loc ?: return null
        for (d in 0..1) {
            val at = minute(timesOn(dayShift(now, d, zone), lat, lng, zone).isha) + delayMs
            if (at > now) return at
        }
        return null
    }

    /** Next time of a reminder that follows a prayer (morning, evening, before sleep); null for other reminders or without a location. */
    fun nextPrayerReminder(scheduleId: String, now: Long, delayMs: Long = AppPrefs.sessionDelayMinutes(scheduleId) * 60_000L): Long? {
        // A reminder the user set to follow a prayer (e.g. 10 min after Dhuhr); its clock time is the fallback.
        AppPrefs.reminderRule(scheduleId)?.let { rule ->
            val loc = AppPrefs.location() ?: return null
            return PrayerClock.next(rule, now, loc)
        }
        return if (scheduleId == SLEEP_SCHEDULE) nextAfterIsha(now, delayMs = delayMs)
        else forSchedule(scheduleId)?.let { nextReminder(it, now, delayMs = delayMs) }
    }

    /**
     * True when the folder's start-over moment ([AppPrefs.resetRule]: by default Dhuhr for the morning azkar and
     * Maghrib for the evening ones) came after the position was saved at [savedAt], so the reader starts again
     * from the first zikr.
     */
    fun positionExpired(
        folderId: String, savedAt: Long, now: Long = System.currentTimeMillis(),
        loc: Pair<Double, Double>? = AppPrefs.location(), zone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val rule = AppPrefs.resetRule(folderId) ?: return false
        if (savedAt <= 0L) return false                       // saved by an older version: keep it
        // Without a location the rule uses typical prayer times, so the folder still starts over once a day.
        return PrayerClock.passedBetween(rule, savedAt, now, loc, zone)
    }
}
