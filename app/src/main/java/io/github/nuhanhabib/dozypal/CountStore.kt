package io.github.nuhanhabib.dozypal

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** The apps DozyPal watches. Nothing outside this list is ever counted. */
enum class ReelApp(val label: String, val color: Int, private val packages: Set<String>) {
    FACEBOOK("Facebook", 0xFF3B82F6.toInt(), setOf("com.facebook.katana")),
    INSTAGRAM("Instagram", 0xFFEC4899.toInt(), setOf("com.instagram.android")),
    TIKTOK(
        "TikTok", 0xFF14B8A6.toInt(),
        setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
    ),
    YOUTUBE("YouTube Shorts", 0xFFEF4444.toInt(), setOf("com.google.android.youtube"));

    companion object {
        fun fromPackage(pkg: CharSequence?): ReelApp? {
            val name = pkg?.toString() ?: return null
            return entries.firstOrNull { name in it.packages }
        }
    }
}

/**
 * Today's reel counts, daily history for reports, the user's profile and settings,
 * kept in private app storage on the phone. Today's counts reset automatically when the date changes.
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
        val today = ReelApp.entries.associateWith { count(it) }.toMutableMap()
        val next = today.getValue(app) + 1
        today[app] = next
        prefs.edit()
            .putInt(countKey(app), next)
            .putString(historyKey(LocalDate.now()), encode(today))
            .apply()
        return next
    }

    /** Counts per app for one day (today included); empty if nothing was recorded. */
    fun dayCounts(date: LocalDate): Map<ReelApp, Int> =
        if (date == LocalDate.now()) ReelApp.entries.associateWith { count(it) }
        else decode(prefs.getString(historyKey(date), null))

    var counterEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var overlayOnLeft: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_LEFT, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_LEFT, value).apply()

    /** Distance of the counter tab from the top of the screen, in pixels; -1 means "not moved yet". */
    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, -1)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

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

    /** Scroll Battles: own friend code and public nickname; null until the user joins. */
    var friendCode: String?
        get() = prefs.getString(KEY_FRIEND_CODE, null)
        set(value) = prefs.edit().putString(KEY_FRIEND_CODE, value).apply()

    var nickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

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
        val today = LocalDate.now()
        val storedDay = prefs.getString(KEY_DAY, null)
        if (storedDay == today.toString()) return

        val editor = prefs.edit().putString(KEY_DAY, today.toString()).putInt(KEY_LAST_MILESTONE, 0)
        // Keep the finished day in history (installs from before v0.3 had no history yet).
        if (storedDay != null && !prefs.contains(HISTORY_PREFIX + storedDay)) {
            val finished = ReelApp.entries.associateWith { prefs.getInt(countKey(it), 0) }
            if (finished.values.any { it > 0 }) editor.putString(HISTORY_PREFIX + storedDay, encode(finished))
        }
        val oldest = today.minusDays(HISTORY_DAYS)
        prefs.all.keys
            .filter { it.startsWith(HISTORY_PREFIX) }
            .filter { key -> runCatching { LocalDate.parse(key.removePrefix(HISTORY_PREFIX)) < oldest }.getOrDefault(true) }
            .forEach(editor::remove)
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
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_FRIEND_CODE = "friend_code"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_LAST_MILESTONE = "last_nudge_milestone"
        private const val KEY_LAST_NIGHT_NUDGE = "last_night_nudge_at"
        private const val HISTORY_PREFIX = "h_"
        private const val HISTORY_DAYS = 400L

        private const val MAX_NAME_LENGTH = 20
        // Letters (any language, including Bangla vowel signs), spaces, dots, apostrophes, hyphens.
        private val NAME_PATTERN = Regex("^[\\p{L}\\p{M} .'-]+$")

        private fun countKey(app: ReelApp) = "count_${app.name}"
        private fun historyKey(date: LocalDate) = HISTORY_PREFIX + date

        private fun encode(counts: Map<ReelApp, Int>) =
            counts.entries.joinToString("|") { "${it.key.name}:${it.value}" }

        private fun decode(raw: String?): Map<ReelApp, Int> {
            raw ?: return emptyMap()
            return raw.split("|").mapNotNull { part ->
                val pieces = part.split(":")
                val app = ReelApp.entries.firstOrNull { it.name == pieces.getOrNull(0) }
                val value = pieces.getOrNull(1)?.toIntOrNull()
                if (app != null && value != null) app to value else null
            }.toMap()
        }

        /** Returns a tidy name, or null if it is empty, too long, or has symbols/digits. */
        fun cleanName(raw: String): String? {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            return name.takeIf { it.length in 1..MAX_NAME_LENGTH && NAME_PATTERN.matches(it) }
        }
    }
}
