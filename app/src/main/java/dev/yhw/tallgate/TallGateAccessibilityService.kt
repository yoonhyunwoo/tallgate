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
        // [1] 하단 탭 바(tab_bar) 존재 여부 확인
        // 인스타그램에서 하단 탭 바가 화면에 보인다면 -> 사용자는 메인 탭(홈, DM, 탐색, 프로필)에 있는 상태입니다.
        val tabBars = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        val hasTabBar = tabBars.any { it.isVisibleToUser }

        if (hasTabBar) {
            // 메인 탭 화면에 있을 때는 하단의 릴스 탭을 직접 눌렀거나 선택된 경우만 홈으로 튕겨냅니다.
            if (prefs.isInstagramReelsBlocked) {
                // A. 릴스 탭 직접 클릭
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    val viewId = event.source?.viewIdResourceName?.lowercase().orEmpty()
                    val desc = event.contentDescription?.toString()?.lowercase().orEmpty()
                    if (viewId.contains("clips_tab") || desc == "reels" || desc == "릴스") {
                        Log.i(TAG, "Reels tab clicked -> redirecting to Home feed")
                        redirectToInstagramHome(root, R.string.toast_blocked_reels)
                        return
                    }
                }

                // B. 릴스 탭이 선택되어 있는 경우
                val clipsTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
                if (clipsTabs.any { it.isSelected }) {
                    Log.i(TAG, "Reels tab is selected -> redirecting to Home feed")
                    redirectToInstagramHome(root, R.string.toast_blocked_reels)
                    return
                }
            }

            // C. 탐색 탭 차단
            if (prefs.isInstagramExploreBlocked) {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    val viewId = event.source?.viewIdResourceName?.lowercase().orEmpty()
                    val desc = event.contentDescription?.toString()?.lowercase().orEmpty()
                    if (viewId.contains("search_tab") || desc.contains("explore") || desc.contains("탐색")) {
                        Log.i(TAG, "Explore tab clicked -> redirecting to Home feed")
                        redirectToInstagramHome(root, R.string.toast_blocked_explore)
                        return
                    }
                }
                val searchTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab")
                if (searchTabs.any { it.isSelected }) {
                    Log.i(TAG, "Explore tab is selected -> redirecting to Home feed")
                    redirectToInstagramHome(root, R.string.toast_blocked_explore)
                    return
                }
            }

            // 하단 탭 바가 있고 릴스/탐색 탭이 아니라면 (즉, 홈 피드, DM, 프로필 등)
            // 홈 피드 안에 릴스 추천 카드가 떠 있든 말든 절대 차단하지 않고 자유롭게 탐색 허용!
            return
        }

        // [2] 하단 탭 바가 사라진 상태 -> 전체화면 세로 숏폼 뷰어(ClipsViewer)인지 검사
        if (prefs.isInstagramReelsBlocked) {
            // DM 대화방 내부(메시지 입력창)는 보호
            if (isInstagramDmRoom(root)) {
                return
            }

            val hasVisibleClipsViewer = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").any { it.isVisibleToUser } ||
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_video_container").any { it.isVisibleToUser } ||
                event.classNameContains("ClipsViewer", "ClipsActivity")

            if (hasVisibleClipsViewer) {
                Log.i(TAG, "Fullscreen Standalone Reels Viewer detected (no tab bar) -> escaping with back")
                escapeWithBack(R.string.toast_blocked_reels)
                return
            }
        }
    }

    /**
     * DM 대화방(채팅창) 내부인지 확인
     */
    private fun isInstagramDmRoom(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer_edittext").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/thread_title").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_text_edit_text").isNotEmpty()
    }

    /**
     * 릴스 탭에 있을 때 앱을 끄지 않고, 홈(feed) 탭을 대신 클릭해 평화롭게 메인 피드로 복귀
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
            root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_watch_fragment").any { it.isVisibleToUser } ||
            root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").any { it.isVisibleToUser }

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
            (desc == "for you" || desc == "추천" || label == "for you" || label == "추천") && node.isVisibleToUser
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
