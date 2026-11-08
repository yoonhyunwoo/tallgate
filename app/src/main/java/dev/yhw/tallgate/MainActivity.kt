package dev.yhw.tallgate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import dev.yhw.tallgate.databinding.ActivityMainBinding
import kotlin.reflect.KMutableProperty0

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = BlockPreferences(this)
        bind(binding.switchYtShorts, prefs::isYouTubeShortsBlocked)
        bind(binding.switchInstaReels, prefs::isInstagramReelsBlocked)
        bind(binding.switchInstaExplore, prefs::isInstagramExploreBlocked)
        bind(binding.switchTiktokFeed, prefs::isTikTokFeedBlocked)
        bind(binding.switchHaptic, prefs::isHapticEnabled)
        bind(binding.switchToast, prefs::isToastEnabled)

        val openAccessibilitySettings = View.OnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.cardStatus.setOnClickListener(openAccessibilitySettings)
        binding.btnEnableService.setOnClickListener(openAccessibilitySettings)
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
}
