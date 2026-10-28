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
 * Today's reel counts and the user's settings, kept in private app storage on the phone.
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

    /** [CHARACTER_BOY], [CHARACTER_GIRL], or null before the user has picked one. */
    var character: String?
        get() = prefs.getString(KEY_CHARACTER, null)
        set(value) = prefs.edit().putString(KEY_CHARACTER, value).apply()

    private fun rollOverIfNewDay() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) == today) return
        val editor = prefs.edit().putString(KEY_DAY, today)
        ReelApp.entries.forEach { editor.putInt(countKey(it), 0) }
        editor.apply()
    }

    companion object {
        const val CHARACTER_BOY = "boy"
        const val CHARACTER_GIRL = "girl"

        const val KEY_ENABLED = "counter_enabled"
        const val KEY_OVERLAY_LEFT = "overlay_left"
        private const val PREFS_NAME = "dozypal"
        private const val KEY_DAY = "day"
        private const val KEY_CHARACTER = "character"

        private fun countKey(app: ReelApp) = "count_${app.name}"
    }
}
