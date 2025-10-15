package com.nidoham.opentube.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;

import com.nidoham.opentube.R;

public class CustomOptionsDialog extends Dialog {

    private OnOptionClickListener listener;
    private final String currentQuality;
    private final String currentScreenScale;
    private final String currentPlaySpeed;

    public interface OnOptionClickListener {
        void onScreenScaleClicked(String currentValue);
        void onPlaySpeedClicked(String currentValue);
        void onQualityClicked(String currentValue);
    }

    public CustomOptionsDialog(@NonNull Context context, String quality, String scaleMode, float speed) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.currentQuality = quality;
        this.currentScreenScale = scaleMode;
        this.currentPlaySpeed = speed + "x";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_custom_options);

        setupWindow();
        setupClickListeners();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (window == null) return;

        WindowManager.LayoutParams params = window.getAttributes();
        boolean isPortrait = getContext().getResources().getConfiguration().orientation 
                == Configuration.ORIENTATION_PORTRAIT;
        
        if (isPortrait) {
            // Compact top-right corner for portrait
            params.gravity = Gravity.TOP | Gravity.END;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.x = dpToPx(8); // 8dp margin from right
            params.y = dpToPx(8); // 8dp margin from top
        } else {
            // Compact right side for landscape
            params.gravity = Gravity.TOP | Gravity.END;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.x = dpToPx(8);
            params.y = dpToPx(8);
        }
        
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        
        window.setAttributes(params);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0.5f); // Less dim for mini dialog
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void setupClickListeners() {
        findViewById(R.id.optionScreenScale).setOnClickListener(v -> {
            if (listener != null) {
                listener.onScreenScaleClicked(currentScreenScale);
                dismiss();
            }
        });

        findViewById(R.id.optionPlaySpeed).setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaySpeedClicked(currentPlaySpeed);
                dismiss();
            }
        });

        findViewById(R.id.optionQualityChange).setOnClickListener(v -> {
            if (listener != null) {
                listener.onQualityClicked(currentQuality);
                dismiss();
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    public void setOnOptionClickListener(OnOptionClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void show() {
        super.show();
        // Add slide-in animation
        Window window = getWindow();
        if (window != null) {
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }
}