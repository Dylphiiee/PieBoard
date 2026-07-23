package com.dylphiiee.pieboard.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dylphiiee.pieboard.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simple branded loading screen shown once when the app is opened. Purely
 * cosmetic — no data loading is gated behind it, it just gives the app an
 * intentional first impression before handing off to [MainActivity].
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DELAY_MS = 1600L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
