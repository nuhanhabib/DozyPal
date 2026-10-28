package io.github.nuhanhabib.dozypal

import kotlin.random.Random

/** Friendly personal messages shown in a bubble next to the counter. */
object Nudges {

    const val DEFAULT_NAME = "friend"

    private val MILESTONES = intArrayOf(20, 40, 60, 80, 100, 150, 200)
    private const val REPEAT_EVERY_AFTER_200 = 50

    private val GENERAL = listOf(
        "Hey {name}, your eyes are getting heavy 😪",
        "{name}, that's {count} reels already! Water break? 💧",
        "Psst {name}... your DozyPal is yawning 🥱",
        "{name}, the reels will still be here tomorrow 😌",
        "Hey {name}, put the phone down for a bit 😴",
        "{name}, {count} reels! Your thumb needs a holiday 👍",
        "Stretch time, {name}! Stand up for a minute 🙆",
        "{name}, how about a short walk instead? 🚶",
        "Hey {name}, blink a few times. Your eyes will thank you 👀",
        "{name}, you unlocked: Professional Scroller 🏆 Maybe stop?",
        "{name}, real life called. It misses you 📞",
        "Your DozyPal is falling asleep, {name} 💤",
        "{name}, look at something far away for 20 seconds 🌳",
        "Hey {name}, text a friend instead of scrolling? 💬",
    )

    private val HEAVY = listOf(
        "{name}... zombie mode activated 🧟 Time to stop!",
        "{count} reels, {name}! Your brain wants a nap 😵",
        "Hey {name}, that's a LOT of reels. Phone down? 🙏",
        "{name}, even your DozyPal gave up 😴💤",
        "{count} reels today, {name}. Let's call it a day 🌅",
        "{name}, your thumb has walked a marathon 🏃 Rest it!",
    )

    private val NIGHT = listOf(
        "{name}, it's {time}. You should sleep now 🌙",
        "Hey {name}, the moon is up and so are you 🌚 Bed time!",
        "{name}, tomorrow-you wants some sleep 😴",
        "Late night scrolling, {name}? Your pillow is waiting 🛏️",
        "It's {time}, {name}. Reels can wait, sleep can't 🌙",
        "{name}, close your eyes. DozyPal already did 💤",
    )

    /** The highest milestone reached by [total] reels: 20, 40, ... 200, then every 50. */
    fun reachedMilestone(total: Int): Int = when {
        total >= 200 -> total / REPEAT_EVERY_AFTER_200 * REPEAT_EVERY_AFTER_200
        else -> MILESTONES.lastOrNull { total >= it } ?: 0
    }

    fun isLateNight(hour: Int): Boolean = hour in 0..4

    fun forMilestone(name: String, total: Int): String {
        val pool = if (total >= 100) HEAVY + GENERAL else GENERAL
        return fill(pool.random(Random), name, total, "")
    }

    fun forNight(name: String, time: String): String = fill(NIGHT.random(Random), name, 0, time)

    private fun fill(template: String, name: String, count: Int, time: String) =
        template.replace("{name}", name).replace("{count}", count.toString()).replace("{time}", time)
}
