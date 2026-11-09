package com.azkar.app.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.azkar.app.R
import com.azkar.app.data.NarratorChain
import com.azkar.app.data.ReaderItem

/**
 * The source page: a bottom sheet over the reader, always in Arabic ([ar] is an Arabic-locale Resources).
 */
object SourceViews {
    private fun dp(ctx: Context, v: Float) = Ui.pxi(ctx, v)

    private fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false, style: Int = 0) =
        TextView(ctx, null, 0, if (style != 0) style else if (bold) R.style.TB else R.style.T).apply {
            text = s; textSize = sp; setTextColor(ctx.getColor(color))
            textDirection = View.TEXT_DIRECTION_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

    private fun card(ctx: Context, fill: Int = R.color.card) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.shape(ctx, ctx.getColor(fill), ctx.getColor(R.color.border), 20f)
        setPadding(dp(ctx, 18f), dp(ctx, 16f), dp(ctx, 18f), dp(ctx, 16f))
    }

    /** Design G's zikr frame: the card with a dashed line inset inside its border. */
    fun framedCard(ctx: Context): android.graphics.drawable.Drawable {
        val inset = dp(ctx, 5f)
        return android.graphics.drawable.LayerDrawable(arrayOf(
            Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border2), 28f),
            Ui.shape(ctx, null, ctx.getColor(R.color.border2), 23f, dashed = true)
        )).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /** Gold pill with the grade (صحيح …). */
    fun gradeBadge(ctx: Context, grade: String) = text(ctx, grade, 12f, R.color.bg, bold = true).apply {
        background = Ui.shape(ctx, ctx.getColor(R.color.gold), null, 999f)
        setPadding(dp(ctx, 12f), dp(ctx, 4f), dp(ctx, 12f), dp(ctx, 4f))
    }

    /** The hadith in the Arabic font, the zikr's words in gold. */
    private fun hadith(ctx: Context, body: CharSequence) = text(ctx, body, 19f, R.color.text, style = R.style.Ar).apply {
        setLineSpacing(0f, 1.55f)
    }

    /** The small copy button in the hadith card's bottom-left corner: copies the hadith and its reference. */
    private fun copyIcon(ctx: Context, ar: Resources, s: com.azkar.app.data.ZikrSource) = android.widget.ImageView(ctx).apply {
        setImageResource(R.drawable.ic_copy_1p75)
        setColorFilter(ctx.getColor(R.color.gold))
        background = Ui.shape(ctx, ctx.getColor(R.color.card2), ctx.getColor(R.color.border2), 999f)
        val pad = dp(ctx, 9f); setPadding(pad, pad, pad, pad)
        contentDescription = ar.getString(R.string.source_copy)
        isClickable = true; isFocusable = true
        setOnClickListener {
            val clip = ctx.getSystemService(ClipboardManager::class.java)
            clip?.setPrimaryClip(ClipData.newPlainText(ar.getString(R.string.source_title), listOf(s.detail, s.reference).filter { it.isNotBlank() }.joinToString("\n\n")))
            Toast.makeText(ctx, ar.getString(R.string.source_copied), Toast.LENGTH_SHORT).show()
        }
    }

    /** The row under the hadith: copy icon on the left, [end] (if any) on the right. */
    private fun hadithFooter(ctx: Context, ar: Resources, s: com.azkar.app.data.ZikrSource, end: View? = null) = FrameLayout(ctx).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        addView(copyIcon(ctx, ar, s), FrameLayout.LayoutParams(dp(ctx, 38f), dp(ctx, 38f), Gravity.LEFT or Gravity.CENTER_VERTICAL))
        if (end != null) addView(end, FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
    }

    /**
     * The chain block: «سند الحديث» with «من النبي ﷺ إلى …» beside it, then the timeline from the Prophet ﷺ
     * (gold dot) through the companion (gold ring) and the narrators (grey rings) to the author (cream square).
     */
    fun chainBlock(ctx: Context, ar: Resources, links: List<NarratorChain.Link>): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        head.addView(text(ctx, ar.getString(R.string.source_chain_title), 15f, R.color.text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        links.lastOrNull { it.role == NarratorChain.Role.AUTHOR }?.let { author ->
            head.addView(text(ctx, ar.getString(R.string.source_chain_span, author.name), 12f, R.color.muted))
        }
        addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 12f); marginStart = dp(ctx, 4f); marginEnd = dp(ctx, 4f) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border), 22f)
            setPadding(dp(ctx, 20f), dp(ctx, 22f), dp(ctx, 20f), dp(ctx, 22f))
            addView(chainTimeline(ctx, links))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun chainTimeline(ctx: Context, links: List<NarratorChain.Link>): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val gold = ctx.getColor(R.color.gold)
        links.forEachIndexed { i, link ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            val rail = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val dot = View(ctx).apply {
                background = when (link.role) {
                    NarratorChain.Role.PROPHET -> Ui.shape(ctx, gold, null, 7f)
                    NarratorChain.Role.AUTHOR -> Ui.shape(ctx, ctx.getColor(R.color.arabic), null, 3f)
                    NarratorChain.Role.COMPANION -> Ui.shape(ctx, null, gold, 7f)
                    else -> Ui.shape(ctx, null, ctx.getColor(R.color.hint), 7f)
                }
            }
            rail.addView(dot, LinearLayout.LayoutParams(dp(ctx, 14f), dp(ctx, 14f)).apply { topMargin = dp(ctx, 4f) })
            if (i < links.size - 1) rail.addView(View(ctx).apply { setBackgroundColor(ctx.getColor(R.color.track)) },
                LinearLayout.LayoutParams(dp(ctx, 2f), 0, 1f))
            row.addView(rail, LinearLayout.LayoutParams(dp(ctx, 16f), LinearLayout.LayoutParams.MATCH_PARENT))
            val labels = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(ctx, 16f), 0, 0, if (i < links.size - 1) dp(ctx, 18f) else 0)
                val nameColor = when (link.role) {
                    NarratorChain.Role.PROPHET -> R.color.gold
                    NarratorChain.Role.AUTHOR -> R.color.arabic
                    else -> R.color.text
                }
                addView(text(ctx, link.name, 15f, nameColor, bold = true))
                addView(text(ctx, link.label, 12f, R.color.muted).apply { setPadding(0, dp(ctx, 2f), 0, 0) })
            }
            row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Style H: a bottom sheet over the reader, with the chain as a short row that opens into the full timeline. */
    fun showSheet(ctx: Context, ar: Resources, item: ReaderItem, marked: (String, String) -> CharSequence) {
        val s = item.source
        val dialog = Dialog(ctx)
        val chain = s.detail.takeIf { it.isNotBlank() }?.let { NarratorChain.parse(it, s.reference) }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundResource(R.drawable.bg_sheet_top)
            setPadding(dp(ctx, 18f), dp(ctx, 10f), dp(ctx, 18f), dp(ctx, 18f))
        }
        root.addView(View(ctx).apply { background = Ui.shape(ctx, ctx.getColor(R.color.border2), null, 2f) },
            LinearLayout.LayoutParams(dp(ctx, 40f), dp(ctx, 4f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(ctx, 12f) })

        val refInHead = s.reference.isNotBlank() && s.detail.isNotBlank() && s.reference.length <= 40
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(ctx, ar.getString(R.string.source_title), 18f, R.color.text, bold = true))
            // A short reference fits under the title; a long one, or one with no hadith text, gets its own card below.
            if (refInHead) addView(text(ctx, s.reference, 12f, R.color.muted), LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(ctx, 10f) })
        if (s.grade.isNotBlank()) head.addView(gradeBadge(ctx, s.grade), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(ctx, 10f) })
        head.addView(FrameLayout(ctx).apply {
            background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(R.color.border), 22f)
            contentDescription = ar.getString(R.string.source_close)
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
            addView(text(ctx, "✕", 15f, R.color.light), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f)))
        root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 12f) })

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun add(v: View) = body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { if (body.childCount > 0) topMargin = dp(ctx, 12f) })

        if (s.reference.isNotBlank() && !refInHead) add(card(ctx, R.color.bg).apply {
            addView(text(ctx, ar.getString(R.string.source_reference), 12f, R.color.gold, bold = true))
            addView(text(ctx, s.reference, 16f, R.color.text).apply { setLineSpacing(0f, 1.35f); setPadding(0, dp(ctx, 6f), 0, 0) })
            if (s.detail.isBlank()) addView(hadithFooter(ctx, ar, s), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 12f) })
        })
        if (s.detail.isNotBlank()) add(card(ctx, R.color.bg).apply {
            addView(hadith(ctx, marked(s.detail, item.arabic)))
            addView(hadithFooter(ctx, ar, s), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 12f) })
        })
        if (chain != null) add(chainBlock(ctx, ar, chain))
        if (s.whenToSay.isNotBlank()) add(card(ctx, R.color.bg).apply {
            addView(text(ctx, ar.getString(R.string.source_when), 12f, R.color.muted))
            addView(text(ctx, s.whenToSay, 16f, R.color.text, bold = true).apply { setPadding(0, dp(ctx, 2f), 0, 0) })
        })

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(text(ctx, ar.getString(R.string.source_back_to_zikr), 14f, R.color.bg, bold = true).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = Ui.shape(ctx, ctx.getColor(R.color.gold), null, 24f)
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(ctx, 48f), 1f))
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 14f) })

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, (ctx.resources.displayMetrics.heightPixels * 0.85f).toInt())
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setDimAmount(0.65f)
        }
        dialog.show()
    }
}
