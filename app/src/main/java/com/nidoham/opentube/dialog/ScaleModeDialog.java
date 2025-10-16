package com.nidoham.opentube.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.nidoham.opentube.R;
import com.nidoham.opentube.player.VideoScaleManager;

/**
 * Dialog for selecting video scale mode
 */
public class ScaleModeDialog {
    
    public interface ScaleModeListener {
        void onScaleModeSelected(@VideoScaleManager.ScaleMode int scaleMode);
    }
    
    /**
     * Show scale mode selection dialog
     */
    public static void show(@NonNull Context context, 
                           @VideoScaleManager.ScaleMode int currentMode,
                           @NonNull ScaleModeListener listener) {
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Video Scale Mode");
        
        // Get all available scale modes
        int[] scaleModes = VideoScaleManager.getAllScaleModes();
        String[] scaleNames = new String[scaleModes.length];
        String[] scaleDescriptions = new String[scaleModes.length];
        
        for (int i = 0; i < scaleModes.length; i++) {
            scaleNames[i] = VideoScaleManager.getScaleModeName(scaleModes[i]);
            scaleDescriptions[i] = getScaleModeDescription(scaleModes[i]);
        }
        
        // Find current selection index
        int currentIndex = 0;
        for (int i = 0; i < scaleModes.length; i++) {
            if (scaleModes[i] == currentMode) {
                currentIndex = i;
                break;
            }
        }
        
        final int[] selectedIndex = {currentIndex};
        
        builder.setSingleChoiceItems(scaleNames, currentIndex, (dialog, which) -> {
            selectedIndex[0] = which;
        });
        
        builder.setPositiveButton("Apply", (dialog, which) -> {
            listener.onScaleModeSelected(scaleModes[selectedIndex[0]]);
        });
        
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /**
     * Show enhanced scale mode dialog with descriptions
     */
    public static void showEnhanced(@NonNull Context context,
                                    @VideoScaleManager.ScaleMode int currentMode,
                                    boolean isLandscape,
                                    @NonNull ScaleModeListener listener) {
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        
        String title = isLandscape ? 
            "Landscape Scale Mode" : "Portrait Scale Mode";
        builder.setTitle(title);
        
        int[] scaleModes = VideoScaleManager.getAllScaleModes();
        String[] items = new String[scaleModes.length];
        
        for (int i = 0; i < scaleModes.length; i++) {
            String name = VideoScaleManager.getScaleModeName(scaleModes[i]);
            String desc = getScaleModeDescription(scaleModes[i]);
            items[i] = name + "\n" + desc;
        }
        
        int currentIndex = 0;
        for (int i = 0; i < scaleModes.length; i++) {
            if (scaleModes[i] == currentMode) {
                currentIndex = i;
                break;
            }
        }
        
        final int[] selectedIndex = {currentIndex};
        
        builder.setSingleChoiceItems(items, currentIndex, (dialog, which) -> {
            selectedIndex[0] = which;
        });
        
        builder.setPositiveButton("Apply", (dialog, which) -> {
            listener.onScaleModeSelected(scaleModes[selectedIndex[0]]);
        });
        
        builder.setNegativeButton("Cancel", null);
        
        builder.setNeutralButton("Reset to Default", (dialog, which) -> {
            listener.onScaleModeSelected(VideoScaleManager.SCALE_DEFAULT);
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /**
     * Get description for each scale mode
     */
    private static String getScaleModeDescription(@VideoScaleManager.ScaleMode int mode) {
        switch (mode) {
            case VideoScaleManager.SCALE_DEFAULT:
                return "Smart fit (recommended)";
            case VideoScaleManager.SCALE_FIT:
                return "Fit with black bars";
            case VideoScaleManager.SCALE_FILL:
                return "Fill screen, may stretch";
            case VideoScaleManager.SCALE_ZOOM:
                return "Zoom to fill, may crop";
            case VideoScaleManager.SCALE_1_1:
                return "Square aspect ratio";
            case VideoScaleManager.SCALE_4_3:
                return "Classic TV format";
            case VideoScaleManager.SCALE_16_9:
                return "Widescreen standard";
            case VideoScaleManager.SCALE_18_9:
                return "Tall phone display";
            case VideoScaleManager.SCALE_21_9:
                return "Ultrawide cinematic";
            default:
                return "";
        }
    }
}