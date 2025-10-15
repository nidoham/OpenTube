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
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.nidoham.opentube.R;
import com.nidoham.opentube.util.constant.PlayerConstants;

/**
 * Manages media notification with enhanced UI matching modern Android standards.
 * Supports seek bar, custom actions, and both mobile and Android TV.
 * This implementation provides the rich media notification style with background artwork
 * and interactive seek bar that appears on Android 11 and above.
 * 
 * Fixed version with working media controls (play, pause, next, previous, seek).
 */
public class PlayerNotificationManager {
    private static final String TAG = "PlayerNotificationManager";
    
    private final Context context;
    private final NotificationManager notificationManager;
    private MediaSessionCompat mediaSession;
    private NotificationCompat.Builder notificationBuilder;
    
    private String currentTitle = "Loading...";
    private String currentArtist = "OpenTube";
    private Bitmap currentArtwork;
    private boolean isPlaying = false;
    private long currentPosition = 0;
    private long duration = 0;
    private boolean isNotificationShown = false;
    private long lastPlaybackStateUpdate = 0;
    private boolean isReceiverRegistered = false;
    
    // Track when we last updated the notification to avoid excessive updates
    private long lastNotificationUpdate = 0;
    private static final long MIN_NOTIFICATION_UPDATE_INTERVAL_MS = 950; // Slightly less than 1 second
    
    /**
     * BroadcastReceiver to handle seek commands from MediaSession.
     * This approach is more reliable than using Service intents.
     */
    private final BroadcastReceiver seekBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PlayerConstants.ACTION_SEEK.equals(intent.getAction())) {
                long position = intent.getLongExtra("position", -1);
                if (position >= 0) {
                    Log.d(TAG, "Seek broadcast received: " + position);
                    currentPosition = position;
                    
                    // Update playback state immediately for UI feedback
                    int state = isPlaying ? 
                        PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                    updatePlaybackStateInternal(state, position, duration);
                }
            }
        }
    };
    
    public PlayerNotificationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) 
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        initializeMediaSession();
        registerSeekReceiver();
    }
    
    
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
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    
    private void registerSeekReceiver() {
        try {
            IntentFilter filter = new IntentFilter(PlayerConstants.ACTION_SEEK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(seekBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(seekBroadcastReceiver, filter);
            }
            isReceiverRegistered = true;
            Log.d(TAG, "Seek broadcast receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering seek receiver", e);
        }
    }
    
    
    private void unregisterSeekReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(seekBroadcastReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Seek broadcast receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered or already unregistered", e);
            }
        }
    }
    
    /**
     * Initialize MediaSession with callbacks for handling media controls.
     * The MediaSession is crucial for enabling the seek bar and modern notification style.
     * 
     * FIXED: Corrected play/pause action handling to properly control playback.
     */
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Log.d(TAG, "MediaSession: onPlay - Resuming playback");
                sendServiceIntent(PlayerConstants.ACTION_PLAY);
            }
            
            @Override
            public void onPause() {
                Log.d(TAG, "MediaSession: onPause - Pausing playback");
                sendServiceIntent(PlayerConstants.ACTION_PAUSE);
            }
            
            @Override
            public void onSkipToNext() {
                Log.d(TAG, "MediaSession: onSkipToNext");
                sendServiceIntent(PlayerConstants.ACTION_NEXT);
            }
            
            @Override
            public void onSkipToPrevious() {
                Log.d(TAG, "MediaSession: onSkipToPrevious");
                sendServiceIntent(PlayerConstants.ACTION_PREVIOUS);
            }
            
            @Override
            public void onSeekTo(long pos) {
                Log.d(TAG, "=== SEEK DEBUG ===");
                Log.d(TAG, "MediaSession onSeekTo called");
                Log.d(TAG, "Seek position: " + pos + " ms");
                Log.d(TAG, "Current duration: " + duration + " ms");
                Log.d(TAG, "Percentage: " + (duration > 0 ? (pos * 100.0 / duration) : 0) + "%");
                
                // Update local position immediately for smooth UI feedback
                currentPosition = pos;
                
                // Send broadcast to notify service about seek
                Intent seekIntent = new Intent(PlayerConstants.ACTION_SEEK);
                seekIntent.setPackage(context.getPackageName());
                seekIntent.putExtra("position", pos);
                context.sendBroadcast(seekIntent);
                
                // Also send service intent as backup
                Intent serviceIntent = new Intent(context, getServiceClass());
                serviceIntent.setAction(PlayerConstants.ACTION_SEEK);
                serviceIntent.putExtra(PlayerConstants.EXTRA_SEEK_POSITION, pos);
                serviceIntent.putExtra("position", pos);
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error starting service for seek", e);
                }
                
                // Update playback state immediately
                int state = isPlaying ? 
                    PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                updatePlaybackStateInternal(state, pos, duration);
                
                Log.d(TAG, "Seek command sent successfully");
            }
            
            @Override
            public void onStop() {
                Log.d(TAG, "MediaSession: onStop");
                sendServiceIntent(PlayerConstants.ACTION_STOP);
            }
        });
        
        updatePlaybackStateInternal(PlaybackStateCompat.STATE_NONE, 0, 0);
        mediaSession.setActive(true);
    }
    
    /**
     * Helper method to send intents to PlayerService.
     * FIXED: Now properly constructs service intents with correct class reference.
     */
    private void sendServiceIntent(String action) {
        Intent intent = new Intent(context, getServiceClass());
        intent.setAction(action);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Service intent sent: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Error starting service with action: " + action, e);
        }
    }
    
    /**
     * Get the PlayerService class dynamically to avoid circular dependencies.
     */
    private Class<?> getServiceClass() {
        try {
            return Class.forName("com.nidoham.opentube.player.PlayerService");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "PlayerService class not found", e);
            throw new RuntimeException("PlayerService class not found", e);
        }
    }
    
    
    private void updatePlaybackStateInternal(int state, long position, long duration) {
        long actions = PlaybackStateCompat.ACTION_PLAY |
                       PlaybackStateCompat.ACTION_PAUSE |
                       PlaybackStateCompat.ACTION_PLAY_PAUSE |
                       PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                       PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                       PlaybackStateCompat.ACTION_SEEK_TO |
                       PlaybackStateCompat.ACTION_STOP;
        
        float playbackSpeed = (state == PlaybackStateCompat.STATE_PLAYING) ? 1.0f : 0.0f;
        
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, playbackSpeed, SystemClock.elapsedRealtime());
        
        mediaSession.setPlaybackState(stateBuilder.build());
        lastPlaybackStateUpdate = SystemClock.elapsedRealtime();
        
        // Log less frequently to avoid log spam
        if (position % 5000 < 1000) { // Log approximately every 5 seconds
            Log.d(TAG, "PlaybackState updated - State: " + state + 
                  ", Position: " + position + ", Duration: " + duration);
        }
    }
    
    
    private void updateMediaMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = 
            new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "OpenTube")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "OpenTube")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        
        if (currentArtwork != null) {
            metadataBuilder
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtwork)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, currentArtwork)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, currentArtwork);
        }
        
        mediaSession.setMetadata(metadataBuilder.build());
    }
    
    
    private void buildNotification() {
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = 
            new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);
        
        int playPauseIcon = isPlaying ? 
            R.drawable.ic_pause : R.drawable.ic_play_arrow;
        String playPauseLabel = isPlaying ? "Pause" : "Play";
        
        notificationBuilder = new NotificationCompat.Builder(context, PlayerConstants.CHANNEL_ID)
            .setStyle(mediaStyle)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("OpenTube")
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setLargeIcon(currentArtwork)
            .setContentIntent(createContentIntent())
            .setDeleteIntent(createServiceIntent(PlayerConstants.ACTION_STOP, 98))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(isPlaying);
        
        notificationBuilder.clearActions();
        
        notificationBuilder.addAction(
            R.drawable.ic_skip_previous,
            "Previous",
            createServiceIntent(PlayerConstants.ACTION_PREVIOUS, 1)
        );
        
        notificationBuilder.addAction(
            playPauseIcon,
            playPauseLabel,
            createServiceIntent(PlayerConstants.ACTION_PAUSE, 2)
        );
        
        notificationBuilder.addAction(
            R.drawable.ic_skip_next,
            "Next",
            createServiceIntent(PlayerConstants.ACTION_NEXT, 3)
        );
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setColor(Color.parseColor("#FF6200EE"));
        }
    }
    
    
    @NonNull
    public Notification getNotification() {
        if (notificationBuilder == null) {
            buildNotification();
        }
        return notificationBuilder.build();
    }
    
    
    public void updateMetadata(@NonNull String title, @NonNull String artist, 
                              @Nullable Bitmap artwork) {
        this.currentTitle = title;
        this.currentArtist = artist;
        this.currentArtwork = artwork;
        
        updateMediaMetadata();
        buildNotification();
        
        if (isNotificationShown) {
            showNotification();
        }
        
        Log.d(TAG, "Metadata updated: " + title + " - " + artist);
    }
    
    
    public void updatePlaybackState(boolean playing, long position, long duration) {
        this.isPlaying = playing;
        this.currentPosition = position;
        this.duration = duration;
        
        int state = playing ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackStateInternal(state, position, duration);
        updateMediaMetadata();
        buildNotification();
        
        if (isNotificationShown) {
            showNotificationThrottled();
        }
    }
    
    
    public void updatePosition(long position) {
        this.currentPosition = position;
        int state = isPlaying ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackStateInternal(state, position, duration);
        
        // Update the notification to reflect new position
        if (isNotificationShown && isPlaying) {
            showNotificationThrottled();
        }
    }
    
    
    private void showNotificationThrottled() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastNotificationUpdate >= MIN_NOTIFICATION_UPDATE_INTERVAL_MS) {
            showNotification();
            lastNotificationUpdate = currentTime;
        }
    }
    
    
    public void showNotification() {
        if (notificationManager != null) {
            if (isNotificationShown) {
                notificationManager.cancel(PlayerConstants.NOTIFICATION_ID);
            }
            
            notificationManager.notify(
                PlayerConstants.NOTIFICATION_ID, 
                getNotification()
            );
            
            isNotificationShown = true;
        }
    }
    
    
    public void cancelNotification() {
        if (notificationManager != null && isNotificationShown) {
            notificationManager.cancel(PlayerConstants.NOTIFICATION_ID);
            isNotificationShown = false;
            Log.d(TAG, "Notification cancelled");
        }
    }
    
    
    private PendingIntent createServiceIntent(String action, int requestCode) {
        Intent intent = new Intent(context, getServiceClass());
        intent.setAction(action);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
    
    
    private PendingIntent createContentIntent() {
        Intent intent = context.getPackageManager()
            .getLaunchIntentForPackage(context.getPackageName());
        
        if (intent == null) {
            intent = new Intent();
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getActivity(context, 0, intent, flags);
    }
    
    
    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession != null ? mediaSession.getSessionToken() : null;
    }
    
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    
    public long getDuration() {
        return duration;
    }
    
    
    public void release() {
        cancelNotification();
        
        unregisterSeekReceiver();
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        if (currentArtwork != null && !currentArtwork.isRecycled()) {
            currentArtwork = null;
        }
        
        notificationBuilder = null;
        
        Log.d(TAG, "PlayerNotificationManager released");
    }
}