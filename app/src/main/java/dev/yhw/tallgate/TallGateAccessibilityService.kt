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
        // [1] 절대 차단하면 안 되는 안전 영역 (DM 대화방, 프로필 등) 화이트리스트 검사
        if (isInstagramSafeZone(root)) {
            return
        }

        // [2] 릴스 탭 클릭 또는 릴스 탭 선택 감지
        if (prefs.isInstagramReelsBlocked) {
            // A. 하단 바의 릴스 탭이 선택되어 있는 경우 -> 홈(feed) 탭으로 부드럽게 자동 전환
            val clipsTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
            val isClipsTabSelected = clipsTabs.any { it.isSelected }

            // B. 릴스 탭을 직접 클릭한 이벤트
            val isClipsTabClicked = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                (event.source?.viewIdResourceName?.contains("clips_tab") == true ||
                 event.contentDescription?.toString()?.equals("Reels", ignoreCase = true) == true ||
                 event.contentDescription?.toString()?.equals("릴스", ignoreCase = true) == true)

            if (isClipsTabSelected || isClipsTabClicked) {
                Log.i(TAG, "Instagram Reels tab detected -> redirecting to Home feed")
                redirectToInstagramHome(root, R.string.toast_blocked_reels)
                return
            }

            // C. 피드 등 외부에서 전체화면 릴스 뷰어(ClipsViewer)로 진입한 경우
            // 주의: 인스타에서 '스토리(Stories)'는 내부적으로 reel_viewer라 불리므로 절대 차단하면 안 됨!
            // 숏폼 릴스는 오직 'Clips' 또는 'clips_video_container' 키워드를 사용함.
            val isStandaloneClipsViewer = event.classNameContains("ClipsViewer", "ClipsActivity") ||
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_video_container").isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty()

            if (isStandaloneClipsViewer) {
                Log.i(TAG, "Instagram Standalone Clips Viewer detected -> escaping")
                escapeWithBack(R.string.toast_blocked_reels)
                return
            }
        }

        // [3] 탐색(돋보기) 탭 차단
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
     * DM 대화창, 스토리 뷰어, 프로필 등 차단하면 안 되는 안전 영역인지 확인
     */
    private fun isInstagramSafeZone(root: AccessibilityNodeInfo): Boolean {
        // DM 탭이 선택되어 있거나, DM 메시지 입력창/대화방에 있는 경우
        val directTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_tab")
        if (directTabs.any { it.isSelected }) {
            return true
        }

        // DM 대화방 내부 요소들 (입력창, 메시지 리스트)
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/row_thread_composer_edittext").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/thread_title").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_text_edit_text").isNotEmpty()
        ) {
            return true
        }

        // 프로필 탭
        val profileTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/profile_tab")
        if (profileTabs.any { it.isSelected }) {
            return true
        }

        return false
    }

    /**
     * 릴스 탭에 있을 때 강제로 앱을 끄지 않고, 홈(feed) 탭을 대신 클릭해 평화롭게 메인으로 이동
     */
    private fun redirectToInstagramHome(root: AccessibilityNodeInfo, toastRes: Int) {
        lastBlockedTimestamp = System.currentTimeMillis()

        val feedTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        val feedTab = feedTabs.firstOrNull()

        if (feedTab != null && !feedTab.isSelected) {
            feedTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // 홈 탭을 찾을 수 없거나 이미 홈인 경우에만 뒤로가기
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        if (prefs.isHapticEnabled) vibrate()
        if (prefs.isToastEnabled) {
            Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleYouTube(root: AccessibilityNodeInfo, event: AccessibilityEvent) {
        if (!prefs.isYouTubeShortsBlocked) return

        // 유튜브 하단 쇼츠 탭 클릭 감지
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
