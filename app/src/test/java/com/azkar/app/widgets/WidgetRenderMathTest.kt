package com.azkar.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRenderMathTest {
    @Test
    fun ringSweepClampsAndConvertsFraction() {
        assertEquals(0f, WidgetRenderMath.ringSweep(-1f), 0.001f)
        assertEquals(180f, WidgetRenderMath.ringSweep(.5f), 0.001f)
        assertEquals(360f, WidgetRenderMath.ringSweep(2f), 0.001f)
    }

    @Test
    fun focusedBeadIsLarger() {
        assertEquals(30f, WidgetRenderMath.beadDiameter(3, 3), 0.001f)
        assertEquals(16f, WidgetRenderMath.beadDiameter(2, 3), 0.001f)
    }

    @Test
    fun barFillHeightClampsToTrack() {
        assertEquals(0f, WidgetRenderMath.barFillHeight(10f, 110f, -1f), 0.001f)
        assertEquals(50f, WidgetRenderMath.barFillHeight(10f, 110f, .5f), 0.001f)
        assertEquals(100f, WidgetRenderMath.barFillHeight(10f, 110f, 2f), 0.001f)
    }
}
