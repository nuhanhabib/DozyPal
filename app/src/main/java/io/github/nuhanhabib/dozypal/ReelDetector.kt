package io.github.nuhanhabib.dozypal

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class ReelChange { NONE, FORWARD, BACKWARD }

/**
 * Decides whether a scroll event inside Facebook/Instagram means "the user moved to another reel".
 * The service then waits a few seconds before counting, so fast scrolling doesn't count.
 *
 * Facebook and Instagram do not offer any official way to know this, so these are heuristics.
 * When Meta changes its apps, this file is the one to update. The rules:
 *  1. The scrolled view must look like a reels pager: almost full screen, showing 1-2 items at a time
 *     (normal feeds show many posts at once, so they don't match). Instagram's pager is also
 *     recognised by its view id.
 *  2. The screen must mention "Reels", so full-screen stories or long videos are skipped.
 *  3. Forward and backward moves are reported separately; only forward ones can be counted.
 *  4. Sponsored reels (ads) are not counted ([isSponsoredNow]).
 *
 * Only labels such as "Reels" or "Sponsored" are checked. Nothing is stored or sent.
 */
class ReelDetector(
    private val screenSize: () -> Pair<Int, Int>,
    private val rootProvider: () -> AccessibilityNodeInfo?,
) {
    private var lastSourceKey: String? = null
    private var lastIndex = -1
    private var lastSwipeAt = 0L
    private var reelsScreenCheckedAt = 0L
    private var reelsScreenCached = false

    fun onScroll(event: AccessibilityEvent, app: ReelApp): ReelChange {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return ReelChange.NONE
        val source = event.source ?: return ReelChange.NONE
        if (!looksLikeReelPager(source, app)) return ReelChange.NONE

        val now = SystemClock.uptimeMillis()
        val sourceKey = "${app.name}:${source.viewIdResourceName ?: source.className}"
        val index = event.fromIndex

        // First scroll seen on this pager: remember where we are.
        if (sourceKey != lastSourceKey) {
            lastSourceKey = sourceKey
            lastIndex = index
            lastSwipeAt = now
            return ReelChange.NONE
        }

        if (index >= 0 && lastIndex >= 0) {
            val change = when {
                index > lastIndex -> ReelChange.FORWARD
                index < lastIndex -> ReelChange.BACKWARD
                else -> ReelChange.NONE
            }
            lastIndex = index
            return change
        }

        // The app doesn't report item positions: treat each swipe gesture as one move.
        lastIndex = index
        if (now - lastSwipeAt < SWIPE_GAP_MS) return ReelChange.NONE
        lastSwipeAt = now
        val scrolledUp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && event.scrollDeltaY < 0
        return if (scrolledUp) ReelChange.BACKWARD else ReelChange.FORWARD
    }

    fun isSponsoredNow(): Boolean =
        anyVisibleNode(rootProvider()) { label -> AD_WORDS.any { label.equals(it, ignoreCase = true) } }

    private fun looksLikeReelPager(source: AccessibilityNodeInfo, app: ReelApp): Boolean {
        val id = source.viewIdResourceName.orEmpty()
        if (app == ReelApp.INSTAGRAM && INSTAGRAM_REEL_IDS.any { id.contains(it) }) return true

        val (screenWidth, screenHeight) = screenSize()
        val bounds = Rect().also { source.getBoundsInScreen(it) }
        val fullScreen = bounds.height() >= screenHeight * 0.7f && bounds.width() >= screenWidth * 0.9f
        val pagerLike = source.childCount in 1..2
        return fullScreen && pagerLike && isReelsScreen()
    }

    private fun isReelsScreen(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - reelsScreenCheckedAt < REELS_CHECK_CACHE_MS) return reelsScreenCached
        reelsScreenCheckedAt = now
        reelsScreenCached = anyVisibleNode(rootProvider()) { label ->
            REEL_WORDS.any { label.contains(it, ignoreCase = true) }
        }
        return reelsScreenCached
    }

    /** Breadth-first search over visible on-screen labels, capped so it stays fast. */
    private inline fun anyVisibleNode(
        root: AccessibilityNodeInfo?,
        match: (String) -> Boolean,
    ): Boolean {
        root ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim()
                val description = node.contentDescription?.toString()?.trim()
                if ((text != null && match(text)) || (description != null && match(description))) return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    companion object {
        private const val SWIPE_GAP_MS = 900L
        private const val REELS_CHECK_CACHE_MS = 3_000L
        private const val MAX_NODES = 500

        private val INSTAGRAM_REEL_IDS = listOf("clips_viewer", "clips_video", "reel_viewer")
        private val REEL_WORDS = listOf("reels", "reel", "রিল")
        private val AD_WORDS = listOf("Sponsored", "Ad", "স্পনসর্ড", "স্পন্সর্ড", "বিজ্ঞাপন")
    }
}
