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
import android.util.Log;
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

import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity
        implements PlayerControlsManager.PlayerControlsCallback,
        PlayerService.PlaybackStateListener,
        PlayerService.MetadataListener,
        PlayerService.QueueListener,
        PlayerService.ErrorListener,
        PlayerService.LoadingListener,
        PlayerService.QualityListener {

    private static final String TAG = "PlayerActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private static final int CONTROLS_AUTO_HIDE_DELAY = 5000;
    private static final int PROGRESS_UPDATE_INTERVAL = 500;

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
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlayerService playerService;
    private boolean isServiceBound = false;
    private boolean hasNotificationPermission = false;
    private boolean controlsVisible = true;
    private Player exoPlayer;

    private boolean isLandscape = false;
    private long savedPlaybackPosition = -1;
    private boolean isActivityResumed = false;

    private WindowInsetsControllerCompat windowInsetsController;
    private PlayerControlsManager controlsManager;
    private VideoScaleManager videoScaleManager;

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

        videoScaleManager = new VideoScaleManager(playerBinding.playerView);
        controlsManager = new PlayerControlsManager(this, this, videoScaleManager);

        videoScaleManager.setScaleModeChangeListener(scaleMode -> {
            // Listener for UI updates if needed in the future
        });

        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        checkNotificationPermission();
        setupPlayerUI();
        setupMetadataUI();
        setupControlsUI();
        setupPlayerControls();

        // Set initial quality from SharedPreferences
        String defaultQuality = controlsManager.getCurrentQuality();
        viewModel.setSelectedQuality(defaultQuality);

        observeViewModel();

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
    }
    
    // ... (setupWindowForFullscreen, setupInsetsController, hideSystemUI, and other initial setup methods remain unchanged) ...

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
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
    }

    private void setupInsetsController() {
        windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            hasNotificationPermission = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

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
                .setMessage("Notification permission is essential for background playback and controls.")
                .setPositiveButton("Grant", (d, w) -> checkNotificationPermission())
                .setNegativeButton("Exit", (d, w) -> {
                    Toast.makeText(this, "Cannot play without notification permission.", Toast.LENGTH_SHORT).show();
                    finish();
                })
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

        // Add scale mode button click listener
        playerBinding.btnCC.setOnClickListener(v -> {
            if (videoScaleManager != null) {
                videoScaleManager.toggleScaleMode();
                String modeName = videoScaleManager.getCurrentScaleModeName();
                Toast.makeText(this, "Scale: " + modeName, Toast.LENGTH_SHORT).show();
            }
        });

        playerBinding.videoProgress.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                if (exoPlayer != null) {
                    exoPlayer.seekTo(position);
                }
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                if (exoPlayer != null && !canceled) {
                    exoPlayer.seekTo(position);
                }
                if (controlsVisible) {
                    handler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY);
                }
            }
        });

        handler.post(updateProgressRunnable);
    }
    
    // ... (togglePlayPause, seekRelative, formatTime, toggleControls, showControls, hideControls methods remain unchanged) ...

    private void togglePlayPause() {
        if (exoPlayer == null) return;

        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
        } else {
            exoPlayer.play();
        }
    }

    private void seekRelative(long milliseconds) {
        if (exoPlayer == null) return;

        long currentPos = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();
        long newPos = Math.max(0, Math.min(duration, currentPos + milliseconds));
        exoPlayer.seekTo(newPos);
    }

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null) {
                long position = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();

                if (duration > 0) {
                    playerBinding.videoProgress.setPosition(position);
                    playerBinding.videoProgress.setDuration(duration);
                    playerBinding.txtCurrentTime.setText(formatTime(position));
                    playerBinding.txtTotalTime.setText(formatTime(duration));
                }

                playerBinding.btnPlayPause.setImageResource(
                        exoPlayer.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow
                );
            }

            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
        }
    };

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = millis / (1000 * 60 * 60);

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
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

    private final Runnable hideControlsRunnable = this::hideControls;

    private void toggleOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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

    /**
     * UPDATED enterFullscreenMode
     */
    private void enterFullscreenMode() {
        hideSystemUI();
        binding.contentScrollView.setVisibility(View.GONE);

        View playerSection = binding.playerSection.getRoot();
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerSection.getLayoutParams();
        // 16:9 অনুপাতটি সরিয়ে দিন যাতে এটি পুরো স্ক্রিন পূরণ করতে পারে
        params.dimensionRatio = null;
        params.width = 0; // Match constraints (parent)
        params.height = 0; // Match constraints (parent)
        // মূল অ্যাক্টিভিটির ConstraintLayout এর সাথে যুক্ত করুন
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        playerSection.setLayoutParams(params);

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }

        if (!controlsVisible) {
            showControls();
        }
    }

    /**
     * UPDATED exitFullscreenMode
     */
    private void exitFullscreenMode() {
        hideSystemUI();
        binding.contentScrollView.setVisibility(View.VISIBLE);

        View playerSection = binding.playerSection.getRoot();
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerSection.getLayoutParams();
        // 16:9 অনুপাতটি পুনরায় সেট করুন
        params.dimensionRatio = "H,16:9";
        params.width = 0; // Match constraints (parent)
        params.height = 0; // উচ্চতা এখন অনুপাত দ্বারা নির্ধারিত হবে
        // নিচের constraint টি সরিয়ে দিন
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        playerSection.setLayoutParams(params);

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }

        if (!controlsVisible) {
            showControls();
        }
    }

    // ... (handleSubscribe, handleLike, etc., and all ViewModel/Service related methods remain unchanged) ...
    // ... The rest of the class code follows without any changes ...

    private void handleSubscribe() {
        metadataBinding.btnSubscribe.setText("SUBSCRIBED");
        metadataBinding.btnSubscribe.setEnabled(false);
        Toast.makeText(this, "Subscribed", Toast.LENGTH_SHORT).show();
    }

    private void handleLike() {
        Toast.makeText(this, "Liked", Toast.LENGTH_SHORT).show();
        controlsBinding.btnLike.setImageTintList(ContextCompat.getColorStateList(this, R.color.seed));
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
        PlayQueueItem item = viewModel.getCurrentItem().getValue();
        if (item != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, item.getUrl());
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ViewModel Observers
    // ═══════════════════════════════════════════════════════════════

    private void observeViewModel() {
        viewModel.getCurrentItem().observe(this, this::updateCurrentItemUI);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getQueueFinished().observe(this, finished -> {
            if (finished) {
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            playerBinding.playerView.setShowBuffering(isLoading ?
                    PlayerView.SHOW_BUFFERING_ALWAYS : PlayerView.SHOW_BUFFERING_NEVER);
        });

        viewModel.getLoadingStatus().observe(this, status -> {
            if (status != null && !status.isEmpty()) {
                Log.d(TAG, "Loading status: " + status);
            }
        });

        viewModel.getSelectedQualityId().observe(this, quality -> {
            if (quality != null) {
                if (controlsManager != null) {
                    controlsManager.setCurrentQuality(quality);
                }
                Log.d(TAG, "Quality selected: " + quality);
            }
        });

        observeMetadata();
    }

    /**
     * Observe metadata LiveData from ViewModel
     */
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
        
        viewModel.getViewCount().observe(this, views -> {
            if(views != null) {
            	metadataBinding.txtMeta.setVisibility(View.VISIBLE);
                metadataBinding.txtMeta.setText(views+ "•views");
            }
        });
    }

    private void updateCurrentItemUI(@Nullable PlayQueueItem item) {
        if (item == null) return;

        if(item.getTitle() != null) {
            metadataBinding.txtTitle.setVisibility(View.VISIBLE);
            metadataBinding.txtTitle.setText(item.getTitle());
        } else {
            metadataBinding.txtTitle.setVisibility(View.VISIBLE);
            metadataBinding.txtTitle.setText("No Title");
        }

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
    }

    private String formatCount(long count) {
        if (count >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }

    @Override
    public void onQualityChanged(String quality) {
        // Manual quality selection
        viewModel.setSelectedQuality(quality);

        // Update service immediately
        if (playerService != null) {
            playerService.setQuality(quality);
        }
    }

    @Override
    public void onPlaybackSpeedChanged(float speed) {
        if (exoPlayer != null) {
            exoPlayer.setPlaybackSpeed(speed);
        }
    }

    @Override
    public Player getPlayer() {
        return exoPlayer;
    }

    // ═══════════════════════════════════════════════════════════════
    // Service Connection and Callback Listeners
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onPlaybackStateChanged(int state, boolean isPlaying, long position, long duration) {
        runOnUiThread(() -> {
            viewModel.updatePlaybackState(state, isPlaying, position, duration);
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
            Log.d(TAG, "Playback ended");
        });
    }

    @Override
    public void onMetadataLoaded(StreamInfo streamInfo) {
        runOnUiThread(() -> {
            Log.d(TAG, "Metadata loaded: " + streamInfo.getName());
            viewModel.updateMetadata(streamInfo);
        });
    }

    @Override
    public void onMetadataError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "Metadata error: " + error);
            viewModel.setError(error);
        });
    }

    @Override
    public void onQueueChanged(int currentIndex, int queueSize) {
        runOnUiThread(() -> {
            Log.d(TAG, "Queue: " + currentIndex + "/" + queueSize);
            viewModel.updateQueueInfo(currentIndex, queueSize);
        });
    }

    @Override
    public void onCurrentItemChanged(PlayQueueItem item) {
        runOnUiThread(() -> {
            viewModel.setCurrentItem(item);
        });
    }

    @Override
    public void onQueueFinished() {
        runOnUiThread(() -> {
            viewModel.setQueueFinished(true);
        });
    }

    @Override
    public void onPlaybackError(String error, Exception exception) {
        runOnUiThread(() -> {
            Log.e(TAG, "Playback error: " + error, exception);
            viewModel.setError(error);
        });
    }

    @Override
    public void onStreamExtractionError(String error, Exception exception) {
        runOnUiThread(() -> {
            Log.e(TAG, "Stream extraction error: " + error, exception);
            viewModel.setError(error);
        });
    }

    @Override
    public void onLoadingStarted(String message) {
        runOnUiThread(() -> {
            Log.d(TAG, "Loading started: " + message);
            viewModel.setLoadingMessage(message);
        });
    }

    @Override
    public void onLoadingProgress(String message) {
        runOnUiThread(() -> {
            Log.d(TAG, "Loading progress: " + message);
            viewModel.setLoadingMessage(message);
        });
    }

    @Override
    public void onLoadingFinished() {
        runOnUiThread(() -> {
            viewModel.setLoading(false);

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                viewModel.getStreamInfo().observe(this, info -> {
                    if (info != null) {
                        ThumbnailExtractor thumbnail = new ThumbnailExtractor(info.getUploaderAvatars());

                        Glide.with(this)
                                .load(thumbnail.getThumbnail())
                                .placeholder(R.drawable.ic_avatar_placeholder)
                                .into(metadataBinding.imgChannelAvatar);
                    }
                });
            }, 1500);
        });
    }

    @Override
    public void onAvailableQualitiesChanged(List<String> qualities) {
        runOnUiThread(() -> {
            Log.d(TAG, "Available qualities: " + qualities);
            viewModel.setAvailableQualities(qualities);
        });
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
                    if (playerBinding != null) {
                        playerBinding.playerView.setVisibility(View.VISIBLE);
                    }
                }, 400);

                exoPlayer.setPlaybackSpeed(controlsManager.getCurrentPlaybackSpeed());

                if (savedPlaybackPosition > 0) {
                    exoPlayer.seekTo(savedPlaybackPosition);
                    savedPlaybackPosition = -1;
                }

                showControls();
            } else {
                Toast.makeText(PlayerActivity.this, "Error initializing player.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (playerService != null) {
                playerService.removePlaybackStateListener(PlayerActivity.this);
                playerService.removeMetadataListener(PlayerActivity.this);
                playerService.removeQueueListener(PlayerActivity.this);
                playerService.removeErrorListener(PlayerActivity.this);
                playerService.removeLoadingListener(PlayerActivity.this);
                playerService.removeQualityListener(PlayerActivity.this);
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
            Toast.makeText(this, "Failed to start playback: Serialization error", Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void loadPlayQueueFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        try {
            if (intent.hasExtra("queue")) {
                playQueue = (PlayQueue) intent.getSerializableExtra("queue");
            }

            if (playQueue != null) {
                viewModel.setPlayQueue(playQueue);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading queue from intent", e);
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

            int scaleMode = savedInstanceState.getInt(KEY_SCALE_MODE, VideoScaleManager.SCALE_DEFAULT);
            String quality = savedInstanceState.getString(KEY_QUALITY, "720p");
            float speed = savedInstanceState.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
            savedPlaybackPosition = savedInstanceState.getLong(KEY_PLAYBACK_POSITION, -1);

            if (videoScaleManager != null) {
                videoScaleManager.restoreState(scaleMode);
            }

            // Restore quality
            viewModel.setSelectedQuality(quality);

            controlsManager.setCurrentPlaybackSpeed(speed);
        } catch (Exception e) {
            Log.e(TAG, "Error restoring state", e);
            loadPlayQueueFromIntent();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
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

        if (exoPlayer != null) {
            outState.putLong(KEY_PLAYBACK_POSITION, exoPlayer.getCurrentPosition());
        }

        if (videoScaleManager != null) {
            outState.putInt(KEY_SCALE_MODE, videoScaleManager.saveState());
        }

        // Save quality state
        String currentQuality = controlsManager != null ? controlsManager.getCurrentQuality() : "720p";
        outState.putString(KEY_QUALITY, currentQuality);

        float currentSpeed = controlsManager != null ? controlsManager.getCurrentPlaybackSpeed() : 1.0f;
        outState.putFloat(KEY_PLAYBACK_SPEED, currentSpeed);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacksAndMessages(null);

        if (controlsManager != null) {
            controlsManager.dismissDialogs();
        }

        if (videoScaleManager != null) {
            videoScaleManager.release();
            videoScaleManager = null;
        }

        if (isServiceBound) {
            if (playerService != null) {
                playerService.removePlaybackStateListener(this);
                playerService.removeMetadataListener(this);
                playerService.removeQueueListener(this);
                playerService.removeErrorListener(this);
                playerService.removeLoadingListener(this);
                playerService.removeQualityListener(this);
            }

            if (playerBinding != null && playerBinding.playerView != null) {
                playerBinding.playerView.setPlayer(null);
            }

            try {
                unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
            isServiceBound = false;
        }

        if (isFinishing()) {
            sendServiceAction(PlayerConstants.ACTION_STOP);
        }
    }

    private void handleBackPressed() {
        if (isLandscape) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            finish();
        }
    }

    @Deprecated
    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        isActivityResumed = true;

        hideSystemUI();

        if (exoPlayer != null && controlsManager != null) {
            exoPlayer.setPlaybackSpeed(controlsManager.getCurrentPlaybackSpeed());
        }

        if (videoScaleManager != null) {
            videoScaleManager.reapplyScaleMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        isActivityResumed = false;

        if (!controlsVisible) {
            showControls();
        }
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
        if (bytes == null) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "Deserialization error", e);
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
            Log.e(TAG, "Serialization error", e);
            return null;
        }
    }
}