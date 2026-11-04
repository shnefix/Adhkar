package com.azkar.app.reminders

import java.util.Calendar
import java.util.TimeZone

/** A moment of the day: midnight or one of the prayers (and sunrise). */
enum class Moment { MIDNIGHT, FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/**
 * "[offsetMin] minutes before (negative) or after [moment]", every day. Used for when a folder starts over
 * and for reminders that follow a prayer. Saved as text like "DHUHR:0" or "FAJR:-30".
 */
data class TimeRule(val moment: Moment, val offsetMin: Int = 0) {
    fun encode() = "${moment.name}:$offsetMin"

    companion object {
        fun decode(raw: String?): TimeRule? {
            val parts = raw?.split(':') ?: return null
            val m = Moment.values().firstOrNull { it.name == parts.getOrNull(0) } ?: return null
            return TimeRule(m, parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
    }
}

/** When [TimeRule]s happen, from the prayer times of a location, or typical times when there is none. */
object PrayerClock {
    /** Used without a location, so a rule still fires once a day near the right time (minutes after midnight). */
    private val FALLBACK = mapOf(
        Moment.FAJR to 5 * 60, Moment.SUNRISE to 6 * 60 + 30, Moment.DHUHR to 12 * 60,
        Moment.ASR to 15 * 60 + 30, Moment.MAGHRIB to 18 * 60, Moment.ISHA to 19 * 60 + 30
    )

    private fun startOfDay(at: Long, zone: TimeZone) = Calendar.getInstance(zone).apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** [moment] on the local day containing [day]. */
    fun momentOn(moment: Moment, day: Long, loc: Pair<Double, Double>?, zone: TimeZone = TimeZone.getDefault()): Long {
        val start = startOfDay(day, zone)
        if (moment == Moment.MIDNIGHT) return start.timeInMillis
        if (loc == null) return start.apply { add(Calendar.MINUTE, FALLBACK.getValue(moment)) }.timeInMillis
        val t = PrayerSessions.timesOn(start.timeInMillis + 12 * 3_600_000L, loc.first, loc.second, zone)
        val d = when (moment) {
            Moment.FAJR -> t.fajr
            Moment.SUNRISE -> t.sunrise
            Moment.DHUHR -> t.dhuhr
            Moment.ASR -> t.asr
            Moment.MAGHRIB -> t.maghrib
            else -> t.isha
        }
        return PrayerSessions.minute(d)
    }

    /** [rule] on the local day containing [day]. */
    fun on(rule: TimeRule, day: Long, loc: Pair<Double, Double>?, zone: TimeZone = TimeZone.getDefault()): Long =
        momentOn(rule.moment, day, loc, zone) + rule.offsetMin * 60_000L

    private fun dayShift(at: Long, days: Int, zone: TimeZone) =
        Calendar.getInstance(zone).apply { timeInMillis = at; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    /** The first time [rule] happens strictly after [now]. */
    fun next(rule: TimeRule, now: Long, loc: Pair<Double, Double>?, zone: TimeZone = TimeZone.getDefault()): Long {
        // An offset can push the time into the day before or after, so look one day either side.
        for (d in -1..2) {
            val t = on(rule, dayShift(now, d, zone), loc, zone)
            if (t > now) return t
        }
        return on(rule, dayShift(now, 3, zone), loc, zone)
    }

    /** True if [rule] happened after [from] and up to [to]. */
    fun passedBetween(rule: TimeRule, from: Long, to: Long, loc: Pair<Double, Double>?, zone: TimeZone = TimeZone.getDefault()): Boolean =
        to > from && next(rule, from, loc, zone) <= to
}
