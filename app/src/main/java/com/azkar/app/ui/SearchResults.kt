package com.azkar.app.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.azkar.app.R
import com.azkar.app.data.Folder
import com.azkar.app.data.LibrarySearch
import com.azkar.app.data.Store
import com.azkar.app.data.Zikr

/**
 * The Library's search results (design A): filter pills, matching folders in a row, then matching azkar with the
 * matched words in gold, their folder, and where they matched when it wasn't the zikr's own text.
 */
object SearchResults {
    enum class Filter { ALL, AZKAR, FOLDERS, SOURCES }

    private const val PAGE = 40

    private fun dp(ctx: Context, v: Float) = Ui.pxi(ctx, v)

    private fun label(ctx: Context, text: CharSequence, sp: Float, color: Int, bold: Boolean = false, style: Int = 0) =
        TextView(ctx, null, 0, if (style != 0) style else if (bold) R.style.TB else R.style.T).apply {
            this.text = text; textSize = sp; setTextColor(ctx.getColor(color))
        }

    private fun gold(ctx: Context, text: String, ranges: List<IntRange>): CharSequence {
        if (ranges.isEmpty()) return text
        return SpannableString(text).apply {
            ranges.forEach { r -> setSpan(ForegroundColorSpan(ctx.getColor(R.color.gold)), r.first, (r.last + 1).coerceAtMost(text.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    private fun section(ctx: Context, title: Int, right: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(ctx, null, 0, R.style.Caps).apply { setText(title); textSize = 12f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(ctx, right, 12f, R.color.muted))
    }

    fun render(
        ctx: Context, body: LinearLayout, query: String, result: LibrarySearch.Result, filter: Filter, shown: Int,
        counts: Map<String?, Int>, onFilter: (Filter) -> Unit, onMore: () -> Unit,
        onFolder: (Folder) -> Unit, onZikr: (Zikr) -> Unit
    ) {
        body.removeAllViews()
        fun add(v: View, top: Float) = body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, top) })
        val sources = result.zikrs.filter { it.where == LibrarySearch.Where.HADITH || it.where == LibrarySearch.Where.SOURCE }

        // Filter pills with their counts.
        val pills = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Filter.ALL to ctx.getString(R.string.search_all, result.folders.size + result.zikrs.size),
            Filter.AZKAR to ctx.getString(R.string.search_azkar, result.zikrs.size),
            Filter.FOLDERS to ctx.getString(R.string.search_folders_n, result.folders.size),
            Filter.SOURCES to ctx.getString(R.string.search_sources, sources.size)
        ).forEach { (f, text) ->
            val on = f == filter
            pills.addView(label(ctx, text, 13f, if (on) R.color.bg else R.color.light, bold = on).apply {
                background = if (on) Ui.shape(ctx, ctx.getColor(R.color.gold), null, 999f)
                    else Ui.shape(ctx, ctx.getColor(R.color.card2), ctx.getColor(R.color.border2), 999f)
                setPadding(dp(ctx, 13f), dp(ctx, 7f), dp(ctx, 13f), dp(ctx, 7f))
                isClickable = true; isFocusable = true
                setOnClickListener { onFilter(f) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(ctx, 8f) })
        }
        add(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(pills) }, 0f)

        val showFolders = (filter == Filter.ALL || filter == Filter.FOLDERS) && result.folders.isNotEmpty()
        val zikrs = when (filter) {
            Filter.ALL, Filter.AZKAR -> result.zikrs
            Filter.SOURCES -> sources
            Filter.FOLDERS -> emptyList()
        }

        if (!showFolders && zikrs.isEmpty()) {
            add(label(ctx, ctx.getString(R.string.search_none, query), 15f, R.color.muted).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(ctx, 40f), 0, 0)
            }, 8f)
            return
        }

        if (showFolders) {
            add(section(ctx, R.string.search_section_folders, result.folders.size.toString()), 20f)
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            result.folders.forEach { hit -> row.addView(folderCard(ctx, hit, counts[hit.folder.id] ?: 0, onFolder),
                LinearLayout.LayoutParams(dp(ctx, 164f), -1).apply { marginStart = dp(ctx, 10f) }) }
            add(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(row)
            }, 10f)
        }

        if (zikrs.isNotEmpty()) {
            add(section(ctx, R.string.search_section_azkar, ctx.getString(R.string.search_best_first, zikrs.size)), 22f)
            zikrs.take(shown).forEach { add(zikrCard(ctx, it, onZikr), 10f) }
            if (zikrs.size > shown) add(label(ctx, ctx.getString(R.string.search_more, zikrs.size - shown), 14f, R.color.gold, bold = true).apply {
                gravity = Gravity.CENTER; minHeight = dp(ctx, 48f)
                background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border), 24f)
                isClickable = true; isFocusable = true
                setOnClickListener { onMore() }
            }, 12f)
        }
    }

    val pageSize get() = PAGE

    private fun folderCard(ctx: Context, hit: LibrarySearch.FolderHit, count: Int, onFolder: (Folder) -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border), 18f)
        setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
        isClickable = true; isFocusable = true
        setOnClickListener { onFolder(hit.folder) }
        val default = Store.isDefaultFolder(hit.folder.id)
        addView(android.widget.FrameLayout(ctx).apply {
            background = Ui.shape(ctx, ctx.getColor(R.color.card2), null, 16f)
            addView(ImageView(ctx).apply {
                setImageResource(if (default) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75)
                setColorFilter(ctx.getColor(if (default) R.color.teal else R.color.gold))
            }, android.widget.FrameLayout.LayoutParams(dp(ctx, 16f), dp(ctx, 16f), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(ctx, 32f), dp(ctx, 32f)))
        addView(label(ctx, gold(ctx, hit.folder.name, hit.ranges), 17f, R.color.text, style = R.style.Ar).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(ctx, 6f), 0, 0)
        })
        addView(label(ctx, ctx.resources.getQuantityString(R.plurals.azkar_count, count, count), 12f, R.color.muted).apply {
            textDirection = View.TEXT_DIRECTION_LTR
        })
    }

    private fun zikrCard(ctx: Context, hit: LibrarySearch.ZikrHit, onZikr: (Zikr) -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border), 18f)
        setPadding(dp(ctx, 16f), dp(ctx, 12f), dp(ctx, 16f), dp(ctx, 12f))
        isClickable = true; isFocusable = true
        setOnClickListener { onZikr(hit.zikr) }
        val z = hit.zikr
        addView(label(ctx, gold(ctx, z.arabic, hit.ranges), 18f, R.color.arabic, style = R.style.Ar).apply {
            textDirection = View.TEXT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setLineSpacing(0f, 1.25f); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        })
        if (hit.where == LibrarySearch.Where.HADITH && hit.snippet.isNotBlank()) {
            val q = hit.snippet
            addView(label(ctx, q, 14f, R.color.muted, style = R.style.Ar).apply {
                textDirection = View.TEXT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                background = Ui.shape(ctx, ctx.getColor(R.color.card2), null, 12f)
                setPadding(dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f))
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 6f) })
        }
        // Right to left, like the zikr above it: folder on the right, the reference on the left.
        val meta = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        meta.addView(label(ctx, Store.folderName(z.folderId), 12f, R.color.teal).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        val where = when (hit.where) {
            LibrarySearch.Where.TITLE -> ctx.getString(R.string.search_in_title)
            LibrarySearch.Where.NOTE -> ctx.getString(R.string.search_in_note)
            LibrarySearch.Where.HADITH -> ctx.getString(R.string.search_in_hadith)
            LibrarySearch.Where.SOURCE -> ctx.getString(R.string.search_in_source)
            LibrarySearch.Where.TEXT -> ""
        }
        if (where.isNotEmpty()) meta.addView(label(ctx, where, 12f, R.color.muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(ctx, 8f) })
        if (z.source.reference.isNotBlank()) meta.addView(label(ctx, z.source.reference, 11f, R.color.gold).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(ctx, 150f)
            background = Ui.shape(ctx, null, ctx.getColor(R.color.border2), 999f)
            setPadding(dp(ctx, 8f), dp(ctx, 2f), dp(ctx, 8f), dp(ctx, 2f))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(ctx, 8f) })
        addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8f) })
    }
}
