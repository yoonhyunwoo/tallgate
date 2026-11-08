package dev.yhw.tallgate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.yhw.tallgate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BlockPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = BlockPreferences.getInstance(this)

        initToggles()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initToggles() {
        binding.switchInstaReels.isChecked = prefs.isInstagramReelsBlocked
        binding.switchInstaExplore.isChecked = prefs.isInstagramExploreBlocked
        binding.switchYtShorts.isChecked = prefs.isYouTubeShortsBlocked
        binding.switchTiktokFeed.isChecked = prefs.isTikTokFeedBlocked
        binding.switchHaptic.isChecked = prefs.isHapticEnabled
        binding.switchToast.isChecked = prefs.isToastEnabled
    }

    private fun setupListeners() {
        binding.switchInstaReels.setOnCheckedChangeListener { _, isChecked ->
            prefs.isInstagramReelsBlocked = isChecked
        }

        binding.switchInstaExplore.setOnCheckedChangeListener { _, isChecked ->
            prefs.isInstagramExploreBlocked = isChecked
        }

        binding.switchYtShorts.setOnCheckedChangeListener { _, isChecked ->
            prefs.isYouTubeShortsBlocked = isChecked
        }

        binding.switchTiktokFeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTikTokFeedBlocked = isChecked
        }

        binding.switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHapticEnabled = isChecked
        }

        binding.switchToast.setOnCheckedChangeListener { _, isChecked ->
            prefs.isToastEnabled = isChecked
        }

        binding.btnEnableService.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.cardStatus.setOnClickListener {
            if (!TallGateAccessibilityService.isServiceEnabled(this)) {
                openAccessibilitySettings()
            }
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = TallGateAccessibilityService.isServiceEnabled(this)

        if (isEnabled) {
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_active_bg))
            binding.cardStatus.strokeColor = ContextCompat.getColor(this, R.color.status_active_stroke)
            binding.tvStatusIcon.text = "🟢"
            binding.tvStatusTitle.text = getString(R.string.status_active_title)
            binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_active_text))
            binding.tvStatusDesc.text = getString(R.string.status_active_desc)
            binding.tvStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.status_active_text))
            binding.btnEnableService.visibility = View.GONE
        } else {
            binding.cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_inactive_bg))
            binding.cardStatus.strokeColor = ContextCompat.getColor(this, R.color.status_inactive_stroke)
            binding.tvStatusIcon.text = "⚠️"
            binding.tvStatusTitle.text = getString(R.string.status_inactive_title)
            binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_inactive_text))
            binding.tvStatusDesc.text = getString(R.string.status_inactive_desc)
            binding.tvStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.status_inactive_text))
            binding.btnEnableService.visibility = View.VISIBLE
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}
