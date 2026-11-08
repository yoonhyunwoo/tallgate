package dev.yhw.tallgate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
    private var lastBlockedAt = -DEBOUNCE_MS

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = BlockPreferences(this)
        isRunning = true
        Log.i(TAG, "TallGateAccessibilityService Connected and listening")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (SystemClock.elapsedRealtime() - lastBlockedAt < DEBOUNCE_MS) return
        if (!prefs.isDisclosureAccepted) return

        when (event.packageName?.toString()) {
            PKG_INSTAGRAM -> handleInstagram(event)
            PKG_YOUTUBE -> handleYouTube(event)
            PKG_TIKTOK_GLOBAL, PKG_TIKTOK_ASIA -> handleTikTok(event)
        }
    }

    /**
     * 이벤트를 발생시킨 앱의 윈도우 루트. 다른 앱 윈도우(런처, 시스템 UI)가 활성 상태면 null.
     */
    private fun rootFor(event: AccessibilityEvent): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: event.source ?: return null
        return root.takeIf { it.packageName?.toString() == event.packageName?.toString() }
    }

    private fun handleInstagram(event: AccessibilityEvent) {
        if (!prefs.isInstagramReelsBlocked && !prefs.isInstagramExploreBlocked) return
        val root = rootFor(event) ?: return

        // 하단 탭 바가 보이면 메인 탭(홈, DM, 탐색, 프로필)에 있는 상태
        val hasTabBar = root.findAccessibilityNodeInfosByViewId("$IG_ID/tab_bar").any { it.isVisibleToUser }

        if (hasTabBar) {
            // 홈의 'For you' 탭은 릴스 뷰어(clips_viewer_container)를 그대로 임베드하므로 'Feed' 탭으로 되돌림
            if (prefs.isInstagramReelsBlocked && switchHomeToFeedTab(root)) {
                Log.i(TAG, "Home 'For you' tab (embedded reels) -> switching to Feed tab")
                announceBlock(R.string.toast_blocked_reels)
                return
            }

            if (prefs.isInstagramExploreBlocked &&
                root.findAccessibilityNodeInfosByViewId("$IG_ID/search_tab").any { it.isSelected }
            ) {
                Log.i(TAG, "Explore tab is selected -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_explore)
            }
            return
        }

        // 하단 탭 바가 사라진 상태 -> 전체화면 세로 숏폼 뷰어(ClipsViewer)인지 검사
        if (!prefs.isInstagramReelsBlocked) return
        // DM 대화방 내부(메시지 입력창)는 보호
        if (isInstagramDmRoom(root)) return

        val hasVisibleClipsViewer =
            root.findAccessibilityNodeInfosByViewId("$IG_ID/clips_viewer_container").any { it.isVisibleToUser } ||
                root.findAccessibilityNodeInfosByViewId("$IG_ID/clips_video_container").any { it.isVisibleToUser } ||
                event.classNameContains("ClipsViewer", "ClipsActivity")

        if (hasVisibleClipsViewer) {
            Log.i(TAG, "Fullscreen Standalone Reels Viewer detected (no tab bar) -> escaping with back")
            escapeWithBack(R.string.toast_blocked_reels)
        }
    }

    /**
     * 홈 상단 탭 스트립(Feed / For you)에서 첫 번째가 아닌 탭이 선택되어 있으면 첫 번째(Feed) 탭을 눌러 복귀
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
     * DM 대화방(채팅창) 내부인지 확인
     */
    private fun isInstagramDmRoom(root: AccessibilityNodeInfo): Boolean {
        return listOf("row_thread_composer", "row_thread_composer_edittext", "thread_title", "direct_text_edit_text")
            .any { root.findAccessibilityNodeInfosByViewId("$IG_ID/$it").isNotEmpty() }
    }

    /**
     * 현재 탭에서 홈 탭을 대신 클릭해 메인 피드로 복귀. 홈 탭을 못 찾으면 뒤로가기.
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

    private fun handleYouTube(event: AccessibilityEvent) {
        if (!prefs.isYouTubeShortsBlocked) return
        val root = rootFor(event) ?: return

        val isShortsScreen =
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.classNameContains("ReelWatch", "ShortsPlayer")) ||
                root.findAccessibilityNodeInfosByViewId("$YT_ID/reel_watch_fragment_root").any { it.isVisibleToUser } ||
                root.findAccessibilityNodeInfosByViewId("$YT_ID/reel_player_page_container").any { it.isVisibleToUser }

        if (isShortsScreen) {
            Log.i(TAG, "YouTube Shorts detected -> escaping")
            escapeWithBack(R.string.toast_blocked_shorts)
        }
    }

    private fun handleTikTok(event: AccessibilityEvent) {
        if (!prefs.isTikTokFeedBlocked) return
        val root = rootFor(event) ?: return

        val isFeed = root.hasDeepNode(maxDepth = 8) { node ->
            val desc = node.desc
            val label = node.label
            (desc == "for you" || desc == "추천" || label == "for you" || label == "추천") && node.isVisibleToUser
        }

        if (isFeed) {
            Log.i(TAG, "TikTok For-You feed detected -> escaping")
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
        isRunning = false
    }

    companion object {
        private const val TAG = "TallGateService"
        private const val DEBOUNCE_MS = 800L

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

        private fun AccessibilityEvent.classNameContains(vararg names: String) =
            names.any { className?.contains(it, ignoreCase = true) == true }

        private fun AccessibilityNodeInfo.hasDeepNode(
            maxDepth: Int,
            depth: Int = 0,
            predicate: (AccessibilityNodeInfo) -> Boolean,
        ): Boolean =
            predicate(this) ||
                (depth < maxDepth && (0 until childCount).any { getChild(it)?.hasDeepNode(maxDepth, depth + 1, predicate) == true })
    }
}
