package com.azkar.app.widgets

import android.content.Context
import android.icu.text.DateFormatSymbols
import android.text.format.DateFormat
import com.azkar.app.R
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.ReaderResume
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.reminders.PrayerSessions
import java.util.Calendar
import java.util.Date

/**
 * Everything the tracking widgets (streak, countdown, bento, today) draw, read from [Tracking],
 * the prayer times and [Store]. Nothing in here is a fixed value: no folder, time or count is assumed.
 */
class TrackingModel(
    val folders: List<String>,
    val title: String,
    val streak: Int,
    val best: Int,
    val today: Tracking.Progress,
    /** The last 7 days, oldest first; the last one is today. */
    val week: List<Day>,
    /** 5 weeks, aligned to the locale's first day of the week, ending with this week. */
    val grid: List<Day>,
    /** Narrow weekday names in [grid] column order. */
    val gridHeader: List<String>,
    val rangeLabel: String,
    val dateLabel: String,
    val sessions: List<SessionInfo>,
    /** The session running now, else the next one to start. */
    val focus: SessionInfo?,
    /** Zikr to show for [focus] (where the reader stopped, or the first one). */
    val dhikr: String?,
    val now: Long,
    val dayStart: Long,
    val dayEnd: Long,
) {
    class Day(val level: Tracking.Level, val complete: Boolean, val isToday: Boolean, val future: Boolean, val initial: String)

    class SessionInfo(
        val session: PrayerSessions.Session,
        val label: String,
        val folderId: String,
        /** Today's start and end (end is unknown without a location). */
        val start: Long,
        val end: Long?,
        /** The next start after now (today's or tomorrow's). */
        val nextStart: Long,
        val progress: Tracking.Progress,
    ) {
        fun running(now: Long) = end != null && now in start until end
    }

    val hasFolders get() = folders.isNotEmpty()
    val focusRunning get() = focus?.running(now) == true

    /** Milliseconds until [focus] ends (while it runs) or starts. */
    val focusDelta: Long get() = focus?.let { if (focusRunning) it.end!! - now else it.nextStart - now }?.coerceAtLeast(0L) ?: 0L

    /** Folder shown in single-folder spots (bento, today): the running/next session if tracked, else the first tracked one. */
    val primaryFolder: String? get() = focus?.folderId?.takeIf { it in folders } ?: folders.firstOrNull()

    companion object {
        fun load(ctx: Context, folder: String?, nowMs: Long = System.currentTimeMillis()): TrackingModel {
            val folders = Tracking.resolve(folder)
            val now = Calendar.getInstance().apply { timeInMillis = nowMs }
            val locale = ctx.resources.configuration.locales[0]
            val narrow = DateFormatSymbols.getInstance(locale).getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW)
            fun day(c: Calendar, isToday: Boolean, future: Boolean) = dayOf(c, folders, isToday, future, narrow[c.get(Calendar.DAY_OF_WEEK)])

            val week = (6 downTo 0).map { back ->
                val c = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
                day(c, back == 0, false)
            }

            // Grid: from the first day of the week four weeks ago to the last day of this week.
            val first = locale.let { Calendar.getInstance(it).firstDayOfWeek }
            val weekStart = (now.clone() as Calendar).apply {
                while (get(Calendar.DAY_OF_WEEK) != first) add(Calendar.DAY_OF_YEAR, -1)
                add(Calendar.DAY_OF_YEAR, -28)
            }
            val todayKey = Tracking.dayKey(now)
            val grid = (0 until 35).map { i ->
                val c = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val key = Tracking.dayKey(c)
                day(c, key == todayKey, key > todayKey)
            }
            val header = (0 until 7).map { i ->
                narrow[(weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.get(Calendar.DAY_OF_WEEK)]
            }
            val dm = DateFormat.getBestDateTimePattern(locale, "dMMM")
            val gridEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 34) }
            val range = "${DateFormat.format(dm, weekStart)} – ${DateFormat.format(dm, gridEnd)}"

            val midnight = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val nextMidnight = (midnight.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

            val sessions = PrayerSessions.Session.values().mapNotNull { s -> session(ctx, s, nowMs, midnight.timeInMillis) }
            val focus = sessions.firstOrNull { it.running(nowMs) && !it.progress.complete }
                ?: sessions.minByOrNull { it.nextStart }

            val title = when {
                folders.size == 1 -> Store.folderName(folders[0])
                folders.toSet() == PrayerSessions.Session.values().map { it.folderId }.toSet() -> ctx.getString(R.string.widget_sessions_title)
                else -> ctx.getString(R.string.widget_all_tracked)
            }
            return TrackingModel(
                folders = folders,
                title = title,
                streak = Tracking.streak(folders, now),
                best = Tracking.best(folders),
                today = Tracking.progress(folders, now),
                week = week,
                grid = grid,
                gridHeader = header,
                rangeLabel = range,
                dateLabel = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), now).toString(),
                sessions = sessions,
                focus = focus,
                dhikr = (focus?.folderId ?: folders.firstOrNull())?.let { currentZikr(it) },
                now = nowMs,
                dayStart = midnight.timeInMillis,
                dayEnd = nextMidnight.timeInMillis,
            )
        }

        /** Morning / evening window: prayer times when a location is known, else the reminder's own time. */
        private fun session(ctx: Context, s: PrayerSessions.Session, now: Long, midnight: Long): SessionInfo? {
            if (Store.folder(s.folderId) == null) return null
            val label = ctx.getString(if (s == PrayerSessions.Session.MORNING) R.string.widget_morning else R.string.widget_evening)
            val dayMs = 24 * 60 * 60_000L
            val window = PrayerSessions.window(s, now)
            val (start, end) = window ?: run {
                val sched = Store.schedule(s.scheduleId) ?: return null
                val at = Calendar.getInstance().apply {
                    timeInMillis = midnight
                    set(Calendar.HOUR_OF_DAY, sched.hour); set(Calendar.MINUTE, sched.minute)
                }.timeInMillis
                at to null
            }
            val nextStart = when {
                start > now -> start
                else -> PrayerSessions.window(s, now + dayMs)?.first ?: (start + dayMs)
            }
            val today = Calendar.getInstance().apply { timeInMillis = now }
            return SessionInfo(s, label, s.folderId, start, end, nextStart, Tracking.progress(listOf(s.folderId), today))
        }

        /** Where the reader stopped in [folder] (if it remembers its position and the session hasn't ended), else its first zikr. */
        private fun currentZikr(folder: String): String? {
            val items = Store.readerItems(folder)
            if (items.isEmpty()) return null
            if (Store.rememberPosition(folder) && !PrayerSessions.positionExpired(folder, AppPrefs.savedPositionTime(folder))) {
                val saved = AppPrefs.savedPosition(folder)
                val (i, _) = ReaderResume.start(items.map { it.id }, items.map { it.target }, saved?.first, saved?.second ?: 0)
                return items.getOrNull(i)?.arabic
            }
            return items.first().arabic
        }

        fun timeLabel(ctx: Context, at: Long): String = DateFormat.getTimeFormat(ctx).format(Date(at))
    }
}

/** Builds one calendar day of [folders]. */
private fun dayOf(c: Calendar, folders: List<String>, isToday: Boolean, future: Boolean, initial: String) =
    TrackingModel.Day(
        level = if (future) Tracking.Level.UPCOMING else Tracking.level(folders, c),
        complete = !future && Tracking.completeOn(folders, c),
        isToday = isToday,
        future = future,
        initial = initial,
    )
