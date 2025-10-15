package com.nidoham.opentube.player;

import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * Optimized video scaling manager with thread-safe operations and memory leak prevention.
 * Manages video scaling modes with emphasis on proper fullscreen fit in landscape orientation.
 */
@UnstableApi
public class VideoScaleManager {
    
    @IntDef({SCALE_FIT, SCALE_FILL, SCALE_ZOOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleMode {}
    
    // Three essential scale modes for optimal user experience
    public static final int SCALE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public static final int SCALE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    public static final int SCALE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    
    private static final String TAG = "VideoScaleManager";
    
    private final WeakReference<PlayerView> playerViewRef;
    private final Handler mainHandler;
    
    @ScaleMode
    private int preferredScaleMode = SCALE_FIT; // User's preferred mode in portrait
    @ScaleMode
    private int currentScaleMode = SCALE_FIT; // Currently applied mode
    private boolean isLandscape = false;
    
    private volatile ScaleModeChangeListener listener;
    
    /**
     * Interface for listening to scale mode changes
     */
    public interface ScaleModeChangeListener {
        void onScaleModeChanged(@ScaleMode int newScaleMode);
    }
    
    /**
     * Configuration for VideoScaleManager
     */
    public static class Config {
        @ScaleMode
        private int defaultScaleMode = SCALE_FIT;
        private boolean autoZoomInLandscape = true;
        
        public Config setDefaultScaleMode(@ScaleMode int mode) {
            this.defaultScaleMode = mode;
            return this;
        }
        
        public Config setAutoZoomInLandscape(boolean autoZoom) {
            this.autoZoomInLandscape = autoZoom;
            return this;
        }
        
        public VideoScaleManager build(@NonNull PlayerView playerView) {
            return new VideoScaleManager(playerView, this);
        }
    }
    
    /**
     * Create a new VideoScaleManager with default configuration
     */
    public VideoScaleManager(@NonNull PlayerView playerView) {
        this(playerView, new Config());
    }
    
    /**
     * Create a new VideoScaleManager with custom configuration
     */
    private VideoScaleManager(@NonNull PlayerView playerView, @NonNull Config config) {
        this.playerViewRef = new WeakReference<>(playerView);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.preferredScaleMode = config.defaultScaleMode;
        this.currentScaleMode = config.defaultScaleMode;
        
        applyScaleModeInternal(config.defaultScaleMode);
    }
    
    /**
     * Create a builder for custom configuration
     */
    public static Config builder() {
        return new Config();
    }
    
    /**
     * Set listener for scale mode changes
     */
    public synchronized void setScaleModeChangeListener(@Nullable ScaleModeChangeListener listener) {
        this.listener = listener;
    }
    
    /**
     * Update orientation and apply appropriate scaling
     * Thread-safe method that can be called from any thread
     */
    public synchronized void updateOrientation(int orientation) {
        boolean wasLandscape = isLandscape;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        
        if (wasLandscape != isLandscape) {
            int newMode = getOptimalScaleMode();
            applyScaleModeInternal(newMode);
        }
    }
    
    /**
     * Toggle between available scale modes
     * Only effective in portrait mode; landscape always uses ZOOM
     */
    public synchronized void toggleScaleMode() {
        if (isLandscape) {
            // Don't allow scale mode changes in landscape
            return;
        }
        
        @ScaleMode int newMode;
        switch (preferredScaleMode) {
            case SCALE_FIT:
                newMode = SCALE_FILL;
                break;
            case SCALE_FILL:
                newMode = SCALE_ZOOM;
                break;
            case SCALE_ZOOM:
            default:
                newMode = SCALE_FIT;
                break;
        }
        
        setScaleMode(newMode);
    }
    
    /**
     * Set specific scale mode
     * Thread-safe method with validation
     */
    public synchronized void setScaleMode(@ScaleMode int scaleMode) {
        if (!isValidScaleMode(scaleMode)) {
            throw new IllegalArgumentException("Invalid scale mode: " + scaleMode);
        }
        
        // Store user preference (only matters in portrait)
        if (!isLandscape) {
            preferredScaleMode = scaleMode;
        }
        
        // In landscape, always use ZOOM regardless of preference
        @ScaleMode int effectiveMode = isLandscape ? SCALE_ZOOM : scaleMode;
        
        if (currentScaleMode != effectiveMode) {
            applyScaleModeInternal(effectiveMode);
        }
    }
    
    /**
     * Get current scale mode
     */
    @ScaleMode
    public synchronized int getCurrentScaleMode() {
        return currentScaleMode;
    }
    
    /**
     * Get optimal scale mode for current orientation
     */
    @ScaleMode
    public synchronized int getOptimalScaleMode() {
        return isLandscape ? SCALE_ZOOM : preferredScaleMode;
    }
    
    /**
     * Get scale mode name for display
     */
    @NonNull
    public static String getScaleModeName(@ScaleMode int scaleMode) {
        switch (scaleMode) {
            case SCALE_FIT:
                return "Fit";
            case SCALE_FILL:
                return "Fill";
            case SCALE_ZOOM:
                return "Zoom";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Get current scale mode name
     */
    @NonNull
    public synchronized String getCurrentScaleModeName() {
        return getScaleModeName(currentScaleMode);
    }
    
    /**
     * Check if in landscape mode
     */
    public synchronized boolean isLandscapeMode() {
        return isLandscape;
    }
    
    /**
     * Reset to default scale mode
     */
    public synchronized void resetToDefault() {
        preferredScaleMode = SCALE_FIT;
        setScaleMode(SCALE_FIT);
    }
    
    /**
     * Check if scale mode can be changed in current orientation
     */
    public synchronized boolean canChangeScaleMode() {
        return !isLandscape;
    }
    
    /**
     * Save current scale mode for state restoration
     */
    @ScaleMode
    public synchronized int saveState() {
        return preferredScaleMode;
    }
    
    /**
     * Restore saved scale mode
     */
    public synchronized void restoreState(@ScaleMode int savedScaleMode) {
        if (!isValidScaleMode(savedScaleMode)) {
            return;
        }
        
        preferredScaleMode = savedScaleMode;
        @ScaleMode int effectiveMode = getOptimalScaleMode();
        applyScaleModeInternal(effectiveMode);
    }
    
    /**
     * Force reapply current optimal scale mode
     * Useful after layout changes or configuration updates
     */
    public synchronized void reapplyScaleMode() {
        applyScaleModeInternal(getOptimalScaleMode());
    }
    
    /**
     * Clean up resources and prevent memory leaks
     */
    public synchronized void release() {
        listener = null;
        PlayerView playerView = playerViewRef.get();
        if (playerView != null) {
            playerViewRef.clear();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Internal method to apply scale mode with proper thread handling
     */
    private void applyScaleModeInternal(@ScaleMode int scaleMode) {
        currentScaleMode = scaleMode;
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyScaleModeToView(scaleMode);
        } else {
            mainHandler.post(() -> applyScaleModeToView(scaleMode));
        }
        
        notifyListener(scaleMode);
    }
    
    /**
     * Apply scale mode to PlayerView (must be called on main thread)
     */
    private void applyScaleModeToView(@ScaleMode int scaleMode) {
        PlayerView playerView = playerViewRef.get();
        if (playerView == null) {
            return;
        }
        
        // Set resize mode on PlayerView
        playerView.setResizeMode(scaleMode);
        
        if (isLandscape) {
            // Ensure content fills the screen completely in landscape
            playerView.setKeepContentOnPlayerReset(true);
            // Force layout update to apply changes immediately
            playerView.requestLayout();
        }
    }
    
    /**
     * Notify listener of scale mode change (thread-safe)
     */
    private void notifyListener(@ScaleMode int scaleMode) {
        ScaleModeChangeListener currentListener = listener;
        if (currentListener != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                currentListener.onScaleModeChanged(scaleMode);
            } else {
                mainHandler.post(() -> {
                    ScaleModeChangeListener listenerToNotify = listener;
                    if (listenerToNotify != null) {
                        listenerToNotify.onScaleModeChanged(scaleMode);
                    }
                });
            }
        }
    }
    
    /**
     * Validate scale mode value
     */
    private boolean isValidScaleMode(int scaleMode) {
        return scaleMode == SCALE_FIT || scaleMode == SCALE_FILL || scaleMode == SCALE_ZOOM;
    }
}
