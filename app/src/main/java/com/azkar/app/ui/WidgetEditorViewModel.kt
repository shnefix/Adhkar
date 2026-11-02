package com.azkar.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.azkar.app.widgets.LargeWidgetProvider
import com.azkar.app.widgets.MediumWidgetProvider
import com.azkar.app.widgets.SmallWidgetProvider
import com.azkar.app.widgets.WidgetKind
import com.azkar.app.widgets.WidgetStore
import com.azkar.app.widgets.WidgetStyle
import com.azkar.app.widgets.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class PlacedWidget(
    val id: Int,
    val kind: WidgetKind,
    val config: WidgetStore.Config
)

@HiltViewModel
class WidgetEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var loaded = false
    private val drafts = LinkedHashMap<Int, PlacedWidget>()
    var pickerFilter: WidgetKind? = null
        private set

    fun setPickerFilter(filter: WidgetKind?) {
        pickerFilter = filter
    }

    fun load(context: Context): List<PlacedWidget> {
        if (!loaded) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(
                WidgetKind.SMALL to SmallWidgetProvider::class.java,
                WidgetKind.MEDIUM to MediumWidgetProvider::class.java,
                WidgetKind.LARGE to LargeWidgetProvider::class.java
            ).forEach { (kind, provider) ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                    drafts[id] = PlacedWidget(id, kind, WidgetStore.config(context, id, kind))
                }
            }
            loaded = true
        }
        return drafts.values.toList()
    }

    fun reload(context: Context): List<PlacedWidget> {
        loaded = false
        return load(context)
    }

    fun selectedId(): Int? = savedStateHandle["selected_widget_id"]

    fun setSelectedId(id: Int) {
        savedStateHandle["selected_widget_id"] = id
    }

    fun activeTab(): Int = savedStateHandle["active_tab"] ?: 0

    fun setActiveTab(tab: Int) {
        savedStateHandle["active_tab"] = tab
    }

    fun update(id: Int, config: WidgetStore.Config) {
        drafts[id]?.let { drafts[id] = it.copy(config = config) }
    }

    fun reset(id: Int, context: Context) {
        val placed = drafts[id] ?: return
        drafts[id] = placed.copy(config = WidgetStore.config(context, id, placed.kind))
    }

    fun apply(context: Context, id: Int) {
        val placed = drafts[id] ?: return
        WidgetStore.setConfig(context, id, placed.config.style, placed.config.folder, placed.config.appearance)
        if (placed.config.appearance.refreshMinutes <= 0) {
            WidgetUpdater.cancelRefresh(context, id, placed.kind)
        } else {
            WidgetUpdater.scheduleRefresh(context, id, placed.kind)
        }
        WidgetUpdater.update(context, AppWidgetManager.getInstance(context), id, placed.kind)
    }

    fun defaultStyle(kind: WidgetKind): WidgetStyle =
        WidgetStyle.values().firstOrNull { kind in it.kinds } ?: WidgetStyle.CLASSIC
}
