package io.github.nuhanhabib.dozypal

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * The small counter pill shown in the top corner over Facebook/Instagram.
 * It uses an accessibility overlay window, so no "draw over other apps" permission is needed,
 * and it ignores touches so it never blocks the reel underneath.
 */
class CounterOverlay(context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var attached = false
    private var onLeft = false

    private val label = TextView(context).apply {
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0xCC7C3AED.toInt())
        }
    }

    fun show(count: Int, left: Boolean) {
        update(count)
        if (attached && left == onLeft) return
        if (attached) windowManager.removeView(label)
        onLeft = left
        windowManager.addView(label, layoutParams(left))
        attached = true
    }

    fun update(count: Int) {
        label.text = "${Mood.forCount(count).emoji} $count"
    }

    fun hide() {
        if (!attached) return
        windowManager.removeView(label)
        attached = false
    }

    private fun layoutParams(left: Boolean) = WindowManager.LayoutParams(
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
        y = dp(72)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
