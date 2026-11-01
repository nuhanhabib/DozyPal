package io.github.nuhanhabib.dozypal

/** How sleepy your DozyPal looks for a given number of reels today. Just for fun, not a health measure. */
enum class Mood(val minReels: Int, val emoji: String, val title: String, val line: String) {
    AWAKE(0, "😃", "Wide awake", "Fresh eyes, clear mind."),
    COZY(20, "🙂", "Getting cozy", "Still doing fine."),
    CHILLED(50, "😌", "Chilled out", "Nice and relaxed... for now."),
    YAWNING(85, "🥱", "Yawning", "That's a big yawn. Eyelids getting heavy..."),
    SLEEPY(120, "😪", "Sleepy", "Your eyes are begging for a break."),
    DOZY(150, "😴", "Super dozy", "Maybe time for a break?"),
    WOOZY(200, "🥴", "Woozy", "Everything's a bit blurry now."),
    DIZZY(300, "😵", "Scroll dizzy", "The feed is spinning. Look away for a bit!"),
    ZOMBIE(450, "🧟", "Zombie mode", "Eyes barely open. Put the phone down!");

    companion object {
        /** Reels at which the eyes are as closed as they get. */
        private const val FULL_DROOP_AT = 450f

        fun forCount(count: Int): Mood = entries.last { count >= it.minReels }

        /** 0 = wide awake, 1 = eyes almost shut. */
        fun droopFor(count: Int): Float = (count / FULL_DROOP_AT).coerceIn(0f, 1f)
    }
}
