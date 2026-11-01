package io.github.nuhanhabib.dozypal

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The daily reel limit the user sets for themself. Reminders, at most one per step:
 *  - a quiet heads-up at [WARN_AT] of the limit,
 *  - a red reminder with a short sound and buzz when the limit is reached,
 *  - and again every [REMIND_EVERY] reels after that.
 */
object DailyLimit {

    const val MIN = 10
    const val MAX = 1000
    const val STEP = 10
    const val DEFAULT = 200
    val PRESETS = intArrayOf(50, 100, 200, 300)

    private const val WARN_AT = 0.8f
    private const val REMIND_EVERY = 25

    enum class Level { WARNING, REACHED, OVER }

    /** One reminder step: [at] is the reel count it belongs to, so steps only ever go up during a day. */
    data class Step(val at: Int, val level: Level)

    /** The highest reminder step [total] has reached for [limit], or null if not even close yet. */
    fun stepFor(total: Int, limit: Int): Step? {
        if (total >= limit) {
            val over = (total - limit) / REMIND_EVERY * REMIND_EVERY
            return Step(limit + over, if (over == 0) Level.REACHED else Level.OVER)
        }
        val warnAt = (limit * WARN_AT).roundToInt()
        return if (total >= warnAt) Step(warnAt, Level.WARNING) else null
    }

    private val WARNING = listOf(
        "Heads up {name}: {count} of {limit} reels. Only {left} left today 👀",
        "{name}, you're close to your limit: {left} reels to go ⏳",
        "Almost there, {name}! {count}/{limit} reels today. Slow down? 🐢",
    )

    private val REACHED = listOf(
        "⏰ {name}, that's your {limit} reels for today! Time to stop 🛑",
        "🛑 Limit reached, {name}! {limit} reels done. Phone down?",
        "⏰ You hit your daily limit of {limit}, {name}. You set it for a reason 💪",
    )

    private val OVER = listOf(
        "⏰ {name}, you're {over} reels over your limit of {limit} 😬",
        "🛑 {count} reels, {name}. That's {over} more than you wanted today",
        "⏰ Still scrolling, {name}? {over} over your limit. Let's stop here 🙏",
    )

    fun message(step: Step, name: String, total: Int, limit: Int): String {
        val pool = when (step.level) {
            Level.WARNING -> WARNING
            Level.REACHED -> REACHED
            Level.OVER -> OVER
        }
        return pool.random(Random)
            .replace("{name}", name)
            .replace("{count}", total.toString())
            .replace("{limit}", limit.toString())
            .replace("{left}", (limit - total).coerceAtLeast(0).toString())
            .replace("{over}", (total - limit).coerceAtLeast(0).toString())
    }
}
