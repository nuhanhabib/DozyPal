package io.github.nuhanhabib.dozypal

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Decides whether a scroll event inside Facebook/Instagram means "the user swiped to a new reel".
 *
 * Facebook and Instagram do not offer any official way to know this, so these are heuristics.
 * When Meta changes its apps, this file is the one to update. The rules:
 *  1. The scrolled view must look like a reels pager: almost full screen, showing 1-2 items at a time
 *     (normal feeds show many posts at once, so they don't match). Instagram's pager is also
 *     recognised by its view id.
 *  2. The screen must mention "Reels", so full-screen stories or long videos are skipped.
 *  3. Only forward swipes count, at most one per gesture.
 *  4. Sponsored reels (ads) are not counted.
 *
 * Only labels such as "Reels" or "Sponsored" are checked. Nothing is stored or sent.
 */
class ReelDetector(
    private val screenSize: () -> Pair<Int, Int>,
    private val rootProvider: () -> AccessibilityNodeInfo?,
) {
    private var lastSourceKey: String? = null
    private var lastIndex = -1
    private var lastCountAt = 0L
    private var reelsScreenCheckedAt = 0L
    private var reelsScreenCached = false

    fun isNewReel(event: AccessibilityEvent, app: ReelApp): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return false
        val source = event.source ?: return false
        if (!looksLikeReelPager(source, app)) return false

        val now = SystemClock.uptimeMillis()
        val sourceKey = "${app.name}:${source.viewIdResourceName ?: source.className}"
        val index = event.fromIndex

        // First scroll seen on this pager: remember where we are, don't count yet.
        if (sourceKey != lastSourceKey) {
            lastSourceKey = sourceKey
            lastIndex = index
            lastCountAt = now
            return false
        }

        val movedForward = if (index >= 0 && lastIndex >= 0) {
            val forward = index > lastIndex
            lastIndex = index
            forward
        } else {
            // The app doesn't report item positions: count one per swipe gesture instead.
            lastIndex = index
            now - lastCountAt > SWIPE_GAP_MS
        }
        if (!movedForward || now - lastCountAt < MIN_GAP_MS) return false

        lastCountAt = now
        return !isSponsored(rootProvider())
    }

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

    private fun isSponsored(root: AccessibilityNodeInfo?): Boolean =
        anyVisibleNode(root) { label -> AD_WORDS.any { label.equals(it, ignoreCase = true) } }

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
        private const val MIN_GAP_MS = 350L
        private const val REELS_CHECK_CACHE_MS = 3_000L
        private const val MAX_NODES = 500

        private val INSTAGRAM_REEL_IDS = listOf("clips_viewer", "clips_video", "reel_viewer")
        private val REEL_WORDS = listOf("reels", "reel", "রিল")
        private val AD_WORDS = listOf("Sponsored", "Ad", "স্পনসর্ড", "স্পন্সর্ড", "বিজ্ঞাপন")
    }
}
