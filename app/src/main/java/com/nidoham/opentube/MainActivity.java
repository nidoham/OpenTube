package com.nidoham.opentube;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.nidoham.opentube.databinding.ActivityMainBinding;

import com.nidoham.opentube.fragments.BaseStateFragment;
import com.nidoham.opentube.fragments.list.*;
import com.nidoham.opentube.search.SearchActivity;

/**
 * MainActivity for FlowTube.
 * Handles app initialization, navigation, and search.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BottomNavigationView bottomNav;
    private int currentNavigationId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge BEFORE setContentView
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int seedColor = ContextCompat.getColor(this, R.color.seed);
        setSystemBarColors(seedColor);
        
        // Handle window insets for edge-to-edge
        setupEdgeToEdge();

        bottomNav = binding.bottomNav;

        setupSearchButton();
        setupBottomNavigation();
        applyDynamicColors();

        // Load initial fragment only if first launch
        if (savedInstanceState == null) {
            loadNavigationItem(R.id.nav_home);
        }
    }

    /**
     * Setup edge-to-edge with proper insets handling for Android 15+
     */
    private void setupEdgeToEdge() {
        View rootView = binding.getRoot();
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Apply padding to AppBarLayout for status bar
            if (binding.appBarLayout != null) {
                binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom()
                );
            }
            
            // Apply padding to bottom navigation to account for gesture bar
            if (binding.bottomNav != null) {
                binding.bottomNav.setPadding(
                    binding.bottomNav.getPaddingLeft(),
                    binding.bottomNav.getPaddingTop(),
                    binding.bottomNav.getPaddingRight(),
                    systemBars.bottom
                );
            }
            
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Setup search button click.
     */
    private void setupSearchButton() {
        binding.searchBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
        });
    }

    /**
     * Setup bottom navigation listener.
     */
    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId != currentNavigationId) { // Prevent reloading same fragment
                currentNavigationId = itemId;
                loadNavigationItem(itemId);
            }
            return true;
        });
    }

    /**
     * Load fragment based on navigation ID.
     */
    private void loadNavigationItem(int navigationId) {
        if (navigationId == R.id.nav_home) {
            BaseStateFragment.loadFragment(getSupportFragmentManager(), new HomeFragment(), false, "home");
        } else if (navigationId == R.id.nav_community) {
            BaseStateFragment.loadFragment(getSupportFragmentManager(), new CommunityFragment(), false, "community");
        } else if (navigationId == R.id.nav_library) {
            BaseStateFragment.loadFragment(getSupportFragmentManager(), new LibraryFragment(), false, "library");
        } else if (navigationId == R.id.nav_subscription) {
            BaseStateFragment.loadFragment(getSupportFragmentManager(), new SubscriptionFragment(), false, "subscription");
        } else {
            BaseStateFragment.loadFragment(getSupportFragmentManager(), new HomeFragment(), false, "home");
        }
    }

    /**
     * Set system bar colors with Android 15 support.
     */
     @Deprecated
    private void setSystemBarColors(int color) {
        Window window = getWindow();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);
        }
        
        // Set light/dark icons based on background
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightNavigationBars(false);
        
        // Disable navigation bar contrast enforcement (Android 10-14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Apply Material You dynamic colors (Android 12+).
     */
   @Deprecated
    private void applyDynamicColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyIfAvailable(this);
        }
    }

    /**
     * Handle back navigation with home-first behavior.
     */
    @Deprecated
    @Override
    public void onBackPressed() {
        finishAffinity();
        super.onBackPressed();
    }

    /**
     * Check if current navigation is Home.
     */
    public boolean isOnHomeNavigation() {
        return currentNavigationId == R.id.nav_home;
    }

    /**
     * Navigate to Home tab.
     */
    public void navigateToHome() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    /**
     * Save current navigation ID on config change.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_navigation_id", currentNavigationId);
    }

    /**
     * Restore current navigation ID after config change.
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentNavigationId = savedInstanceState.getInt("current_navigation_id", R.id.nav_home);
        bottomNav.setSelectedItemId(currentNavigationId);
    }

    @Override
    protected void onDestroy() {
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(null);
        }
        super.onDestroy();
    }
}