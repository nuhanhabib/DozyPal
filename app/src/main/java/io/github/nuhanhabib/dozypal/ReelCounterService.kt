package io.github.nuhanhabib.dozypal

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Listens (only inside Facebook, Instagram, TikTok and YouTube, see res/xml/reel_service_config.xml)
 * for swipes to a new reel or short. A reel is counted once it has stayed on screen for [WATCH_MS], so fast scrolling
 * doesn't count. Shows the floating counter and nudge bubbles while one of those apps is open, and
 * daily limit reminders (with a short sound and buzz) when the user has set a limit.
 */
class ReelCounterService : AccessibilityService() {

    private lateinit var store: CountStore
    private lateinit var detector: ReelDetector
    private lateinit var overlay: CounterOverlay
    private lateinit var keyguard: KeyguardManager
    private lateinit var power: PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp: ReelApp? = null
    private var pendingApp: ReelApp? = null
    private var awayChecks = 0

    // Events only arrive from the watched apps, so we poll to notice when the user leaves them.
    // Unknown (null) counts as "left": other apps' windows are hidden from us by the package filter.
    private val foregroundCheck = object : Runnable {
        override fun run() {
            if (!isUserLooking()) {
                hideOverlay()
                return
            }
            val inReelApp = ReelApp.fromPackage(rootInActiveWindow?.packageName) != null
            awayChecks = if (inReelApp) 0 else awayChecks + 1
            if (awayChecks >= AWAY_CHECKS_TO_HIDE) hideOverlay() else handler.postDelayed(this, FOREGROUND_POLL_MS)
        }
    }

    private val countAfterWatching = Runnable {
        val app = pendingApp ?: return@Runnable
        pendingApp = null
        if (!store.counterEnabled || currentApp != app || !isUserLooking() || detector.isSponsoredNow()) {
            return@Runnable
        }
        val count = store.increment(app)
        overlay.update(count, store.total(), isOverLimit())
        if (!maybeShowLimitReminder()) maybeShowNudge()
        if (Social.isJoined(this, store)) Social.requestSync(store)
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = hideOverlay()
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CountStore.KEY_ENABLED -> if (!store.counterEnabled) hideOverlay()
            CountStore.KEY_OVERLAY_LEFT, CountStore.KEY_OVERLAY_OPACITY,
            CountStore.KEY_LIMIT_ENABLED, CountStore.KEY_LIMIT -> currentApp?.let { showOverlay(it) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = CountStore(this)
        overlay = CounterOverlay(this, onMoved = { y -> store.overlayY = y })
        keyguard = getSystemService(KeyguardManager::class.java)
        power = getSystemService(PowerManager::class.java)
        detector = ReelDetector(
            screenSize = { resources.displayMetrics.let { it.widthPixels to it.heightPixels } },
            rootProvider = { rootInActiveWindow },
        )
        store.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        val screenOff = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, screenOff, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, screenOff)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized || !store.counterEnabled || !isUserLooking()) return
        val app = ReelApp.fromPackage(event.packageName) ?: return

        showOverlay(app)
        when (detector.onScroll(event, app)) {
            ReelChange.FORWARD -> scheduleCount(app)
            ReelChange.BACKWARD -> cancelPendingCount()
            ReelChange.NONE -> Unit
        }
    }

    /** Count this reel only if the user is still on it after [WATCH_MS]. */
    private fun scheduleCount(app: ReelApp) {
        handler.removeCallbacks(countAfterWatching)
        pendingApp = app
        handler.postDelayed(countAfterWatching, WATCH_MS)
    }

    private fun cancelPendingCount() {
        handler.removeCallbacks(countAfterWatching)
        pendingApp = null
    }

    /** Shows the next daily limit reminder if one is due; returns true if it did. */
    private fun maybeShowLimitReminder(): Boolean {
        if (!store.limitEnabled) return false
        val total = store.total()
        val limit = store.dailyLimit
        val step = DailyLimit.stepFor(total, limit) ?: return false
        if (step.at <= store.lastLimitAlert) return false
        store.lastLimitAlert = step.at
        // Don't follow the reminder with a regular nudge for the same reels.
        store.lastNudgeMilestone = maxOf(store.lastNudgeMilestone, Nudges.reachedMilestone(total))

        val name = store.userName ?: Nudges.DEFAULT_NAME
        val crossed = step.level != DailyLimit.Level.WARNING
        overlay.showBubble(DailyLimit.message(step, name, total, limit), alert = crossed)
        if (crossed && store.limitSound) playLimitAlert()
        return true
    }

    private fun isOverLimit(): Boolean = store.limitEnabled && store.total() >= store.dailyLimit

    /** A short notification sound and double buzz, following the phone's sound mode (silent stays silent). */
    private fun playLimitAlert() {
        val audio = getSystemService(AudioManager::class.java) ?: return
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                playSound()
                vibrate()
            }
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> Unit
        }
    }

    private fun playSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(this, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120), -1)) }
    }

    private fun maybeShowNudge() {
        val total = store.total()
        val name = store.userName ?: Nudges.DEFAULT_NAME
        val milestone = Nudges.reachedMilestone(total)
        val now = LocalTime.now()
        val nowMs = System.currentTimeMillis()

        val text = when {
            milestone > store.lastNudgeMilestone -> {
                store.lastNudgeMilestone = milestone
                Nudges.forMilestone(name, total)
            }
            Nudges.isLateNight(now.hour) && nowMs - store.lastNightNudgeAt > NIGHT_NUDGE_GAP_MS -> {
                store.lastNightNudgeAt = nowMs
                Nudges.forNight(name, now.format(TIME_FORMAT))
            }
            else -> null
        }
        text?.let(overlay::showBubble)
    }

    private fun isUserLooking(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    private fun showOverlay(app: ReelApp) {
        if (!store.counterEnabled) return
        currentApp = app
        awayChecks = 0
        overlay.show(store.count(app), store.total(), isOverLimit(), store.overlayOnLeft, store.overlayOpacity, store.overlayY)
        handler.removeCallbacks(foregroundCheck)
        handler.postDelayed(foregroundCheck, FOREGROUND_POLL_MS)
    }

    private fun hideOverlay() {
        currentApp = null
        cancelPendingCount()
        handler.removeCallbacks(foregroundCheck)
        if (::overlay.isInitialized) overlay.hide()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        if (::store.isInitialized) {
            store.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
            unregisterReceiver(screenOffReceiver)
        }
        super.onDestroy()
    }

    private companion object {
        const val WATCH_MS = 3_000L
        const val FOREGROUND_POLL_MS = 500L
        const val AWAY_CHECKS_TO_HIDE = 2
        const val NIGHT_NUDGE_GAP_MS = 30 * 60 * 1000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    }
}
