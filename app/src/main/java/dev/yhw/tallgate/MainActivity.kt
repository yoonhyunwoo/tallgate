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
        bind(binding.switchInstaReels, prefs::isInstagramReelsBlocked)
        bind(binding.switchInstaExplore, prefs::isInstagramExploreBlocked)
        bind(binding.switchYtShorts, prefs::isYouTubeShortsBlocked)
        bind(binding.switchTiktokFeed, prefs::isTikTokFeedBlocked)
        bind(binding.switchHaptic, prefs::isHapticEnabled)
        bind(binding.switchToast, prefs::isToastEnabled)

        val openSettingsIfDisabled = View.OnClickListener {
            if (!TallGateAccessibilityService.isServiceEnabled(this)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        binding.cardStatus.setOnClickListener(openSettingsIfDisabled)
        binding.btnEnableService.setOnClickListener(openSettingsIfDisabled)
    }

    override fun onResume() {
        super.onResume()
        val enabled = TallGateAccessibilityService.isServiceEnabled(this)
        val textColor = getColor(if (enabled) R.color.status_active_text else R.color.status_inactive_text)

        binding.cardStatus.setCardBackgroundColor(
            getColor(if (enabled) R.color.status_active_bg else R.color.status_inactive_bg)
        )
        binding.cardStatus.strokeColor =
            getColor(if (enabled) R.color.status_active_stroke else R.color.status_inactive_stroke)
        binding.tvStatusIcon.text = if (enabled) "🟢" else "⚠️"
        binding.tvStatusTitle.setText(if (enabled) R.string.status_active_title else R.string.status_inactive_title)
        binding.tvStatusTitle.setTextColor(textColor)
        binding.tvStatusDesc.setText(if (enabled) R.string.status_active_desc else R.string.status_inactive_desc)
        binding.tvStatusDesc.setTextColor(textColor)
        binding.btnEnableService.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun bind(toggle: MaterialSwitch, pref: KMutableProperty0<Boolean>) {
        toggle.isChecked = pref.get()
        toggle.setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }
}
