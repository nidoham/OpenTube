package com.nidoham.opentube.player;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.nidoham.opentube.player.managers.PlayerNotificationManager;
import com.nidoham.opentube.util.constant.PlayerConstants;
import com.nidoham.stream.data.RxStreamInfoExtractor; // নতুন ইমপোর্ট

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Flowable; // নতুন ইমপোর্ট
import io.reactivex.rxjava3.disposables.CompositeDisposable; // নতুন ইমপোর্ট
import io.reactivex.rxjava3.disposables.Disposable; // নতুন ইমপোর্ট

/**
 * Refactored PlayerService with RxJava for stream extraction.
 */
public class PlayerService extends Service {

    private static final String TAG = "PlayerService";
    private static final int MAX_RETRIES = 2;
    private static final int MAX_CACHE_SIZE = 50;
    private static final int POSITION_UPDATE_INTERVAL_MS = 1000;
    private static final int QUALITY_CHANGE_DELAY_MS = 500;
    
    private final AtomicBoolean isLoadingStream = new AtomicBoolean(false);
    private final AtomicBoolean isForeground = new AtomicBoolean(false);
    
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private PlayQueue playQueue;
    private PlayerNotificationManager notificationManager;
    
    private final LruCache<String, StreamData> streamCache = 
        new LruCache<>(MAX_CACHE_SIZE);
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new PlayerServiceBinder();
    
    private String qualityPreference = "720p";
    
    // RxJava সাবস্ক্রিপশন ম্যানেজ করার জন্য
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

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
                    
                    if (notificationManager != null) {
                        notificationManager.updatePosition(position);
                    }
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
        initializeNotificationManager();
        setupPlayerListeners();
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
    
    private void initializeNotificationManager() {
        notificationManager = new PlayerNotificationManager(this);
    }

    private void setupPlayerListeners() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "Playback state changed: " + playbackState);
                updateNotificationState();
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
                updateNotificationState();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
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
            case PlayerConstants.ACTION_SEEK:
                handleSeekAction(intent);
                break;
            case PlayerConstants.ACTION_CHANGE_QUALITY:
                handleChangeQuality(intent);
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        return START_STICKY;
    }

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
    
    private void startForegroundService() {
        if (isForeground.getAndSet(true)) {
            return;
        }
        
        if (notificationManager != null) {
            startForeground(PlayerConstants.NOTIFICATION_ID, notificationManager.getNotification());
            notificationManager.showNotification();
        }
        
        Log.d(TAG, "Started foreground service");
    }
    
    private void stopForegroundService() {
        if (!isForeground.getAndSet(false)) {
            return;
        }
        
        if (notificationManager != null) {
            notificationManager.cancelNotification();
        }
        
        stopForeground(true);
        handler.removeCallbacks(positionUpdateRunnable);
        Log.d(TAG, "Stopped foreground service");
    }

    private void playCurrentItem() {
        if (playQueue == null || playQueue.isEmpty() || playQueue.getItem() == null) {
            notifyPlaybackError("No item to play", new IllegalStateException("No item to play"));
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        String itemUrl = item.getUrl();
        Log.d(TAG, "Playing item: " + item.getTitle() + " [" + itemUrl + "]");
        notifyCurrentItemChanged(item);
        
        updateNotificationMetadata(item);

        StreamData cachedData = streamCache.get(itemUrl);
        if (cachedData != null && cachedData.hasValidStreams()) {
            playWithStreamData(cachedData);
        } else {
            extractAndPlay(itemUrl);
        }
    }

    private void extractAndPlay(String videoUrl) {
        Disposable disposable = RxStreamInfoExtractor.extract(videoUrl)
                .retryWhen(errors -> {
                    AtomicInteger counter = new AtomicInteger();
                    return errors.flatMap(error -> {
                        if (counter.incrementAndGet() > MAX_RETRIES) {
                            return Flowable.error(error);
                        }
                        Log.d(TAG, "Retrying extraction... Attempt " + counter.get());
                        notifyLoadingProgress("Retrying... Attempt " + counter.get());
                        return Flowable.timer(2, TimeUnit.SECONDS);
                    });
                })
                .doOnSubscribe(d -> {
                    isLoadingStream.set(true);
                    notifyLoadingStarted("Extracting stream information...");
                })
                .doFinally(() -> {
                    isLoadingStream.set(false);
                    notifyLoadingFinished();
                })
                .subscribe(
                        // onSuccess
                        streamInfo -> {
                            StreamData streamData = new StreamData(streamInfo);
                            streamData.selectQuality(qualityPreference);
                            
                            streamCache.put(videoUrl, streamData);
                            
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
                                    handler.postDelayed(this::handleNextAction, 1500);
                                }
                            } else {
                                playWithStreamData(streamData);
                            }
                        },
                        // onError
                        error -> {
                            Log.e(TAG, "Error extracting stream info after retries", error);
                            String errorMsg = "Failed to load stream: " + error.getMessage();
                            notifyStreamExtractionError(errorMsg, (Exception) error);
                            notifyMetadataError(errorMsg);
                            
                            if (playQueue.getIndex() < playQueue.size() - 1) {
                                handler.postDelayed(this::handleNextAction, 1500);
                            }
                        }
                );
        
        compositeDisposable.add(disposable);
    }

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
        stopForegroundService();
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
    
    private void handleSeekAction(Intent intent) {
        long position = intent.getLongExtra(PlayerConstants.EXTRA_SEEK_POSITION, -1);
        if (position >= 0 && exoPlayer != null) {
            Log.d(TAG, "Seeking to position: " + position);
            exoPlayer.seekTo(position);
            
            if (notificationManager != null) {
                notificationManager.updatePosition(position);
            }
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

    private void updateNotificationMetadata(PlayQueueItem item) {
        if (notificationManager == null || item == null) return;
        
        String title = item.getTitle();
        String artist = item.getUploader();
        Bitmap artwork = null;
        
        notificationManager.updateMetadata(title, artist, artwork);
    }
    
    private void updateNotificationState() {
        if (notificationManager == null || exoPlayer == null) return;
        
        boolean isPlaying = exoPlayer.isPlaying();
        long position = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();
        
        notificationManager.updatePlaybackState(isPlaying, position, duration);
    }
    
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
        
        stopForegroundService();
        
        // সমস্ত RxJava সাবস্ক্রিপশন বাতিল করা হচ্ছে
        compositeDisposable.clear();
        
        handler.removeCallbacks(positionUpdateRunnable);
        handler.removeCallbacksAndMessages(null);
        
        playbackListeners.clear();
        metadataListeners.clear();
        queueListeners.clear();
        errorListeners.clear();
        loadingListeners.clear();
        qualityListeners.clear();
        
        if (notificationManager != null) {
            notificationManager.release();
            notificationManager = null;
        }
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        trackSelector = null;
        streamCache.evictAll();
        playQueue = null;
        
        Log.d(TAG, "PlayerService destroyed and cleaned up");
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ═══════════════════════════════════════════════════════════════
    // Listener Management Methods
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