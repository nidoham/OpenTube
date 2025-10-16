package com.nidoham.opentube.player.managers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.nidoham.opentube.R;
import com.nidoham.opentube.util.constant.PlayerConstants;

import java.lang.ref.WeakReference;

/**
 * Production-ready notification manager with optimized performance,
 * proper resource management, and full Android 8+ compatibility.
 * 
 * Features:
 * - MediaSession integration for seek bar support
 * - Memory leak prevention with WeakReference
 * - Throttled updates for battery efficiency
 * - Android TV D-pad navigation support
 * - Proper lifecycle management
 */
public class PlayerNotificationManager {
    
    private static final long MIN_UPDATE_INTERVAL_MS = 900;
    private static final long METADATA_UPDATE_DEBOUNCE_MS = 300;
    private static final int MAX_BITMAP_SIZE = 512;
    
    private final WeakReference<Context> contextRef;
    private volatile NotificationManager notificationManager;
    private volatile MediaSessionCompat mediaSession;
    private volatile NotificationCompat.Builder notificationBuilder;
    
    private volatile String currentTitle = "Loading...";
    private volatile String currentArtist = "OpenTube";
    private volatile Bitmap currentArtwork;
    private volatile boolean isPlaying = false;
    private volatile long currentPosition = 0;
    private volatile long duration = 0;
    private volatile boolean isNotificationShown = false;
    
    private volatile long lastNotificationUpdate = 0;
    private volatile long lastMetadataUpdate = 0;
    private volatile long lastPlaybackStateUpdate = 0;
    
    private volatile boolean isReceiverRegistered = false;
    private volatile boolean isReleased = false;
    
    private final Object notificationLock = new Object();
    private final Object metadataLock = new Object();
    
    private final BroadcastReceiver seekBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isReleased || intent == null) return;
            
            if (PlayerConstants.ACTION_SEEK.equals(intent.getAction())) {
                long position = intent.getLongExtra("position", -1);
                if (position >= 0) {
                    currentPosition = position;
                    int state = isPlaying ? 
                        PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                    updatePlaybackStateInternal(state, position, duration);
                }
            }
        }
    };
    
    public PlayerNotificationManager(@NonNull Context context) {
        this.contextRef = new WeakReference<>(context.getApplicationContext());
        
        Context ctx = getContext();
        if (ctx != null) {
            this.notificationManager = (NotificationManager) 
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            
            createNotificationChannel();
            initializeMediaSession();
            registerSeekReceiver();
        }
    }
    
    @Nullable
    private Context getContext() {
        return contextRef.get();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        
        Context context = getContext();
        if (context == null || notificationManager == null) return;
        
        NotificationChannel channel = new NotificationChannel(
            PlayerConstants.CHANNEL_ID,
            PlayerConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        );
        
        channel.setDescription("Media playback controls");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setBypassDnd(false);
        
        notificationManager.createNotificationChannel(channel);
    }
    
    private void registerSeekReceiver() {
        Context context = getContext();
        if (context == null || isReceiverRegistered) return;
        
        try {
            IntentFilter filter = new IntentFilter(PlayerConstants.ACTION_SEEK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(seekBroadcastReceiver, filter, 
                    Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(seekBroadcastReceiver, filter);
            }
            isReceiverRegistered = true;
        } catch (Exception ignored) {
        }
    }
    
    private void unregisterSeekReceiver() {
        if (!isReceiverRegistered) return;
        
        Context context = getContext();
        if (context != null) {
            try {
                context.unregisterReceiver(seekBroadcastReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        isReceiverRegistered = false;
    }
    
    private void initializeMediaSession() {
        Context context = getContext();
        if (context == null) return;
        
        mediaSession = new MediaSessionCompat(context, "PlayerNotificationManager");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendServiceIntent(PlayerConstants.ACTION_PAUSE);
            }
            
            @Override
            public void onPause() {
                sendServiceIntent(PlayerConstants.ACTION_PAUSE);
            }
            
            @Override
            public void onSkipToNext() {
                sendServiceIntent(PlayerConstants.ACTION_NEXT);
            }
            
            @Override
            public void onSkipToPrevious() {
                sendServiceIntent(PlayerConstants.ACTION_PREVIOUS);
            }
            
            @Override
            public void onSeekTo(long pos) {
                if (pos < 0 || pos > duration) return;
                
                currentPosition = pos;
                
                Intent seekIntent = new Intent(PlayerConstants.ACTION_SEEK);
                Context ctx = getContext();
                if (ctx != null) {
                    seekIntent.setPackage(ctx.getPackageName());
                    seekIntent.putExtra("position", pos);
                    ctx.sendBroadcast(seekIntent);
                }
                
                Intent serviceIntent = createServiceIntent(PlayerConstants.ACTION_SEEK);
                if (serviceIntent != null) {
                    serviceIntent.putExtra(PlayerConstants.EXTRA_SEEK_POSITION, pos);
                    serviceIntent.putExtra("position", pos);
                    startServiceSafely(serviceIntent);
                }
                
                int state = isPlaying ? 
                    PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                updatePlaybackStateInternal(state, pos, duration);
            }
            
            @Override
            public void onStop() {
                sendServiceIntent(PlayerConstants.ACTION_STOP);
            }
        });
        
        updatePlaybackStateInternal(PlaybackStateCompat.STATE_NONE, 0, 0);
        mediaSession.setActive(true);
    }
    
    private void sendServiceIntent(String action) {
        if (action == null) return;
        
        Intent intent = createServiceIntent(action);
        if (intent != null) {
            startServiceSafely(intent);
        }
    }
    
    @Nullable
    private Intent createServiceIntent(String action) {
        Context context = getContext();
        if (context == null) return null;
        
        try {
            Class<?> serviceClass = Class.forName("com.nidoham.opentube.player.PlayerService");
            Intent intent = new Intent(context, serviceClass);
            intent.setAction(action);
            return intent;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    private void startServiceSafely(Intent intent) {
        if (intent == null) return;
        
        Context context = getContext();
        if (context == null) return;
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }
    
    private void updatePlaybackStateInternal(int state, long position, long duration) {
        if (mediaSession == null || isReleased) return;
        
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastPlaybackStateUpdate < 100 && 
            state != PlaybackStateCompat.STATE_NONE) {
            return;
        }
        lastPlaybackStateUpdate = currentTime;
        
        long actions = PlaybackStateCompat.ACTION_PLAY |
                       PlaybackStateCompat.ACTION_PAUSE |
                       PlaybackStateCompat.ACTION_PLAY_PAUSE |
                       PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                       PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                       PlaybackStateCompat.ACTION_SEEK_TO |
                       PlaybackStateCompat.ACTION_STOP;
        
        float playbackSpeed = (state == PlaybackStateCompat.STATE_PLAYING) ? 1.0f : 0.0f;
        
        try {
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, playbackSpeed, SystemClock.elapsedRealtime());
            
            mediaSession.setPlaybackState(stateBuilder.build());
        } catch (Exception ignored) {
        }
    }
    
    private void updateMediaMetadata() {
        if (mediaSession == null || isReleased) return;
        
        synchronized (metadataLock) {
            try {
                MediaMetadataCompat.Builder metadataBuilder = 
                    new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, 
                            currentTitle != null ? currentTitle : "Unknown")
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, 
                            currentArtist != null ? currentArtist : "Unknown")
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "OpenTube")
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, 
                            currentTitle != null ? currentTitle : "Unknown")
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, 
                            currentArtist != null ? currentArtist : "Unknown")
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
                
                if (currentArtwork != null && !currentArtwork.isRecycled()) {
                    metadataBuilder
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtwork)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, currentArtwork)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, currentArtwork);
                }
                
                mediaSession.setMetadata(metadataBuilder.build());
            } catch (Exception ignored) {
            }
        }
    }
    
    private void buildNotification() {
        Context context = getContext();
        if (context == null || mediaSession == null || isReleased) return;
        
        synchronized (notificationLock) {
            try {
                androidx.media.app.NotificationCompat.MediaStyle mediaStyle = 
                    new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(createPendingServiceIntent(
                            PlayerConstants.ACTION_STOP, 98));
                
                int playPauseIcon = isPlaying ? 
                    R.drawable.ic_pause : R.drawable.ic_play_arrow;
                String playPauseLabel = isPlaying ? "Pause" : "Play";
                
                notificationBuilder = new NotificationCompat.Builder(context, 
                    PlayerConstants.CHANNEL_ID)
                    .setStyle(mediaStyle)
                    .setContentTitle(currentTitle != null ? currentTitle : "Unknown")
                    .setContentText(currentArtist != null ? currentArtist : "Unknown")
                    .setSubText("OpenTube")
                    .setSmallIcon(R.drawable.ic_play_arrow)
                    .setLargeIcon(currentArtwork)
                    .setContentIntent(createContentIntent())
                    .setDeleteIntent(createPendingServiceIntent(PlayerConstants.ACTION_STOP, 98))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                    .setOngoing(isPlaying)
                    .setSilent(true);
                
                notificationBuilder.clearActions();
                
                notificationBuilder.addAction(
                    R.drawable.ic_skip_previous,
                    "Previous",
                    createPendingServiceIntent(PlayerConstants.ACTION_PREVIOUS, 1)
                );
                
                notificationBuilder.addAction(
                    playPauseIcon,
                    playPauseLabel,
                    createPendingServiceIntent(PlayerConstants.ACTION_PAUSE, 2)
                );
                
                notificationBuilder.addAction(
                    R.drawable.ic_skip_next,
                    "Next",
                    createPendingServiceIntent(PlayerConstants.ACTION_NEXT, 3)
                );
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    notificationBuilder.setColor(Color.parseColor("#FF6200EE"));
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    notificationBuilder.setForegroundServiceBehavior(
                        Notification.FOREGROUND_SERVICE_IMMEDIATE);
                }
            } catch (Exception ignored) {
            }
        }
    }
    
    @NonNull
    public Notification getNotification() {
        synchronized (notificationLock) {
            if (notificationBuilder == null || isReleased) {
                buildNotification();
            }
            
            if (notificationBuilder != null) {
                try {
                    return notificationBuilder.build();
                } catch (Exception e) {
                    return createFallbackNotification();
                }
            }
            
            return createFallbackNotification();
        }
    }
    
    @NonNull
    private Notification createFallbackNotification() {
        Context context = getContext();
        if (context == null) {
            return new Notification();
        }
        
        return new NotificationCompat.Builder(context, PlayerConstants.CHANNEL_ID)
            .setContentTitle("OpenTube")
            .setContentText("Loading...")
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }
    
    public void updateMetadata(@NonNull String title, @NonNull String artist, 
                              @Nullable Bitmap artwork) {
        if (isReleased) return;
        
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastMetadataUpdate < METADATA_UPDATE_DEBOUNCE_MS) {
            return;
        }
        lastMetadataUpdate = currentTime;
        
        synchronized (metadataLock) {
            boolean metadataChanged = false;
            
            if (!title.equals(this.currentTitle)) {
                this.currentTitle = title;
                metadataChanged = true;
            }
            
            if (!artist.equals(this.currentArtist)) {
                this.currentArtist = artist;
                metadataChanged = true;
            }
            
            if (artwork != this.currentArtwork) {
                recycleBitmapSafely(this.currentArtwork);
                this.currentArtwork = scaleBitmapSafely(artwork);
                metadataChanged = true;
            }
            
            if (metadataChanged) {
                updateMediaMetadata();
                buildNotification();
                
                if (isNotificationShown) {
                    showNotification();
                }
            }
        }
    }
    
    @Nullable
    private Bitmap scaleBitmapSafely(@Nullable Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            
            if (width <= MAX_BITMAP_SIZE && height <= MAX_BITMAP_SIZE) {
                return source;
            }
            
            float scale = Math.min(
                (float) MAX_BITMAP_SIZE / width,
                (float) MAX_BITMAP_SIZE / height
            );
            
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            
            return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
        } catch (Exception e) {
            return source;
        }
    }
    
    private void recycleBitmapSafely(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                bitmap.recycle();
            } catch (Exception ignored) {
            }
        }
    }
    
    public void updatePlaybackState(boolean playing, long position, long duration) {
        if (isReleased) return;
        
        boolean stateChanged = this.isPlaying != playing;
        this.isPlaying = playing;
        this.currentPosition = position;
        this.duration = duration;
        
        int state = playing ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackStateInternal(state, position, duration);
        
        if (stateChanged) {
            updateMediaMetadata();
            buildNotification();
        }
        
        if (isNotificationShown) {
            showNotificationThrottled();
        }
    }
    
    public void updatePosition(long position) {
        if (isReleased || position < 0) return;
        
        this.currentPosition = position;
        
        int state = isPlaying ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackStateInternal(state, position, duration);
        
        if (isNotificationShown && isPlaying) {
            showNotificationThrottled();
        }
    }
    
    private void showNotificationThrottled() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastNotificationUpdate >= MIN_UPDATE_INTERVAL_MS) {
            showNotification();
            lastNotificationUpdate = currentTime;
        }
    }
    
    public void showNotification() {
        if (notificationManager == null || isReleased) return;
        
        synchronized (notificationLock) {
            try {
                Notification notification = getNotification();
                notificationManager.notify(PlayerConstants.NOTIFICATION_ID, notification);
                isNotificationShown = true;
            } catch (Exception ignored) {
            }
        }
    }
    
    public void cancelNotification() {
        if (notificationManager == null || !isNotificationShown) return;
        
        synchronized (notificationLock) {
            try {
                notificationManager.cancel(PlayerConstants.NOTIFICATION_ID);
                isNotificationShown = false;
            } catch (Exception ignored) {
            }
        }
    }
    
    @Nullable
    private PendingIntent createPendingServiceIntent(String action, int requestCode) {
        Intent intent = createServiceIntent(action);
        if (intent == null) return null;
        
        Context context = getContext();
        if (context == null) return null;
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        try {
            return PendingIntent.getService(context, requestCode, intent, flags);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Nullable
    private PendingIntent createContentIntent() {
        Context context = getContext();
        if (context == null) return null;
        
        Intent intent;
        try {
            Class<?> playerActivityClass = 
                Class.forName("com.nidoham.opentube.player.PlayerActivity");
            intent = new Intent(context, playerActivityClass);
            intent.putExtra("position", currentPosition);
            intent.putExtra("duration", duration);
            intent.putExtra("title", currentTitle);
            intent.putExtra("artist", currentArtist);
            intent.putExtra("from_notification", true);
        } catch (ClassNotFoundException e) {
            intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
            
            if (intent == null) {
                intent = new Intent();
            }
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        try {
            return PendingIntent.getActivity(context, 0, intent, flags);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Nullable
    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession != null && !isReleased ? mediaSession.getSessionToken() : null;
    }
    
    public boolean isPlaying() {
        return isPlaying && !isReleased;
    }
    
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public boolean isShowing() {
        return isNotificationShown && !isReleased;
    }
    
    public void release() {
        if (isReleased) return;
        isReleased = true;
        
        cancelNotification();
        unregisterSeekReceiver();
        
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {
            }
            mediaSession = null;
        }
        
        synchronized (metadataLock) {
            recycleBitmapSafely(currentArtwork);
            currentArtwork = null;
        }
        
        synchronized (notificationLock) {
            notificationBuilder = null;
        }
        
        notificationManager = null;
        contextRef.clear();
    }
}