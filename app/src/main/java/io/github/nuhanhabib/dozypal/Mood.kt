package io.github.nuhanhabib.dozypal

/** How sleepy your DozyPal looks for a given number of reels today. Just for fun, not a health measure. */
enum class Mood(val minReels: Int, val emoji: String, val title: String, val line: String) {
    AWAKE(0, "😀", "Wide awake", "Fresh eyes, clear mind."),
    COZY(15, "🙂", "Getting cozy", "Still doing fine."),
    SLEEPY(40, "😪", "Sleepy", "Those eyelids are getting heavy..."),
    DOZY(80, "😴", "Super dozy", "Maybe time for a break?"),
    ZOMBIE(150, "🥱", "Zombie mode", "Eyes barely open. Put the phone down!");

    companion object {
        /** Reels at which the eyes are as closed as they get. */
        private const val FULL_DROOP_AT = 150f

        fun forCount(count: Int): Mood = entries.last { count >= it.minReels }

        /** 0 = wide awake, 1 = eyes almost shut. */
        fun droopFor(count: Int): Float = (count / FULL_DROOP_AT).coerceIn(0f, 1f)
    }
}
