package com.nidoham.opentube.player.managers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages ExoPlayer instance and playback operations.
 * Handles player lifecycle, state changes, and media source preparation.
 */
public class PlaybackManager {
    private static final String TAG = "PlaybackManager";
    
    private static final int MIN_BUFFER_MS = 2000;
    private static final int MAX_BUFFER_MS = 20000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 200;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500;
    private static final int BACK_BUFFER_MS = 5000;
    private static final int SEEK_INCREMENT_MS = 5000;
    
    private final Context context;
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isReleased = new AtomicBoolean(false);
    
    private PlaybackListener playbackListener;
    
    /**
     * Callback interface for playback events
     */
    public interface PlaybackListener {
        void onPlaybackStateChanged(int state, boolean isPlaying);
        void onPlayerError(PlaybackException error);
        void onPlaybackEnded();
        void onIsLoadingChanged(boolean isLoading);
    }
    
    public PlaybackManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Initialize ExoPlayer with optimized settings
     */
    public void initialize() {
        if (isInitialized.getAndSet(true)) {
            Log.w(TAG, "PlaybackManager already initialized");
            return;
        }
        
        trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .setMaxVideoSizeSd() // Default to SD for better performance
                .build()
        );
        
        LoadControl loadControl = new DefaultLoadControl.Builder()
            .setAllocator(new DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(-1) // Unlimited
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(BACK_BUFFER_MS, false)
            .build();
        
        exoPlayer = new ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setHandleAudioBecomingNoisy(true) // Handle audio focus
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keep CPU awake during playback
            .build();
        
        setupPlayerListeners();
        Log.d(TAG, "PlaybackManager initialized successfully");
    }
    
    private void setupPlayerListeners() {
        if (exoPlayer == null) return;
        
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(playbackState, exoPlayer.isPlaying());
                }
                
                if (playbackState == Player.STATE_ENDED && playbackListener != null) {
                    playbackListener.onPlaybackEnded();
                } else if (playbackState == Player.STATE_READY && !exoPlayer.isPlaying()) {
                    // Auto-play when ready
                    exoPlayer.play();
                }
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(
                        exoPlayer.getPlaybackState(), 
                        isPlaying
                    );
                }
            }
            
            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                if (playbackListener != null) {
                    playbackListener.onIsLoadingChanged(isLoading);
                }
            }
            
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                if (playbackListener != null) {
                    playbackListener.onPlayerError(error);
                }
            }
        });
    }
    
    /**
     * Play merged video and audio streams
     */
    public void playMergedStream(@NonNull String videoUrl, @NonNull String audioUrl) {
        if (exoPlayer == null || isReleased.get()) {
            Log.e(TAG, "Cannot play: Player not initialized or released");
            return;
        }
        
        try {
            Log.d(TAG, "Playing merged stream - Video: " + videoUrl);
            Log.d(TAG, "Playing merged stream - Audio: " + audioUrl);
            
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .setConnectTimeoutMs(15000) // Increased timeout
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);
            
            // Create video source
            MediaItem videoMediaItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build();
            
            ProgressiveMediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(1024 * 1024) // 1MB chunks
                .createMediaSource(videoMediaItem);
            
            // Create audio source
            MediaItem audioMediaItem = new MediaItem.Builder()
                .setUri(audioUrl)
                .setMimeType(MimeTypes.AUDIO_AAC)
                .build();
            
            ProgressiveMediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(512 * 1024) // 512KB chunks
                .createMediaSource(audioMediaItem);
            
            MergingMediaSource mergedSource = new MergingMediaSource(
                true,  // adjustPeriodTimeOffsets
                false, // clipDurations
                videoSource,
                audioSource
            );
            
            // Prepare and play
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaSource(mergedSource, true);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            Log.d(TAG, "Playback started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing merged stream", e);
            if (playbackListener != null) {
                playbackListener.onPlayerError(
                    new PlaybackException("Playback error: " + e.getMessage(), e, 
                        PlaybackException.ERROR_CODE_UNSPECIFIED)
                );
            }
        }
    }
    
    /**
     * Play/Pause toggle
     */
    public void togglePlayPause() {
        if (exoPlayer == null) return;
        
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
        } else {
            exoPlayer.play();
        }
    }
    
    /**
     * Seek to specific position
     */
    public void seekTo(long positionMs) {
        if (exoPlayer != null && !isReleased.get()) {
            exoPlayer.seekTo(positionMs);
        }
    }
    
    /**
     * Stop playback
     */
    public void stop() {
        if (exoPlayer != null && !isReleased.get()) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
    }
    
    /**
     * Set playback speed
     */
    public void setPlaybackSpeed(float speed) {
        if (exoPlayer != null && !isReleased.get()) {
            exoPlayer.setPlaybackSpeed(speed);
        }
    }
    
    // Getters
    @Nullable
    public ExoPlayer getPlayer() {
        return isReleased.get() ? null : exoPlayer;
    }
    
    public boolean isPlaying() {
        return exoPlayer != null && !isReleased.get() && exoPlayer.isPlaying();
    }
    
    public long getCurrentPosition() {
        return exoPlayer != null && !isReleased.get() ? exoPlayer.getCurrentPosition() : 0;
    }
    
    public long getDuration() {
        return exoPlayer != null && !isReleased.get() ? exoPlayer.getDuration() : 0;
    }
    
    public int getPlaybackState() {
        return exoPlayer != null && !isReleased.get() ? 
            exoPlayer.getPlaybackState() : Player.STATE_IDLE;
    }
    
    public void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }
    
    /**
     * Release all resources
     */
    public void release() {
        if (isReleased.getAndSet(true)) {
            Log.w(TAG, "PlaybackManager already released");
            return;
        }
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        trackSelector = null;
        playbackListener = null;
        isInitialized.set(false);
        
        Log.d(TAG, "PlaybackManager released");
    }
}
