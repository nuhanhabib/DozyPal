package io.github.nuhanhabib.dozypal

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** The two apps DozyPal watches. Nothing outside this list is ever counted. */
enum class ReelApp(val packageName: String) {
    FACEBOOK("com.facebook.katana"),
    INSTAGRAM("com.instagram.android");

    companion object {
        fun fromPackage(pkg: CharSequence?): ReelApp? =
            entries.firstOrNull { it.packageName == pkg?.toString() }
    }
}

/**
 * Today's reel counts, the user's profile and settings, kept in private app storage on the phone.
 * Counts reset automatically when the date changes.
 */
class CountStore(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun count(app: ReelApp): Int {
        rollOverIfNewDay()
        return prefs.getInt(countKey(app), 0)
    }

    fun total(): Int = ReelApp.entries.sumOf { count(it) }

    fun increment(app: ReelApp): Int {
        val next = count(app) + 1
        prefs.edit().putInt(countKey(app), next).apply()
        return next
    }

    var counterEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var overlayOnLeft: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_LEFT, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_LEFT, value).apply()

    /** Counter opacity in percent, [MIN_OPACITY]..100. */
    var overlayOpacity: Int
        get() = prefs.getInt(KEY_OVERLAY_OPACITY, DEFAULT_OPACITY)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_OPACITY, value.coerceIn(MIN_OPACITY, 100)).apply()

    /** Set on the welcome screen; null until the user has filled it in. */
    var userName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /** [GENDER_MALE] or [GENDER_FEMALE]; picks the avatar. */
    var gender: String?
        get() = prefs.getString(KEY_GENDER, null)
        set(value) = prefs.edit().putString(KEY_GENDER, value).apply()

    val isFemale: Boolean get() = gender == GENDER_FEMALE

    /** Highest nudge milestone already shown today, so each one pops up only once. */
    var lastNudgeMilestone: Int
        get() {
            rollOverIfNewDay()
            return prefs.getInt(KEY_LAST_MILESTONE, 0)
        }
        set(value) = prefs.edit().putInt(KEY_LAST_MILESTONE, value).apply()

    var lastNightNudgeAt: Long
        get() = prefs.getLong(KEY_LAST_NIGHT_NUDGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NIGHT_NUDGE, value).apply()

    private fun rollOverIfNewDay() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) == today) return
        val editor = prefs.edit().putString(KEY_DAY, today).putInt(KEY_LAST_MILESTONE, 0)
        ReelApp.entries.forEach { editor.putInt(countKey(it), 0) }
        editor.apply()
    }

    companion object {
        // Stored values kept from v0.1 ("boy"/"girl") so existing installs keep their avatar.
        const val GENDER_MALE = "boy"
        const val GENDER_FEMALE = "girl"

        const val KEY_ENABLED = "counter_enabled"
        const val KEY_OVERLAY_LEFT = "overlay_left"
        const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        const val MIN_OPACITY = 30
        private const val DEFAULT_OPACITY = 60
        private const val PREFS_NAME = "dozypal"
        private const val KEY_DAY = "day"
        private const val KEY_NAME = "user_name"
        private const val KEY_GENDER = "character"
        private const val KEY_LAST_MILESTONE = "last_nudge_milestone"
        private const val KEY_LAST_NIGHT_NUDGE = "last_night_nudge_at"

        private const val MAX_NAME_LENGTH = 20
        // Letters (any language, including Bangla vowel signs), spaces, dots, apostrophes, hyphens.
        private val NAME_PATTERN = Regex("^[\\p{L}\\p{M} .'-]+$")

        private fun countKey(app: ReelApp) = "count_${app.name}"

        /** Returns a tidy name, or null if it is empty, too long, or has symbols/digits. */
        fun cleanName(raw: String): String? {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            return name.takeIf { it.length in 1..MAX_NAME_LENGTH && NAME_PATTERN.matches(it) }
        }
    }
}
