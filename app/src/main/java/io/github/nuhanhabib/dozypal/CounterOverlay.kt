package io.github.nuhanhabib.dozypal

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * The small counter pill shown in the top corner over Facebook/Instagram, plus a short-lived
 * nudge bubble under it. Both use accessibility overlay windows, so no "draw over other apps"
 * permission is needed, and both ignore touches so they never block the reel underneath.
 */
class CounterOverlay(context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private var counterAttached = false
    private var bubbleAttached = false
    private var onLeft = false

    private val label = TextView(context).apply {
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0xFF7C3AED.toInt())
        }
    }

    private val bubble = TextView(context).apply {
        textSize = 14f
        setTextColor(0xFF2E1065.toInt())
        maxWidth = (screenWidth * 0.7f).toInt()
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xF2FFFFFF.toInt())
            setStroke(dp(2), 0xFF7C3AED.toInt())
        }
    }

    private val hideBubble = Runnable { removeBubble() }

    fun show(count: Int, left: Boolean, opacityPercent: Int) {
        update(count)
        label.alpha = opacityPercent / 100f
        if (counterAttached && left == onLeft) return
        if (counterAttached) windowManager.removeView(label)
        removeBubble()
        onLeft = left
        windowManager.addView(label, layoutParams(left, y = dp(72)))
        counterAttached = true
    }

    fun update(count: Int) {
        label.text = "${Mood.forCount(count).emoji} $count"
    }

    fun showBubble(text: String) {
        if (!counterAttached) return
        bubble.text = text
        bubble.removeCallbacks(hideBubble)
        if (!bubbleAttached) {
            windowManager.addView(bubble, layoutParams(onLeft, y = dp(116)))
            bubbleAttached = true
        }
        bubble.postDelayed(hideBubble, BUBBLE_MS)
    }

    fun hide() {
        removeBubble()
        if (!counterAttached) return
        windowManager.removeView(label)
        counterAttached = false
    }

    private fun removeBubble() {
        bubble.removeCallbacks(hideBubble)
        if (!bubbleAttached) return
        windowManager.removeView(bubble)
        bubbleAttached = false
    }

    private fun layoutParams(left: Boolean, y: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or if (left) Gravity.START else Gravity.END
        x = dp(12)
        this.y = y
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val BUBBLE_MS = 4_500L
    }
}
