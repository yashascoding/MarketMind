package com.example.marketmind

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.marketmind.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.setPadding(0, systemBars.top, 0, 0)
            binding.bottomNavigation.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_12dp) +
                    systemBars.bottom
            }
            insets
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.navItemDashboard.setOnClickListener {
            navigateTo(navController, R.id.navigation_dashboard)
        }
        binding.navItemPortfolio.setOnClickListener {
            navigateTo(navController, R.id.navigation_portfolio)
        }
        binding.navItemAnalytics.setOnClickListener {
            navigateTo(navController, R.id.navigation_analytics)
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility =
                if (destination.id == R.id.navigation_auth) View.GONE else View.VISIBLE
            binding.navItemDashboard.isSelected = destination.id == R.id.navigation_dashboard
            binding.navItemPortfolio.isSelected = destination.id == R.id.navigation_portfolio
            binding.navItemAnalytics.isSelected = destination.id == R.id.navigation_analytics
        }
    }

    private fun navigateTo(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return

        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, false, true)
            .build()
        navController.navigate(destinationId, null, options)
    }
}