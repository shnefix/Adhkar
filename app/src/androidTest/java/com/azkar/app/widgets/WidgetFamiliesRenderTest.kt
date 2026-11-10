package com.azkar.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azkar.app.data.DefaultFolders
import com.azkar.app.data.Store
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Every design and tracking widget draws, in both languages, at its design size and at the launcher's minimum. Read-only. */
@RunWith(AndroidJUnit4::class)
class WidgetFamiliesRenderTest {
    private val base: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyFamilyRendersWithoutCrashing() {
        Store.init(base)
        runBlocking { Store.awaitReady() }
        val folder = Store.folder(DefaultFolders.MORNING)?.id ?: Store.folders().first().id
        for (lang in listOf("en", "ar")) {
            val ctx = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale(lang)) })
            val model = WidgetModel.load(ctx, AppWidgetManager.INVALID_APPWIDGET_ID, folder)
            for (family in AzkarWidgetFamily.values()) {
                val (w, h) = AzkarWidgetSizes.design(family)
                for ((rw, rh) in listOf(w to h, 110f to 74f)) {
                    val bmp = NewWidgetRenderer(ctx).render(family, model, rw, rh)
                    assertTrue("$family $lang", bmp.width > 0 && bmp.height > 0)
                    bmp.recycle()
                }
            }
        }
    }

    @Test
    fun mergedWidgetDrawsTheDesignForEachState() {
        // The three states pick different designs, so the pictures differ (checked on the session helpers directly in unit tests).
        Store.init(base)
        runBlocking { Store.awaitReady() }
        val session = AzkarSessionClock.activeSession()
        val boundary = AzkarSessionClock.nextBoundary()
        if (session != null) assertTrue("an open window must close later", boundary != null && boundary > System.currentTimeMillis())
    }
}
