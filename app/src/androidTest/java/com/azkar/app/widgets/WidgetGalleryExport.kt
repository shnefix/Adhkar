package com.azkar.app.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.icu.text.DateFormatSymbols
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azkar.app.R
import com.azkar.app.ThemeMode
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.DefaultFolders
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.reminders.PrayerSessions
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * Not a check: draws every widget design with the app's own renderers and saves the PNGs to
 * Android/data/com.azkar.app/files/widget-gallery, for the design sheet in docs/.
 * Tracking widgets use a sample streak, so nothing is written to the tracking history.
 */
@RunWith(AndroidJUnit4::class)
class WidgetGalleryExport {
    private val base: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = 3f
    private val sampleId = 987_654

    @Test
    fun exportAllDesigns() {
        Store.init(base)
        runBlocking { Store.awaitReady() }
        val out = File(base.getExternalFilesDir(null), "widget-gallery").apply { deleteRecursively(); mkdirs() }

        // A morning folder part-way through: 4th zikr, a few recitations in.
        val folder = Store.folder(DefaultFolders.MORNING)?.id ?: Store.folders().first().id
        val items = Store.readerItems(folder)
        val idx = 3.coerceAtMost(items.size - 1)
        val count = (items[idx].target / 3).coerceIn(0, items[idx].target - 1)
        val oldProgress = WidgetStore.progress(base, sampleId)
        WidgetStore.saveProgress(base, sampleId, idx, count)
        // A zikr with a long count, for the bead designs.
        val beadIdx = items.indexOfFirst { it.target >= 33 }.takeIf { it >= 0 } ?: idx

        val oldTheme = AppPrefs.theme()
        try {
            for (lang in listOf("en", "ar")) {
                val ctx = localized(lang)
                val model = WidgetModel.load(ctx, sampleId, folder)

                // Design and tracking widgets
                for (family in AzkarWidgetFamily.values()) {
                    val (w, h) = AzkarWidgetSizes.design(family)
                    val m = if (family.name.startsWith("H_") && beadIdx != idx) beadModel(ctx, folder, beadIdx) else model
                    val bmp = NewWidgetRenderer(ctx).render(family, m, w, h, if (family.tracking) sampleTracking(ctx, folder) else null)
                    save(bmp, File(out, "${family.name}_$lang.png"))
                }

                // Classic widgets: every style and size, in both app themes
                for ((theme, themeName) in listOf(ThemeMode.CURRENT to "teal", ThemeMode.OLED to "oled")) {
                    AppPrefs.setTheme(theme)
                    for (style in WidgetStyle.values()) for (kind in style.kinds) {
                        val (w, h) = when (kind) {
                            WidgetKind.SMALL -> 158f to 158f
                            WidgetKind.MEDIUM -> 338f to 158f
                            WidgetKind.LARGE -> 338f to 354f
                        }
                        val m = if (style == WidgetStyle.TASBIH && beadIdx != idx) beadModel(ctx, folder, beadIdx) else model
                        val bmp = WidgetRenderer(ctx).render(style, kind, m, w, h, density)
                        save(bmp, File(out, "CLASSIC-${kind.name}-${style.name}-${themeName}_$lang.png"))
                    }
                }
            }
        } finally {
            AppPrefs.setTheme(oldTheme)
            WidgetStore.saveProgress(base, sampleId, oldProgress.first, oldProgress.second)
            WidgetStore.clear(base, sampleId)
        }
    }

    private fun beadModel(ctx: Context, folder: String, i: Int): WidgetModel {
        val id = sampleId + 1
        val target = Store.readerItems(folder)[i].target
        WidgetStore.saveProgress(ctx, id, i, (target * 0.4f).toInt())
        return WidgetModel.load(ctx, id, folder).also { WidgetStore.clear(ctx, id) }
    }

    private fun localized(lang: String): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale(lang))
        return base.createConfigurationContext(cfg)
    }

    /** A sample streak: 12 days, best 21, the morning session running with 9 of 24 read. */
    private fun sampleTracking(ctx: Context, folder: String): TrackingModel {
        val locale = ctx.resources.configuration.locales[0]
        val narrow = DateFormatSymbols.getInstance(locale).getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW)
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val hour = 3_600_000L
        val dayStart = midnight.timeInMillis
        val now = dayStart + 8 * hour + 40 * 60_000L

        fun dayAt(back: Int) = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, -back) }
        val week = (6 downTo 0).map { back ->
            val c = dayAt(back)
            Tracking.Level.FULL.let { full ->
                TrackingModel.Day(if (back == 0) Tracking.Level.LOW else full, back != 0, back == 0, false, narrow[c.get(Calendar.DAY_OF_WEEK)])
            }
        }
        // Five weeks ending this week, starting on the locale's first weekday.
        val first = Calendar.getInstance().firstDayOfWeek
        val todayCol = (midnight.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        val levels = listOf(
            Tracking.Level.FULL, Tracking.Level.MID, Tracking.Level.FULL, Tracking.Level.MISSED, Tracking.Level.LOW,
            Tracking.Level.FULL, Tracking.Level.FULL, Tracking.Level.MID, Tracking.Level.FULL, Tracking.Level.FULL,
        )
        val grid = (0 until 35).map { i ->
            val back = (28 + todayCol) - i
            val c = dayAt(back)
            val future = back < 0
            val level = when {
                future -> Tracking.Level.UPCOMING
                back == 0 -> Tracking.Level.LOW
                back <= 12 -> Tracking.Level.FULL
                else -> levels[i % levels.size]
            }
            TrackingModel.Day(level, level == Tracking.Level.FULL, back == 0, future, narrow[c.get(Calendar.DAY_OF_WEEK)])
        }
        val header = (0 until 7).map { narrow[(first - 1 + it) % 7 + 1] }

        val morning = TrackingModel.SessionInfo(
            PrayerSessions.Session.MORNING, ctx.getString(R.string.widget_morning), folder,
            dayStart + 5 * hour + 10 * 60_000L, dayStart + 11 * hour + 40 * 60_000L,
            dayStart + 29 * hour + 10 * 60_000L, Tracking.Progress(9, 24),
        )
        val evening = TrackingModel.SessionInfo(
            PrayerSessions.Session.EVENING, ctx.getString(R.string.widget_evening), DefaultFolders.EVENING,
            dayStart + 15 * hour + 35 * 60_000L, dayStart + 19 * hour + 50 * 60_000L,
            dayStart + 15 * hour + 35 * 60_000L, Tracking.Progress(0, 22),
        )
        return TrackingModel(
            folders = listOf(folder, DefaultFolders.EVENING),
            title = Store.folderName(folder),
            streak = 12, best = 21,
            today = Tracking.Progress(9, 46),
            week = week, grid = grid, gridHeader = header,
            rangeLabel = android.text.format.DateFormat.format("d MMM", dayAt(28 + todayCol)).toString() +
                " – " + android.text.format.DateFormat.format("d MMM", midnight).toString(),
            dateLabel = android.text.format.DateFormat.format("EEE, d MMM", midnight).toString(),
            sessions = listOf(morning, evening), focus = morning,
            dhikr = Store.readerItems(folder).firstOrNull()?.arabic,
            now = now, dayStart = dayStart, dayEnd = dayStart + 24 * hour,
        )
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
}
