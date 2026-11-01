package io.github.nuhanhabib.dozypal

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

enum class ReelChange { NONE, FORWARD, BACKWARD }

/**
 * Decides whether a scroll event means "the user moved to another reel / short".
 * The service then waits a few seconds before counting, so fast scrolling doesn't count.
 *
 * None of these apps offer an official way to know this, so these are heuristics.
 * When an app changes its layout, this file is the one to update. The rules:
 *  1. The scrolled view must look like a reels pager: almost full screen, showing 1-2 items at a time
 *     (normal feeds show many posts at once, so they don't match). TikTok keeps the previous and next
 *     video loaded too, so up to 3 there. Instagram's and YouTube's pagers are also recognised by their
 *     view ids. Sideways scrolls (such as TikTok's For You / Following tabs) are ignored.
 *  2. The screen must mention "Reels" (Facebook/Instagram) or "Shorts" (YouTube), so stories and
 *     normal long videos are skipped. TikTok needs no label.
 *  3. Forward and backward moves are reported separately; only forward ones can be counted.
 *  4. Sponsored reels (ads) are not counted ([isSponsoredNow]).
 *
 * Only labels such as "Reels" or "Sponsored" are checked. Nothing is stored or sent.
 */
class ReelDetector(
    private val screenSize: () -> Pair<Int, Int>,
    private val rootProvider: () -> AccessibilityNodeInfo?,
) {
    // Per pager, so two pagers on one screen (TikTok's tabs pager around its feed) don't reset each other.
    private val lastIndexBySource = HashMap<String, Int>()
    private var lastSwipeAt = 0L
    private var reelsScreenApp: ReelApp? = null
    private var reelsScreenCheckedAt = 0L
    private var reelsScreenCached = false

    fun onScroll(event: AccessibilityEvent, app: ReelApp): ReelChange {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return ReelChange.NONE
        if (isSideways(event)) return ReelChange.NONE
        val source = event.source ?: return ReelChange.NONE
        if (!looksLikeReelPager(source, app)) return ReelChange.NONE

        val now = SystemClock.uptimeMillis()
        val sourceKey = "${app.name}:${source.viewIdResourceName ?: source.className}"
        val index = event.fromIndex
        val lastIndex = lastIndexBySource.put(sourceKey, index)

        // First scroll seen on this pager: remember where we are.
        if (lastIndex == null) {
            lastSwipeAt = now
            return ReelChange.NONE
        }

        if (index >= 0 && lastIndex >= 0) {
            return when {
                index > lastIndex -> ReelChange.FORWARD
                index < lastIndex -> ReelChange.BACKWARD
                else -> ReelChange.NONE
            }
        }

        // The app doesn't report item positions: treat each swipe gesture as one move.
        if (now - lastSwipeAt < SWIPE_GAP_MS) return ReelChange.NONE
        lastSwipeAt = now
        val scrolledUp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && event.scrollDeltaY < 0
        return if (scrolledUp) ReelChange.BACKWARD else ReelChange.FORWARD
    }

    fun isSponsoredNow(): Boolean =
        anyVisibleNode(rootProvider()) { label -> AD_WORDS.any { label.equals(it, ignoreCase = true) } }

    private fun looksLikeReelPager(source: AccessibilityNodeInfo, app: ReelApp): Boolean {
        val id = source.viewIdResourceName.orEmpty()
        if (REEL_IDS[app].orEmpty().any { id.contains(it) }) return true

        val (screenWidth, screenHeight) = screenSize()
        val bounds = Rect().also { source.getBoundsInScreen(it) }
        val fullScreen = bounds.height() >= screenHeight * 0.6f && bounds.width() >= screenWidth * 0.9f
        // TikTok's whole home feed is short videos, so no "Reels"/"Shorts" label is needed there.
        if (app == ReelApp.TIKTOK) return fullScreen && source.childCount in 1..3
        return fullScreen && source.childCount in 1..2 && isReelsScreen(app)
    }

    /** A scroll that moved only left/right, e.g. switching TikTok's For You / Following tabs. */
    private fun isSideways(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        // -1 means "not reported" for both deltas.
        val movedX = event.scrollDeltaX != 0 && event.scrollDeltaX != -1
        val movedY = event.scrollDeltaY != 0 && event.scrollDeltaY != -1
        return movedX && !movedY
    }

    private fun isReelsScreen(app: ReelApp): Boolean {
        val now = SystemClock.uptimeMillis()
        if (app == reelsScreenApp && now - reelsScreenCheckedAt < REELS_CHECK_CACHE_MS) return reelsScreenCached
        reelsScreenApp = app
        reelsScreenCheckedAt = now
        val words = REEL_WORDS[app].orEmpty()
        reelsScreenCached = anyVisibleNode(rootProvider()) { label ->
            words.any { label.contains(it, ignoreCase = true) }
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

        private val REEL_IDS = mapOf(
            ReelApp.INSTAGRAM to listOf("clips_viewer", "clips_video", "reel_viewer"),
            ReelApp.YOUTUBE to listOf("reel_recycler", "reel_player", "shorts_"),
        )
        private val FB_IG_WORDS = listOf("reels", "reel", "রিল")
        private val REEL_WORDS = mapOf(
            ReelApp.FACEBOOK to FB_IG_WORDS,
            ReelApp.INSTAGRAM to FB_IG_WORDS,
            ReelApp.YOUTUBE to listOf("shorts", "শর্টস"),
        )
        private val AD_WORDS = listOf("Sponsored", "Ad", "স্পনসর্ড", "স্পন্সর্ড", "বিজ্ঞাপন")
    }
}
