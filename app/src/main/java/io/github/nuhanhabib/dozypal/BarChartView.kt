package io.github.nuhanhabib.dozypal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** A simple bar chart for the reports screen. The last bar (today) is highlighted. */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * resources.displayMetrics.scaledDensity
        color = Color.rgb(107, 102, 128)
    }
    private var values: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    fun setData(values: List<Int>, labels: List<String>) {
        this.values = values
        this.labels = labels
        contentDescription = labels.zip(values).joinToString { (label, value) -> "$label $value" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val showValues = values.size <= 7
        val labelArea = 20 * density
        val valueArea = if (showValues) 18 * density else 4 * density
        val chartHeight = height - labelArea - valueArea
        val slot = width.toFloat() / values.size
        val barWidth = slot * if (showValues) 0.55f else 0.7f
        val maxValue = max(values.max(), 1)
        val radius = barWidth / 3

        values.forEachIndexed { i, value ->
            val barHeight = max(chartHeight * value / maxValue, 2 * density)
            val left = i * slot + (slot - barWidth) / 2
            val top = valueArea + chartHeight - barHeight
            barPaint.color = if (i == values.lastIndex) PURPLE else PURPLE_LIGHT
            canvas.drawRoundRect(left, top, left + barWidth, valueArea + chartHeight, radius, radius, barPaint)

            val cx = left + barWidth / 2
            if (showValues && value > 0) canvas.drawText(value.toString(), cx, top - 4 * density, textPaint)
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let {
                canvas.drawText(it, cx, height - 4 * density, textPaint)
            }
        }
    }

    private companion object {
        val PURPLE = Color.rgb(124, 58, 237)
        val PURPLE_LIGHT = Color.rgb(196, 181, 253)
    }
}
