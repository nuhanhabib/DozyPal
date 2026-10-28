package io.github.nuhanhabib.dozypal

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Listens (only inside Facebook and Instagram, see res/xml/reel_service_config.xml) for swipes
 * to a new reel, bumps today's count, and shows the floating counter while one of those apps is open.
 */
class ReelCounterService : AccessibilityService() {

    private lateinit var store: CountStore
    private lateinit var detector: ReelDetector
    private lateinit var overlay: CounterOverlay
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp: ReelApp? = null

    // Events only arrive from Facebook/Instagram, so we poll to notice when the user leaves them.
    private val foregroundCheck = object : Runnable {
        override fun run() {
            val pkg = rootInActiveWindow?.packageName
            if (pkg != null && ReelApp.fromPackage(pkg) == null) {
                hideOverlay()
            } else {
                handler.postDelayed(this, FOREGROUND_POLL_MS)
            }
        }
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CountStore.KEY_ENABLED -> if (!store.counterEnabled) hideOverlay()
            CountStore.KEY_OVERLAY_LEFT -> currentApp?.let { showOverlay(it) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = CountStore(this)
        overlay = CounterOverlay(this)
        detector = ReelDetector(
            screenSize = { resources.displayMetrics.let { it.widthPixels to it.heightPixels } },
            rootProvider = { rootInActiveWindow },
        )
        store.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized || !store.counterEnabled) return
        val app = ReelApp.fromPackage(event.packageName) ?: return

        showOverlay(app)
        if (detector.isNewReel(event, app)) {
            overlay.update(store.increment(app))
        }
    }

    private fun showOverlay(app: ReelApp) {
        if (!store.counterEnabled) return
        currentApp = app
        overlay.show(store.count(app), store.overlayOnLeft)
        handler.removeCallbacks(foregroundCheck)
        handler.postDelayed(foregroundCheck, FOREGROUND_POLL_MS)
    }

    private fun hideOverlay() {
        currentApp = null
        handler.removeCallbacks(foregroundCheck)
        if (::overlay.isInitialized) overlay.hide()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onDestroy()
    }

    private companion object {
        const val FOREGROUND_POLL_MS = 1_000L
    }
}
