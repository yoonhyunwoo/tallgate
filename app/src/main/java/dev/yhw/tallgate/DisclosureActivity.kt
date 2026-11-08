package dev.yhw.tallgate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import dev.yhw.tallgate.databinding.ActivityDisclosureBinding

class DisclosureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDisclosureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAgree.setOnClickListener {
            BlockPreferences(this).isDisclosureAccepted = true
            startActivity(Intent(this, MainActivity::class.java))
            if (!TallGateAccessibilityService.isServiceEnabled(this)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            finish()
        }
        binding.btnDecline.setOnClickListener { finishAffinity() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAffinity()
        })
    }
}
