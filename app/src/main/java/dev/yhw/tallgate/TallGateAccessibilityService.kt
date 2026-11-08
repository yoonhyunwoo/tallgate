package dev.yhw.tallgate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TallGateAccessibilityService : AccessibilityService() {

    private lateinit var prefs: BlockPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedAt = -DEBOUNCE_MS
    private var lastScanAt = -SCAN_THROTTLE_MS
    private var pendingScan: Trigger? = null
    private val trailingScan = Runnable {
        pendingScan?.let { scan(it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = BlockPreferences(this)
        isRunning = true
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val trigger = Trigger(event.packageName?.toString() ?: return, event.eventType, event.className)

        val throttled = trigger.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            SystemClock.elapsedRealtime() - lastScanAt < SCAN_THROTTLE_MS
        if (throttled) {
            if (pendingScan == null) handler.postDelayed(trailingScan, SCAN_THROTTLE_MS)
            pendingScan = trigger
            return
        }
        scan(trigger)
    }

    private fun scan(trigger: Trigger) {
        pendingScan = null
        handler.removeCallbacks(trailingScan)
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedAt < DEBOUNCE_MS) return
        if (!prefs.isDisclosureAccepted) return
        lastScanAt = now

        when (trigger.pkg) {
            PKG_INSTAGRAM -> handleInstagram(trigger)
            PKG_YOUTUBE -> handleYouTube(trigger)
            PKG_TIKTOK_GLOBAL, PKG_TIKTOK_ASIA -> handleTikTok(trigger)
        }
    }

    /**
     * Window root for the event package. Null if an external window (launcher, system UI) is active.
     */
    private fun rootFor(trigger: Trigger): AccessibilityNodeInfo? =
        rootInActiveWindow?.takeIf { it.packageName?.toString() == trigger.pkg }

    private fun handleInstagram(trigger: Trigger) {
        if (!prefs.isInstagramReelsBlocked && !prefs.isInstagramExploreBlocked) return
        val root = rootFor(trigger) ?: return

        // Bottom tab bar visible -> user is on a main tab (Home, Search, DM, Profile)
        val hasTabBar = root.findAccessibilityNodeInfosByViewId("$IG_ID/tab_bar").any { it.isVisibleToUser }

        if (hasTabBar) {
            // Home 'For you' tab embeds Reels viewer; switch back to 'Feed' tab
            if (prefs.isInstagramReelsBlocked && switchHomeToFeedTab(root)) {
                Log.i(TAG, "Instagram: switching 'For you' tab to Feed")
                announceBlock(R.string.toast_blocked_reels)
                return
            }

            if (prefs.isInstagramExploreBlocked &&
                root.findAccessibilityNodeInfosByViewId("$IG_ID/search_tab").any { it.isSelected }
            ) {
                Log.i(TAG, "Instagram: redirecting Explore tab to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_explore)
            }
            return
        }

        // Tab bar hidden -> check for standalone fullscreen Reels viewer
        if (!prefs.isInstagramReelsBlocked) return
        // Preserve active DM chat thread
        if (isInstagramDmRoom(root)) return

        val hasVisibleClipsViewer =
            root.findAccessibilityNodeInfosByViewId("$IG_ID/clips_viewer_container").any { it.isVisibleToUser } ||
                root.findAccessibilityNodeInfosByViewId("$IG_ID/clips_video_container").any { it.isVisibleToUser } ||
                trigger.classNameContains("ClipsViewer", "ClipsActivity")

        if (hasVisibleClipsViewer) {
            Log.i(TAG, "Instagram: Reels viewer detected -> escaping with back")
            escapeWithBack(R.string.toast_blocked_reels)
        }
    }

    /**
     * Switches to the first tab ('Feed') if an alternative tab is selected in the home tab strip.
     */
    private fun switchHomeToFeedTab(root: AccessibilityNodeInfo): Boolean {
        val strip = root.findAccessibilityNodeInfosByViewId("$IG_ID/merged_feeds_static_tab_strip")
            .firstOrNull() ?: return false
        val selectedIndex = (0 until strip.childCount).indexOfFirst { index ->
            val tab = strip.getChild(index)
            tab != null && (0 until tab.childCount).any { tab.getChild(it)?.isSelected == true }
        }
        if (selectedIndex <= 0) return false
        return strip.getChild(0)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    /**
     * Checks if the active view belongs to an Instagram DM chat room.
     */
    private fun isInstagramDmRoom(root: AccessibilityNodeInfo): Boolean {
        return listOf("row_thread_composer", "row_thread_composer_edittext", "thread_title", "direct_text_edit_text")
            .any { root.findAccessibilityNodeInfosByViewId("$IG_ID/$it").isNotEmpty() }
    }

    /**
     * Clicks the Home tab to return to the main feed, falling back to global back navigation.
     */
    private fun redirectToInstagramHome(root: AccessibilityNodeInfo, toastRes: Int) {
        val homeTab = root.findAccessibilityNodeInfosByViewId("$IG_ID/feed_switcher").firstOrNull()
        if (homeTab != null) {
            homeTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        announceBlock(toastRes)
    }

    private fun announceBlock(toastRes: Int) {
        lastBlockedAt = SystemClock.elapsedRealtime()
        if (prefs.isHapticEnabled) vibrate()
        if (prefs.isToastEnabled) {
            Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleYouTube(trigger: Trigger) {
        if (!prefs.isYouTubeShortsBlocked) return
        val root = rootFor(trigger) ?: return

        val isShortsScreen =
            (trigger.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                trigger.classNameContains("ReelWatch", "ShortsPlayer")) ||
                root.findAccessibilityNodeInfosByViewId("$YT_ID/reel_watch_fragment_root").any { it.isVisibleToUser } ||
                root.findAccessibilityNodeInfosByViewId("$YT_ID/reel_player_page_container").any { it.isVisibleToUser }

        if (isShortsScreen) {
            Log.i(TAG, "YouTube: Shorts detected -> escaping with back")
            escapeWithBack(R.string.toast_blocked_shorts)
        }
    }

    private fun handleTikTok(trigger: Trigger) {
        if (!prefs.isTikTokFeedBlocked) return
        val root = rootFor(trigger) ?: return

        val isFeed = root.hasDeepNode(maxDepth = 8) { node ->
            val desc = node.desc
            val label = node.label
            (desc == "for you" || desc == "추천" || label == "for you" || label == "추천") && node.isVisibleToUser
        }

        if (isFeed) {
            Log.i(TAG, "TikTok: For You feed detected -> escaping with back")
            escapeWithBack(R.string.toast_blocked_tiktok)
        }
    }

    private fun escapeWithBack(toastRes: Int) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        announceBlock(toastRes)
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(trailingScan)
        isRunning = false
    }

    companion object {
        private const val TAG = "TallGateService"
        private const val DEBOUNCE_MS = 800L
        private const val SCAN_THROTTLE_MS = 250L

        private const val PKG_INSTAGRAM = "com.instagram.android"
        private const val PKG_YOUTUBE = "com.google.android.youtube"
        private const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        private const val PKG_TIKTOK_ASIA = "com.ss.android.ugc.trill"
        private const val IG_ID = "$PKG_INSTAGRAM:id"
        private const val YT_ID = "$PKG_YOUTUBE:id"

        @Volatile
        var isRunning = false
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (isRunning) return true

            val self = ComponentName(context, TallGateAccessibilityService::class.java)
            val enabledServices =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices?.split(':')?.any { ComponentName.unflattenFromString(it) == self } == true
        }

        private val AccessibilityNodeInfo.desc get() = contentDescription?.toString()?.lowercase().orEmpty()
        private val AccessibilityNodeInfo.label get() = text?.toString()?.lowercase().orEmpty()

        private class Trigger(val pkg: String, val eventType: Int, val className: CharSequence?) {
            fun classNameContains(vararg names: String) =
                names.any { className?.contains(it, ignoreCase = true) == true }
        }

        private fun AccessibilityNodeInfo.hasDeepNode(
            maxDepth: Int,
            depth: Int = 0,
            predicate: (AccessibilityNodeInfo) -> Boolean,
        ): Boolean =
            predicate(this) ||
                (depth < maxDepth && (0 until childCount).any { getChild(it)?.hasDeepNode(maxDepth, depth + 1, predicate) == true })
    }
}
