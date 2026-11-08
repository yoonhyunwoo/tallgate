package dev.yhw.tallgate

import android.content.Context
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class BlockPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("tallgate_block_prefs", Context.MODE_PRIVATE)

    var isInstagramReelsBlocked by flag("key_insta_reels", true)
    var isInstagramExploreBlocked by flag("key_insta_explore", false)
    var isYouTubeShortsBlocked by flag("key_yt_shorts", true)
    var isTikTokFeedBlocked by flag("key_tiktok_feed", true)
    var isHapticEnabled by flag("key_haptic", true)
    var isToastEnabled by flag("key_toast", true)
    var isDisclosureAccepted by flag("key_disclosure_accepted", false)

    private fun flag(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }
}
