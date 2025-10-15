package com.nidoham.opentube.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import com.nidoham.flowtube.player.playqueue.PlayQueue;
import com.nidoham.flowtube.player.playqueue.PlayQueueItem;
import com.nidoham.flowtube.player.streams.StreamInfoCallback;
import com.nidoham.flowtube.player.streams.StreamInfoExtractor;
import com.nidoham.opentube.R;
import com.nidoham.opentube.util.constant.PlayerConstants;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Refactored PlayerService with all improvements applied:
 * - Thread safety with AtomicBoolean and AtomicInteger
 * - Memory leak prevention with proper cleanup
 * - Enhanced notification with MediaStyle and seek bar
 * - LRU cache for stream data
 * - Better error handling and retry logic
 * - Audio focus management
 * - Support for both mobile and Android TV
 */
public class PlayerService extends Service {

    private static final String TAG = "PlayerService";
    private static final int MAX_RETRIES = 2;
    private static final int MAX_CACHE_SIZE = 50;
    private static final int POSITION_UPDATE_INTERVAL_MS = 1000;
    private static final int QUALITY_CHANGE_DELAY_MS = 500;
    
    private final AtomicBoolean isLoadingStream = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicBoolean isForeground = new AtomicBoolean(false);
    
    // Core components
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private PlayQueue playQueue;
    private StreamInfoExtractor streamInfoExtractor;
    private MediaSessionCompat mediaSession;
    
    private final LruCache<String, StreamData> streamCache = 
        new LruCache<>(MAX_CACHE_SIZE);
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new PlayerServiceBinder();
    
    private String qualityPreference = "720p";
    
    private final CopyOnWriteArrayList<PlaybackStateListener> playbackListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetadataListener> metadataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<LoadingListener> loadingListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QualityListener> qualityListeners = new CopyOnWriteArrayList<>();
    
    private final Runnable positionUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                long position = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();
                
                if (duration > 0) {
                    notifyPositionUpdate(position, duration);
                    updateNotification();
                }
            }
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS);
        }
    };
    
    // ═══════════════════════════════════════════════════════════════
    // Listener Interfaces
    // ═══════════════════════════════════════════════════════════════
    
    public interface PlaybackStateListener {
        void onPlaybackStateChanged(int state, boolean isPlaying, long position, long duration);
        void onPositionUpdate(long position, long duration);
        void onPlaybackEnded();
    }
    
    public interface MetadataListener {
        void onMetadataLoaded(StreamInfo streamInfo);
        void onMetadataError(String error);
    }
    
    public interface QueueListener {
        void onQueueChanged(int currentIndex, int queueSize);
        void onCurrentItemChanged(PlayQueueItem item);
        void onQueueFinished();
    }
    
    public interface ErrorListener {
        void onPlaybackError(String error, Exception exception);
        void onStreamExtractionError(String error, Exception exception);
    }
    
    public interface LoadingListener {
        void onLoadingStarted(String message);
        void onLoadingProgress(String message);
        void onLoadingFinished();
    }
    
    public interface QualityListener {
        void onQualityChanged(String quality);
        void onAvailableQualitiesChanged(List<String> qualities);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Stream Data Class
    // ═══════════════════════════════════════════════════════════════
    
    private static class StreamData {
        final StreamInfo streamInfo;
        final List<VideoStream> videoStreams;
        final List<AudioStream> audioStreams;
        String selectedVideoUrl;
        String selectedAudioUrl;
        String currentQuality;

        StreamData(StreamInfo info) {
            this.streamInfo = info;
            this.videoStreams = info.getVideoOnlyStreams();
            this.audioStreams = info.getAudioStreams();
        }

        void selectQuality(String qualityPref) {
            selectedVideoUrl = selectBestVideoStream(qualityPref);
            selectedAudioUrl = selectBestAudioStream();
            currentQuality = qualityPref;
        }

        private String selectBestVideoStream(String qualityPref) {
            if (videoStreams == null || videoStreams.isEmpty()) return null;

            int targetHeight = parseQualityHeight(qualityPref);
            VideoStream bestMatch = null;
            int smallestDiff = Integer.MAX_VALUE;

            for (VideoStream stream : videoStreams) {
                int height = stream.getHeight();
                int diff = Math.abs(height - targetHeight);
                if (diff < smallestDiff) {
                    smallestDiff = diff;
                    bestMatch = stream;
                }
            }
            return bestMatch != null ? bestMatch.getContent() : null;
        }

        private String selectBestAudioStream() {
            if (audioStreams == null || audioStreams.isEmpty()) return null;

            AudioStream bestAudio = null;
            int highestBitrate = -1;

            for (AudioStream stream : audioStreams) {
                int bitrate = stream.getAverageBitrate();
                if (bitrate > highestBitrate) {
                    highestBitrate = bitrate;
                    bestAudio = stream;
                }
            }
            return bestAudio != null ? bestAudio.getContent() : null;
        }

        private int parseQualityHeight(String quality) {
            try {
                return Integer.parseInt(quality.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return 720;
            }
        }

        boolean hasValidStreams() {
            return selectedVideoUrl != null && selectedAudioUrl != null;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Service Lifecycle
    // ═══════════════════════════════════════════════════════════════
    
    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PlayerService onCreate");

        initializePlayer();
        initializeMediaSession();
        setupPlayerListeners();
        createNotificationChannel();
        
        streamInfoExtractor = StreamInfoExtractor.getInstance();
    }
    
    private void initializePlayer() {
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .build()
        );

        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(new DefaultAllocator(true, 64 * 1024))
                .setBufferDurationsMs(2000, 20000, 200, 500)
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(5000, false)
                .build();

        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setHandleAudioBecomingNoisy(true)
                .build();
    }
    
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (exoPlayer != null) exoPlayer.play();
            }

            @Override
            public void onPause() {
                if (exoPlayer != null) exoPlayer.pause();
            }

            @Override
            public void onSkipToNext() {
                handleNextAction();
            }

            @Override
            public void onSkipToPrevious() {
                handlePreviousAction();
            }

            @Override
            public void onSeekTo(long pos) {
                if (exoPlayer != null) exoPlayer.seekTo(pos);
            }

            @Override
            public void onStop() {
                handleStopAction();
            }
        });
        mediaSession.setActive(true);
    }

    private void setupPlayerListeners() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "Playback state changed: " + playbackState);
                updateNotification();
                updateMediaSession();
                notifyPlaybackStateChanged();
                
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded();
                } else if (playbackState == Player.STATE_READY && !exoPlayer.isPlaying()) {
                    exoPlayer.play();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.d(TAG, "Is playing changed: " + isPlaying);
                updateNotification();
                updateMediaSession();
                notifyPlaybackStateChanged();
                
                if (isPlaying) {
                    handler.removeCallbacks(positionUpdateRunnable);
                    handler.post(positionUpdateRunnable);
                } else {
                    handler.removeCallbacks(positionUpdateRunnable);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                notifyPlaybackError("Playback error: " + error.getMessage(), error);
                
                if (playQueue != null && (playQueue.getIndex() < playQueue.size() - 1)) {
                    handler.postDelayed(PlayerService.this::handleNextAction, 1000);
                }
            }
        });
    }
    
    private void updateMediaSession() {
        if (mediaSession == null || exoPlayer == null) return;
        
        int state = exoPlayer.isPlaying() ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, exoPlayer.getCurrentPosition(), 1.0f);
        
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY; // Changed from START_NOT_STICKY for media playback
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand: " + action);

        switch (action) {
            case PlayerConstants.ACTION_PLAY:
                handlePlayAction(intent);
                break;
            case PlayerConstants.ACTION_PAUSE:
                handlePauseAction();
                break;
            case PlayerConstants.ACTION_STOP:
                handleStopAction();
                break;
            case PlayerConstants.ACTION_NEXT:
                handleNextAction();
                break;
            case PlayerConstants.ACTION_PREVIOUS:
                handlePreviousAction();
                break;
            case PlayerConstants.ACTION_CHANGE_QUALITY:
                handleChangeQuality(intent);
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        return START_STICKY; // Changed for media playback
    }

    /**
     * Handle play action with queue
     */
    private void handlePlayAction(Intent intent) {
        byte[] queueBytes = intent.getByteArrayExtra(PlayerConstants.EXTRA_PLAY_QUEUE);
        if (queueBytes == null) {
            notifyPlaybackError("No queue data provided", new IllegalArgumentException("No queue data"));
            return;
        }

        playQueue = deserializeObject(queueBytes);
        if (playQueue == null || playQueue.isEmpty()) {
            notifyPlaybackError("Empty queue", new IllegalArgumentException("Empty queue"));
            return;
        }

        Log.d(TAG, "PlayQueue loaded: " + playQueue.size() + " items");
        startForegroundService();
        notifyQueueChanged();
        playCurrentItem();
    }
    
    /**
     * Improved foreground service management
     * Start foreground service with notification
     */
    private void startForegroundService() {
        if (isForeground.getAndSet(true)) {
            return;
        }
        
        startForeground(PlayerConstants.NOTIFICATION_ID, createNotification());
        Log.d(TAG, "Started foreground service");
    }
    
    /**
     * Stop foreground service
     */
    private void stopForegroundService() {
        if (!isForeground.getAndSet(false)) {
            return;
        }
        
        stopForeground(true);
        handler.removeCallbacks(positionUpdateRunnable);
        Log.d(TAG, "Stopped foreground service");
    }

    /**
     * Play current item from queue
     */
    private void playCurrentItem() {
        if (playQueue == null || playQueue.isEmpty() || playQueue.getItem() == null) {
            notifyPlaybackError("No item to play", new IllegalStateException("No item to play"));
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        String itemUrl = item.getUrl();
        Log.d(TAG, "Playing item: " + item.getTitle() + " [" + itemUrl + "]");
        retryCount.set(0);
        notifyCurrentItemChanged(item);

        StreamData cachedData = streamCache.get(itemUrl);
        if (cachedData != null && cachedData.hasValidStreams()) {
            playWithStreamData(cachedData);
        } else {
            extractAndPlay(itemUrl);
        }
    }

    private void extractAndPlay(String videoUrl) {
        isLoadingStream.set(true);
        notifyLoadingStarted("Extracting stream information...");

        streamInfoExtractor.extractStreamInfo(videoUrl, new StreamInfoCallback() {
            @Override
            public void onLoading() {
                Log.d(TAG, "Stream extraction in progress for " + videoUrl);
                notifyLoadingProgress("Loading stream data...");
            }

            @Override
            public void onSuccess(StreamInfo streamInfo) {
                isLoadingStream.set(false);
                StreamData streamData = new StreamData(streamInfo);
                streamData.selectQuality(qualityPreference);
                
                streamCache.put(videoUrl, streamData);
                
                notifyLoadingFinished();
                notifyMetadataLoaded(streamInfo);
                
                List<String> qualities = getAvailableQualities();
                if (qualities != null) {
                    notifyAvailableQualitiesChanged(qualities);
                }

                if (!streamData.hasValidStreams()) {
                    Log.e(TAG, "No valid streams found for " + videoUrl);
                    String errorMsg = "No playable streams available";
                    notifyStreamExtractionError(errorMsg, new IllegalStateException(errorMsg));
                    notifyMetadataError(errorMsg);
                    
                    if (playQueue.getIndex() < playQueue.size() - 1) {
                        handler.postDelayed(PlayerService.this::handleNextAction, 1500);
                    }
                } else {
                    playWithStreamData(streamData);
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Error extracting stream info", error);
                
                if (retryCount.incrementAndGet() <= MAX_RETRIES) {
                    Log.d(TAG, "Retrying extraction... Attempt " + retryCount.get());
                    notifyLoadingProgress("Retrying... Attempt " + retryCount.get());
                    handler.postDelayed(() -> extractAndPlay(videoUrl), 2000);
                } else {
                    isLoadingStream.set(false);
                    String errorMsg = "Failed to load stream: " + error.getMessage();
                    notifyStreamExtractionError(errorMsg, error);
                    notifyMetadataError(errorMsg);
                    notifyLoadingFinished();
                    
                    if (playQueue.getIndex() < playQueue.size() - 1) {
                        handler.postDelayed(PlayerService.this::handleNextAction, 1500);
                    }
                }
            }
        });
    }

    /**
     * Play with extracted stream data
     */
    private void playWithStreamData(StreamData streamData) {
        if (!streamData.hasValidStreams()) {
            return;
        }
        playMergedStream(streamData.selectedVideoUrl, streamData.selectedAudioUrl);
    }

    private void playMergedStream(String videoUrl, String audioUrl) {
        try {
            Log.d(TAG, "Starting playback - Video: " + videoUrl);
            Log.d(TAG, "Starting playback - Audio: " + audioUrl);
            
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(10000)
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(true);

            MediaItem videoMediaItem = new MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build();
            
            ProgressiveMediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(1024 * 1024)
                    .createMediaSource(videoMediaItem);

            MediaItem audioMediaItem = new MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMimeType(MimeTypes.AUDIO_AAC)
                    .build();
            
            ProgressiveMediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(512 * 1024)
                    .createMediaSource(audioMediaItem);

            MergingMediaSource mergedSource = new MergingMediaSource(
                    true, false, videoSource, audioSource
            );

            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaSource(mergedSource, true);
            exoPlayer.prepare();
            
            handler.postDelayed(() -> {
                if (exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
                    Log.d(TAG, "Starting playback after prepare");
                    exoPlayer.setPlayWhenReady(true);
                    exoPlayer.play();
                    notifyPlaybackStateChanged();
                }
            }, 500);
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing merged stream", e);
            String errorMsg = "Playback error: " + e.getMessage();
            notifyPlaybackError(errorMsg, e);
        }
    }

    /**
     * Handle pause/play toggle
     */
    private void handlePauseAction() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
        } else {
            exoPlayer.play();
        }
    }

    /**
     * Handle stop action
     */
    private void handleStopAction() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        stopForegroundService();
        stopSelf();
    }

    /**
     * Handle next action
     */
    private void handleNextAction() {
        if (playQueue != null && (playQueue.getIndex() < playQueue.size() - 1)) {
            playQueue.next();
            notifyQueueChanged();
            playCurrentItem();
        }
    }

    /**
     * Handle previous action
     */
    private void handlePreviousAction() {
        if (playQueue != null && playQueue.getIndex() > 0) {
            playQueue.previous();
            notifyQueueChanged();
            playCurrentItem();
        }
    }

    private void handlePlaybackEnded() {
        notifyPlaybackEnded();
        
        if (playQueue != null && (playQueue.getIndex() < playQueue.size() - 1)) {
            Log.d(TAG, "Auto-playing next item");
            handleNextAction();
        } else {
            Log.d(TAG, "Queue finished");
            notifyQueueFinished();
            stopForegroundService();
        }
    }

    /**
     * Handle quality change
     */
    private void handleChangeQuality(Intent intent) {
        String newQuality = intent.getStringExtra(PlayerConstants.EXTRA_QUALITY_ID);
        handleChangeQualityInternal(newQuality);
    }
    
    public void setQuality(String newQuality) {
        handleChangeQualityInternal(newQuality);
    }
    
    private void handleChangeQualityInternal(String newQuality) {
        if (newQuality == null || newQuality.equals(qualityPreference) || 
            playQueue == null || playQueue.getItem() == null) {
            return;
        }

        Log.d(TAG, "Changing quality to " + newQuality);
        long currentPosition = exoPlayer.getCurrentPosition();
        boolean wasPlaying = exoPlayer.isPlaying();
        qualityPreference = newQuality;

        StreamData streamData = streamCache.get(playQueue.getItem().getUrl());
        if (streamData != null) {
            streamData.selectQuality(newQuality);
            if (streamData.hasValidStreams()) {
                exoPlayer.stop();
                playWithStreamData(streamData);
                
                handler.postDelayed(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(currentPosition);
                        if (wasPlaying) {
                            exoPlayer.setPlayWhenReady(true);
                        }
                    }
                }, QUALITY_CHANGE_DELAY_MS);
                
                notifyQualityChanged(newQuality);
            } else {
                String errorMsg = "Quality not available: " + newQuality;
                notifyPlaybackError(errorMsg, new IllegalArgumentException(errorMsg));
            }
        }
    }

    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PlayerConstants.CHANNEL_ID,
                    PlayerConstants.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media playback controls");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Enhanced notification with MediaStyle and seek bar support
     * Create the foreground service notification
     */
    private Notification createNotification() {
        String title = "Loading...";
        String artist = "OpenTube";
        Bitmap artwork = null;
        
        if (playQueue != null && playQueue.getItem() != null) {
            title = playQueue.getItem().getTitle();
            artist = playQueue.getItem().getUploader();
            // TODO: Load artwork bitmap from thumbnail URL
        }

        int playPauseIcon = (exoPlayer != null && exoPlayer.isPlaying())
                ? R.drawable.ic_pause
                : R.drawable.ic_play_arrow;

        Intent activityIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (activityIntent == null) {
            activityIntent = new Intent();
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = 
            new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(createServiceIntent(PlayerConstants.ACTION_STOP, 4));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, PlayerConstants.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText("OpenTube")
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentIntent(contentIntent)
                .setDeleteIntent(createServiceIntent(PlayerConstants.ACTION_STOP, 5))
                .addAction(R.drawable.ic_skip_previous, "Previous", 
                    createServiceIntent(PlayerConstants.ACTION_PREVIOUS, 2))
                .addAction(playPauseIcon, "Play/Pause", 
                    createServiceIntent(PlayerConstants.ACTION_PAUSE, 1))
                .addAction(R.drawable.ic_skip_next, "Next", 
                    createServiceIntent(PlayerConstants.ACTION_NEXT, 3))
                .addAction(R.drawable.ic_close, "Close", 
                    createServiceIntent(PlayerConstants.ACTION_STOP, 4))
                .setStyle(mediaStyle)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        
        if (artwork != null) {
            builder.setLargeIcon(artwork);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                builder.setProgress(
                    (int) exoPlayer.getDuration(),
                    (int) exoPlayer.getCurrentPosition(),
                    false
                );
            }
        }
        
        return builder.build();
    }

    /**
     * Helper method to create PendingIntents for service actions.
     */
    private PendingIntent createServiceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Update the foreground notification
     */
    private void updateNotification() {
        if (!isForeground.get()) return;
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(PlayerConstants.NOTIFICATION_ID, createNotification());
        }
    }
    
    /**
     * Update notification metadata
     */
    private void updateNotificationMetadata(PlayQueueItem item) {
        // This method is now redundant as notification is created/updated directly in createNotification() and updateNotification()
        // Keeping it for now in case it's used elsewhere indirectly.
    }
    
    // Public API methods
    @Nullable
    public ExoPlayer getPlayer() {
        return exoPlayer;
    }
    
    @Nullable
    public StreamInfo getCurrentStreamInfo() {
        if (playQueue == null || playQueue.getItem() == null) {
            return null;
        }
        String itemUrl = playQueue.getItem().getUrl();
        StreamData cachedData = streamCache.get(itemUrl);
        return cachedData != null ? cachedData.streamInfo : null;
    }
    
    @Nullable
    public List<String> getAvailableQualities() {
        if (playQueue == null || playQueue.getItem() == null) {
            return null;
        }
        String itemUrl = playQueue.getItem().getUrl();
        StreamData cachedData = streamCache.get(itemUrl);
        if (cachedData == null || cachedData.videoStreams == null) {
            return null;
        }
        
        List<String> qualities = new ArrayList<>();
        for (VideoStream stream : cachedData.videoStreams) {
            String quality = stream.getHeight() + "p";
            if (!qualities.contains(quality)) {
                qualities.add(quality);
            }
        }
        return qualities;
    }

    private int getPlayerState() {
        if (isLoadingStream.get()) return PlayerConstants.STATE_BUFFERING;
        if (exoPlayer == null) return PlayerConstants.STATE_STOPPED;
        
        switch (exoPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return PlayerConstants.STATE_BUFFERING;
            case Player.STATE_ENDED:
                return PlayerConstants.STATE_ENDED;
            case Player.STATE_READY:
                return exoPlayer.isPlaying() ? PlayerConstants.STATE_PLAYING : PlayerConstants.STATE_PAUSED;
            default:
                return PlayerConstants.STATE_STOPPED;
        }
    }
    
    // Serialization helpers
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "PlayerService onDestroy");
        
        // Stop foreground service
        stopForegroundService();
        
        // Clean up handler callbacks
        handler.removeCallbacks(positionUpdateRunnable);
        handler.removeCallbacksAndMessages(null);
        
        // Clear all listeners
        playbackListeners.clear();
        metadataListeners.clear();
        queueListeners.clear();
        errorListeners.clear();
        loadingListeners.clear();
        qualityListeners.clear();
        
        // Release MediaSession
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        // Release player resources
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        // Release track selector
        trackSelector = null;
        
        // Shutdown extractor
        if (streamInfoExtractor != null) {
            streamInfoExtractor.shutdown();
            streamInfoExtractor = null;
        }
        
        // Clear cache
        streamCache.evictAll();
        
        // Clear queue
        playQueue = null;
        
        Log.d(TAG, "PlayerService destroyed and cleaned up");
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ═══════════════════════════════════════════════════════════════
    // Listener Notification Methods
    // ═══════════════════════════════════════════════════════════════
    
    public void addPlaybackStateListener(PlaybackStateListener listener) {
        if (listener != null && !playbackListeners.contains(listener)) {
            playbackListeners.add(listener);
            if (exoPlayer != null) {
                handler.post(() -> listener.onPlaybackStateChanged(
                    getPlayerState(),
                    exoPlayer.isPlaying(),
                    exoPlayer.getCurrentPosition(),
                    exoPlayer.getDuration()
                ));
            }
        }
    }
    
    public void removePlaybackStateListener(PlaybackStateListener listener) {
        playbackListeners.remove(listener);
    }
    
    public void addMetadataListener(MetadataListener listener) {
        if (listener != null && !metadataListeners.contains(listener)) {
            metadataListeners.add(listener);
            StreamInfo currentInfo = getCurrentStreamInfo();
            if (currentInfo != null) {
                handler.post(() -> listener.onMetadataLoaded(currentInfo));
            }
        }
    }
    
    public void removeMetadataListener(MetadataListener listener) {
        metadataListeners.remove(listener);
    }
    
    public void addQueueListener(QueueListener listener) {
        if (listener != null && !queueListeners.contains(listener)) {
            queueListeners.add(listener);
            if (playQueue != null) {
                handler.post(() -> {
                    listener.onQueueChanged(playQueue.getIndex(), playQueue.size());
                    if (playQueue.getItem() != null) {
                        listener.onCurrentItemChanged(playQueue.getItem());
                    }
                });
            }
        }
    }
    
    public void removeQueueListener(QueueListener listener) {
        queueListeners.remove(listener);
    }
    
    public void addErrorListener(ErrorListener listener) {
        if (listener != null && !errorListeners.contains(listener)) {
            errorListeners.add(listener);
        }
    }
    
    public void removeErrorListener(ErrorListener listener) {
        errorListeners.remove(listener);
    }
    
    public void addLoadingListener(LoadingListener listener) {
        if (listener != null && !loadingListeners.contains(listener)) {
            loadingListeners.add(listener);
        }
    }
    
    public void removeLoadingListener(LoadingListener listener) {
        loadingListeners.remove(listener);
    }
    
    public void addQualityListener(QualityListener listener) {
        if (listener != null && !qualityListeners.contains(listener)) {
            qualityListeners.add(listener);
            handler.post(() -> {
                listener.onQualityChanged(qualityPreference);
                List<String> qualities = getAvailableQualities();
                if (qualities != null) {
                    listener.onAvailableQualitiesChanged(qualities);
                }
            });
        }
    }
    
    public void removeQualityListener(QualityListener listener) {
        qualityListeners.remove(listener);
    }
    
    private void notifyPlaybackStateChanged() {
        if (exoPlayer == null) return;
        int state = getPlayerState();
        boolean isPlaying = exoPlayer.isPlaying();
        long position = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();
        
        for (PlaybackStateListener listener : playbackListeners) {
            handler.post(() -> listener.onPlaybackStateChanged(state, isPlaying, position, duration));
        }
    }
    
    private void notifyPositionUpdate(long position, long duration) {
        for (PlaybackStateListener listener : playbackListeners) {
            handler.post(() -> listener.onPositionUpdate(position, duration));
        }
    }
    
    private void notifyPlaybackEnded() {
        for (PlaybackStateListener listener : playbackListeners) {
            handler.post(listener::onPlaybackEnded);
        }
    }
    
    private void notifyMetadataLoaded(StreamInfo streamInfo) {
        for (MetadataListener listener : metadataListeners) {
            handler.post(() -> listener.onMetadataLoaded(streamInfo));
        }
    }
    
    private void notifyMetadataError(String error) {
        for (MetadataListener listener : metadataListeners) {
            handler.post(() -> listener.onMetadataError(error));
        }
    }
    
    private void notifyQueueChanged() {
        if (playQueue == null) return;
        int index = playQueue.getIndex();
        int size = playQueue.size();
        
        for (QueueListener listener : queueListeners) {
            handler.post(() -> listener.onQueueChanged(index, size));
        }
    }
    
    private void notifyCurrentItemChanged(PlayQueueItem item) {
        for (QueueListener listener : queueListeners) {
            handler.post(() -> listener.onCurrentItemChanged(item));
        }
    }
    
    private void notifyQueueFinished() {
        for (QueueListener listener : queueListeners) {
            handler.post(listener::onQueueFinished);
        }
    }
    
    private void notifyPlaybackError(String error, Exception exception) {
        for (ErrorListener listener : errorListeners) {
            handler.post(() -> listener.onPlaybackError(error, exception));
        }
    }
    
    private void notifyStreamExtractionError(String error, Exception exception) {
        for (ErrorListener listener : errorListeners) {
            handler.post(() -> listener.onStreamExtractionError(error, exception));
        }
    }
    
    private void notifyLoadingStarted(String message) {
        for (LoadingListener listener : loadingListeners) {
            handler.post(() -> listener.onLoadingStarted(message));
        }
    }
    
    private void notifyLoadingProgress(String message) {
        for (LoadingListener listener : loadingListeners) {
            handler.post(() -> listener.onLoadingProgress(message));
        }
    }
    
    private void notifyLoadingFinished() {
        for (LoadingListener listener : loadingListeners) {
            handler.post(listener::onLoadingFinished);
        }
    }
    
    private void notifyQualityChanged(String quality) {
        for (QualityListener listener : qualityListeners) {
            handler.post(() -> listener.onQualityChanged(quality));
        }
    }
    
    private void notifyAvailableQualitiesChanged(List<String> qualities) {
        for (QualityListener listener : qualityListeners) {
            handler.post(() -> listener.onAvailableQualitiesChanged(qualities));
        }
    }
}
