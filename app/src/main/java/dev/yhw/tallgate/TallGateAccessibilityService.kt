package dev.yhw.tallgate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TallGateAccessibilityService : AccessibilityService() {

    private lateinit var prefs: BlockPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastBlockedTimestamp = 0L
    private val debounceCooldownMs = 800L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = BlockPreferences.getInstance(this)
        isRunning = true
        Log.d(TAG, "TallGateAccessibilityService Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBlockedTimestamp < debounceCooldownMs) {
            return
        }

        when (packageName) {
            PKG_INSTAGRAM -> handleInstagramEvent(event)
            PKG_YOUTUBE -> handleYouTubeEvent(event)
            PKG_TIKTOK_GLOBAL, PKG_TIKTOK_ASIA -> handleTikTokEvent(event)
        }
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source ?: return

        // 1. 인스타그램 릴스 차단 검사
        if (prefs.isInstagramReelsBlocked) {
            if (isInstagramReelsScreen(rootNode, event)) {
                blockAndEscape(R.string.toast_blocked_reels)
                return
            }
        }

        // 2. 인스타그램 탐색(돋보기) 탭 차단 검사
        if (prefs.isInstagramExploreBlocked) {
            if (isInstagramExploreScreen(rootNode, event)) {
                blockAndEscape(R.string.toast_blocked_explore)
                return
            }
        }
    }

    private fun handleYouTubeEvent(event: AccessibilityEvent) {
        if (!prefs.isYouTubeShortsBlocked) return
        val rootNode = rootInActiveWindow ?: event.source ?: return

        if (isYouTubeShortsScreen(rootNode, event)) {
            blockAndEscape(R.string.toast_blocked_shorts)
        }
    }

    private fun handleTikTokEvent(event: AccessibilityEvent) {
        if (!prefs.isTikTokFeedBlocked) return
        val rootNode = rootInActiveWindow ?: event.source ?: return

        if (isTikTokFeedScreen(rootNode)) {
            blockAndEscape(R.string.toast_blocked_tiktok)
        }
    }

    /**
     * 인스타그램 릴스 뷰어 감지 로직
     */
    private fun isInstagramReelsScreen(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("ClipsViewer", ignoreCase = true) ||
            className.contains("ClipsActivity", ignoreCase = true)
        ) {
            return true
        }

        // 노드 트리에서 릴스 고유 리소스 ID 및 텍스트 탐색
        val matchingNodes = findNodes(root, maxDepth = 4) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            viewId.contains("clips_video_container") ||
            viewId.contains("clips_viewer") ||
            viewId.contains("reel_viewer") ||
            (viewId.contains("clips_tab") && (desc == "reels" || desc == "릴스")) ||
            desc.contains("reels viewer") ||
            desc.contains("릴스 뷰어")
        }

        return matchingNodes.isNotEmpty()
    }

    /**
     * 인스타그램 탐색 탭 감지 로직
     */
    private fun isInstagramExploreScreen(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val matchingNodes = findNodes(root, maxDepth = 3) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            viewId.contains("search_tab") && (desc.contains("탐색") || desc.contains("explore"))
        }
        return matchingNodes.isNotEmpty()
    }

    /**
     * 유튜브 쇼츠 화면 감지 로직
     */
    private fun isYouTubeShortsScreen(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        if (className.contains("ReelWatch", ignoreCase = true)) {
            return true
        }

        val matchingNodes = findNodes(root, maxDepth = 4) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            viewId.contains("reel_player") ||
            viewId.contains("reel_watch") ||
            (viewId.contains("pivot") && (desc == "shorts" || desc == "쇼츠"))
        }

        return matchingNodes.isNotEmpty()
    }

    /**
     * 틱톡 추천 피드 감지 로직
     */
    private fun isTikTokFeedScreen(root: AccessibilityNodeInfo): Boolean {
        val matchingNodes = findNodes(root, maxDepth = 3) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            desc == "for you" || desc == "추천" || text == "for you" || text == "추천"
        }
        return matchingNodes.isNotEmpty()
    }

    /**
     * 차단 실행: 뒤로가기 실행 + 진동 + 토스트
     */
    private fun blockAndEscape(messageResId: Int) {
        lastBlockedTimestamp = System.currentTimeMillis()

        // 1. 시스템 뒤로가기(Back) 액션으로 즉각 튕겨내기
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 2. 햅틱 피드백
        if (prefs.isHapticEnabled) {
            triggerHaptic()
        }

        // 3. 토스트 팝업 안내
        if (prefs.isToastEnabled) {
            mainHandler.post {
                Toast.makeText(applicationContext, getString(messageResId), Toast.LENGTH_SHORT).show()
            }
        }

        Log.i(TAG, "Blocked target short-form screen successfully.")
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    /**
     * 가벼운 계층 탐색 헬퍼 (배터리 및 CPU 부하 최소화)
     */
    private fun findNodes(
        root: AccessibilityNodeInfo,
        maxDepth: Int = 4,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo?, currentDepth: Int) {
            if (node == null || currentDepth > maxDepth || result.isNotEmpty()) return

            if (predicate(node)) {
                result.add(node)
                return
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), currentDepth + 1)
                if (result.isNotEmpty()) break
            }
        }

        traverse(root, 0)
        return result
    }

    override fun onInterrupt() {
        Log.d(TAG, "TallGateAccessibilityService Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "TallGateAccessibilityService Destroyed")
    }

    companion object {
        private const val TAG = "TallGateService"

        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_ASIA = "com.ss.android.ugc.trill"

        @Volatile
        var isRunning: Boolean = false

        /**
         * 안드로이드 시스템 설정에서 이 접근성 서비스가 활성화되어 있는지 확인
         */
        fun isServiceEnabled(context: Context): Boolean {
            if (isRunning) return true

            val expectedComponentName = ComponentName(context, TallGateAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
