package com.azkar.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.reminders.Moment
import com.azkar.app.reminders.TimeRule

private val MOMENT_NAMES = mapOf(
    Moment.FAJR to R.string.moment_fajr, Moment.SUNRISE to R.string.moment_sunrise, Moment.DHUHR to R.string.moment_dhuhr,
    Moment.ASR to R.string.moment_asr, Moment.MAGHRIB to R.string.moment_maghrib, Moment.ISHA to R.string.moment_isha
)

/** Minutes before (negative) or after a prayer offered by [pickTimeRule]. */
private val OFFSETS = listOf(-60, -30, -20, -10, 0, 10, 20, 30, 45, 60, 90, 120)

/** "20 min after Fajr", "At Dhuhr", "At midnight", or, for null, "Never". */
fun timeRuleLabel(ctx: Context, rule: TimeRule?): String {
    if (rule == null) return ctx.getString(R.string.rule_never)
    if (rule.moment == Moment.MIDNIGHT) return ctx.getString(R.string.rule_midnight)
    val name = ctx.getString(MOMENT_NAMES.getValue(rule.moment))
    return when {
        rule.offsetMin < 0 -> ctx.getString(R.string.rule_before, -rule.offsetMin, name)
        rule.offsetMin > 0 -> ctx.getString(R.string.rule_after, rule.offsetMin, name)
        else -> ctx.getString(R.string.rule_at, name)
    }
}

/**
 * Two steps: the moment (a prayer, or midnight / never when allowed), then for a prayer how many minutes before or
 * after it. [onPicked] gets null for "never".
 */
fun Fragment.pickTimeRule(title: Int, current: TimeRule?, allowNever: Boolean, allowMidnight: Boolean, onPicked: (TimeRule?) -> Unit) {
    val ctx = requireContext()
    val moments: List<Moment?> = (if (allowNever) listOf<Moment?>(null) else emptyList()) +
        (if (allowMidnight) listOf(Moment.MIDNIGHT) else emptyList()) + MOMENT_NAMES.keys
    val names = moments.map { m ->
        when (m) {
            null -> ctx.getString(R.string.rule_never)
            Moment.MIDNIGHT -> ctx.getString(R.string.rule_midnight)
            else -> ctx.getString(MOMENT_NAMES.getValue(m))
        }
    }
    AlertDialog.Builder(ctx)
        .setTitle(title)
        .setSingleChoiceItems(names.toTypedArray(), moments.indexOf(current?.moment).coerceAtLeast(0)) { d, which ->
            d.dismiss()
            val m = moments[which]
            if (m == null || m == Moment.MIDNIGHT) { onPicked(m?.let { TimeRule(it) }); return@setSingleChoiceItems }
            val choices = OFFSETS.map { TimeRule(m, it) }
            val selected = choices.indexOfFirst { it == current }.takeIf { it >= 0 } ?: OFFSETS.indexOf(0)
            AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.rule_offset_title, ctx.getString(MOMENT_NAMES.getValue(m))))
                .setSingleChoiceItems(choices.map { timeRuleLabel(ctx, it) }.toTypedArray(), selected) { d2, i ->
                    d2.dismiss()
                    onPicked(choices[i])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
