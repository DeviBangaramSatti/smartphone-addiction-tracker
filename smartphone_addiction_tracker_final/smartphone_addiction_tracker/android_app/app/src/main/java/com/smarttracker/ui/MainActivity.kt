package com.smarttracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.smarttracker.R
import com.smarttracker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 *
 * Single-activity architecture with Navigation Component.
 * Bottom nav tabs:
 *   Dashboard  → DashboardFragment  (usage stats + ML prediction)
 *   Browser    → InAppBrowserFragment (tracked web browsing)
 *   Settings   → SettingsFragment   (cloud sync, auth, alerts)
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)
    }
}
