package com.nidoham.opentube.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;


import com.nidoham.flowtube.player.playqueue.PlayQueue;
import com.nidoham.flowtube.player.streams.StreamInfoExtractor;
import com.nidoham.flowtube.player.playqueue.PlayQueueItem;
import com.nidoham.flowtube.player.streams.StreamInfoCallback;
import com.nidoham.newpipe.image.ThumbnailExtractor;
import com.nidoham.opentube.R;
import com.nidoham.opentube.util.constant.PlayerConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

public class PlayerService extends Service {

    private static final String TAG = "PlayerService";

    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private PlayQueue playQueue;
    private StreamInfoExtractor streamInfoExtractor;

    private final ConcurrentHashMap<String, StreamData> streamCache = new ConcurrentHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new PlayerServiceBinder();

    private String qualityPreference = "720p";
    private volatile boolean isLoadingStream = false;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    
    // Live metadata callback interface
    public interface MetadataUpdateListener {
        void onMetadataLoaded(StreamInfo streamInfo);
        void onMetadataLoadingProgress(String status);
        void onMetadataError(String error);
    }
    
    private MetadataUpdateListener metadataListener;

    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }
    
    // Methods to set and remove metadata listener
    public void setMetadataUpdateListener(MetadataUpdateListener listener) {
        this.metadataListener = listener;
    }
    
    public void removeMetadataUpdateListener() {
        this.metadataListener = null;
    }
    
    // Get current stream info for the playing item
    @Nullable
    public StreamInfo getCurrentStreamInfo() {
        if (playQueue == null || playQueue.getItem() == null) {
            return null;
        }
        String itemUrl = playQueue.getItem().getUrl();
        StreamData cachedData = streamCache.get(itemUrl);
        return cachedData != null ? cachedData.streamInfo : null;
    }
    
    // Get available qualities for current stream
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

    @Nullable
    public ExoPlayer getPlayer() {
        return exoPlayer;
    }

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
                return 720; // Default
            }
        }

        boolean hasValidStreams() {
            return selectedVideoUrl != null && selectedAudioUrl != null;
        }
    }

    
    /**
     * Callback interface for playback state changes
     */
    public interface PlaybackStateListener {
        void onPlaybackStateChanged(int state, boolean isPlaying, long position, long duration);
        void onPositionUpdate(long position, long duration);
        void onPlaybackEnded();
    }
    
    /**
     * Callback interface for metadata updates
     */
    public interface MetadataListener {
        void onMetadataLoaded(StreamInfo streamInfo);
        void onMetadataError(String error);
    }
    
    /**
     * Callback interface for queue changes
     */
    public interface QueueListener {
        void onQueueChanged(int currentIndex, int queueSize);
        void onCurrentItemChanged(PlayQueueItem item);
        void onQueueFinished();
    }
    
    /**
     * Callback interface for errors
     */
    public interface ErrorListener {
        void onPlaybackError(String error, Exception exception);
        void onStreamExtractionError(String error, Exception exception);
    }
    
    /**
     * Callback interface for loading status
     */
    public interface LoadingListener {
        void onLoadingStarted(String message);
        void onLoadingProgress(String message);
        void onLoadingFinished();
    }
    
    /**
     * Callback interface for quality changes
     */
    public interface QualityListener {
        void onQualityChanged(String quality);
        void onAvailableQualitiesChanged(List<String> qualities);
    }
    
    // Listener storage
    private final CopyOnWriteArrayList<PlaybackStateListener> playbackListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetadataListener> metadataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<LoadingListener> loadingListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QualityListener> qualityListeners = new CopyOnWriteArrayList<>();
    
    // Position update runnable
    private final Runnable positionUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                long position = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();
                if (duration > 0) {
                    notifyPositionUpdate(position, duration);
                }
            }
            handler.postDelayed(this, 1000); // Update every second
        }
    };
    
    
    public void addPlaybackStateListener(PlaybackStateListener listener) {
        if (listener != null && !playbackListeners.contains(listener)) {
            playbackListeners.add(listener);
            // Immediately notify with current state
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
            // Immediately notify with current metadata if available
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
            // Immediately notify with current queue state
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
            // Immediately notify with current quality
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PlayerService onCreate");

        // Initialize track selector with proper configuration
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .build()
        );

        // Configure LoadControl with aggressive buffering for smooth playback
        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(new DefaultAllocator(true, 64 * 1024)) // বেশি allocator size
                .setBufferDurationsMs(
                    2000,  // minBufferMs - একটু বাড়ানো
                    20000, // maxBufferMs - বেশি buffer
                    200,   // bufferForPlaybackMs - আরো কম!
                    500    // bufferForPlaybackAfterRebufferMs - কম রাখা
                )
                .setTargetBufferBytes(-1) // Unlimited
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(5000, false) // 5 second back buffer
                .build();

        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setHandleAudioBecomingNoisy(true) // Audio focus handling
                .build();
        
        streamInfoExtractor = StreamInfoExtractor.getInstance();
        setupPlayerListeners();
        createNotificationChannel();
    }

    private void setupPlayerListeners() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "Playback state changed: " + playbackState);
                updateNotification();
                broadcastStateUpdate();
                notifyPlaybackStateChanged();
                
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded();
                } else if (playbackState == Player.STATE_READY && !exoPlayer.isPlaying()) {
                    // Auto-play when ready
                    exoPlayer.play();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.d(TAG, "Is playing changed: " + isPlaying);
                updateNotification();
                broadcastStateUpdate();
                notifyPlaybackStateChanged();
                
                if (isPlaying) {
                    handler.removeCallbacks(positionUpdateRunnable);
                    handler.post(positionUpdateRunnable);
                } else {
                    handler.removeCallbacks(positionUpdateRunnable);
                }
            }
            
            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                Log.d(TAG, "Loading changed: " + isLoading + " at position: " + exoPlayer.getCurrentPosition());
                // Log করছি কখন loading হচ্ছে
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                broadcastStateUpdate(null, "Playback error: " + error.getMessage(), false, false);
                notifyPlaybackError("Playback error: " + error.getMessage(), error);
                
                if (playQueue != null && (playQueue.getIndex() < playQueue.size() - 1)) {
                    handler.postDelayed(PlayerService.this::handleNextAction, 1000);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
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

        return START_NOT_STICKY;
    }

    private void handlePlayAction(Intent intent) {
        byte[] queueBytes = intent.getByteArrayExtra(PlayerConstants.EXTRA_PLAY_QUEUE);
        if (queueBytes == null) {
            broadcastStateUpdate(null, "No queue data provided", false, false);
            notifyPlaybackError("No queue data provided", new IllegalArgumentException("No queue data"));
            return;
        }

        playQueue = deserializeObject(queueBytes);
        if (playQueue == null || playQueue.isEmpty()) {
            broadcastStateUpdate(null, "Empty queue", false, false);
            notifyPlaybackError("Empty queue", new IllegalArgumentException("Empty queue"));
            return;
        }

        Log.d(TAG, "PlayQueue loaded: " + playQueue.size() + " items");
        startForeground(PlayerConstants.NOTIFICATION_ID, createNotification());
        notifyQueueChanged();
        playCurrentItem();
    }

    private void playCurrentItem() {
        if (playQueue == null || playQueue.isEmpty() || playQueue.getItem() == null) {
            broadcastStateUpdate(null, "No item to play", false, false);
            notifyPlaybackError("No item to play", new IllegalStateException("No item to play"));
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        String itemUrl = item.getUrl();
        Log.d(TAG, "Playing item: " + item.getTitle() + " [" + itemUrl + "]");
        retryCount = 0;
        broadcastStateUpdate(item, null, false, false);
        notifyCurrentItemChanged(item);

        StreamData cachedData = streamCache.get(itemUrl);
        if (cachedData != null && cachedData.hasValidStreams()) {
            playWithStreamData(cachedData);
        } else {
            extractAndPlay(itemUrl);
        }
    }

    private void extractAndPlay(String videoUrl) {
        isLoadingStream = true;
        broadcastStateUpdate(null, null, true, false);
        notifyLoadingStarted("Extracting stream information...");
        
        if (metadataListener != null) {
            handler.post(() -> metadataListener.onMetadataLoadingProgress("Extracting stream information..."));
        }

        streamInfoExtractor.extractStreamInfo(videoUrl, new StreamInfoCallback() {
            @Override
            public void onLoading() {
                Log.d(TAG, "Stream extraction in progress for " + videoUrl);
                notifyLoadingProgress("Loading stream data...");
                
                if (metadataListener != null) {
                    handler.post(() -> metadataListener.onMetadataLoadingProgress("Loading stream data..."));
                }
            }

            @Override
            public void onSuccess(StreamInfo streamInfo) {
                isLoadingStream = false;
                StreamData streamData = new StreamData(streamInfo);
                streamData.selectQuality(qualityPreference);
                streamCache.put(videoUrl, streamData);
                
                notifyLoadingFinished();
                
                notifyMetadataLoaded(streamInfo);
                
                if (metadataListener != null) {
                    handler.post(() -> metadataListener.onMetadataLoaded(streamInfo));
                }
                
                broadcastMetadataUpdate(streamInfo);
                
                List<String> qualities = getAvailableQualities();
                if (qualities != null) {
                    notifyAvailableQualitiesChanged(qualities);
                }

                if (!streamData.hasValidStreams()) {
                    Log.e(TAG, "No valid streams found for " + videoUrl);
                    String errorMsg = "No playable streams available";
                    broadcastStateUpdate(null, errorMsg, false, false);
                    notifyStreamExtractionError(errorMsg, new IllegalStateException(errorMsg));
                    notifyMetadataError(errorMsg);
                    
                    if (metadataListener != null) {
                        handler.post(() -> metadataListener.onMetadataError(errorMsg));
                    }
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
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    Log.d(TAG, "Retrying extraction... Attempt " + retryCount);
                    notifyLoadingProgress("Retrying... Attempt " + retryCount);
                    
                    if (metadataListener != null) {
                        handler.post(() -> metadataListener.onMetadataLoadingProgress("Retrying... Attempt " + retryCount));
                    }
                    handler.postDelayed(() -> extractAndPlay(videoUrl), 2000);
                } else {
                    isLoadingStream = false;
                    String errorMsg = "Failed to load stream: " + error.getMessage();
                    broadcastStateUpdate(null, errorMsg, false, false);
                    notifyStreamExtractionError(errorMsg, error);
                    notifyMetadataError(errorMsg);
                    notifyLoadingFinished();
                    
                    if (metadataListener != null) {
                        handler.post(() -> metadataListener.onMetadataError(errorMsg));
                    }
                    if (playQueue.getIndex() < playQueue.size() - 1) {
                        handler.postDelayed(PlayerService.this::handleNextAction, 1500);
                    }
                }
            }
        });
    }

    private void playWithStreamData(StreamData streamData) {
        if (!streamData.hasValidStreams()) {
            broadcastStateUpdate(null, "Invalid stream data", false, false);
            return;
        }
        playMergedStream(streamData.selectedVideoUrl, streamData.selectedAudioUrl);
    }

    private void playMergedStream(String videoUrl, String audioUrl) {
        try {
            Log.d(TAG, "Starting playback - Video: " + videoUrl);
            Log.d(TAG, "Starting playback - Audio: " + audioUrl);
            
            // Configure DataSource with optimized settings and larger buffer
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(10000)
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(true);

            // Create MediaItem with proper MIME types for MPEG4 video
            MediaItem videoMediaItem = new MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build();
            
            ProgressiveMediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(1024 * 1024) // 1MB chunks
                    .createMediaSource(videoMediaItem);

            // Create MediaItem with proper MIME types for M4A audio
            MediaItem audioMediaItem = new MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMimeType(MimeTypes.AUDIO_AAC)
                    .build();
            
            ProgressiveMediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(512 * 1024) // 512KB chunks for audio
                    .createMediaSource(audioMediaItem);

            // Merge video and audio sources
            MergingMediaSource mergedSource = new MergingMediaSource(
                    /* adjustPeriodTimeOffsets= */ true,
                    /* clipDurations= */ false, // false করা হয়েছে
                    videoSource, 
                    audioSource
            );

            // Clear and prepare player
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaSource(mergedSource, /* resetPosition= */ true);
            
            // Prepare without auto-play first
            exoPlayer.prepare();
            
            // Wait for player to be ready, then start
            handler.postDelayed(() -> {
                if (exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
                    Log.d(TAG, "Starting playback after prepare");
                    exoPlayer.setPlayWhenReady(true);
                    exoPlayer.play();
                    broadcastStateUpdate(null, null, false, false);
                    notifyPlaybackStateChanged();
                }
            }, 500); // 500ms delay বাড়ানো হয়েছে
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing merged stream", e);
            String errorMsg = "Playback error: " + e.getMessage();
            broadcastStateUpdate(null, errorMsg, false, false);
            notifyPlaybackError(errorMsg, e);
        }
    }

    private void handlePauseAction() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
        } else {
            exoPlayer.play();
        }
    }

    private void handleStopAction() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        stopForeground(true);
        stopSelf();
    }

    private void handleNextAction() {
        if (playQueue != null && (playQueue.getIndex() < playQueue.size() - 1)) {
            playQueue.next();
            notifyQueueChanged();
            playCurrentItem();
        }
    }

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
            broadcastStateUpdate(null, null, false, true);
            notifyQueueFinished();
        }
    }

    public void setQuality(String newQuality) {
        handleChangeQualityInternal(newQuality);
    }

    private void handleChangeQuality(Intent intent) {
        String newQuality = intent.getStringExtra(PlayerConstants.EXTRA_QUALITY_ID);
        handleChangeQualityInternal(newQuality);
    }
    
    private void handleChangeQualityInternal(String newQuality) {
        if (newQuality == null || newQuality.equals(qualityPreference) || playQueue == null || playQueue.getItem() == null) {
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
                // Stop current playback first
                exoPlayer.stop();
                
                // Play with new quality
                playWithStreamData(streamData);
                
                // Seek to saved position after a small delay
                handler.postDelayed(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(currentPosition);
                        if (wasPlaying) {
                            exoPlayer.setPlayWhenReady(true);
                        }
                    }
                }, 500); // Quality change এর জন্য একটু বেশি সময়
                
                notifyQualityChanged(newQuality);
            } else {
                String errorMsg = "Quality not available: " + newQuality;
                broadcastStateUpdate(null, errorMsg, false, false);
                notifyPlaybackError(errorMsg, new IllegalArgumentException(errorMsg));
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PlayerConstants.CHANNEL_ID,
                    PlayerConstants.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media playback controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        String title = "Loading...";
        String artist = "OpenTube";
        if (playQueue != null && playQueue.getItem() != null) {
            title = playQueue.getItem().getTitle();
            artist = playQueue.getItem().getUploader();
        }

        int playPauseIcon = (exoPlayer != null && exoPlayer.isPlaying())
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;

        Intent activityIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (activityIntent == null) {
            activityIntent = new Intent();
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, PlayerConstants.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_media_previous, "Previous", createServiceIntent(PlayerConstants.ACTION_PREVIOUS, 2))
                .addAction(playPauseIcon, "Play/Pause", createServiceIntent(PlayerConstants.ACTION_PAUSE, 1))
                .addAction(android.R.drawable.ic_media_next, "Next", createServiceIntent(PlayerConstants.ACTION_NEXT, 3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createServiceIntent(PlayerConstants.ACTION_STOP, 4))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    private PendingIntent createServiceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(PlayerConstants.NOTIFICATION_ID, createNotification());
        }
    }

    private void broadcastStateUpdate() {
        broadcastStateUpdate(null, null, isLoadingStream, false);
    }
    
    private void broadcastStateUpdate(@Nullable PlayQueueItem item, @Nullable String error, boolean isLoading, boolean isQueueFinished) {
        if (playQueue == null) return;

        Intent intent = new Intent(PlayerConstants.BROADCAST_PLAYBACK_STATE);
        intent.putExtra(PlayerConstants.EXTRA_STATE, getPlayerState());
        intent.putExtra(PlayerConstants.EXTRA_QUEUE_INDEX, playQueue.getIndex());
        intent.putExtra(PlayerConstants.EXTRA_QUEUE_SIZE, playQueue.size());

        if (item != null) {
            byte[] itemBytes = serializeObject(item);
            if (itemBytes != null) {
                intent.putExtra(PlayerConstants.EXTRA_CURRENT_ITEM, itemBytes);
            }
        }
        if (error != null) {
            intent.putExtra("error", error);
            Log.e(TAG, "Broadcasting error: " + error);
        }
        if (isLoading) {
            intent.putExtra("is_loading", true);
        }
        if (isQueueFinished) {
            intent.putExtra("queue_finished", true);
        }

        sendBroadcast(intent);
    }
    
    private void broadcastMetadataUpdate(StreamInfo streamInfo) {
        Intent intent = new Intent(PlayerConstants.BROADCAST_METADATA_UPDATE);
        intent.putExtra(PlayerConstants.EXTRA_TITLE, streamInfo.getName());
        intent.putExtra(PlayerConstants.EXTRA_UPLOADER, streamInfo.getUploaderName());
        intent.putExtra(PlayerConstants.EXTRA_UPLOADER_URL, streamInfo.getUploaderUrl());
        intent.putExtra(PlayerConstants.EXTRA_DURATION, streamInfo.getDuration());
        intent.putExtra(PlayerConstants.EXTRA_VIEW_COUNT, streamInfo.getViewCount());
        intent.putExtra(PlayerConstants.EXTRA_LIKE_COUNT, streamInfo.getLikeCount());
        intent.putExtra(PlayerConstants.EXTRA_DESCRIPTION, streamInfo.getDescription() != null ? 
                streamInfo.getDescription().getContent() : "");
                
        ThumbnailExtractor thumbnail = new ThumbnailExtractor(streamInfo.getThumbnails());
        
        intent.putExtra(PlayerConstants.EXTRA_THUMBNAIL_URL, thumbnail.getThumbnail());
        intent.putExtra(PlayerConstants.EXTRA_UPLOAD_DATE, streamInfo.getUploadDate());
        
        // Add available qualities
        if (playQueue != null && playQueue.getItem() != null) {
            List<String> qualities = getAvailableQualities();
            if (qualities != null && !qualities.isEmpty()) {
                intent.putExtra(PlayerConstants.EXTRA_AVAILABLE_QUALITIES, qualities.toArray(new String[0]));
            }
        }
        intent.putExtra(PlayerConstants.EXTRA_CURRENT_QUALITY, qualityPreference);
        
        sendBroadcast(intent);
        Log.d(TAG, "Metadata broadcast sent for: " + streamInfo.getName());
    }

    private int getPlayerState() {
        if (isLoadingStream) return PlayerConstants.STATE_BUFFERING;
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "PlayerService onDestroy");
        
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
        
        // Release player resources
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        // Release track selector
        if (trackSelector != null) {
            trackSelector = null;
        }
        
        // Shutdown extractor
        if (streamInfoExtractor != null) {
            streamInfoExtractor.shutdown();
            streamInfoExtractor = null;
        }
        
        // Clear cache
        streamCache.clear();
        
        // Clear metadata listener
        metadataListener = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}