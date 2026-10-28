package io.github.nuhanhabib.dozypal

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Listens (only inside Facebook and Instagram, see res/xml/reel_service_config.xml) for swipes
 * to a new reel. A reel is counted once it has stayed on screen for [WATCH_MS], so fast scrolling
 * doesn't count. Shows the floating counter and nudge bubbles while one of those apps is open.
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

    // Events only arrive from Facebook/Instagram, so we poll to notice when the user leaves them.
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
        overlay.update(store.increment(app))
        maybeShowNudge()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = hideOverlay()
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CountStore.KEY_ENABLED -> if (!store.counterEnabled) hideOverlay()
            CountStore.KEY_OVERLAY_LEFT, CountStore.KEY_OVERLAY_OPACITY -> currentApp?.let { showOverlay(it) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = CountStore(this)
        overlay = CounterOverlay(this)
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
        overlay.show(store.count(app), store.overlayOnLeft, store.overlayOpacity)
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
        const val WATCH_MS = 5_000L
        const val FOREGROUND_POLL_MS = 500L
        const val AWAY_CHECKS_TO_HIDE = 2
        const val NIGHT_NUDGE_GAP_MS = 30 * 60 * 1000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    }
}
