package dev.yhw.tallgate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
    private var lastBlockedTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = BlockPreferences(this)
        isRunning = true
        Log.i(TAG, "TallGateAccessibilityService Connected and listening")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBlockedTimestamp < DEBOUNCE_MS) {
            return
        }

        val root = rootInActiveWindow ?: event.source ?: return
        when (packageName) {
            PKG_INSTAGRAM -> handleInstagram(root, event)
            PKG_YOUTUBE -> handleYouTube(root, event)
            PKG_TIKTOK_GLOBAL, PKG_TIKTOK_ASIA -> handleTikTok(root, event)
        }
    }

    private fun handleInstagram(root: AccessibilityNodeInfo, event: AccessibilityEvent) {
        // [1] 릴스/탐색 탭 직접 클릭 이벤트 (홈 피드에 있든 어디에 있든 누르는 순간 즉각 방어)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val viewId = event.source?.viewIdResourceName?.lowercase().orEmpty()
            val desc = event.contentDescription?.toString()?.lowercase().orEmpty()

            if (prefs.isInstagramReelsBlocked && (viewId.contains("clips_tab") || desc == "reels" || desc == "릴스")) {
                Log.i(TAG, "Instagram Reels tab clicked -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_reels)
                return
            }

            if (prefs.isInstagramExploreBlocked && (viewId.contains("search_tab") || desc.contains("explore") || desc.contains("탐색"))) {
                Log.i(TAG, "Instagram Explore tab clicked -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_explore)
                return
            }
        }

        // [2] 안전 영역 검사: 홈 피드, DM, 프로필 화면에 머물고 있을 때는 절대 차단하지 않음!
        // 홈 피드 내에 '추천 릴스' 카드나 캐러셀이 박혀 있어도 홈 피드 자체는 자유롭게 탐색 가능
        if (isInstagramSafeZone(root)) {
            return
        }

        // [3] 하단 바의 릴스 탭이 선택되어 있는 경우 -> 홈 탭으로 부드럽게 자동 전환
        if (prefs.isInstagramReelsBlocked) {
            val clipsTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
            if (clipsTabs.any { it.isSelected }) {
                Log.i(TAG, "Instagram Reels tab is selected -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_reels)
                return
            }

            // [4] 홈 피드 밖에서 전체화면 릴스 뷰어(ClipsViewer)로 진입한 경우에만 뒤로가기 실행
            // (주의: 하단 탭 바가 사라진 순수 전체화면 숏폼 플레이어 상태)
            val hasClipsViewerContainer = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty()
            val hasClipsVideoNoTabBar = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_video_container").isNotEmpty() &&
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar").isEmpty()
            val classNameMatch = event.classNameContains("ClipsViewer", "ClipsActivity")

            if (classNameMatch || hasClipsViewerContainer || hasClipsVideoNoTabBar) {
                Log.i(TAG, "Instagram Standalone Clips Viewer detected! (classMatch=$classNameMatch, viewerContainer=$hasClipsViewerContainer, videoNoTabBar=$hasClipsVideoNoTabBar, className=${event.className}, eventType=${event.eventType}) -> escaping")
                escapeWithBack(R.string.toast_blocked_reels)
                return
            }
        }

        // [5] 탐색(돋보기) 탭 차단
        if (prefs.isInstagramExploreBlocked) {
            val searchTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab")
            if (searchTabs.any { it.isSelected }) {
                Log.i(TAG, "Instagram Explore tab detected -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_explore)
                return
            }
        }
    }

    /**
     * 안전 영역 (홈 피드, DM 대화창, 프로필): 이 영역에서는 릴스 캐러셀 등이 보여도 홈을 차단하지 않음
     */
    private fun isInstagramSafeZone(root: AccessibilityNodeInfo): Boolean {
        // 1. 홈(feed) 탭이 선택되어 있는 경우 -> 홈 피드는 절대 차단하지 않음!
        val feedTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (feedTabs.any { it.isSelected }) {
            Log.d(TAG, "SafeZone: feed_tab is selected")
            return true
        }

        // 2. DM 탭이 선택되어 있거나, DM 메시지 대화방/입력창에 있는 경우
        val directTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_tab")
        if (directTabs.any { it.isSelected }) {
            return true
        }

        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer_edittext").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/thread_title").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_text_edit_text").isNotEmpty()
        ) {
            return true
        }

        // 3. 프로필 탭이 선택되어 있는 경우
        val profileTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/profile_tab")
        if (profileTabs.any { it.isSelected }) {
            return true
        }

        return false
    }

    /**
     * 릴스 탭에 있을 때 강제로 앱을 끄지 않고, 홈(feed) 탭을 대신 클릭해 평화롭게 메인으로 복귀
     */
    private fun redirectToInstagramHome(root: AccessibilityNodeInfo, toastRes: Int) {
        lastBlockedTimestamp = System.currentTimeMillis()

        val feedTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        val feedTab = feedTabs.firstOrNull()

        if (feedTab != null && !feedTab.isSelected) {
            feedTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        if (prefs.isHapticEnabled) vibrate()
        if (prefs.isToastEnabled) {
            Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleYouTube(root: AccessibilityNodeInfo, event: AccessibilityEvent) {
        if (!prefs.isYouTubeShortsBlocked) return

        val isShortsClick = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            (event.contentDescription?.toString()?.equals("Shorts", ignoreCase = true) == true ||
             event.contentDescription?.toString()?.equals("쇼츠", ignoreCase = true) == true)

        val isShortsScreen = event.classNameContains("ReelWatch", "ShortsPlayer") ||
            root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_watch_fragment").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").isNotEmpty()

        if (isShortsClick || isShortsScreen) {
            Log.i(TAG, "YouTube Shorts detected -> escaping")
            escapeWithBack(R.string.toast_blocked_shorts)
        }
    }

    private fun handleTikTok(root: AccessibilityNodeInfo, event: AccessibilityEvent) {
        if (!prefs.isTikTokFeedBlocked) return

        val isFeed = root.hasDeepNode(maxDepth = 8) { node ->
            val desc = node.desc
            val label = node.label
            desc == "for you" || desc == "추천" || label == "for you" || label == "추천"
        }

        if (isFeed) {
            Log.i(TAG, "TikTok For-You feed detected -> escaping")
            escapeWithBack(R.string.toast_blocked_tiktok)
        }
    }

    private fun escapeWithBack(toastRes: Int) {
        lastBlockedTimestamp = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (prefs.isHapticEnabled) vibrate()
        if (prefs.isToastEnabled) {
            Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
        }
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

        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_ASIA = "com.ss.android.ugc.trill"

        @Volatile
        var isRunning = false
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (isRunning) return true

            val expected = ComponentName(context, TallGateAccessibilityService::class.java).flattenToString()
            val enabledServices =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
        }

        private val AccessibilityNodeInfo.id get() = viewIdResourceName?.lowercase().orEmpty()
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
