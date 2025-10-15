package com.nidoham.opentube.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;

import com.nidoham.opentube.dialog.CustomOptionsDialog;

/**
 * Manages all player controls, dialogs, and user interactions.
 * This class separates UI control logic from the Activity to improve maintainability.
 * 
 * Features:
 * - Quality selection (144p to 1080p or 144p to 4K based on support)
 * - Playback speed control
 * - Video scale mode adjustment
 * - State persistence across configuration changes
 * - SharedPreferences for default quality and high quality support
 */
public class PlayerControlsManager {
    
    // SharedPreferences constants
    private static final String PREFS_NAME = "PlayerControlsPrefs";
    private static final String PREF_DEFAULT_QUALITY = "default_quality";
    private static final String PREF_SUPPORTS_HIGH_QUALITY = "supports_high_quality";
    private static final String DEFAULT_QUALITY_VALUE = "720p";
    private static final boolean DEFAULT_HIGH_QUALITY_SUPPORT = false;
    
    // Scale mode constants
    private static final int SCALE_MODE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private static final int SCALE_MODE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    private static final int SCALE_MODE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    private static final String[] SCALE_MODE_NAMES = {"Fit (Default)", "Fill Screen", "Zoom"};
    
    // Quality constants - Standard quality (144p to 1080p)
    private static final String[] QUALITY_OPTIONS_STANDARD = {"144p", "240p", "360p", "480p", "720p", "1080p"};
    private static final String[] QUALITY_LABELS_STANDARD = {
        "144p (Low)", 
        "240p", 
        "360p (SD)", 
        "480p", 
        "720p (HD)", 
        "1080p (Full HD)"
    };
    
    // Quality constants - High quality (144p to 4K)
    private static final String[] QUALITY_OPTIONS_HIGH = {"144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"};
    private static final String[] QUALITY_LABELS_HIGH = {
        "144p (Low)", 
        "240p", 
        "360p (SD)", 
        "480p", 
        "720p (HD)", 
        "1080p (Full HD)",
        "1440p (2K)",
        "2160p (4K)"
    };
    
    // Speed constants
    private static final String[] SPEED_LABELS = {"0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "1.75x", "2.0x"};
    private static final float[] SPEED_VALUES = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    
    private final Context context;
    private final PlayerControlsCallback callback;
    private final SharedPreferences sharedPreferences;
    
    // High quality support flag
    private boolean supportsHighQuality;
    
    // Current quality (loaded from SharedPreferences)
    private String currentQuality;
    private int currentScaleMode = SCALE_MODE_FIT;
    private float currentPlaybackSpeed = 1.0f;
    
    private CustomOptionsDialog customOptionsDialog;
    
    /**
     * Callback interface for player control actions
     */
    public interface PlayerControlsCallback {
        void onQualityChanged(String quality);
        void onScaleModeChanged(int scaleMode);
        void onPlaybackSpeedChanged(float speed);
        Player getPlayer();
    }
    
    public PlayerControlsManager(Context context, PlayerControlsCallback callback, boolean supportsHighQuality) {
        this.context = context;
        this.callback = callback;
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load high quality support from SharedPreferences
        this.supportsHighQuality = sharedPreferences.getBoolean(PREF_SUPPORTS_HIGH_QUALITY, supportsHighQuality);
        
        // Load default quality from SharedPreferences
        this.currentQuality = sharedPreferences.getString(PREF_DEFAULT_QUALITY, DEFAULT_QUALITY_VALUE);
        
        // Validate quality in case settings changed
        this.currentQuality = validateQuality(this.currentQuality);
    }
    
    /**
     * Constructor with default high quality support from SharedPreferences
     */
    public PlayerControlsManager(Context context, PlayerControlsCallback callback) {
        this(context, callback, DEFAULT_HIGH_QUALITY_SUPPORT);
    }
    
    /**
     * Shows the main settings dialog with quality, speed, and scale options
     */
    public void showSettingsDialog() {
        // Get display-friendly quality label
        String qualityDisplayName = getQualityDisplayName(currentQuality);
        
        customOptionsDialog = new CustomOptionsDialog(
            context,
            qualityDisplayName,
            SCALE_MODE_NAMES[currentScaleMode],
            currentPlaybackSpeed
        );
        
        customOptionsDialog.setOnOptionClickListener(new CustomOptionsDialog.OnOptionClickListener() {
            @Override
            public void onScreenScaleClicked(String currentValue) {
                showScaleDialog();
            }
            
            @Override
            public void onPlaySpeedClicked(String currentValue) {
                showSpeedDialog();
            }
            
            @Override
            public void onQualityClicked(String currentValue) {
                showQualityDialog();
            }
        });
        
        customOptionsDialog.show();
    }
    
    /**
     * Shows quality selection dialog based on quality support
     */
    private void showQualityDialog() {
        String[] qualityOptions = getAvailableQualityOptions();
        String[] qualityLabels = getAvailableQualityLabels();
        
        int selectedIndex = getQualityIndex(currentQuality);
        
        new AlertDialog.Builder(context)
            .setTitle("Select Video Quality")
            .setSingleChoiceItems(qualityLabels, selectedIndex, (dialog, which) -> {
                String selectedQuality = qualityOptions[which];
                if (!selectedQuality.equals(currentQuality)) {
                    currentQuality = selectedQuality;
                    
                    // Save to SharedPreferences
                    sharedPreferences.edit()
                        .putString(PREF_DEFAULT_QUALITY, selectedQuality)
                        .apply();
                    
                    callback.onQualityChanged(selectedQuality);
                    
                    String message = "Quality set to " + selectedQuality;
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                
                // Update main dialog if still showing
                if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
                    customOptionsDialog.dismiss();
                    showSettingsDialog();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Shows playback speed selection dialog
     */
    private void showSpeedDialog() {
        int selectedIndex = getSpeedIndex(currentPlaybackSpeed);
        
        new AlertDialog.Builder(context)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(SPEED_LABELS, selectedIndex, (dialog, which) -> {
                currentPlaybackSpeed = SPEED_VALUES[which];
                
                Player player = callback.getPlayer();
                if (player != null) {
                    player.setPlaybackSpeed(currentPlaybackSpeed);
                }
                
                callback.onPlaybackSpeedChanged(currentPlaybackSpeed);
                Toast.makeText(context, "Speed: " + SPEED_LABELS[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                
                // Update main dialog if still showing
                if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
                    customOptionsDialog.dismiss();
                    showSettingsDialog();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Shows scale mode selection dialog
     */
    private void showScaleDialog() {
        new AlertDialog.Builder(context)
            .setTitle("Video Scale Mode")
            .setSingleChoiceItems(SCALE_MODE_NAMES, currentScaleMode, (dialog, which) -> {
                currentScaleMode = which;
                callback.onScaleModeChanged(currentScaleMode);
                Toast.makeText(context, "Scale: " + SCALE_MODE_NAMES[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                
                // Update main dialog if still showing
                if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
                    customOptionsDialog.dismiss();
                    showSettingsDialog();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Gets available quality options based on support
     */
    private String[] getAvailableQualityOptions() {
        return supportsHighQuality ? QUALITY_OPTIONS_HIGH : QUALITY_OPTIONS_STANDARD;
    }
    
    /**
     * Gets available quality labels based on support
     */
    private String[] getAvailableQualityLabels() {
        return supportsHighQuality ? QUALITY_LABELS_HIGH : QUALITY_LABELS_STANDARD;
    }
    
    /**
     * Gets the index of the current quality in the options array
     * Returns 0 (144p) as default if quality not found
     */
    private int getQualityIndex(String quality) {
        if (quality == null || quality.isEmpty()) {
            return 0; // Default to 144p
        }
        
        String[] qualityOptions = getAvailableQualityOptions();
        for (int i = 0; i < qualityOptions.length; i++) {
            if (qualityOptions[i].equals(quality)) {
                return i;
            }
        }
        return 0; // Default to 144p if not found
    }
    
    /**
     * Gets the index of the current speed in the values array
     * Returns 3 (1.0x) as default if speed not found
     */
    private int getSpeedIndex(float speed) {
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(SPEED_VALUES[i] - speed) < 0.01f) {
                return i;
            }
        }
        return 3; // Default to 1.0x
    }
    
    /**
     * Gets a user-friendly display name for the quality setting
     */
    private String getQualityDisplayName(String quality) {
        if (quality == null || quality.isEmpty()) {
            return "144p (Low)";
        }
        
        int index = getQualityIndex(quality);
        String[] qualityLabels = getAvailableQualityLabels();
        return qualityLabels[index];
    }
    
    /**
     * Dismisses the custom options dialog if showing
     */
    public void dismissDialogs() {
        if (customOptionsDialog != null && customOptionsDialog.isShowing()) {
            customOptionsDialog.dismiss();
        }
    }
    
    /**
     * Validates and sanitizes quality value based on quality support
     */
    private String validateQuality(String quality) {
        if (quality == null || quality.isEmpty()) {
            return DEFAULT_QUALITY_VALUE; // Default to 720p
        }
        
        // Check if quality is valid for current support level
        String[] validQualities = getAvailableQualityOptions();
        for (String validQuality : validQualities) {
            if (validQuality.equals(quality)) {
                return quality;
            }
        }
        
        return DEFAULT_QUALITY_VALUE; // Return 720p if invalid
    }
    
    // Getters and setters for state management
    
    public String getCurrentQuality() {
        return currentQuality;
    }
    
    public void setCurrentQuality(String quality) {
        this.currentQuality = validateQuality(quality);
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putString(PREF_DEFAULT_QUALITY, this.currentQuality)
            .apply();
    }
    
    public int getCurrentScaleMode() {
        return currentScaleMode;
    }
    
    public void setCurrentScaleMode(int scaleMode) {
        // Validate scale mode
        if (scaleMode >= 0 && scaleMode < SCALE_MODE_NAMES.length) {
            this.currentScaleMode = scaleMode;
        } else {
            this.currentScaleMode = SCALE_MODE_FIT; // Default to Fit
        }
    }
    
    public float getCurrentPlaybackSpeed() {
        return currentPlaybackSpeed;
    }
    
    public void setCurrentPlaybackSpeed(float speed) {
        // Validate speed (must be positive and reasonable)
        if (speed > 0 && speed <= 2.0f) {
            this.currentPlaybackSpeed = speed;
        } else {
            this.currentPlaybackSpeed = 1.0f; // Default to normal speed
        }
    }
    
    public boolean isSupportsHighQuality() {
        return supportsHighQuality;
    }
    
    public void setSupportsHighQuality(boolean supportsHighQuality) {
        this.supportsHighQuality = supportsHighQuality;
        
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putBoolean(PREF_SUPPORTS_HIGH_QUALITY, supportsHighQuality)
            .apply();
        
        // Revalidate current quality in case it's no longer available
        String validatedQuality = validateQuality(this.currentQuality);
        if (!validatedQuality.equals(this.currentQuality)) {
            this.currentQuality = validatedQuality;
            sharedPreferences.edit()
                .putString(PREF_DEFAULT_QUALITY, this.currentQuality)
                .apply();
        }
    }
    
    // Static getters for scale mode constants
    
    public static int getScaleModeFit() {
        return SCALE_MODE_FIT;
    }
    
    public static int getScaleModeFill() {
        return SCALE_MODE_FILL;
    }
    
    public static int getScaleModeZoom() {
        return SCALE_MODE_ZOOM;
    }
    
    /**
     * Gets all available quality options based on support level
     */
    public String[] getQualityOptions() {
        return getAvailableQualityOptions().clone();
    }
    
    /**
     * Gets all quality labels for display based on support level
     */
    public String[] getQualityLabels() {
        return getAvailableQualityLabels().clone();
    }
    
    /**
     * Gets the default quality from SharedPreferences
     */
    public static String getDefaultQuality(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_DEFAULT_QUALITY, DEFAULT_QUALITY_VALUE);
    }
    
    /**
     * Gets the high quality support setting from SharedPreferences
     */
    public static boolean getDefaultHighQualitySupport(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SUPPORTS_HIGH_QUALITY, DEFAULT_HIGH_QUALITY_SUPPORT);
    }
    
    /**
     * Sets the default quality in SharedPreferences
     */
    public static void setDefaultQuality(Context context, String quality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_DEFAULT_QUALITY, quality).apply();
    }
    
    /**
     * Sets the high quality support in SharedPreferences
     */
    public static void setDefaultHighQualitySupport(Context context, boolean supportsHighQuality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SUPPORTS_HIGH_QUALITY, supportsHighQuality).apply();
    }
}