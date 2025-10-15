package com.nidoham.opentube.player.managers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.nidoham.opentube.R;
import com.nidoham.opentube.util.constant.PlayerConstants;

/**
 * Manages media notification with enhanced UI matching modern Android standards.
 * Supports seek bar, custom actions, and both mobile and Android TV.
 */
public class PlayerNotificationManager {
    private static final String TAG = "PlayerNotificationManager";
    
    private final Context context;
    private final NotificationManager notificationManager;
    private MediaSessionCompat mediaSession;
    
    // Notification state
    private String currentTitle = "Loading...";
    private String currentArtist = "OpenTube";
    private Bitmap currentArtwork;
    private boolean isPlaying = false;
    private long currentPosition = 0;
    private long duration = 0;
    
    public PlayerNotificationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) 
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        initializeMediaSession();
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
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Initialize MediaSession for media controls
     */
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        // Set initial playback state
        updatePlaybackState(PlaybackStateCompat.STATE_NONE, 0, 0);
        
        mediaSession.setActive(true);
    }
    
    /**
     * Update playback state in MediaSession
     */
    private void updatePlaybackState(int state, long position, long duration) {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, position, 1.0f);
        
        mediaSession.setPlaybackState(stateBuilder.build());
    }
    
    /**
     * Create enhanced notification with seek bar and modern UI
     */
    @NonNull
    public Notification createNotification() {
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = 
            new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2) // Previous, Play/Pause, Next
                .setShowCancelButton(true)
                .setCancelButtonIntent(createServiceIntent(PlayerConstants.ACTION_STOP, 99));
        
        int playPauseIcon = isPlaying ? 
            R.drawable.ic_pause : R.drawable.ic_play_arrow;
        String playPauseLabel = isPlaying ? "Pause" : "Play";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PlayerConstants.CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("OpenTube") // App name
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
        
        // Previous button
        builder.addAction(
            R.drawable.ic_skip_previous,
            "Previous",
            createServiceIntent(PlayerConstants.ACTION_PREVIOUS, 1)
        );
        
        // Play/Pause button
        builder.addAction(
            playPauseIcon,
            playPauseLabel,
            createServiceIntent(PlayerConstants.ACTION_PAUSE, 2)
        );
        
        // Next button
        builder.addAction(
            R.drawable.ic_skip_next,
            "Next",
            createServiceIntent(PlayerConstants.ACTION_NEXT, 3)
        );
        
        builder.addAction(
            R.drawable.ic_close,
            "Close",
            createServiceIntent(PlayerConstants.ACTION_STOP, 4)
        );
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(Color.parseColor("#8B0000")); // Dark red/burgundy
        }
        
        int playbackState = isPlaying ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackState(playbackState, currentPosition, duration);
        
        return builder.build();
    }
    
    /**
     * Update notification metadata
     */
    public void updateMetadata(@NonNull String title, @NonNull String artist, 
                              @Nullable Bitmap artwork) {
        this.currentTitle = title;
        this.currentArtist = artist;
        this.currentArtwork = artwork;
        
        MediaMetadataCompat.Builder metadataBuilder = 
            new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "OpenTube")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        
        if (artwork != null) {
            metadataBuilder.putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART, 
                artwork
            );
        }
        
        mediaSession.setMetadata(metadataBuilder.build());
    }
    
    /**
     * Update playback state
     */
    public void updatePlaybackState(boolean playing, long position, long duration) {
        this.isPlaying = playing;
        this.currentPosition = position;
        this.duration = duration;
        
        int state = playing ? 
            PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackState(state, position, duration);
    }
    
    /**
     * Show notification
     */
    public void showNotification() {
        if (notificationManager != null) {
            notificationManager.notify(
                PlayerConstants.NOTIFICATION_ID, 
                createNotification()
            );
        }
    }
    
    /**
     * Cancel notification
     */
    public void cancelNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(PlayerConstants.NOTIFICATION_ID);
        }
    }
    
    /**
     * Create PendingIntent for service actions
     */
    private PendingIntent createServiceIntent(String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
    
    /**
     * Create PendingIntent for opening app
     */
    private PendingIntent createContentIntent() {
        Intent intent = context.getPackageManager()
            .getLaunchIntentForPackage(context.getPackageName());
        
        if (intent == null) {
            intent = new Intent();
        }
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        return PendingIntent.getActivity(context, 0, intent, flags);
    }
    
    /**
     * Get MediaSession token
     */
    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }
    
    /**
     * Release resources
     */
    public void release() {
        cancelNotification();
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        currentArtwork = null;
        
        Log.d(TAG, "PlayerNotificationManager released");
    }
}
