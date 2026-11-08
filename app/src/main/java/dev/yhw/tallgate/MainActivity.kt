package dev.yhw.tallgate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import dev.yhw.tallgate.databinding.ActivityMainBinding
import kotlin.reflect.KMutableProperty0

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BlockPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = BlockPreferences(this)
        bind(binding.switchYtShorts, prefs::isYouTubeShortsBlocked)
        bind(binding.switchInstaReels, prefs::isInstagramReelsBlocked)
        bind(binding.switchTiktokFeed, prefs::isTikTokFeedBlocked)

        val openAccessibilitySettings = View.OnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.cardStatus.setOnClickListener(openAccessibilitySettings)
        binding.btnEnableService.setOnClickListener(openAccessibilitySettings)
        binding.btnMore.setOnClickListener(::showMenu)
    }

    override fun onResume() {
        super.onResume()
        binding.cardStatus.visibility =
            if (TallGateAccessibilityService.isServiceEnabled(this)) View.GONE else View.VISIBLE
    }

    private fun bind(toggle: MaterialSwitch, pref: KMutableProperty0<Boolean>) {
        toggle.isChecked = pref.get()
        toggle.setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    private fun showMenu(anchor: View) {
        val toggles = mapOf(
            R.id.menu_insta_explore to prefs::isInstagramExploreBlocked,
            R.id.menu_haptic to prefs::isHapticEnabled,
            R.id.menu_toast to prefs::isToastEnabled,
        )
        PopupMenu(this, anchor).apply {
            inflate(R.menu.main_menu)
            toggles.forEach { (id, pref) -> menu.findItem(id).isChecked = pref.get() }
            setOnMenuItemClickListener { item ->
                toggles[item.itemId]?.let { it.set(!it.get()) } != null
            }
            show()
        }
    }
}
