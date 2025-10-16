package com.nidoham.opentube.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

import com.bumptech.glide.Glide;
import com.nidoham.newpipe.image.ThumbnailExtractor;
import com.nidoham.opentube.R;
import com.nidoham.opentube.databinding.ActivityPlayerBinding;
import com.nidoham.opentube.databinding.IncludePlayerBinding;
import com.nidoham.opentube.databinding.IncludeMetadataBinding;
import com.nidoham.opentube.databinding.IncludePlayerControlsBinding;
import com.nidoham.opentube.util.constant.PlayerConstants;
import com.nidoham.opentube.util.UserManager;
import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/**
 * Optimized PlayerActivity - Production-ready video player
 * 
 * Features:
 * - Proper lifecycle management (stops on minimize, plays on resume)
 * - Memory leak prevention with WeakReference handlers
 * - Thread-safe service binding/unbinding
 * - Complete cleanup on app close
 * - No background playback (stops when minimized)
 * - Configuration change handling
 * - Immersive fullscreen experience
 */
public class PlayerActivity extends AppCompatActivity
        implements PlayerControlsManager.PlayerControlsCallback,
        PlayerService.PlaybackStateListener,
        PlayerService.MetadataListener,
        PlayerService.QueueListener,
        PlayerService.ErrorListener,
        PlayerService.LoadingListener,
        PlayerService.QualityListener {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private static final int CONTROLS_AUTO_HIDE_DELAY = 5000;
    private static final int PROGRESS_UPDATE_INTERVAL = 500;
    private static final int HISTORY_SAVE_INTERVAL = 30000;
    private static final long PLAYER_VISIBILITY_DELAY = 400;

    private static final String KEY_PLAY_QUEUE = "play_queue";
    private static final String KEY_SCALE_MODE = "scale_mode";
    private static final String KEY_QUALITY = "current_quality";
    private static final String KEY_PLAYBACK_SPEED = "playback_speed";
    private static final String KEY_PLAYBACK_POSITION = "playback_position";

    private ActivityPlayerBinding binding;
    private IncludePlayerBinding playerBinding;
    private IncludeMetadataBinding metadataBinding;
    private IncludePlayerControlsBinding controlsBinding;

    private PlayerViewModel viewModel;
    private PlayQueue playQueue;
    
    private volatile PlayerService playerService;
    private volatile Player exoPlayer;
    private volatile boolean isServiceBound = false;
    private volatile boolean hasNotificationPermission = false;
    private volatile boolean controlsVisible = true;
    private volatile boolean isLandscape = false;
    private volatile boolean isActivityVisible = false;
    private volatile boolean shouldStopOnPause = false;

    private long savedPlaybackPosition = -1;
    private long lastSavedPosition = 0;
    private long videoStartPosition = 0;
    private boolean hasTrackedVideoStart = false;

    private WindowInsetsControllerCompat windowInsetsController;
    private PlayerControlsManager controlsManager;
    private VideoScaleManager videoScaleManager;
    private UserManager userManager;
    
    private final MainThreadHandler handler = new MainThreadHandler(this);
    
    private static class MainThreadHandler extends Handler {
        private final WeakReference<PlayerActivity> activityRef;
        
        MainThreadHandler(PlayerActivity activity) {
            super(Looper.getMainLooper());
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Nullable
        PlayerActivity getActivity() {
            return activityRef.get();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindowForFullscreen();

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerBinding = IncludePlayerBinding.bind(binding.playerSection.getRoot());
        metadataBinding = IncludeMetadataBinding.bind(binding.videoMetadata.getRoot());
        controlsBinding = IncludePlayerControlsBinding.bind(binding.playerControls.getRoot());

        setupInsetsController();
        hideSystemUI();

        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        userManager = UserManager.getInstance(this);
        
        videoScaleManager = new VideoScaleManager(playerBinding.playerView);
        controlsManager = new PlayerControlsManager(this, this, videoScaleManager);

        ensureUserAuthentication();
        checkNotificationPermission();
        
        setupPlayerUI();
        setupMetadataUI();
        setupControlsUI();
        setupPlayerControls();
        observeViewModel();

        String defaultQuality = controlsManager.getCurrentQuality();
        viewModel.setSelectedQuality(defaultQuality);

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            loadPlayQueueFromIntent();
        }

        if (playQueue == null || playQueue.isEmpty()) {
            Toast.makeText(this, "No video to play", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startAndBindPlayerService();
        }

        updateOrientation(getResources().getConfiguration().orientation);
        
        handler.postDelayed(historySaveRunnable, HISTORY_SAVE_INTERVAL);
    }

    private void setupWindowForFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
    }

    private void setupInsetsController() {
        windowInsetsController = WindowCompat.getInsetsController(
            getWindow(), 
            getWindow().getDecorView()
        );
        
        if (windowInsetsController != null) {
            windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            windowInsetsController.setAppearanceLightStatusBars(false);
            windowInsetsController.setAppearanceLightNavigationBars(false);
        }
    }

    private void hideSystemUI() {
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars());
            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    private void ensureUserAuthentication() {
        if (!userManager.isLoggedIn()) {
            userManager.signInAnonymously(new UserManager.AuthCallback() {
                @Override
                public void onSuccess(@NonNull String userId) {
                }

                @Override
                public void onFailure(@NonNull Exception e) {
                }
            });
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                hasNotificationPermission = true;
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
                );
            }
        } else {
            hasNotificationPermission = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            hasNotificationPermission = grantResults.length > 0 && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (hasNotificationPermission) {
                if (playQueue != null && !playQueue.isEmpty()) {
                    startAndBindPlayerService();
                }
            } else {
                showPermissionDialog();
            }
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Notification permission is required for playback controls.")
            .setPositiveButton("Grant", (d, w) -> checkNotificationPermission())
            .setNegativeButton("Exit", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void setupPlayerUI() {
        playerBinding.playerView.setVisibility(View.GONE);
        playerBinding.playerView.setControllerAutoShow(false);
        playerBinding.playerView.setControllerHideOnTouch(false);
        playerBinding.playerView.setResizeMode(videoScaleManager.getCurrentScaleMode());
        playerBinding.playerView.setUseController(false);
        playerBinding.controlsOverlay.setVisibility(View.VISIBLE);
        playerBinding.playerView.setOnClickListener(v -> toggleControls());
        playerBinding.btnSettings.setOnClickListener(v -> controlsManager.showSettingsDialog());
    }

    private void setupMetadataUI() {
        metadataBinding.btnSubscribe.setOnClickListener(v -> handleSubscribe());
    }

    private void setupControlsUI() {
        controlsBinding.btnLike.setOnClickListener(v -> handleLike());
        controlsBinding.btnDislike.setOnClickListener(v -> handleDislike());
        controlsBinding.btnShare.setOnClickListener(v -> shareVideo());
        controlsBinding.btnDownload.setOnClickListener(v -> handleDownload());
        controlsBinding.btnSave.setOnClickListener(v -> handleSave());
    }

    private void setupPlayerControls() {
        playerBinding.btnBack.setOnClickListener(v -> handleBackPressed());
        playerBinding.btnPlayPause.setOnClickListener(v -> togglePlayPause());
        playerBinding.btnPrevious.setOnClickListener(v -> seekRelative(-10000));
        playerBinding.btnNext.setOnClickListener(v -> seekRelative(10000));
        playerBinding.btnOrientation.setOnClickListener(v -> toggleOrientation());
        playerBinding.btnCC.setOnClickListener(v -> {
            if (videoScaleManager != null) {
                videoScaleManager.toggleScaleMode();
                Toast.makeText(this, "Scale: " + videoScaleManager.getCurrentScaleModeName(), 
                    Toast.LENGTH_SHORT).show();
            }
        });

        playerBinding.videoProgress.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                Player player = exoPlayer;
                if (player != null) {
                    player.seekTo(position);
                }
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                Player player = exoPlayer;
                if (player != null && !canceled) {
                    player.seekTo(position);
                }
                if (controlsVisible) {
                    handler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY);
                }
            }
        });

        handler.post(updateProgressRunnable);
    }

    private final UpdateProgressRunnable updateProgressRunnable = new UpdateProgressRunnable(this);
    private final HideControlsRunnable hideControlsRunnable = new HideControlsRunnable(this);
    private final HistorySaveRunnable historySaveRunnable = new HistorySaveRunnable(this);
    
    private static class UpdateProgressRunnable implements Runnable {
        private final WeakReference<PlayerActivity> activityRef;
        
        UpdateProgressRunnable(PlayerActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void run() {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;
            
            Player player = activity.exoPlayer;
            if (player != null) {
                long position = player.getCurrentPosition();
                long duration = player.getDuration();

                if (duration > 0) {
                    activity.playerBinding.videoProgress.setPosition(position);
                    activity.playerBinding.videoProgress.setDuration(duration);
                    activity.playerBinding.txtCurrentTime.setText(formatTime(position));
                    activity.playerBinding.txtTotalTime.setText(formatTime(duration));
                }

                activity.playerBinding.btnPlayPause.setImageResource(
                    player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow
                );
            }

            activity.handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
        }
        
        private String formatTime(long millis) {
            long seconds = (millis / 1000) % 60;
            long minutes = (millis / (1000 * 60)) % 60;
            long hours = millis / (1000 * 60 * 60);

            if (hours > 0) {
                return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format(Locale.US, "%d:%02d", minutes, seconds);
            }
        }
    }
    
    private static class HideControlsRunnable implements Runnable {
        private final WeakReference<PlayerActivity> activityRef;
        
        HideControlsRunnable(PlayerActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void run() {
            PlayerActivity activity = activityRef.get();
            if (activity != null) {
                activity.hideControls();
            }
        }
    }
    
    private static class HistorySaveRunnable implements Runnable {
        private final WeakReference<PlayerActivity> activityRef;
        
        HistorySaveRunnable(PlayerActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void run() {
            PlayerActivity activity = activityRef.get();
            if (activity == null) return;
            
            Player player = activity.exoPlayer;
            if (player != null && player.isPlaying()) {
                activity.saveVideoProgress();
            }
            activity.handler.postDelayed(this, HISTORY_SAVE_INTERVAL);
        }
    }

    private void togglePlayPause() {
        Player player = exoPlayer;
        if (player == null) return;

        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
            if (!hasTrackedVideoStart) {
                trackVideoStart();
            }
        }
    }

    private void seekRelative(long milliseconds) {
        Player player = exoPlayer;
        if (player == null) return;

        long currentPos = player.getCurrentPosition();
        long duration = player.getDuration();
        long newPos = Math.max(0, Math.min(duration, currentPos + milliseconds));
        player.seekTo(newPos);
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        playerBinding.controlsOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .withStartAction(() -> playerBinding.controlsOverlay.setVisibility(View.VISIBLE))
            .start();
        controlsVisible = true;

        if (isLandscape && windowInsetsController != null) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }

        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY);
    }

    private void hideControls() {
        playerBinding.controlsOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> playerBinding.controlsOverlay.setVisibility(View.GONE))
            .start();
        controlsVisible = false;

        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        }

        hideSystemUI();
    }

    private void toggleOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateOrientation(newConfig.orientation);
    }

    private void updateOrientation(int orientation) {
        boolean wasLandscape = isLandscape;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);

        videoScaleManager.updateOrientation(orientation);

        if (isLandscape) {
            enterFullscreenMode();
        } else {
            exitFullscreenMode();
        }

        if (wasLandscape != isLandscape) {
            if (controlsVisible) {
                showControls();
            } else {
                hideControls();
            }
        }

        hideSystemUI();
    }

    private void enterFullscreenMode() {
        hideSystemUI();
        binding.contentScrollView.setVisibility(View.GONE);

        View playerSection = binding.playerSection.getRoot();
        ConstraintLayout.LayoutParams params = 
            (ConstraintLayout.LayoutParams) playerSection.getLayoutParams();
        params.dimensionRatio = null;
        params.width = 0;
        params.height = 0;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        playerSection.setLayoutParams(params);

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }

        if (!controlsVisible) {
            showControls();
        }
    }

    private void exitFullscreenMode() {
        hideSystemUI();
        binding.contentScrollView.setVisibility(View.VISIBLE);

        View playerSection = binding.playerSection.getRoot();
        ConstraintLayout.LayoutParams params = 
            (ConstraintLayout.LayoutParams) playerSection.getLayoutParams();
        params.dimensionRatio = "H,16:9";
        params.width = 0;
        params.height = 0;
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        playerSection.setLayoutParams(params);

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }

        if (!controlsVisible) {
            showControls();
        }
    }

    private void handleSubscribe() {
        metadataBinding.btnSubscribe.setText("SUBSCRIBED");
        metadataBinding.btnSubscribe.setEnabled(false);
        Toast.makeText(this, "Subscribed", Toast.LENGTH_SHORT).show();
    }

    private void handleLike() {
        Toast.makeText(this, "Liked", Toast.LENGTH_SHORT).show();
        controlsBinding.btnLike.setImageTintList(
            ContextCompat.getColorStateList(this, R.color.seed));
    }

    private void handleDislike() {
        Toast.makeText(this, "Disliked", Toast.LENGTH_SHORT).show();
    }

    private void handleDownload() {
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    private void handleSave() {
        Toast.makeText(this, "Saved to playlist", Toast.LENGTH_SHORT).show();
    }

    private void shareVideo() {
        PlayQueueItem item = viewModel.getCurrentItemValue();
        if (item != null && item.getUrl() != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, item.getUrl());
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        }
    }

    private void trackVideoStart() {
        if (hasTrackedVideoStart) return;
        
        PlayQueueItem currentItem = playQueue != null ? playQueue.getItem() : null;
        if (currentItem == null) return;

        Player player = exoPlayer;
        videoStartPosition = player != null ? player.getCurrentPosition() : 0;
        hasTrackedVideoStart = true;
    }

    private void trackVideoCompletion() {
        PlayQueueItem currentItem = playQueue != null ? playQueue.getItem() : null;
        Player player = exoPlayer;
        if (currentItem == null || player == null || !hasTrackedVideoStart) return;

        long watchedDuration = player.getCurrentPosition() - videoStartPosition;
    }

    private void saveVideoProgress() {
        PlayQueueItem currentItem = playQueue != null ? playQueue.getItem() : null;
        Player player = exoPlayer;
        if (currentItem == null || player == null) return;

        long currentPosition = player.getCurrentPosition();
        
        if (Math.abs(currentPosition - lastSavedPosition) < 5000) {
            return;
        }

        lastSavedPosition = currentPosition;
    }

    private void observeViewModel() {
        viewModel.getCurrentItem().observe(this, this::updateCurrentItemUI);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getQueueFinished().observe(this, finished -> {
            if (finished != null && finished) {
                if (hasTrackedVideoStart) {
                    trackVideoCompletion();
                }
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            playerBinding.playerView.setShowBuffering(
                Boolean.TRUE.equals(isLoading) ? 
                    PlayerView.SHOW_BUFFERING_ALWAYS : PlayerView.SHOW_BUFFERING_NEVER
            );
        });

        viewModel.getSelectedQualityId().observe(this, quality -> {
            if (quality != null && controlsManager != null) {
                controlsManager.setCurrentQuality(quality);
            }
        });

        observeMetadata();
    }

    private void observeMetadata() {
        viewModel.getVideoTitle().observe(this, title -> {
            if (title != null && !title.isEmpty()) {
                metadataBinding.txtTitle.setText(title);
                metadataBinding.txtTitle.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getUploaderName().observe(this, uploader -> {
            if (uploader != null && !uploader.isEmpty()) {
                metadataBinding.txtChannelName.setText(uploader);
            }
        });
        
        viewModel.getFormattedViewCount().observe(this, viewCount -> {
            if (viewCount != null && !viewCount.equals("0 views")) {
                metadataBinding.txtMeta.setVisibility(View.VISIBLE);
                metadataBinding.txtMeta.setText(viewCount);
            }
        });
    }

    private void updateCurrentItemUI(@Nullable PlayQueueItem item) {
        if (item == null) return;

        String title = item.getTitle();
        metadataBinding.txtTitle.setVisibility(View.VISIBLE);
        metadataBinding.txtTitle.setText(title != null ? title : "No Title");

        String uploader = item.getUploader();
        if (uploader != null && !uploader.isEmpty()) {
            metadataBinding.txtMeta.setText(uploader);
            metadataBinding.txtChannelName.setText(uploader);
            metadataBinding.txtMeta.setVisibility(View.VISIBLE);
        } else {
            metadataBinding.txtMeta.setVisibility(View.GONE);
        }

        Glide.with(this)
            .load(R.drawable.ic_avatar_placeholder)
            .circleCrop()
            .into(metadataBinding.imgChannelAvatar);
                
        hasTrackedVideoStart = false;
        videoStartPosition = 0;
        lastSavedPosition = 0;
    }

    @Override
    public void onQualityChanged(String quality) {
        viewModel.setSelectedQuality(quality);

        PlayerService service = playerService;
        if (service != null) {
            service.setQuality(quality);
        }
    }

    @Override
    public void onPlaybackSpeedChanged(float speed) {
        Player player = exoPlayer;
        if (player != null) {
            player.setPlaybackSpeed(speed);
        }
    }

    @Override
    public Player getPlayer() {
        return exoPlayer;
    }

    @Override
    public void onPlaybackStateChanged(int state, boolean isPlaying, long position, long duration) {
        runOnUiThread(() -> {
            viewModel.updatePlaybackState(state, isPlaying, position, duration);
            
            if (isPlaying && !hasTrackedVideoStart) {
                trackVideoStart();
            }
        });
    }

    @Override
    public void onPositionUpdate(long position, long duration) {
        runOnUiThread(() -> {
            viewModel.updatePosition(position);
            viewModel.updateDuration(duration);
        });
    }

    @Override
    public void onPlaybackEnded() {
        runOnUiThread(() -> {
            if (hasTrackedVideoStart) {
                trackVideoCompletion();
            }
        });
    }

    @Override
    public void onMetadataLoaded(StreamInfo streamInfo) {
        runOnUiThread(() -> viewModel.updateMetadata(streamInfo));
    }

    @Override
    public void onMetadataError(String error) {
        runOnUiThread(() -> viewModel.setError(error));
    }

    @Override
    public void onQueueChanged(int currentIndex, int queueSize) {
        runOnUiThread(() -> viewModel.updateQueueInfo(currentIndex, queueSize));
    }

    @Override
    public void onCurrentItemChanged(PlayQueueItem item) {
        runOnUiThread(() -> {
            viewModel.setCurrentItem(item);
            
            if (hasTrackedVideoStart && exoPlayer != null) {
                trackVideoCompletion();
            }
            
            hasTrackedVideoStart = false;
            videoStartPosition = 0;
            lastSavedPosition = 0;
        });
    }

    @Override
    public void onQueueFinished() {
        runOnUiThread(() -> {
            viewModel.setQueueFinished(true);
            if (hasTrackedVideoStart) {
                trackVideoCompletion();
            }
        });
    }

    @Override
    public void onPlaybackError(String error, Exception exception) {
        runOnUiThread(() -> viewModel.setError(error));
    }

    @Override
    public void onStreamExtractionError(String error, Exception exception) {
        runOnUiThread(() -> viewModel.setError(error));
    }

    @Override
    public void onLoadingStarted(String message) {
        runOnUiThread(() -> viewModel.setLoadingMessage(message));
    }

    @Override
    public void onLoadingProgress(String message) {
        runOnUiThread(() -> viewModel.setLoadingMessage(message));
    }

    @Override
    public void onLoadingFinished() {
        runOnUiThread(() -> {
            viewModel.setLoading(false);

            handler.postDelayed(() -> {
                StreamInfo info = viewModel.getStreamInfoValue();
                if (info != null && info.getUploaderAvatars() != null) {
                    ThumbnailExtractor thumbnail = 
                        new ThumbnailExtractor(info.getUploaderAvatars());

                    Glide.with(this)
                        .load(thumbnail.getThumbnail())
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .circleCrop()
                        .into(metadataBinding.imgChannelAvatar);
                }
            }, 1500);
        });
    }

    @Override
    public void onAvailableQualitiesChanged(List<String> qualities) {
        runOnUiThread(() -> viewModel.setAvailableQualities(qualities));
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.PlayerServiceBinder binder = (PlayerService.PlayerServiceBinder) service;
            playerService = binder.getService();
            isServiceBound = true;

            playerService.addPlaybackStateListener(PlayerActivity.this);
            playerService.addMetadataListener(PlayerActivity.this);
            playerService.addQueueListener(PlayerActivity.this);
            playerService.addErrorListener(PlayerActivity.this);
            playerService.addLoadingListener(PlayerActivity.this);
            playerService.addQualityListener(PlayerActivity.this);

            StreamInfo currentStreamInfo = playerService.getCurrentStreamInfo();
            if (currentStreamInfo != null) {
                viewModel.updateMetadata(currentStreamInfo);
            }

            List<String> qualities = playerService.getAvailableQualities();
            if (qualities != null) {
                viewModel.setAvailableQualities(qualities);
            }

            exoPlayer = playerService.getPlayer();
            if (exoPlayer != null) {
                playerBinding.playerView.setPlayer(exoPlayer);

                handler.postDelayed(() -> {
                    if (playerBinding != null && playerBinding.playerView != null) {
                        playerBinding.playerView.setVisibility(View.VISIBLE);
                    }
                }, PLAYER_VISIBILITY_DELAY);

                exoPlayer.setPlaybackSpeed(controlsManager.getCurrentPlaybackSpeed());

                if (savedPlaybackPosition > 0) {
                    exoPlayer.seekTo(savedPlaybackPosition);
                    savedPlaybackPosition = -1;
                }

                showControls();
            } else {
                Toast.makeText(PlayerActivity.this, 
                    "Error initializing player", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            PlayerService service = playerService;
            if (service != null) {
                service.removePlaybackStateListener(PlayerActivity.this);
                service.removeMetadataListener(PlayerActivity.this);
                service.removeQueueListener(PlayerActivity.this);
                service.removeErrorListener(PlayerActivity.this);
                service.removeLoadingListener(PlayerActivity.this);
                service.removeQualityListener(PlayerActivity.this);
            }

            isServiceBound = false;
            playerService = null;
            exoPlayer = null;
        }
    };

    private void startAndBindPlayerService() {
        if (playQueue == null || playQueue.isEmpty()) return;

        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerConstants.ACTION_PLAY);

        byte[] queueBytes = serializeObject(playQueue);
        if (queueBytes == null) {
            Toast.makeText(this, "Failed to start playback", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.putExtra(PlayerConstants.EXTRA_PLAY_QUEUE, queueBytes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void sendServiceAction(String action) {
        if (action == null) return;
        
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPlayerService() {
        sendServiceAction(PlayerConstants.ACTION_STOP);
    }

    private void loadPlayQueueFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        try {
            if (intent.hasExtra("queue")) {
                Object queue = intent.getSerializableExtra("queue");
                if (queue instanceof PlayQueue) {
                    playQueue = (PlayQueue) queue;
                    viewModel.setPlayQueue(playQueue);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        try {
            byte[] bytes = savedInstanceState.getByteArray(KEY_PLAY_QUEUE);
            if (bytes != null) {
                playQueue = deserializeObject(bytes);
                if (playQueue != null) {
                    viewModel.setPlayQueue(playQueue);
                }
            }

            int scaleMode = savedInstanceState.getInt(KEY_SCALE_MODE, 
                VideoScaleManager.SCALE_DEFAULT);
            String quality = savedInstanceState.getString(KEY_QUALITY, "720p");
            float speed = savedInstanceState.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
            savedPlaybackPosition = savedInstanceState.getLong(KEY_PLAYBACK_POSITION, -1);

            if (videoScaleManager != null) {
                videoScaleManager.restoreState(scaleMode);
            }

            viewModel.setSelectedQuality(quality);
            controlsManager.setCurrentPlaybackSpeed(speed);
        } catch (Exception e) {
            loadPlayQueueFromIntent();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityVisible = true;
        shouldStopOnPause = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityVisible = true;
        shouldStopOnPause = false;

        hideSystemUI();

        Player player = exoPlayer;
        if (player != null && controlsManager != null) {
            player.setPlaybackSpeed(controlsManager.getCurrentPlaybackSpeed());
        }

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }
        
        if (savedPlaybackPosition > 0 && player != null) {
            player.seekTo(savedPlaybackPosition);
            player.play();
            savedPlaybackPosition = -1;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;

        Player player = exoPlayer;
        if (player != null) {
            savedPlaybackPosition = player.getCurrentPosition();
            
            if (hasTrackedVideoStart) {
                saveVideoProgress();
            }
            
            if (player.isPlaying()) {
                player.pause();
            }
        }

        if (!controlsVisible) {
            showControls();
        }
        
        shouldStopOnPause = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActivityVisible = false;
        
        Player player = exoPlayer;
        if (player != null && hasTrackedVideoStart) {
            saveVideoProgress();
        }
        
        if (shouldStopOnPause && !isFinishing() && !isChangingConfigurations()) {
            stopPlayerService();
            unbindPlayerService();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (playQueue != null) {
            byte[] bytes = serializeObject(playQueue);
            if (bytes != null) {
                outState.putByteArray(KEY_PLAY_QUEUE, bytes);
            }
        }

        Player player = exoPlayer;
        if (player != null) {
            outState.putLong(KEY_PLAYBACK_POSITION, player.getCurrentPosition());
        }

        if (videoScaleManager != null) {
            outState.putInt(KEY_SCALE_MODE, videoScaleManager.saveState());
        }

        String currentQuality = controlsManager != null ? 
            controlsManager.getCurrentQuality() : "720p";
        outState.putString(KEY_QUALITY, currentQuality);

        float currentSpeed = controlsManager != null ? 
            controlsManager.getCurrentPlaybackSpeed() : 1.0f;
        outState.putFloat(KEY_PLAYBACK_SPEED, currentSpeed);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        
        if (hasTrackedVideoStart && exoPlayer != null) {
            trackVideoCompletion();
        }

        if (controlsManager != null) {
            controlsManager.dismissDialogs();
            controlsManager = null;
        }

        if (videoScaleManager != null) {
            videoScaleManager.release();
            videoScaleManager = null;
        }

        if (isServiceBound) {
            unbindPlayerService();
        }

        if (isFinishing()) {
            stopPlayerService();
        }

        super.onDestroy();
    }

    private void unbindPlayerService() {
        if (!isServiceBound) return;
        
        PlayerService service = playerService;
        if (service != null) {
            service.removePlaybackStateListener(this);
            service.removeMetadataListener(this);
            service.removeQueueListener(this);
            service.removeErrorListener(this);
            service.removeLoadingListener(this);
            service.removeQualityListener(this);
        }

        if (playerBinding != null && playerBinding.playerView != null) {
            playerBinding.playerView.setPlayer(null);
        }

        try {
            unbindService(serviceConnection);
        } catch (Exception ignored) {
        }
        
        isServiceBound = false;
        playerService = null;
        exoPlayer = null;
    }

    private void handleBackPressed() {
        if (isLandscape) {
            setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            if (hasTrackedVideoStart) {
                saveVideoProgress();
            }
            finish();
        }
    }

    @Deprecated
    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            hideSystemUI();

            if (isLandscape && videoScaleManager != null) {
                handler.postDelayed(() -> {
                    if (playerBinding != null && playerBinding.playerView != null) {
                        ViewGroup.LayoutParams params = playerBinding.playerView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        playerBinding.playerView.setLayoutParams(params);
                        playerBinding.playerView.requestLayout();
                    }
                    if (videoScaleManager != null) {
                        videoScaleManager.reapplyScaleMode();
                    }
                }, 100);
            }
        }
    }

    @Nullable
    private <T extends Serializable> T deserializeObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            Object obj = ois.readObject();
            return (T) obj;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private <T extends Serializable> byte[] serializeObject(T object) {
        if (object == null) return null;
        
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}