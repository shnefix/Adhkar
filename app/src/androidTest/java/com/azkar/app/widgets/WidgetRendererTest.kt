package com.azkar.app.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azkar.app.ThemeMode
import com.azkar.app.R
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class WidgetRendererTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun checklistIsNotAvailableForSmallWidgets() {
        assertFalse(WidgetKind.SMALL in WidgetStyle.CHECKLIST.kinds)
    }

    @Test
    fun rendersEverySupportedStyleInBothThemes() {
        Store.init(context)
        val model = runBlocking {
            Store.awaitReady()
            val folder = Store.folders().first()
            WidgetModel(folder.name, Store.readerItems(folder.id), emptyList(), 0, 0, "")
        }
        val cases = listOf(
            WidgetStyle.CLASSIC to WidgetKind.SMALL,
            WidgetStyle.CLASSIC to WidgetKind.MEDIUM,
            WidgetStyle.CLASSIC to WidgetKind.LARGE,
            WidgetStyle.CHECKLIST to WidgetKind.MEDIUM,
            WidgetStyle.CHECKLIST to WidgetKind.LARGE,
            WidgetStyle.TASBIH to WidgetKind.SMALL,
            WidgetStyle.TASBIH to WidgetKind.LARGE,
            WidgetStyle.CALLIGRAPHY to WidgetKind.SMALL,
            WidgetStyle.CALLIGRAPHY to WidgetKind.MEDIUM,
        )

        for (theme in intArrayOf(ThemeMode.CURRENT, ThemeMode.OLED)) {
            AppPrefs.setTheme(theme)
            for ((style, kind) in cases) {
                val (width, height) = when (kind) {
                    WidgetKind.SMALL -> 158f to 158f
                    WidgetKind.MEDIUM -> 338f to 158f
                    WidgetKind.LARGE -> 338f to 354f
                }
                val bitmap = WidgetRenderer(context).render(style, kind, model, width, height, 1f)
                assertEquals(width.toInt(), bitmap.width)
                assertEquals(height.toInt(), bitmap.height)
                assertTrue(bitmap.hasAlpha())
                assertTrue(bitmap.getPixel(1, bitmap.height / 2) != 0)
                bitmap.recycle()
            }
        }
    }

    @Test
    fun themeColorsMeetReadableContrast() {
        val resources = context.resources
        val palettes = listOf(
            R.color.widget_teal_background to R.color.widget_teal_text,
            R.color.widget_teal_background to R.color.widget_teal_muted,
            R.color.widget_oled_background to R.color.widget_oled_text,
            R.color.widget_oled_background to R.color.widget_oled_muted,
        )
        for ((backgroundId, foregroundId) in palettes) {
            val background = resources.getColor(backgroundId, context.theme)
            val foreground = resources.getColor(foregroundId, context.theme)
            assertTrue(contrast(background, foreground) >= 4.5)
        }

    }

    @Test
    fun rendersEmptyAndTinyWidgetsWithoutCrashing() {
        Store.init(context)
        val empty = WidgetModel("Empty", emptyList(), emptyList(), 0, 0, "")
        for (theme in intArrayOf(ThemeMode.CURRENT, ThemeMode.OLED)) {
            AppPrefs.setTheme(theme)
            val bitmap = WidgetRenderer(context).render(
                WidgetStyle.CLASSIC, WidgetKind.SMALL, empty, -10f, 0f, 1f
            )
            assertEquals(1, bitmap.width)
            assertEquals(1, bitmap.height)
            bitmap.recycle()
        }
    }

    @Test
    fun rendersLongArabicTextWithAppTheme() {
        val longText = "sample text ".repeat(120)
        val model = WidgetModel(
            "A folder with an unusually long name that must be clipped",
            listOf(com.azkar.app.data.ReaderItem(longText, 100)),
            emptyList(),
            0,
            0,
            ""
        )
        AppPrefs.setTheme(ThemeMode.OLED)
        val bitmap = WidgetRenderer(context).render(
            WidgetStyle.CALLIGRAPHY, WidgetKind.MEDIUM, model, 338f, 158f, 1f
        )
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        bitmap.recycle()
    }

    private fun contrast(first: Int, second: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        fun luminance(color: Int): Double =
            0.2126 * channel(color shr 16 and 0xFF) +
                0.7152 * channel(color shr 8 and 0xFF) +
                0.0722 * channel(color and 0xFF)
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }
}
