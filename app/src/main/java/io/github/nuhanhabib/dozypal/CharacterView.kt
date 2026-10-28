package io.github.nuhanhabib.dozypal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The DozyPal character: a boy or girl face whose eyelids droop, eyes get dark circles,
 * head tilts and mouth turns into a yawn as today's reel count goes up. It also blinks now and then.
 */
class CharacterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var isGirl = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private var droop = 0f
    private var blink = 0f
    private var droopAnimator: ValueAnimator? = null

    private val blinkLoop = object : Runnable {
        override fun run() {
            ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = 220
                addUpdateListener {
                    blink = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            postDelayed(this, Random.nextLong(2_500, 5_500))
        }
    }

    fun setReelCount(count: Int, animate: Boolean) {
        val target = Mood.droopFor(count)
        droopAnimator?.cancel()
        if (!animate) {
            droop = target
            invalidate()
            return
        }
        droopAnimator = ValueAnimator.ofFloat(droop, target).apply {
            duration = 700
            addUpdateListener {
                droop = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(blinkLoop, 2_000)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkLoop)
        droopAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.36f

        canvas.save()
        canvas.rotate(droop * 10f, cx, cy + r)

        if (isGirl) drawLongHair(canvas, cx, cy, r)

        paint.style = Paint.Style.FILL
        paint.color = SKIN
        canvas.drawCircle(cx, cy, r, paint)

        // Rosy cheeks fade away as the pal gets tired.
        paint.color = Color.argb(((1f - droop) * 110).toInt(), 244, 114, 182)
        canvas.drawCircle(cx - r * 0.58f, cy + r * 0.3f, r * 0.13f, paint)
        canvas.drawCircle(cx + r * 0.58f, cy + r * 0.3f, r * 0.13f, paint)

        drawHairTop(canvas, cx, cy, r)

        val eyeY = cy - r * 0.02f
        drawEye(canvas, cx - r * 0.38f, eyeY, r * 0.24f, r * 0.17f)
        drawEye(canvas, cx + r * 0.38f, eyeY, r * 0.24f, r * 0.17f)
        drawMouth(canvas, cx, cy + r * 0.45f, r)

        canvas.restore()
        if (droop > 0.5f) drawZzz(canvas, cx, cy, r)
    }

    private fun drawLongHair(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.color = HAIR_GIRL
        canvas.drawRoundRect(cx - r * 1.14f, cy - r * 0.9f, cx + r * 1.14f, cy + r * 1.1f, r * 0.5f, r * 0.5f, paint)
    }

    private fun drawHairTop(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        oval.set(cx - r * 1.03f, cy - r * 1.1f, cx + r * 1.03f, cy + r * 0.9f)
        path.reset()
        path.moveTo(cx - r * 1.03f, cy - r * 0.1f)
        path.arcTo(oval, 180f, 180f)
        path.lineTo(cx + r * 0.9f, cy - r * 0.35f)
        path.quadTo(cx + r * 0.45f, cy - r * 0.6f, cx + r * 0.1f, cy - r * 0.42f)
        path.quadTo(cx - r * 0.3f, cy - r * 0.66f, cx - r * 0.9f, cy - r * 0.35f)
        path.close()
        paint.color = if (isGirl) HAIR_GIRL else HAIR_BOY
        canvas.drawPath(path, paint)

        if (isGirl) {
            // A little bow.
            val bx = cx + r * 0.62f
            val by = cy - r * 0.82f
            val s = r * 0.18f
            paint.color = BOW
            path.reset()
            path.moveTo(bx, by)
            path.lineTo(bx - s * 1.4f, by - s)
            path.lineTo(bx - s * 1.4f, by + s)
            path.close()
            path.moveTo(bx, by)
            path.lineTo(bx + s * 1.4f, by - s)
            path.lineTo(bx + s * 1.4f, by + s)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawCircle(bx, by, s * 0.45f, paint)
        }
    }

    private fun drawEye(canvas: Canvas, ex: Float, ey: Float, ew: Float, eh: Float) {
        // Dark circles grow with tiredness.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((droop * 100).toInt(), 110, 70, 140)
        canvas.drawOval(ex - ew * 1.05f, ey, ex + ew * 1.05f, ey + eh * 1.9f, paint)

        oval.set(ex - ew, ey - eh, ex + ew, ey + eh)
        paint.color = Color.WHITE
        canvas.drawOval(oval, paint)

        // Pupils sink a little as the pal gets sleepy.
        paint.color = PUPIL
        canvas.drawCircle(ex, ey + eh * (0.15f + droop * 0.25f), eh * 0.62f, paint)

        val closed = max(droop * 0.85f, blink)
        if (closed > 0.01f) {
            val lidBottom = ey - eh + 2 * eh * closed
            canvas.save()
            path.reset()
            path.addOval(oval, Path.Direction.CW)
            canvas.clipPath(path)
            paint.color = LID
            canvas.drawRect(ex - ew - 1, ey - eh - 1, ex + ew + 1, lidBottom, paint)
            canvas.restore()

            val t = ((lidBottom - ey) / eh).coerceIn(-1f, 1f)
            val halfWidth = ew * sqrt(1 - t * t)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = eh * 0.2f
            paint.color = PUPIL
            canvas.drawLine(ex - halfWidth, lidBottom, ex + halfWidth, lidBottom, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = eh * 0.12f
        paint.color = OUTLINE
        canvas.drawOval(oval, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMouth(canvas: Canvas, cx: Float, my: Float, r: Float) {
        paint.color = MOUTH
        when {
            droop < 0.3f -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = r * 0.06f
                oval.set(cx - r * 0.22f, my - r * 0.18f, cx + r * 0.22f, my + r * 0.08f)
                canvas.drawArc(oval, 20f, 140f, false, paint)
            }
            droop < 0.65f -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = r * 0.06f
                canvas.drawLine(cx - r * 0.14f, my, cx + r * 0.14f, my, paint)
            }
            else -> {
                // Yawn gets bigger the dozier it gets.
                val s = (droop - 0.65f) / 0.35f
                paint.style = Paint.Style.FILL
                canvas.drawOval(
                    cx - r * (0.08f + 0.06f * s), my - r * (0.06f + 0.1f * s),
                    cx + r * (0.08f + 0.06f * s), my + r * (0.08f + 0.1f * s),
                    paint,
                )
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawZzz(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.argb(((droop - 0.5f) * 2 * 255).toInt().coerceIn(0, 255), 124, 58, 237)
        paint.textSize = r * 0.24f
        canvas.drawText("z", cx + r * 0.95f, cy - r * 0.7f, paint)
        paint.textSize = r * 0.34f
        canvas.drawText("Z", cx + r * 1.12f, cy - r * 1.0f, paint)
    }

    private companion object {
        val SKIN = Color.rgb(246, 210, 181)
        val LID = Color.rgb(232, 188, 158)
        val HAIR_BOY = Color.rgb(59, 42, 32)
        val HAIR_GIRL = Color.rgb(90, 56, 37)
        val BOW = Color.rgb(244, 114, 182)
        val PUPIL = Color.rgb(46, 16, 101)
        val OUTLINE = Color.rgb(120, 90, 80)
        val MOUTH = Color.rgb(150, 70, 70)
    }
}
