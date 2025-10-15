package com.nidoham.opentube.util.constant;

import com.nidoham.opentube.BuildConfig;

/**
 * PlayerConstants - Player Service এবং Activity এর মধ্যে যোগাযোগের জন্য সকল Constants
 * 
 * এই ক্লাসে player সংক্রান্ত সকল constant values রাখা হয়েছে যাতে
 * code maintenance সহজ হয় এবং একই জায়গায় সব পাওয়া যায়
 */
public final class PlayerConstants {
    
    // মন্তব্যঃ এই ক্লাসের কোনো instance তৈরি করা যাবে না
    private PlayerConstants() {
        throw new AssertionError("Cannot instantiate PlayerConstants");
    }
    
    // মন্তব্যঃ Base package name - সকল action এর prefix হিসেবে ব্যবহৃত হবে
    private static final String BASE_PACKAGE = BuildConfig.APPLICATION_ID;
    
    // ═══════════════════════════════════════════════════════════════
    // Intent Actions - Service কন্ট্রোল করার জন্য
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Video play শুরু করার action
     */
    public static final String ACTION_PLAY = BASE_PACKAGE + ".ACTION_PLAY";
    
    /**
     * মন্তব্যঃ Video pause করার action
     */
    public static final String ACTION_PAUSE = BASE_PACKAGE + ".ACTION_PAUSE";
    
    /**
     * মন্তব্যঃ Playback সম্পূর্ণভাবে বন্ধ করার action
     */
    public static final String ACTION_STOP = BASE_PACKAGE + ".ACTION_STOP";
    
    /**
     * মন্তব্যঃ Queue এর পরবর্তী video play করার action
     */
    public static final String ACTION_NEXT = BASE_PACKAGE + ".ACTION_NEXT";
    
    /**
     * মন্তব্যঃ Queue এর আগের video play করার action
     */
    public static final String ACTION_PREVIOUS = BASE_PACKAGE + ".ACTION_PREVIOUS";
    
    /**
     * মন্তব্যঃ Video এর নির্দিষ্ট position এ seek করার action
     * এই action টি notification এর seek bar থেকে trigger হয়
     * এবং EXTRA_SEEK_POSITION এর মাধ্যমে target position পাঠানো হয়
     */
    public static final String ACTION_SEEK = BASE_PACKAGE + ".ACTION_SEEK";
    
    /**
     * মন্তব্যঃ Video quality পরিবর্তন করার action
     */
    public static final String ACTION_CHANGE_QUALITY = BASE_PACKAGE + ".ACTION_CHANGE_QUALITY";
    
    // ═══════════════════════════════════════════════════════════════
    // Intent Extra Keys - Data পাঠানোর জন্য
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Play queue object পাঠানোর key
     */
    public static final String EXTRA_PLAY_QUEUE = "extra_play_queue";
    
    /**
     * মন্তব্যঃ Video stream URL পাঠানোর key
     */
    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    
    /**
     * মন্তব্যঃ Audio stream URL পাঠানোর key
     */
    public static final String EXTRA_AUDIO_URL = "extra_audio_url";
    
    /**
     * মন্তব্যঃ Selected quality ID পাঠানোর key
     */
    public static final String EXTRA_QUALITY_ID = "extra_quality_id";
    
    /**
     * মন্তব্যঃ Seek operation এর target position (milliseconds এ)
     * ACTION_SEEK এর সাথে ব্যবহৃত হয়
     */
    public static final String EXTRA_SEEK_POSITION = "extra_seek_position";
    
    // ═══════════════════════════════════════════════════════════════
    // Broadcast Actions এবং Extras - Service থেকে UI তে update পাঠানোর জন্য
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Playback state change broadcast করার action
     */
    public static final String BROADCAST_PLAYBACK_STATE = BASE_PACKAGE + ".PLAYBACK_STATE";
    
    /**
     * মন্তব্যঃ Stream metadata update broadcast করার action
     * (title, uploader, duration, view count, thumbnail ইত্যাদি live update এর জন্য)
     */
    public static final String BROADCAST_METADATA_UPDATE = BASE_PACKAGE + ".METADATA_UPDATE";
    
    /**
     * মন্তব্যঃ Player এর বর্তমান state (playing, paused, stopped)
     */
    public static final String EXTRA_STATE = "extra_state";
    
    /**
     * মন্তব্যঃ Video এর বর্তমান playback position (milliseconds এ)
     */
    public static final String EXTRA_POSITION = "extra_position";
    
    /**
     * মন্তব্যঃ Video এর মোট duration (milliseconds এ)
     */
    public static final String EXTRA_DURATION = "extra_duration";
    
    /**
     * মন্তব্যঃ বর্তমানে play হচ্ছে যে video এর PlayQueueItem
     */
    public static final String EXTRA_CURRENT_ITEM = "extra_current_item";
    
    /**
     * মন্তব্যঃ Queue এ বর্তমান video এর index position
     */
    public static final String EXTRA_QUEUE_INDEX = "extra_queue_index";
    
    /**
     * মন্তব্যঃ Queue এর মোট video সংখ্যা
     */
    public static final String EXTRA_QUEUE_SIZE = "extra_queue_size";
    
    // ═══════════════════════════════════════════════════════════════
    // Metadata Extra Keys - Broadcast এর মাধ্যমে metadata পাঠানোর জন্য
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Video এর title
     */
    public static final String EXTRA_TITLE = "extra_title";
    
    /**
     * মন্তব্যঃ Video uploader এর নাম
     */
    public static final String EXTRA_UPLOADER = "extra_uploader";
    
    /**
     * মন্তব্যঃ Uploader এর channel URL
     */
    public static final String EXTRA_UPLOADER_URL = "extra_uploader_url";
    
    /**
     * মন্তব্যঃ Video এর description/বিবরণ
     */
    public static final String EXTRA_DESCRIPTION = "extra_description";
    
    /**
     * মন্তব্যঃ Video এর view count
     */
    public static final String EXTRA_VIEW_COUNT = "extra_view_count";
    
    /**
     * মন্তব্যঃ Video এর like count
     */
    public static final String EXTRA_LIKE_COUNT = "extra_like_count";
    
    /**
     * মন্তব্যঃ Video এর thumbnail URL
     */
    public static final String EXTRA_THUMBNAIL_URL = "extra_thumbnail_url";
    
    /**
     * মন্তব্যঃ Video upload করার তারিখ
     */
    public static final String EXTRA_UPLOAD_DATE = "extra_upload_date";
    
    /**
     * মন্তব্যঃ Available video qualities এর array (e.g., ["360p", "720p", "1080p"])
     */
    public static final String EXTRA_AVAILABLE_QUALITIES = "extra_available_qualities";
    
    /**
     * মন্তব্যঃ বর্তমানে selected video quality
     */
    public static final String EXTRA_CURRENT_QUALITY = "extra_current_quality";
    
    /**
     * মন্তব্যঃ Metadata loading status message
     */
    public static final String EXTRA_LOADING_STATUS = "extra_loading_status";
    
    /**
     * মন্তব্যঃ Error message (যদি কোনো error হয়)
     */
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";
    
    /**
     * মন্তব্যঃ Metadata loading হচ্ছে কিনা
     */
    public static final String EXTRA_IS_LOADING = "extra_is_loading";
    
    // ═══════════════════════════════════════════════════════════════
    // Notification Configuration
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Player notification এর unique ID
     */
    public static final int NOTIFICATION_ID = 1001;
    
    /**
     * মন্তব্যঃ Notification channel এর ID (Android 8.0+)
     */
    public static final String CHANNEL_ID = "opentube_player_channel";
    
    /**
     * মন্তব্যঃ Notification channel এর user-visible নাম
     */
    public static final String CHANNEL_NAME = "Video Player";
    
    // ═══════════════════════════════════════════════════════════════
    // Player State Constants
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * মন্তব্যঃ Player state যখন video play হচ্ছে
     */
    public static final int STATE_PLAYING = 1;
    
    /**
     * মন্তব্যঃ Player state যখন video pause করা আছে
     */
    public static final int STATE_PAUSED = 2;
    
    /**
     * মন্তব্যঃ Player state যখন video buffering হচ্ছে
     */
    public static final int STATE_BUFFERING = 3;
    
    /**
     * মন্তব্যঃ Player state যখন playback সম্পূর্ণভাবে বন্ধ
     */
    public static final int STATE_STOPPED = 4;
    
    /**
     * মন্তব্যঃ Player state যখন video শেষ হয়ে গেছে
     */
    public static final int STATE_ENDED = 5;
}