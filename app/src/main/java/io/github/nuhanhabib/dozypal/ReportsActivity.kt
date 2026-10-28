package io.github.nuhanhabib.dozypal

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Daily, weekly and monthly reports built from the history kept on the phone. */
class ReportsActivity : Activity() {

    private enum class Period(val days: Int) { DAY(1), WEEK(7), MONTH(30) }

    private lateinit var store: CountStore
    private lateinit var tabs: Map<Period, TextView>
    private lateinit var totalView: TextView
    private lateinit var totalLabel: TextView
    private lateinit var comparison: TextView
    private lateinit var chartCard: View
    private lateinit var chart: BarChartView
    private lateinit var stats: TextView
    private lateinit var breakdown: LinearLayout
    private var period = Period.DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        store = CountStore(this)

        totalView = findViewById(R.id.report_total)
        totalLabel = findViewById(R.id.report_total_label)
        comparison = findViewById(R.id.report_comparison)
        chartCard = findViewById(R.id.report_chart_card)
        chart = findViewById(R.id.report_chart)
        stats = findViewById(R.id.report_stats)
        breakdown = findViewById(R.id.report_breakdown)
        tabs = mapOf(
            Period.DAY to findViewById(R.id.tab_daily),
            Period.WEEK to findViewById(R.id.tab_weekly),
            Period.MONTH to findViewById(R.id.tab_monthly),
        )
        tabs.forEach { (p, view) -> view.setOnClickListener { period = p; render() } }
        findViewById<View>(R.id.reports_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        tabs.forEach { (p, view) -> view.isSelected = p == period }

        val today = LocalDate.now()
        val days = (period.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val perDay = days.map { store.dayCounts(it) }
        val totals = perDay.map { it.values.sum() }
        val total = totals.sum()
        val previousTotal = (period.days * 2 - 1 downTo period.days)
            .sumOf { store.dayCounts(today.minusDays(it.toLong())).values.sum() }

        totalView.text = total.toString()
        totalLabel.text = getString(
            when (period) {
                Period.DAY -> R.string.report_label_day
                Period.WEEK -> R.string.report_label_week
                Period.MONTH -> R.string.report_label_month
            }
        )
        comparison.text = comparisonText(total, previousTotal)

        if (period == Period.DAY) {
            chartCard.visibility = View.GONE
            stats.text = getString(R.string.report_stats_day, previousTotal, Mood.forCount(total).let { "${it.emoji} ${it.title}" })
        } else {
            chartCard.visibility = View.VISIBLE
            chart.setData(totals, days.mapIndexed { i, d -> chartLabel(d, i) })
            val busiest = days.indices.maxByOrNull { totals[it] }!!
            val calmest = days.indices.minByOrNull { totals[it] }!!
            stats.text = getString(
                R.string.report_stats_range,
                (total.toFloat() / period.days).roundToInt(),
                dayName(days[busiest]), totals[busiest],
                dayName(days[calmest]), totals[calmest],
            )
        }

        val perApp = ReelApp.entries.associateWith { app -> perDay.sumOf { it[app] ?: 0 } }
        renderBreakdown(perApp, total)
    }

    private fun comparisonText(total: Int, previous: Int): String {
        val unit = getString(
            when (period) {
                Period.DAY -> R.string.report_prev_day
                Period.WEEK -> R.string.report_prev_week
                Period.MONTH -> R.string.report_prev_month
            }
        )
        if (previous == 0) return getString(R.string.report_no_previous, unit)
        val percent = ((total - previous) * 100f / previous).roundToInt()
        return when {
            percent > 0 -> getString(R.string.report_more, percent, unit)
            percent < 0 -> getString(R.string.report_less, abs(percent), unit)
            else -> getString(R.string.report_same, unit)
        }
    }

    private fun chartLabel(date: LocalDate, index: Int): String = when (period) {
        Period.WEEK -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        // Label every 5th bar so 30 labels don't overlap; always label today.
        else -> if (index % 5 == 4 || index == period.days - 1) date.dayOfMonth.toString() else ""
    }

    private fun dayName(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}"

    private fun renderBreakdown(perApp: Map<ReelApp, Int>, total: Int) {
        breakdown.removeAllViews()
        val density = resources.displayMetrics.density
        perApp.forEach { (app, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, 0)
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(app.color)
                }
            }
            row.addView(dot, LinearLayout.LayoutParams((10 * density).toInt(), (10 * density).toInt()))
            row.addView(TextView(this).apply {
                text = app.label
                setTextColor(getColor(R.color.text))
                textSize = 14f
                setPadding((8 * density).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = count.toString()
                setTextColor(getColor(R.color.text))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            breakdown.addView(row)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = maxOf(total, 1)
                progress = count
                progressTintList = android.content.res.ColorStateList.valueOf(app.color)
            }
            breakdown.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (8 * density).toInt()))
        }
    }
}
