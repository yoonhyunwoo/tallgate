package dev.yhw.tallgate

import android.content.Context
import android.content.SharedPreferences

class BlockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isInstagramReelsBlocked: Boolean
        get() = prefs.getBoolean(KEY_INSTA_REELS, true)
        set(value) = prefs.edit().putBoolean(KEY_INSTA_REELS, value).apply()

    var isInstagramExploreBlocked: Boolean
        get() = prefs.getBoolean(KEY_INSTA_EXPLORE, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTA_EXPLORE, value).apply()

    var isYouTubeShortsBlocked: Boolean
        get() = prefs.getBoolean(KEY_YT_SHORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_YT_SHORTS, value).apply()

    var isTikTokFeedBlocked: Boolean
        get() = prefs.getBoolean(KEY_TIKTOK_FEED, true)
        set(value) = prefs.edit().putBoolean(KEY_TIKTOK_FEED, value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var isToastEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOAST, true)
        set(value) = prefs.edit().putBoolean(KEY_TOAST, value).apply()

    companion object {
        private const val PREFS_NAME = "tallgate_block_prefs"

        const val KEY_INSTA_REELS = "key_insta_reels"
        const val KEY_INSTA_EXPLORE = "key_insta_explore"
        const val KEY_YT_SHORTS = "key_yt_shorts"
        const val KEY_TIKTOK_FEED = "key_tiktok_feed"
        const val KEY_HAPTIC = "key_haptic"
        const val KEY_TOAST = "key_toast"

        @Volatile
        private var instance: BlockPreferences? = null

        fun getInstance(context: Context): BlockPreferences {
            return instance ?: synchronized(this) {
                instance ?: BlockPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
