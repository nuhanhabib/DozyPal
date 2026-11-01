package io.github.nuhanhabib.dozypal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * The counter: a small tab docked to the left or right screen edge (like Android's accessibility
 * button) that can be dragged up and down, plus a short-lived nudge bubble beside it.
 * Both use accessibility overlay windows, so no "draw over other apps" permission is needed.
 * Only the tab itself takes touches (for dragging); the bubble ignores them.
 * Once the daily limit is crossed the tab turns red, and limit reminders use a red bubble.
 */
class CounterOverlay(
    context: Context,
    private val onMoved: (y: Int) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    private var tabAttached = false
    private var bubbleAttached = false
    private var onLeft = false
    private var overLimit = false
    private var tabParams: WindowManager.LayoutParams? = null

    private val tab = TextView(context).apply {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        minWidth = dp(46)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private val bubble = TextView(context).apply {
        textSize = 14f
        maxWidth = (screenWidth * 0.65f).toInt()
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    private val nudgeBackground = bubbleBackground(fill = 0xF2FFFFFF.toInt(), stroke = 0xFF7C3AED.toInt())
    private val alertBackground = bubbleBackground(fill = 0xF7FEF2F2.toInt(), stroke = ALERT_COLOR)

    private val hideBubble = Runnable { removeBubble() }

    init {
        enableDragging()
    }

    fun show(count: Int, total: Int, overLimit: Boolean, left: Boolean, opacityPercent: Int, savedY: Int) {
        update(count, total, overLimit)
        tab.alpha = opacityPercent / 100f
        if (tabAttached && left == onLeft) return
        if (tabAttached) windowManager.removeView(tab)
        removeBubble()
        onLeft = left
        tab.background = tabBackground(left)
        val y = if (savedY >= 0) savedY else (screenHeight * 0.3f).toInt()
        tabParams = layoutParams(left, x = 0, y = y.coerceIn(0, screenHeight - dp(80)), touchable = true)
        windowManager.addView(tab, tabParams)
        tabAttached = true
    }

    /** Shows this app's [count]; the face matches today's [total] across all apps, like the home screen. */
    fun update(count: Int, total: Int, overLimit: Boolean) {
        tab.text = "${Mood.forCount(total).emoji}\n$count"
        if (overLimit != this.overLimit) {
            this.overLimit = overLimit
            (tab.background as? GradientDrawable)?.setColor(tabColor())
        }
    }

    /** [alert] is for daily limit reminders: a red bubble that stays a little longer, and the tab bounces. */
    fun showBubble(text: String, alert: Boolean = false) {
        val params = tabParams ?: return
        if (!tabAttached) return
        bubble.text = text
        bubble.background = if (alert) alertBackground else nudgeBackground
        bubble.setTextColor(if (alert) 0xFF7F1D1D.toInt() else 0xFF2E1065.toInt())
        bubble.removeCallbacks(hideBubble)
        val bubbleParams = layoutParams(onLeft, x = dp(56), y = params.y, touchable = false)
        if (bubbleAttached) windowManager.updateViewLayout(bubble, bubbleParams)
        else windowManager.addView(bubble, bubbleParams)
        bubbleAttached = true
        bubble.postDelayed(hideBubble, if (alert) ALERT_BUBBLE_MS else BUBBLE_MS)
        if (alert) bounceTab()
    }

    fun hide() {
        removeBubble()
        if (!tabAttached) return
        windowManager.removeView(tab)
        tabAttached = false
    }

    private fun removeBubble() {
        bubble.removeCallbacks(hideBubble)
        if (!bubbleAttached) return
        windowManager.removeView(bubble)
        bubbleAttached = false
    }

    private fun bounceTab() {
        tab.animate().cancel()
        tab.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).withEndAction {
            tab.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
        }.start()
    }

    /** Drag the tab up and down along the edge; the new position is remembered. */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableDragging() {
        var startRawY = 0f
        var startY = 0
        tab.setOnTouchListener { _, event ->
            val params = tabParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startY = params.y
                    removeBubble()
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = (startY + (event.rawY - startRawY).toInt()).coerceIn(0, screenHeight - tab.height)
                    windowManager.updateViewLayout(tab, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onMoved(params.y)
            }
            true
        }
    }

    private fun tabColor() = if (overLimit) ALERT_COLOR else TAB_COLOR

    /** Rounded only on the side facing the screen, flat against the edge. */
    private fun tabBackground(left: Boolean) = GradientDrawable().apply {
        val r = dp(24).toFloat()
        cornerRadii = if (left) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        setColor(tabColor())
    }

    private fun bubbleBackground(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    private fun layoutParams(left: Boolean, x: Int, y: Int, touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or if (left) Gravity.START else Gravity.END
        this.x = x
        this.y = y
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val BUBBLE_MS = 4_500L
        const val ALERT_BUBBLE_MS = 6_000L
        const val TAB_COLOR = 0xFF6D28D9.toInt()
        const val ALERT_COLOR = 0xFFDC2626.toInt()
    }
}
