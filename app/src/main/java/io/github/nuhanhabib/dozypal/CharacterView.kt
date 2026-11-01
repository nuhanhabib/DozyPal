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
 * The DozyPal character: a boy or girl face that wears a different expression for each [Mood]
 * (like the mood's emoji), from a big grin to a green zombie. On top of that, dark circles grow and the
 * head tilts as today's reel count goes up, and open eyes blink now and then.
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
    private var mood = Mood.AWAKE
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
        mood = Mood.forCount(count)
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
        val zombie = mood == Mood.ZOMBIE

        canvas.save()
        canvas.rotate(droop * 10f + if (mood == Mood.WOOZY) 6f else 0f, cx, cy + r)

        if (isGirl) drawLongHair(canvas, cx, cy, r)

        paint.style = Paint.Style.FILL
        paint.color = if (zombie) SKIN_ZOMBIE else SKIN
        canvas.drawCircle(cx, cy, r, paint)
        drawCheeks(canvas, cx, cy, r)
        drawHairTop(canvas, cx, cy, r)

        val eyeY = cy - r * 0.02f
        val ew = r * 0.24f
        val eh = r * 0.17f
        drawEyes(canvas, cx - r * 0.38f, cx + r * 0.38f, eyeY, ew, eh)
        drawMouth(canvas, cx, cy + r * 0.45f, r)
        drawExtras(canvas, cx, cy, r, eyeY, ew, eh)

        canvas.restore()
        if (mood == Mood.SLEEPY || mood == Mood.DOZY) drawZzz(canvas, cx, cy, r)
        if (mood == Mood.DIZZY) drawStars(canvas, cx, cy, r)
    }

    private fun drawCheeks(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val alpha = when (mood) {
            Mood.AWAKE, Mood.COZY, Mood.CHILLED -> 110
            Mood.WOOZY -> 170 // flushed
            Mood.YAWNING -> 60
            else -> 0
        }
        if (alpha == 0) return
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 244, 114, 182)
        val size = if (mood == Mood.WOOZY) 0.18f else 0.13f
        canvas.drawCircle(cx - r * 0.58f, cy + r * 0.3f, r * size, paint)
        canvas.drawCircle(cx + r * 0.58f, cy + r * 0.3f, r * size, paint)
    }

    private fun drawLongHair(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (mood == Mood.ZOMBIE) HAIR_ZOMBIE else HAIR_GIRL
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
        paint.style = Paint.Style.FILL
        paint.color = when {
            mood == Mood.ZOMBIE -> HAIR_ZOMBIE
            isGirl -> HAIR_GIRL
            else -> HAIR_BOY
        }
        canvas.drawPath(path, paint)

        if (isGirl) {
            // A little bow.
            val bx = cx + r * 0.62f
            val by = cy - r * 0.82f
            val s = r * 0.18f
            paint.color = if (mood == Mood.ZOMBIE) BOW_ZOMBIE else BOW
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

    private fun drawEyes(canvas: Canvas, leftX: Float, rightX: Float, ey: Float, ew: Float, eh: Float) {
        for (ex in listOf(leftX, rightX)) drawDarkCircle(canvas, ex, ey, ew, eh)
        when (mood) {
            // 😃 big bright eyes with a sparkle.
            Mood.AWAKE -> for (ex in listOf(leftX, rightX)) {
                drawOpenEye(canvas, ex, ey, ew, eh * 1.15f, lid = blink, pupil = 0.7f, sparkle = true)
            }
            // 🙂 relaxed open eyes.
            Mood.COZY -> for (ex in listOf(leftX, rightX)) {
                drawOpenEye(canvas, ex, ey, ew, eh, lid = max(0.15f, blink), pupil = 0.62f, sparkle = true)
            }
            // 😌 peacefully closed.
            Mood.CHILLED -> for (ex in listOf(leftX, rightX)) drawClosedEye(canvas, ex, ey, ew, eh, lashes = false)
            // 🥱 heavy, half-shut.
            Mood.YAWNING -> for (ex in listOf(leftX, rightX)) {
                drawOpenEye(canvas, ex, ey, ew, eh, lid = max(0.6f, blink), pupil = 0.62f)
            }
            // 😪 and 😴: shut, with lashes.
            Mood.SLEEPY, Mood.DOZY -> for (ex in listOf(leftX, rightX)) {
                drawClosedEye(canvas, ex, ey + eh * 0.2f, ew, eh, lashes = true)
            }
            // 🥴 one eye open, one drooping.
            Mood.WOOZY -> {
                drawOpenEye(canvas, leftX, ey, ew, eh * 1.1f, lid = max(0.2f, blink), pupil = 0.55f)
                drawOpenEye(canvas, rightX, ey, ew, eh, lid = max(0.72f, blink), pupil = 0.62f)
            }
            // 😵 X eyes.
            Mood.DIZZY -> for (ex in listOf(leftX, rightX)) drawCrossEye(canvas, ex, ey, ew, eh)
            // 🧟 blank, bloodshot stare.
            Mood.ZOMBIE -> for (ex in listOf(leftX, rightX)) {
                drawOpenEye(canvas, ex, ey, ew, eh, lid = 0.45f, pupil = 0.22f, white = ZOMBIE_EYE, veins = true)
            }
        }
    }

    private fun drawDarkCircle(canvas: Canvas, ex: Float, ey: Float, ew: Float, eh: Float) {
        // Dark circles grow with tiredness.
        val alpha = (droop * if (mood == Mood.ZOMBIE) 170 else 100).toInt()
        if (alpha <= 0) return
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 110, 70, 140)
        canvas.drawOval(ex - ew * 1.05f, ey, ex + ew * 1.05f, ey + eh * 1.9f, paint)
    }

    private fun drawOpenEye(
        canvas: Canvas,
        ex: Float,
        ey: Float,
        ew: Float,
        eh: Float,
        lid: Float,
        pupil: Float,
        sparkle: Boolean = false,
        white: Int = Color.WHITE,
        veins: Boolean = false,
    ) {
        oval.set(ex - ew, ey - eh, ex + ew, ey + eh)
        paint.style = Paint.Style.FILL
        paint.color = white
        canvas.drawOval(oval, paint)

        if (veins) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = eh * 0.07f
            paint.color = VEIN
            canvas.drawLine(ex - ew * 0.9f, ey + eh * 0.1f, ex - ew * 0.45f, ey + eh * 0.3f, paint)
            canvas.drawLine(ex + ew * 0.9f, ey + eh * 0.15f, ex + ew * 0.5f, ey + eh * 0.35f, paint)
            paint.style = Paint.Style.FILL
        }

        // Pupils sink a little as the pal gets sleepy.
        val pupilY = ey + eh * (0.15f + droop * 0.25f)
        paint.color = PUPIL
        canvas.drawCircle(ex, pupilY, eh * pupil, paint)
        if (sparkle) {
            paint.color = Color.WHITE
            canvas.drawCircle(ex + eh * pupil * 0.35f, pupilY - eh * pupil * 0.4f, eh * pupil * 0.3f, paint)
        }

        if (lid > 0.01f) {
            val lidBottom = ey - eh + 2 * eh * lid
            canvas.save()
            path.reset()
            path.addOval(oval, Path.Direction.CW)
            canvas.clipPath(path)
            paint.color = if (mood == Mood.ZOMBIE) LID_ZOMBIE else LID
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

    /** A closed eye: a downward curve, like 😌 or 😴. */
    private fun drawClosedEye(canvas: Canvas, ex: Float, ey: Float, ew: Float, eh: Float, lashes: Boolean) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = eh * 0.24f
        paint.color = PUPIL
        oval.set(ex - ew * 0.85f, ey - eh * 0.9f, ex + ew * 0.85f, ey + eh * 0.6f)
        canvas.drawArc(oval, 20f, 140f, false, paint)
        if (lashes) {
            paint.strokeWidth = eh * 0.14f
            val baseY = ey + eh * 0.55f
            for (dx in listOf(-0.5f, 0f, 0.5f)) {
                canvas.drawLine(ex + ew * dx, baseY, ex + ew * dx * 1.3f, baseY + eh * 0.4f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawCrossEye(canvas: Canvas, ex: Float, ey: Float, ew: Float, eh: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = eh * 0.26f
        paint.color = PUPIL
        val s = eh * 0.85f
        canvas.drawLine(ex - s, ey - s, ex + s, ey + s, paint)
        canvas.drawLine(ex - s, ey + s, ex + s, ey - s, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMouth(canvas: Canvas, cx: Float, my: Float, r: Float) {
        paint.color = MOUTH
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.06f
        when (mood) {
            Mood.AWAKE -> {
                // 😃 big open grin with teeth.
                oval.set(cx - r * 0.3f, my - r * 0.25f, cx + r * 0.3f, my + r * 0.25f)
                paint.style = Paint.Style.FILL
                canvas.drawArc(oval, 0f, 180f, true, paint)
                paint.color = Color.WHITE
                canvas.drawRect(cx - r * 0.25f, my, cx + r * 0.25f, my + r * 0.06f, paint)
                paint.color = TONGUE
                canvas.drawCircle(cx, my + r * 0.19f, r * 0.08f, paint)
            }
            Mood.COZY -> smile(canvas, cx, my, r, width = 0.22f)
            Mood.CHILLED -> smile(canvas, cx, my, r, width = 0.16f)
            Mood.YAWNING -> {
                // 🥱 wide yawn.
                paint.style = Paint.Style.FILL
                canvas.drawOval(cx - r * 0.17f, my - r * 0.18f, cx + r * 0.17f, my + r * 0.2f, paint)
                paint.color = TONGUE
                canvas.drawOval(cx - r * 0.1f, my + r * 0.05f, cx + r * 0.1f, my + r * 0.18f, paint)
            }
            Mood.SLEEPY -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - r * 0.12f, my, cx + r * 0.12f, my + r * 0.02f, paint)
            }
            Mood.DOZY, Mood.DIZZY -> {
                // 😴 / 😵 small round "o".
                paint.style = Paint.Style.FILL
                canvas.drawOval(cx - r * 0.08f, my - r * 0.08f, cx + r * 0.08f, my + r * 0.1f, paint)
            }
            Mood.WOOZY -> {
                // 🥴 wobbly line.
                paint.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(cx - r * 0.24f, my)
                path.quadTo(cx - r * 0.12f, my - r * 0.1f, cx, my)
                path.quadTo(cx + r * 0.12f, my + r * 0.1f, cx + r * 0.24f, my - r * 0.02f)
                canvas.drawPath(path, paint)
            }
            Mood.ZOMBIE -> {
                // 🧟 crooked open mouth with a couple of teeth.
                paint.style = Paint.Style.FILL
                paint.color = ZOMBIE_MOUTH
                path.reset()
                path.moveTo(cx - r * 0.26f, my - r * 0.02f)
                path.lineTo(cx + r * 0.24f, my - r * 0.08f)
                path.lineTo(cx + r * 0.2f, my + r * 0.14f)
                path.lineTo(cx - r * 0.22f, my + r * 0.16f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = ZOMBIE_TOOTH
                canvas.drawRect(cx - r * 0.14f, my - r * 0.03f, cx - r * 0.06f, my + r * 0.05f, paint)
                canvas.drawRect(cx + r * 0.04f, my - r * 0.05f, cx + r * 0.12f, my + r * 0.04f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun smile(canvas: Canvas, cx: Float, my: Float, r: Float, width: Float) {
        paint.style = Paint.Style.STROKE
        oval.set(cx - r * width, my - r * 0.18f, cx + r * width, my + r * 0.08f)
        canvas.drawArc(oval, 20f, 140f, false, paint)
    }

    private fun drawExtras(canvas: Canvas, cx: Float, cy: Float, r: Float, eyeY: Float, ew: Float, eh: Float) {
        paint.style = Paint.Style.FILL
        when (mood) {
            // 🥱 a little tear from yawning.
            Mood.YAWNING -> drawTear(canvas, cx + r * 0.38f + ew * 0.9f, eyeY + eh * 1.2f, r * 0.06f)
            // 😪 the sleepy snot bubble.
            Mood.SLEEPY -> {
                paint.color = BUBBLE_FILL
                canvas.drawCircle(cx + r * 0.16f, cy + r * 0.3f, r * 0.13f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.025f
                paint.color = BUBBLE_LINE
                canvas.drawCircle(cx + r * 0.16f, cy + r * 0.3f, r * 0.13f, paint)
            }
            // 🧟 a stitched scar.
            Mood.ZOMBIE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = r * 0.03f
                paint.color = SCAR
                val sx = cx - r * 0.62f
                val sy = cy - r * 0.45f
                canvas.drawLine(sx, sy, sx + r * 0.3f, sy + r * 0.16f, paint)
                for (i in 0..2) {
                    val px = sx + r * 0.08f + i * r * 0.08f
                    val py = sy + r * 0.04f + i * r * 0.045f
                    canvas.drawLine(px + r * 0.03f, py - r * 0.06f, px - r * 0.03f, py + r * 0.06f, paint)
                }
            }
            else -> Unit
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawTear(canvas: Canvas, x: Float, y: Float, s: Float) {
        paint.style = Paint.Style.FILL
        paint.color = TEAR
        path.reset()
        path.moveTo(x, y - s * 1.6f)
        path.quadTo(x + s, y, x, y + s)
        path.quadTo(x - s, y, x, y - s * 1.6f)
        canvas.drawPath(path, paint)
    }

    private fun drawZzz(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.argb(if (mood == Mood.DOZY) 255 else 150, 124, 58, 237)
        paint.textSize = r * 0.24f
        canvas.drawText("z", cx + r * 0.95f, cy - r * 0.7f, paint)
        paint.textSize = r * 0.34f
        canvas.drawText("Z", cx + r * 1.12f, cy - r * 1.0f, paint)
    }

    /** 😵 little stars circling the head. */
    private fun drawStars(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = STAR
        for ((dx, dy, s) in listOf(Triple(-0.85f, -1.0f, 0.1f), Triple(0.0f, -1.22f, 0.08f), Triple(0.9f, -0.95f, 0.11f))) {
            drawStar(canvas, cx + r * dx, cy + r * dy, r * s)
        }
    }

    private fun drawStar(canvas: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        for (i in 0 until 10) {
            val angle = Math.PI / 5 * i - Math.PI / 2
            val radius = if (i % 2 == 0) s else s * 0.45f
            val px = x + (radius * Math.cos(angle)).toFloat()
            val py = y + (radius * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private companion object {
        val SKIN = Color.rgb(246, 210, 181)
        val LID = Color.rgb(232, 188, 158)
        val SKIN_ZOMBIE = Color.rgb(163, 196, 140)
        val LID_ZOMBIE = Color.rgb(136, 168, 116)
        val HAIR_BOY = Color.rgb(59, 42, 32)
        val HAIR_GIRL = Color.rgb(90, 56, 37)
        val HAIR_ZOMBIE = Color.rgb(64, 70, 52)
        val BOW = Color.rgb(244, 114, 182)
        val BOW_ZOMBIE = Color.rgb(140, 110, 120)
        val PUPIL = Color.rgb(46, 16, 101)
        val OUTLINE = Color.rgb(120, 90, 80)
        val MOUTH = Color.rgb(150, 70, 70)
        val TONGUE = Color.rgb(236, 120, 130)
        val TEAR = Color.rgb(96, 165, 250)
        val BUBBLE_FILL = Color.argb(110, 147, 197, 253)
        val BUBBLE_LINE = Color.argb(200, 96, 165, 250)
        val STAR = Color.rgb(250, 204, 21)
        val ZOMBIE_EYE = Color.rgb(236, 236, 210)
        val VEIN = Color.rgb(220, 80, 80)
        val ZOMBIE_MOUTH = Color.rgb(70, 40, 45)
        val ZOMBIE_TOOTH = Color.rgb(240, 236, 200)
        val SCAR = Color.rgb(90, 60, 60)
    }
}
