package com.nidoham.opentube.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
 * Advanced video scaling manager similar to MX Player and YouTube.
 * Features:
 * - Multiple aspect ratios (Default, 1:1, 4:3, 16:9, 18:9, 21:9)
 * - Smart auto-fit in portrait (no black bars)
 * - Proper fullscreen in landscape (no black bars)
 * - Persistent user preferences
 */
@UnstableApi
public class VideoScaleManager {
    
    @IntDef({SCALE_DEFAULT, SCALE_FIT, SCALE_FILL, SCALE_ZOOM, SCALE_1_1, SCALE_4_3, SCALE_16_9, SCALE_18_9, SCALE_21_9})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleMode {}
    
    // Scale modes
    public static final int SCALE_DEFAULT = 0;  // Auto-fit (YouTube style)
    public static final int SCALE_FIT = 1;      // Fit with black bars
    public static final int SCALE_FILL = 2;     // Fill (crop if needed)
    public static final int SCALE_ZOOM = 3;     // Zoom to fill
    public static final int SCALE_1_1 = 4;      // 1:1 Square
    public static final int SCALE_4_3 = 5;      // 4:3 Classic
    public static final int SCALE_16_9 = 6;     // 16:9 Standard
    public static final int SCALE_18_9 = 7;     // 18:9 Tall
    public static final int SCALE_21_9 = 8;     // 21:9 Ultrawide
    
    private static final String TAG = "VideoScaleManager";
    private static final String PREFS_NAME = "video_scale_prefs";
    private static final String KEY_PORTRAIT_MODE = "portrait_scale_mode";
    private static final String KEY_LANDSCAPE_MODE = "landscape_scale_mode";
    
    private final WeakReference<PlayerView> playerViewRef;
    private final WeakReference<Context> contextRef;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    
    @ScaleMode
    private int portraitScaleMode = SCALE_DEFAULT;
    @ScaleMode
    private int landscapeScaleMode = SCALE_DEFAULT;
    @ScaleMode
    private int currentScaleMode = SCALE_DEFAULT;
    
    private boolean isLandscape = false;
    private volatile ScaleModeChangeListener listener;
    
    public interface ScaleModeChangeListener {
        void onScaleModeChanged(@ScaleMode int newScaleMode);
    }
    
    public VideoScaleManager(@NonNull PlayerView playerView) {
        this.playerViewRef = new WeakReference<>(playerView);
        this.contextRef = new WeakReference<>(playerView.getContext());
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = playerView.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        loadSavedPreferences();
        applyScaleModeInternal(getOptimalScaleMode());
    }
    
    private void loadSavedPreferences() {
        portraitScaleMode = prefs.getInt(KEY_PORTRAIT_MODE, SCALE_DEFAULT);
        landscapeScaleMode = prefs.getInt(KEY_LANDSCAPE_MODE, SCALE_DEFAULT);
        
        if (!isValidScaleMode(portraitScaleMode)) portraitScaleMode = SCALE_DEFAULT;
        if (!isValidScaleMode(landscapeScaleMode)) landscapeScaleMode = SCALE_DEFAULT;
    }
    
    private void savePreferences() {
        prefs.edit()
            .putInt(KEY_PORTRAIT_MODE, portraitScaleMode)
            .putInt(KEY_LANDSCAPE_MODE, landscapeScaleMode)
            .apply();
    }
    
    public synchronized void setScaleModeChangeListener(@Nullable ScaleModeChangeListener listener) {
        this.listener = listener;
    }
    
    public synchronized void updateOrientation(int orientation) {
        boolean wasLandscape = isLandscape;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        
        if (wasLandscape != isLandscape) {
            int newMode = getOptimalScaleMode();
            applyScaleModeInternal(newMode);
        }
    }
    
    /**
     * Toggle through all available scale modes
     */
    public synchronized void toggleScaleMode() {
        @ScaleMode int currentMode = isLandscape ? landscapeScaleMode : portraitScaleMode;
        @ScaleMode int newMode;
        
        switch (currentMode) {
            case SCALE_DEFAULT:
                newMode = SCALE_FIT;
                break;
            case SCALE_FIT:
                newMode = SCALE_FILL;
                break;
            case SCALE_FILL:
                newMode = SCALE_ZOOM;
                break;
            case SCALE_ZOOM:
                newMode = SCALE_16_9;
                break;
            case SCALE_16_9:
                newMode = SCALE_4_3;
                break;
            case SCALE_4_3:
                newMode = SCALE_1_1;
                break;
            case SCALE_1_1:
                newMode = SCALE_18_9;
                break;
            case SCALE_18_9:
                newMode = SCALE_21_9;
                break;
            case SCALE_21_9:
            default:
                newMode = SCALE_DEFAULT;
                break;
        }
        
        setScaleMode(newMode);
    }
    
    /**
     * Set specific scale mode for current orientation
     */
    public synchronized void setScaleMode(@ScaleMode int scaleMode) {
        if (!isValidScaleMode(scaleMode)) {
            Log.w(TAG, "Invalid scale mode: " + scaleMode);
            return;
        }
        
        if (isLandscape) {
            landscapeScaleMode = scaleMode;
        } else {
            portraitScaleMode = scaleMode;
        }
        
        savePreferences();
        applyScaleModeInternal(scaleMode);
    }
    
    @ScaleMode
    public synchronized int getCurrentScaleMode() {
        return currentScaleMode;
    }
    
    @ScaleMode
    public synchronized int getOptimalScaleMode() {
        return isLandscape ? landscapeScaleMode : portraitScaleMode;
    }
    
    /**
     * Get display name for scale mode
     */
    @NonNull
    public static String getScaleModeName(@ScaleMode int scaleMode) {
        switch (scaleMode) {
            case SCALE_DEFAULT:
                return "Default";
            case SCALE_FIT:
                return "Fit";
            case SCALE_FILL:
                return "Fill";
            case SCALE_ZOOM:
                return "Zoom";
            case SCALE_1_1:
                return "1:1";
            case SCALE_4_3:
                return "4:3";
            case SCALE_16_9:
                return "16:9";
            case SCALE_18_9:
                return "18:9";
            case SCALE_21_9:
                return "21:9";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Get all available scale modes
     */
    @NonNull
    public static int[] getAllScaleModes() {
        return new int[]{
            SCALE_DEFAULT,
            SCALE_FIT,
            SCALE_FILL,
            SCALE_ZOOM,
            SCALE_16_9,
            SCALE_4_3,
            SCALE_1_1,
            SCALE_18_9,
            SCALE_21_9
        };
    }
    
    @NonNull
    public synchronized String getCurrentScaleModeName() {
        return getScaleModeName(currentScaleMode);
    }
    
    public synchronized boolean isLandscapeMode() {
        return isLandscape;
    }
    
    public synchronized void resetToDefault() {
        portraitScaleMode = SCALE_DEFAULT;
        landscapeScaleMode = SCALE_DEFAULT;
        savePreferences();
        setScaleMode(SCALE_DEFAULT);
    }
    
    @ScaleMode
    public synchronized int saveState() {
        return isLandscape ? landscapeScaleMode : portraitScaleMode;
    }
    
    public synchronized void restoreState(@ScaleMode int savedScaleMode) {
        if (!isValidScaleMode(savedScaleMode)) {
            return;
        }
        
        if (isLandscape) {
            landscapeScaleMode = savedScaleMode;
        } else {
            portraitScaleMode = savedScaleMode;
        }
        
        savePreferences();
        applyScaleModeInternal(savedScaleMode);
    }
    
    public synchronized void reapplyScaleMode() {
        applyScaleModeInternal(getOptimalScaleMode());
    }
    
    public synchronized void release() {
        listener = null;
        PlayerView playerView = playerViewRef.get();
        if (playerView != null) {
            playerViewRef.clear();
        }
        Context context = contextRef.get();
        if (context != null) {
            contextRef.clear();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════
    
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
     * Apply scale mode to PlayerView with intelligent defaults
     */
    private void applyScaleModeToView(@ScaleMode int scaleMode) {
        PlayerView playerView = playerViewRef.get();
        if (playerView == null) {
            return;
        }
        
        int resizeMode;
        String aspectRatio = null;
        
        switch (scaleMode) {
            case SCALE_DEFAULT:
                // Smart default: Fill in landscape, Fit in portrait (no black bars)
                if (isLandscape) {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                } else {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                }
                break;
                
            case SCALE_FIT:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
                
            case SCALE_FILL:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
                
            case SCALE_ZOOM:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
                
            case SCALE_1_1:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                aspectRatio = "1:1";
                break;
                
            case SCALE_4_3:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                aspectRatio = "4:3";
                break;
                
            case SCALE_16_9:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                aspectRatio = "16:9";
                break;
                
            case SCALE_18_9:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                aspectRatio = "18:9";
                break;
                
            case SCALE_21_9:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                aspectRatio = "21:9";
                break;
                
            default:
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }
        
        playerView.setResizeMode(resizeMode);
        
        // Apply aspect ratio if specified
        if (aspectRatio != null) {
            String[] parts = aspectRatio.split(":");
            if (parts.length == 2) {
                try {
                    float width = Float.parseFloat(parts[0]);
                    float height = Float.parseFloat(parts[1]);
                    float ratio = width / height;
                    
                    // Set aspect ratio on the video surface
                    if (playerView.getVideoSurfaceView() instanceof AspectRatioFrameLayout) {
                        ((AspectRatioFrameLayout) playerView.getVideoSurfaceView())
                            .setAspectRatio(ratio);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid aspect ratio: " + aspectRatio, e);
                }
            }
        }
        
        // Ensure proper layout in landscape (no black bars at top)
        if (isLandscape) {
            playerView.setKeepContentOnPlayerReset(true);
            
            // Remove any padding that might cause black bars
            playerView.setPadding(0, 0, 0, 0);
        }
        
        playerView.requestLayout();
        
        Log.d(TAG, String.format("Applied scale mode: %s (resize=%d) in %s", 
            getScaleModeName(scaleMode), resizeMode, isLandscape ? "landscape" : "portrait"));
    }
    
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
    
    private boolean isValidScaleMode(int scaleMode) {
        return scaleMode >= SCALE_DEFAULT && scaleMode <= SCALE_21_9;
    }
}