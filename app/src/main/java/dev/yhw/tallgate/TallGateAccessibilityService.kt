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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (System.currentTimeMillis() - lastBlockedTimestamp < DEBOUNCE_MS) return

        for (rule in RULES) {
            if (packageName !in rule.packages || !rule.isEnabled(prefs)) continue
            val root = rootInActiveWindow ?: event.source ?: return
            if (rule.matches(root, event)) {
                blockAndEscape(rule.toastRes)
                return
            }
        }
    }

    private fun blockAndEscape(toastRes: Int) {
        lastBlockedTimestamp = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (prefs.isHapticEnabled) vibrate()
        if (prefs.isToastEnabled) Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    private class Rule(
        val packages: Set<String>,
        val isEnabled: (BlockPreferences) -> Boolean,
        val toastRes: Int,
        val matches: (AccessibilityNodeInfo, AccessibilityEvent) -> Boolean,
    )

    companion object {
        private const val TAG = "TallGateService"
        private const val DEBOUNCE_MS = 800L

        private val INSTAGRAM = setOf("com.instagram.android")
        private val YOUTUBE = setOf("com.google.android.youtube")
        private val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

        private val RULES = listOf(
            Rule(INSTAGRAM, BlockPreferences::isInstagramReelsBlocked, R.string.toast_blocked_reels) { root, event ->
                event.classNameContains("ClipsViewer", "ClipsActivity") ||
                    root.hasNode(maxDepth = 4) {
                        it.id.contains("clips_video_container") ||
                            it.id.contains("clips_viewer") ||
                            it.id.contains("reel_viewer") ||
                            (it.id.contains("clips_tab") && (it.desc == "reels" || it.desc == "릴스")) ||
                            it.desc.contains("reels viewer") ||
                            it.desc.contains("릴스 뷰어")
                    }
            },
            Rule(INSTAGRAM, BlockPreferences::isInstagramExploreBlocked, R.string.toast_blocked_explore) { root, _ ->
                root.hasNode(maxDepth = 3) {
                    it.id.contains("search_tab") && (it.desc.contains("탐색") || it.desc.contains("explore"))
                }
            },
            Rule(YOUTUBE, BlockPreferences::isYouTubeShortsBlocked, R.string.toast_blocked_shorts) { root, event ->
                event.classNameContains("ReelWatch") ||
                    root.hasNode(maxDepth = 4) {
                        it.id.contains("reel_player") ||
                            it.id.contains("reel_watch") ||
                            (it.id.contains("pivot") && (it.desc == "shorts" || it.desc == "쇼츠"))
                    }
            },
            Rule(TIKTOK, BlockPreferences::isTikTokFeedBlocked, R.string.toast_blocked_tiktok) { root, _ ->
                root.hasNode(maxDepth = 3) {
                    it.desc == "for you" || it.desc == "추천" || it.label == "for you" || it.label == "추천"
                }
            },
        )

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

        private fun AccessibilityNodeInfo.hasNode(
            maxDepth: Int,
            depth: Int = 0,
            predicate: (AccessibilityNodeInfo) -> Boolean,
        ): Boolean =
            predicate(this) ||
                (depth < maxDepth && (0 until childCount).any { getChild(it)?.hasNode(maxDepth, depth + 1, predicate) == true })
    }
}
