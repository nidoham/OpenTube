package com.nidoham.opentube.player;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
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
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

import com.nidoham.opentube.player.managers.PlayerNotificationManager;
import com.nidoham.opentube.util.constant.PlayerConstants;
import com.nidoham.opentube.util.UserManager;
import com.nidoham.stream.data.RxStreamInfoExtractor;
import com.nidoham.stream.database.timeline.watched.FirebaseWatchHistoryManager;
import com.nidoham.stream.database.timeline.watched.model.WatchHistory;
import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlayerService extends Service {

    private static final int MAX_RETRIES = 2;
    private static final int MAX_CACHE_SIZE_ITEMS = 20;
    private static final int MAX_CACHE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int POSITION_UPDATE_INTERVAL_MS = 3000;
    private static final int HISTORY_SAVE_INTERVAL_MS = 30000;
    private static final int QUALITY_CHANGE_DELAY_MS = 300;
    private static final long FOREGROUND_NOTIFICATION_TIMEOUT_MS = 3000;
    private static final long MIN_HISTORY_SAVE_POSITION_MS = 5000;
    private static final long NEAR_END_THRESHOLD_MS = 10000;
    
    private final AtomicBoolean isLoadingStream = new AtomicBoolean(false);
    private final AtomicBoolean isForeground = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isHistoryManagerReady = new AtomicBoolean(false);
    private final Object historyLock = new Object();
    
    private volatile ExoPlayer exoPlayer;
    private volatile DefaultTrackSelector trackSelector;
    private volatile PlayQueue playQueue;
    private volatile PlayerNotificationManager notificationManager;
    private volatile FirebaseWatchHistoryManager historyManager;
    private volatile MediaSessionCompat mediaSession;
    private volatile PowerManager.WakeLock wakeLock;
    private volatile AudioManager audioManager;
    private volatile boolean hasAudioFocus = false;
    
    private final ThreadSafeStreamCache streamCache = new ThreadSafeStreamCache(MAX_CACHE_SIZE_ITEMS);
    private final MainThreadHandler handler = new MainThreadHandler(this);
    private final IBinder binder = new PlayerServiceBinder();
    
    private volatile String qualityPreference = "720p";
    private volatile String currentStreamUrl;
    private volatile Disposable currentExtractionDisposable;
    
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final CopyOnWriteArrayList<PlaybackStateListener> playbackListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetadataListener> metadataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<LoadingListener> loadingListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QualityListener> qualityListeners = new CopyOnWriteArrayList<>();
    
    private final PositionUpdateRunnable positionUpdateRunnable = new PositionUpdateRunnable(this);
    private final HistorySaveRunnable historySaveRunnable = new HistorySaveRunnable(this);
    
    private static class MainThreadHandler extends Handler {
        private final WeakReference<PlayerService> serviceRef;
        
        MainThreadHandler(PlayerService service) {
            super(Looper.getMainLooper());
            this.serviceRef = new WeakReference<>(service);
        }
        
        PlayerService getService() {
            return serviceRef.get();
        }
    }
    
    private static class PositionUpdateRunnable implements Runnable {
        private final WeakReference<PlayerService> serviceRef;
        
        PositionUpdateRunnable(PlayerService service) {
            this.serviceRef = new WeakReference<>(service);
        }
        
        @Override
        public void run() {
            PlayerService service = serviceRef.get();
            if (service == null) return;
            
            ExoPlayer player = service.exoPlayer;
            if (player != null && player.isPlaying()) {
                long position = player.getCurrentPosition();
                long duration = player.getDuration();
                
                if (duration > 0) {
                    service.notifyPositionUpdate(position, duration);
                    service.updateMediaSessionPosition(position, duration);
                    
                    PlayerNotificationManager nm = service.notificationManager;
                    if (nm != null) {
                        nm.updatePosition(position);
                    }
                }
            }
            
            Handler h = service.handler;
            if (h != null) {
                h.postDelayed(this, POSITION_UPDATE_INTERVAL_MS);
            }
        }
    }
    
    private static class HistorySaveRunnable implements Runnable {
        private final WeakReference<PlayerService> serviceRef;
        
        HistorySaveRunnable(PlayerService service) {
            this.serviceRef = new WeakReference<>(service);
        }
        
        @Override
        public void run() {
            PlayerService service = serviceRef.get();
            if (service == null) return;
            
            service.saveWatchHistoryInternal(false);
            
            Handler h = service.handler;
            if (h != null) {
                h.postDelayed(this, HISTORY_SAVE_INTERVAL_MS);
            }
        }
    }

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

    private static class StreamData {
        final StreamInfo streamInfo;
        final List<VideoStream> videoStreams;
        final List<AudioStream> audioStreams;
        volatile String selectedVideoUrl;
        volatile String selectedAudioUrl;
        volatile String currentQuality;
        final long timestamp;
        final int sizeBytes;

        StreamData(StreamInfo info) {
            this.streamInfo = info;
            this.videoStreams = info.getVideoOnlyStreams() != null ? 
                new ArrayList<>(info.getVideoOnlyStreams()) : new ArrayList<>();
            this.audioStreams = info.getAudioStreams() != null ? 
                new ArrayList<>(info.getAudioStreams()) : new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
            this.sizeBytes = estimateSize();
        }

        synchronized void selectQuality(String qualityPref) {
            selectedVideoUrl = selectBestVideoStream(qualityPref);
            selectedAudioUrl = selectBestAudioStream();
            currentQuality = qualityPref;
        }

        private String selectBestVideoStream(String qualityPref) {
            if (videoStreams.isEmpty()) return null;

            int targetHeight = parseQualityHeight(qualityPref);
            VideoStream bestMatch = null;
            int smallestDiff = Integer.MAX_VALUE;

            for (VideoStream stream : videoStreams) {
                if (stream == null) continue;
                
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
            if (audioStreams.isEmpty()) return null;

            AudioStream bestAudio = null;
            int highestBitrate = -1;

            for (AudioStream stream : audioStreams) {
                if (stream == null) continue;
                
                int bitrate = stream.getAverageBitrate();
                if (bitrate > highestBitrate) {
                    highestBitrate = bitrate;
                    bestAudio = stream;
                }
            }
            
            return bestAudio != null ? bestAudio.getContent() : null;
        }

        private int parseQualityHeight(String quality) {
            if (quality == null) return 720;
            
            try {
                return Integer.parseInt(quality.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return 720;
            }
        }
        
        private int estimateSize() {
            return (streamInfo != null ? 5000 : 0) + 
                   (videoStreams.size() * 200) + 
                   (audioStreams.size() * 100);
        }

        synchronized boolean hasValidStreams() {
            return selectedVideoUrl != null && selectedAudioUrl != null;
        }
        
        boolean isExpired(long maxAgeMs) {
            return (System.currentTimeMillis() - timestamp) > maxAgeMs;
        }
    }
    
    private static class ThreadSafeStreamCache {
        private final LruCache<String, StreamData> cache;
        private final Object lock = new Object();
        private int totalSizeBytes = 0;
        
        ThreadSafeStreamCache(int maxSize) {
            this.cache = new LruCache<String, StreamData>(maxSize) {
                @Override
                protected void entryRemoved(boolean evicted, String key, 
                                          StreamData oldValue, StreamData newValue) {
                    if (oldValue != null) {
                        totalSizeBytes -= oldValue.sizeBytes;
                    }
                }
            };
        }
        
        void put(String key, StreamData data) {
            if (key == null || data == null) return;
            
            synchronized (lock) {
                if (totalSizeBytes + data.sizeBytes > MAX_CACHE_SIZE_BYTES) {
                    cache.evictAll();
                    totalSizeBytes = 0;
                }
                
                cache.put(key, data);
                totalSizeBytes += data.sizeBytes;
            }
        }
        
        StreamData get(String key) {
            if (key == null) return null;
            
            synchronized (lock) {
                StreamData data = cache.get(key);
                if (data != null && data.isExpired(TimeUnit.HOURS.toMillis(1))) {
                    cache.remove(key);
                    return null;
                }
                return data;
            }
        }
        
        void clear() {
            synchronized (lock) {
                cache.evictAll();
                totalSizeBytes = 0;
            }
        }
    }

    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }
    
    private final BroadcastReceiver audioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                    exoPlayer.pause();
                    notifyPlaybackStateChanged();
                }
            }
        }
    };
    
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = 
        new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                handleAudioFocusChange(focusChange);
            }
        };

    @Override
    public void onCreate() {
        super.onCreate();
        
        if (!isInitialized.compareAndSet(false, true)) {
            return;
        }
        
        acquireWakeLock();
        initializeAudioManager();
        initializePlayer();
        initializeMediaSession();
        initializeNotificationManager();
        initializeHistoryManager();
        setupPlayerListeners();
        registerReceivers();
        
        startDummyForeground();
    }
    
    private void startDummyForeground() {
        handler.postDelayed(() -> {
            if (!isForeground.get() && notificationManager != null) {
                try {
                    startForeground(PlayerConstants.NOTIFICATION_ID, 
                        notificationManager.getNotification());
                    isForeground.set(true);
                } catch (Exception ignored) {
                }
            }
        }, 100);
    }
    
    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, 
                    "PlayerService::WakeLock"
                );
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(TimeUnit.HOURS.toMillis(3));
            }
        } catch (Exception ignored) {
        }
    }
    
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }
    
    private void initializeAudioManager() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }
    
    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            
            android.media.AudioFocusRequest focusRequest = 
                new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
        }
        
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        return hasAudioFocus;
    }
    
    private void abandonAudioFocus() {
        if (audioManager != null && hasAudioFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
                
                android.media.AudioFocusRequest focusRequest = 
                    new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
                
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            hasAudioFocus = false;
        }
    }
    
    private void handleAudioFocusChange(int focusChange) {
        ExoPlayer player = exoPlayer;
        if (player == null) return;
        
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (!player.isPlaying()) {
                    player.setVolume(1.0f);
                    player.play();
                    notifyPlaybackStateChanged();
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS:
                if (player.isPlaying()) {
                    player.pause();
                    notifyPlaybackStateChanged();
                }
                abandonAudioFocus();
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) {
                    player.pause();
                    notifyPlaybackStateChanged();
                }
                break;
                
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player.isPlaying()) {
                    player.setVolume(0.3f);
                }
                break;
        }
    }
    
    private void registerReceivers() {
        try {
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(audioNoisyReceiver, filter);
            }
        } catch (Exception ignored) {
        }
    }
    
    private void unregisterReceivers() {
        try {
            unregisterReceiver(audioNoisyReceiver);
        } catch (Exception ignored) {
        }
    }
    
    private void initializePlayer() {
        boolean isWiFi = isWiFiConnected();
        
        trackSelector = new DefaultTrackSelector(this, new AdaptiveTrackSelection.Factory());
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setForceHighestSupportedBitrate(false)
                .setMaxVideoBitrate(isWiFi ? Integer.MAX_VALUE : 2_000_000)
                .build()
        );

        int bufferMultiplier = isWiFi ? 2 : 1;
        LoadControl loadControl = new DefaultLoadControl.Builder()
            .setAllocator(new DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                2000 * bufferMultiplier, 
                20000 * bufferMultiplier, 
                200, 
                500
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(5000, false)
            .build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build();

        exoPlayer = new ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build();
    }
    
    private boolean isWiFiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null && 
                       capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            } else {
                android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && 
                       networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "PlayerService");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                handlePlayPauseAction();
            }
            
            @Override
            public void onPause() {
                handlePlayPauseAction();
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
                handleSeekToPosition(pos);
            }
            
            @Override
            public void onStop() {
                handleStopAction();
            }
        });
        
        mediaSession.setActive(true);
        updateMediaSessionState(PlaybackStateCompat.STATE_NONE, 0, 0);
    }
    
    private void initializeNotificationManager() {
        notificationManager = new PlayerNotificationManager(this);
    }
    
    private void initializeHistoryManager() {
        UserManager userManager = UserManager.getInstance(this);
        
        if (!userManager.isLoggedIn()) {
            userManager.signInAnonymously(new UserManager.AuthCallback() {
                @Override
                public void onSuccess(@NonNull String userId) {
                    synchronized (historyLock) {
                        historyManager = new FirebaseWatchHistoryManager();
                        isHistoryManagerReady.set(true);
                    }
                }

                @Override
                public void onFailure(@NonNull Exception e) {
                    isHistoryManagerReady.set(false);
                }
            });
        } else {
            synchronized (historyLock) {
                historyManager = new FirebaseWatchHistoryManager();
                isHistoryManagerReady.set(true);
            }
        }
    }

    private void setupPlayerListeners() {
        if (exoPlayer == null) return;
        
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateNotificationState();
                notifyPlaybackStateChanged();
                
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded();
                } else if (playbackState == Player.STATE_READY) {
                    if (requestAudioFocus()) {
                        exoPlayer.play();
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateNotificationState();
                notifyPlaybackStateChanged();
                
                if (isPlaying) {
                    handler.removeCallbacks(positionUpdateRunnable);
                    handler.post(positionUpdateRunnable);
                    
                    handler.removeCallbacks(historySaveRunnable);
                    handler.postDelayed(historySaveRunnable, HISTORY_SAVE_INTERVAL_MS);
                } else {
                    handler.removeCallbacks(positionUpdateRunnable);
                    handler.removeCallbacks(historySaveRunnable);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                notifyPlaybackError("Playback error: " + 
                    (error.getMessage() != null ? error.getMessage() : "Unknown"), error);
                
                if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
                    handler.postDelayed(PlayerService.this::handleNextAction, 1500);
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
        
        switch (action) {
            case PlayerConstants.ACTION_PLAY:
                handlePlayAction(intent);
                break;
            case PlayerConstants.ACTION_PAUSE:
                handlePlayPauseAction();
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
        }

        return START_STICKY;
    }

    private void handlePlayAction(Intent intent) {
        if (playQueue != null && !playQueue.isEmpty() && exoPlayer != null) {
            if (!exoPlayer.isPlaying()) {
                if (requestAudioFocus()) {
                    exoPlayer.play();
                    notifyPlaybackStateChanged();
                }
            }
            ensureForegroundService();
            return;
        }
        
        byte[] queueBytes = intent.getByteArrayExtra(PlayerConstants.EXTRA_PLAY_QUEUE);
        if (queueBytes == null) {
            notifyPlaybackError("No queue data provided", 
                new IllegalArgumentException("No queue data"));
            return;
        }

        PlayQueue queue = deserializeObject(queueBytes);
        if (queue == null || queue.isEmpty()) {
            notifyPlaybackError("Empty or invalid queue", 
                new IllegalArgumentException("Empty queue"));
            return;
        }

        playQueue = queue;
        ensureForegroundService();
        notifyQueueChanged();
        playCurrentItem();
    }
    
    private void ensureForegroundService() {
        if (isForeground.get()) {
            return;
        }
        
        handler.post(() -> {
            if (notificationManager != null) {
                PlayQueueItem currentItem = playQueue != null ? playQueue.getItem() : null;
                if (currentItem != null) {
                    updateNotificationMetadata(currentItem);
                }
                
                try {
                    startForeground(PlayerConstants.NOTIFICATION_ID, 
                        notificationManager.getNotification());
                    isForeground.set(true);
                } catch (Exception ignored) {
                }
            }
        });
    }
    
    private void stopForegroundService() {
        if (!isForeground.compareAndSet(true, false)) {
            return;
        }
        
        if (notificationManager != null) {
            notificationManager.cancelNotification();
        }
        
        stopForeground(true);
        
        handler.removeCallbacks(positionUpdateRunnable);
        handler.removeCallbacks(historySaveRunnable);
    }

    private void playCurrentItem() {
        if (playQueue == null || playQueue.isEmpty()) {
            notifyPlaybackError("No item to play", new IllegalStateException("No item"));
            return;
        }

        PlayQueueItem item = playQueue.getItem();
        if (item == null) {
            notifyPlaybackError("Invalid queue item", new IllegalStateException("Null item"));
            return;
        }

        String itemUrl = item.getUrl();
        if (itemUrl == null || itemUrl.isEmpty()) {
            notifyPlaybackError("Invalid item URL", new IllegalArgumentException("Empty URL"));
            return;
        }
        
        currentStreamUrl = itemUrl;
        notifyCurrentItemChanged(item);
        updateNotificationMetadata(item);

        StreamData cachedData = streamCache.get(itemUrl);
        if (cachedData != null && cachedData.hasValidStreams()) {
            playWithStreamDataAndResume(cachedData, item);
        } else {
            extractAndPlay(itemUrl, item);
        }
    }

    private void extractAndPlay(String videoUrl, PlayQueueItem item) {
        if (currentExtractionDisposable != null && !currentExtractionDisposable.isDisposed()) {
            currentExtractionDisposable.dispose();
        }
        
        currentExtractionDisposable = RxStreamInfoExtractor.extract(videoUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retryWhen(errors -> {
                    AtomicInteger counter = new AtomicInteger();
                    return errors.flatMap(error -> {
                        if (counter.incrementAndGet() > MAX_RETRIES) {
                            return Flowable.error(error);
                        }
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
                    streamInfo -> handleStreamInfoSuccess(streamInfo, videoUrl, item),
                    error -> handleStreamInfoError(error, videoUrl)
                );
        
        compositeDisposable.add(currentExtractionDisposable);
    }
    
    private void handleStreamInfoSuccess(StreamInfo streamInfo, String videoUrl, PlayQueueItem item) {
        if (streamInfo == null) {
            handleStreamInfoError(new IllegalStateException("Null StreamInfo"), videoUrl);
            return;
        }
        
        StreamData streamData = new StreamData(streamInfo);
        streamData.selectQuality(qualityPreference);
        
        streamCache.put(videoUrl, streamData);
        notifyMetadataLoaded(streamInfo);
        
        List<String> qualities = getAvailableQualitiesFromStreamData(streamData);
        if (qualities != null && !qualities.isEmpty()) {
            notifyAvailableQualitiesChanged(qualities);
        }

        if (!streamData.hasValidStreams()) {
            String errorMsg = "No playable streams available for selected quality";
            notifyStreamExtractionError(errorMsg, new IllegalStateException(errorMsg));
            notifyMetadataError(errorMsg);
            
            if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
                handler.postDelayed(this::handleNextAction, 2000);
            }
        } else {
            playWithStreamDataAndResume(streamData, item);
        }
    }
    
    private void handleStreamInfoError(Throwable error, String videoUrl) {
        String errorMsg = "Failed to load stream: " + 
            (error.getMessage() != null ? error.getMessage() : "Unknown error");
        notifyStreamExtractionError(errorMsg, error instanceof Exception ? 
            (Exception) error : new Exception(error));
        notifyMetadataError(errorMsg);
        
        if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
            handler.postDelayed(this::handleNextAction, 2000);
        }
    }

    private void playWithStreamDataAndResume(StreamData streamData, PlayQueueItem item) {
        if (streamData == null || !streamData.hasValidStreams() || item == null) {
            return;
        }
        
        playMergedStream(streamData.selectedVideoUrl, streamData.selectedAudioUrl);

        if (!isHistoryManagerReady.get()) {
            handler.postDelayed(() -> {
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(true);
                    if (requestAudioFocus()) {
                        exoPlayer.play();
                    }
                    notifyPlaybackStateChanged();
                }
            }, 500);
            return;
        }
        
        String itemUrl = item.getUrl();
        long itemDuration = item.getDuration() * 1000L;
        
        getWatchHistoryForCurrentItem(itemUrl, new FirebaseWatchHistoryManager.SingleHistoryCallback() {
            @Override
            public void onSuccess(@Nullable WatchHistory history) {
                handler.post(() -> {
                    if (exoPlayer == null) return;
                    
                    if (history != null && history.getLastDuration() > MIN_HISTORY_SAVE_POSITION_MS 
                        && history.getLastDuration() < itemDuration - NEAR_END_THRESHOLD_MS) {
                        
                        long resumePosition = history.getLastDuration();
                        exoPlayer.seekTo(resumePosition);
                    }
                    
                    exoPlayer.setPlayWhenReady(true);
                    if (requestAudioFocus()) {
                        exoPlayer.play();
                    }
                    notifyPlaybackStateChanged();
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                handler.post(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.setPlayWhenReady(true);
                        if (requestAudioFocus()) {
                            exoPlayer.play();
                        }
                        notifyPlaybackStateChanged();
                    }
                });
            }
        });
    }

    private void playMergedStream(String videoUrl, String audioUrl) {
        if (videoUrl == null || audioUrl == null || exoPlayer == null) {
            notifyPlaybackError("Invalid stream URLs", 
                new IllegalArgumentException("Null URLs"));
            return;
        }
        
        try {
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
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
            
        } catch (Exception e) {
            String errorMsg = "Playback initialization error: " + 
                (e.getMessage() != null ? e.getMessage() : "Unknown");
            notifyPlaybackError(errorMsg, e);
        }
    }

    private void handlePlayPauseAction() {
        if (exoPlayer == null) return;
        
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
            abandonAudioFocus();
        } else {
            if (requestAudioFocus()) {
                exoPlayer.play();
            }
        }
        notifyPlaybackStateChanged();
    }

    private void handleStopAction() {
        saveWatchHistoryInternal(true);
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        
        abandonAudioFocus();
        stopForegroundService();
        stopSelf();
    }

    private void handleNextAction() {
        saveWatchHistoryInternal(true);
        
        if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
            playQueue.next();
            notifyQueueChanged();
            playCurrentItem();
        } else {
            handleStopAction();
        }
    }

    private void handlePreviousAction() {
        saveWatchHistoryInternal(true);
        
        if (playQueue != null && playQueue.getIndex() > 0) {
            playQueue.previous();
            notifyQueueChanged();
            playCurrentItem();
        } else if (exoPlayer != null) {
            exoPlayer.seekTo(0);
        }
    }
    
    private void handleSeekAction(Intent intent) {
        long position = intent.getLongExtra(PlayerConstants.EXTRA_SEEK_POSITION, -1);
        handleSeekToPosition(position);
    }
    
    private void handleSeekToPosition(long position) {
        if (position >= 0 && exoPlayer != null) {
            exoPlayer.seekTo(position);
            updateMediaSessionPosition(position, exoPlayer.getDuration());
            
            if (notificationManager != null) {
                notificationManager.updatePosition(position);
            }
        }
    }

    private void handlePlaybackEnded() {
        saveWatchHistoryInternal(true);
        notifyPlaybackEnded();
        
        if (playQueue != null && playQueue.getIndex() < playQueue.size() - 1) {
            handleNextAction();
        } else {
            notifyQueueFinished();
            abandonAudioFocus();
            stopForegroundService();
        }
    }

    private void handleChangeQuality(Intent intent) {
        String newQuality = intent.getStringExtra(PlayerConstants.EXTRA_QUALITY_ID);
        setQualityInternal(newQuality);
    }
    
    public void setQuality(String newQuality) {
        setQualityInternal(newQuality);
    }
    
    private void setQualityInternal(String newQuality) {
        if (newQuality == null || newQuality.equals(qualityPreference) || 
            playQueue == null || playQueue.getItem() == null || exoPlayer == null) {
            return;
        }

        long currentPosition = exoPlayer.getCurrentPosition();
        boolean wasPlaying = exoPlayer.isPlaying();
        qualityPreference = newQuality;

        String itemUrl = playQueue.getItem().getUrl();
        StreamData streamData = itemUrl != null ? streamCache.get(itemUrl) : null;
        
        if (streamData != null) {
            streamData.selectQuality(newQuality);
            
            if (streamData.hasValidStreams()) {
                exoPlayer.stop();
                playMergedStream(streamData.selectedVideoUrl, streamData.selectedAudioUrl);
                
                handler.postDelayed(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(currentPosition);
                        if (wasPlaying && requestAudioFocus()) {
                            exoPlayer.setPlayWhenReady(true);
                            exoPlayer.play();
                        }
                        notifyPlaybackStateChanged();
                    }
                }, QUALITY_CHANGE_DELAY_MS);
                
                notifyQualityChanged(newQuality);
            } else {
                String errorMsg = "Quality not available: " + newQuality;
                notifyPlaybackError(errorMsg, new IllegalArgumentException(errorMsg));
            }
        }
    }

    private void saveWatchHistoryInternal(boolean immediate) {
        if (!isHistoryManagerReady.get() || playQueue == null || exoPlayer == null) {
            return;
        }

        PlayQueueItem currentItem = playQueue.getItem();
        if (currentItem == null) {
            return;
        }

        long currentPosition = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();

        if (currentPosition < MIN_HISTORY_SAVE_POSITION_MS) {
            return;
        }

        boolean isNearEnd = duration != C.TIME_UNSET && 
            currentPosition >= duration - NEAR_END_THRESHOLD_MS;

        synchronized (historyLock) {
            if (historyManager != null) {
                if (isNearEnd) {
                    historyManager.insertOrUpdateHistory(0, currentItem);
                } else {
                    historyManager.insertOrUpdateHistory(currentPosition, currentItem);
                }
            }
        }
    }

    private void updateNotificationMetadata(PlayQueueItem item) {
        if (notificationManager == null || item == null) return;
        
        String title = item.getTitle() != null ? item.getTitle() : "Unknown Title";
        String artist = item.getUploader() != null ? item.getUploader() : "Unknown Artist";
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
    
    private void updateMediaSessionState(int state, long position, long duration) {
        if (mediaSession == null) return;
        
        long actions = PlaybackStateCompat.ACTION_PLAY |
                       PlaybackStateCompat.ACTION_PAUSE |
                       PlaybackStateCompat.ACTION_PLAY_PAUSE |
                       PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                       PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                       PlaybackStateCompat.ACTION_SEEK_TO |
                       PlaybackStateCompat.ACTION_STOP;
        
        float playbackSpeed = (state == PlaybackStateCompat.STATE_PLAYING) ? 1.0f : 0.0f;
        
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, playbackSpeed)
            .build();
        
        mediaSession.setPlaybackState(playbackState);
    }
    
    private void updateMediaSessionPosition(long position, long duration) {
        if (exoPlayer == null) return;
        
        int state = exoPlayer.isPlaying() ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updateMediaSessionState(state, position, duration);
    }

    @Nullable
    public ExoPlayer getPlayer() {
        return exoPlayer;
    }
    
    @Nullable
    public StreamInfo getCurrentStreamInfo() {
        if (currentStreamUrl == null) {
            return null;
        }
        
        StreamData cachedData = streamCache.get(currentStreamUrl);
        return cachedData != null ? cachedData.streamInfo : null;
    }
    
    @Nullable
    public List<String> getAvailableQualities() {
        if (currentStreamUrl == null) {
            return null;
        }
        
        StreamData cachedData = streamCache.get(currentStreamUrl);
        return getAvailableQualitiesFromStreamData(cachedData);
    }
    
    private List<String> getAvailableQualitiesFromStreamData(StreamData streamData) {
        if (streamData == null || streamData.videoStreams == null) {
            return null;
        }
        
        List<String> qualities = new ArrayList<>();
        for (VideoStream stream : streamData.videoStreams) {
            if (stream == null) continue;
            
            String quality = stream.getHeight() + "p";
            if (!qualities.contains(quality)) {
                qualities.add(quality);
            }
        }
        return qualities.isEmpty() ? null : qualities;
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
                return exoPlayer.isPlaying() ? 
                    PlayerConstants.STATE_PLAYING : PlayerConstants.STATE_PAUSED;
            default:
                return PlayerConstants.STATE_STOPPED;
        }
    }

    private void getWatchHistoryForCurrentItem(String videoUrl, 
                                               FirebaseWatchHistoryManager.SingleHistoryCallback callback) {
        if (!isHistoryManagerReady.get() || videoUrl == null) {
            callback.onError("History not available");
            return;
        }
        
        synchronized (historyLock) {
            if (historyManager != null) {
                historyManager.getHistoryByUrl(videoUrl, callback);
            } else {
                callback.onError("History manager not initialized");
            }
        }
    }
    
    @Nullable
    private <T extends Serializable> T deserializeObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        saveWatchHistoryInternal(true);
        handleStopAction();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        saveWatchHistoryInternal(true);
        stopForegroundService();
        
        if (currentExtractionDisposable != null && !currentExtractionDisposable.isDisposed()) {
            currentExtractionDisposable.dispose();
        }
        compositeDisposable.clear();
        
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
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        
        abandonAudioFocus();
        unregisterReceivers();
        releaseWakeLock();
        
        trackSelector = null;
        streamCache.clear();
        playQueue = null;
        currentStreamUrl = null;
        
        synchronized (historyLock) {
            historyManager = null;
        }
        
        isInitialized.set(false);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void addPlaybackStateListener(PlaybackStateListener listener) {
        if (listener == null || playbackListeners.contains(listener)) return;
        
        playbackListeners.add(listener);
        
        if (exoPlayer != null) {
            handler.post(() -> {
                if (exoPlayer != null) {
                    listener.onPlaybackStateChanged(
                        getPlayerState(),
                        exoPlayer.isPlaying(),
                        exoPlayer.getCurrentPosition(),
                        exoPlayer.getDuration()
                    );
                }
            });
        }
    }
    
    public void removePlaybackStateListener(PlaybackStateListener listener) {
        playbackListeners.remove(listener);
    }
    
    public void addMetadataListener(MetadataListener listener) {
        if (listener == null || metadataListeners.contains(listener)) return;
        
        metadataListeners.add(listener);
        
        StreamInfo currentInfo = getCurrentStreamInfo();
        if (currentInfo != null) {
            handler.post(() -> listener.onMetadataLoaded(currentInfo));
        }
    }
    
    public void removeMetadataListener(MetadataListener listener) {
        metadataListeners.remove(listener);
    }
    
    public void addQueueListener(QueueListener listener) {
        if (listener == null || queueListeners.contains(listener)) return;
        
        queueListeners.add(listener);
        
        if (playQueue != null) {
            handler.post(() -> {
                listener.onQueueChanged(playQueue.getIndex(), playQueue.size());
                PlayQueueItem item = playQueue.getItem();
                if (item != null) {
                    listener.onCurrentItemChanged(item);
                }
            });
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
        if (listener == null || qualityListeners.contains(listener)) return;
        
        qualityListeners.add(listener);
        
        handler.post(() -> {
            listener.onQualityChanged(qualityPreference);
            List<String> qualities = getAvailableQualities();
            if (qualities != null) {
                listener.onAvailableQualitiesChanged(qualities);
            }
        });
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
        
        updateMediaSessionState(
            isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
            position, duration
        );
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
            handler.post(() -> listener.onLoadingFinished());
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