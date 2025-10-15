package com.nidoham.opentube.player;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nidoham.newpipe.image.ThumbnailExtractor;
import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;

/**
 * PlayerViewModel - Manages player state and bridges Activity with Service
 * 
 * Features:
 * - Maintains PlayQueue state
 * - Tracks playback state (playing, paused, position, duration)
 * - Manages quality selection
 * - Handles live metadata updates
 * - Retains data across Activity recreation (configuration changes)
 * - Provides LiveData for UI observation
 */
public class PlayerViewModel extends ViewModel {
    
    // ═══════════════════════════════════════════════════════════════
    // PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();
    private final MutableLiveData<PlayQueueItem> currentItem = new MutableLiveData<>();
    
    // ═══════════════════════════════════════════════════════════════
    // Playback State
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<Integer> playerState = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> duration = new MutableLiveData<>(0L);
    
    // ═══════════════════════════════════════════════════════════════
    // Queue Information
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<Integer> queueIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> queueSize = new MutableLiveData<>(0);
    
    // ═══════════════════════════════════════════════════════════════
    // Quality Selection
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<String> selectedQualityId = new MutableLiveData<>("720p");
    private final MutableLiveData<List<String>> availableQualities = new MutableLiveData<>();
    
    // ═══════════════════════════════════════════════════════════════
    // Loading and Error State
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadingStatus = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> queueFinished = new MutableLiveData<>(false);
    
    // ═══════════════════════════════════════════════════════════════
    // Metadata Information (NEW)
    // ═══════════════════════════════════════════════════════════════
    
    private final MutableLiveData<StreamInfo> streamInfo = new MutableLiveData<>();
    private final MutableLiveData<String> videoTitle = new MutableLiveData<>();
    private final MutableLiveData<String> uploaderName = new MutableLiveData<>();
    private final MutableLiveData<String> uploaderUrl = new MutableLiveData<>();
    private final MutableLiveData<String> description = new MutableLiveData<>();
    private final MutableLiveData<Long> viewCount = new MutableLiveData<>();
    private final MutableLiveData<Long> likeCount = new MutableLiveData<>();
    private final MutableLiveData<String> thumbnailUrl = new MutableLiveData<>();
    private final MutableLiveData<String> uploadDate = new MutableLiveData<>();
    private final MutableLiveData<Boolean> metadataLoaded = new MutableLiveData<>(false);
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<PlayQueue> getPlayQueue() {
        return playQueue;
    }
    
    public LiveData<PlayQueueItem> getCurrentItem() {
        return currentItem;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - Playback State
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<Integer> getPlayerState() {
        return playerState;
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    public LiveData<Long> getCurrentPosition() {
        return currentPosition;
    }
    
    public LiveData<Long> getDuration() {
        return duration;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - Queue Information
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<Integer> getQueueIndex() {
        return queueIndex;
    }
    
    public LiveData<Integer> getQueueSize() {
        return queueSize;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - Quality Selection
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<String> getSelectedQualityId() {
        return selectedQualityId;
    }
    
    public LiveData<List<String>> getAvailableQualities() {
        return availableQualities;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - Loading and Error State
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getLoadingStatus() {
        return loadingStatus;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getQueueFinished() {
        return queueFinished;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Getters - Metadata Information (NEW)
    // ═══════════════════════════════════════════════════════════════
    
    public LiveData<StreamInfo> getStreamInfo() {
        return streamInfo;
    }
    
    public LiveData<String> getVideoTitle() {
        return videoTitle;
    }
    
    public LiveData<String> getUploaderName() {
        return uploaderName;
    }
    
    public LiveData<String> getUploaderUrl() {
        return uploaderUrl;
    }
    
    public LiveData<String> getDescription() {
        return description;
    }
    
    public LiveData<Long> getViewCount() {
        return viewCount;
    }
    
    public LiveData<Long> getLikeCount() {
        return likeCount;
    }
    
    public LiveData<String> getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public LiveData<String> getUploadDate() {
        return uploadDate;
    }
    
    public LiveData<Boolean> getMetadataLoaded() {
        return metadataLoaded;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════════
    
    public void setPlayQueue(PlayQueue queue) {
        playQueue.setValue(queue);
        if (queue != null) {
            queueSize.setValue(queue.size());
            queueIndex.setValue(queue.getIndex());
        }
    }
    
    public void setCurrentItem(PlayQueueItem item) {
        currentItem.setValue(item);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - Playback State
    // ═══════════════════════════════════════════════════════════════
    
    public void setPlayerState(int state) {
        playerState.setValue(state);
        isPlaying.setValue(state == 1); // STATE_PLAYING = 1
    }
    
    public void updatePlaybackState(int state, boolean playing, long position, long dur) {
        playerState.setValue(state);
        isPlaying.setValue(playing);
        currentPosition.setValue(position);
        duration.setValue(dur);
    }
    
    public void updatePosition(long position) {
        currentPosition.setValue(position);
    }
    
    public void updateDuration(long dur) {
        duration.setValue(dur);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - Queue Information
    // ═══════════════════════════════════════════════════════════════
    
    public void updateQueueInfo(int index, int size) {
        queueIndex.setValue(index);
        queueSize.setValue(size);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - Quality Selection
    // ═══════════════════════════════════════════════════════════════
    
    public void setSelectedQuality(String qualityId) {
        selectedQualityId.setValue(qualityId);
    }
    
    public void setAvailableQualities(List<String> qualities) {
        availableQualities.setValue(qualities);
    }
    
    public void setAvailableQualities(String[] qualities) {
        if (qualities != null) {
            availableQualities.setValue(java.util.Arrays.asList(qualities));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - Loading and Error State
    // ═══════════════════════════════════════════════════════════════
    
    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
        if (!loading) {
            loadingStatus.setValue(null);
        }
    }
    
    public void setLoadingStatus(String status) {
        loadingStatus.setValue(status);
        isLoading.setValue(status != null);
    }
    
    public void setLoadingMessage(String message) {
        setLoadingStatus(message);
    }
    
    public void setError(String error) {
        errorMessage.setValue(error);
    }
    
    public void clearError() {
        errorMessage.setValue(null);
    }
    
    public void setQueueFinished(boolean finished) {
        queueFinished.setValue(finished);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Setters - Metadata Information (NEW)
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Update all metadata from StreamInfo object
     */
    public void updateMetadata(StreamInfo info) {
        if (info == null) {
            clearMetadata();
            return;
        }
        
        streamInfo.setValue(info);
        videoTitle.setValue(info.getName());
        uploaderName.setValue(info.getUploaderName());
        uploaderUrl.setValue(info.getUploaderUrl());
        description.setValue(info.getDescription() != null ? info.getDescription().getContent() : "");
        viewCount.setValue(info.getViewCount());
        likeCount.setValue(info.getLikeCount());
        
        ThumbnailExtractor thumbnail = new ThumbnailExtractor(info.getThumbnails());
        
        thumbnailUrl.setValue(thumbnail.getThumbnail());
//        uploadDate.setValue(info.getUploadDate());
        metadataLoaded.setValue(true);
    }
    
    /**
     * Update metadata from broadcast intent extras
     */
    public void updateMetadataFromBroadcast(
            String title,
            String uploader,
            String uploaderUrlStr,
            String desc,
            long views,
            long likes,
            String thumbnail,
            String date,
            String[] qualities,
            String currentQuality
    ) {
        videoTitle.setValue(title);
        uploaderName.setValue(uploader);
        uploaderUrl.setValue(uploaderUrlStr);
        description.setValue(desc);
        viewCount.setValue(views);
        likeCount.setValue(likes);
        thumbnailUrl.setValue(thumbnail);
        uploadDate.setValue(date);
        
        if (qualities != null) {
            setAvailableQualities(qualities);
        }
        
        if (currentQuality != null) {
            selectedQualityId.setValue(currentQuality);
        }
        
        metadataLoaded.setValue(true);
    }
    
    /**
     * Set individual metadata fields
     */
    public void setVideoTitle(String title) {
        videoTitle.setValue(title);
    }
    
    public void setUploaderName(String name) {
        uploaderName.setValue(name);
    }
    
    public void setUploaderUrl(String url) {
        uploaderUrl.setValue(url);
    }
    
    public void setDescription(String desc) {
        description.setValue(desc);
    }
    
    public void setViewCount(long count) {
        viewCount.setValue(count);
    }
    
    public void setLikeCount(long count) {
        likeCount.setValue(count);
    }
    
    public void setThumbnailUrl(String url) {
        thumbnailUrl.setValue(url);
    }
    
    public void setUploadDate(String date) {
        uploadDate.setValue(date);
    }
    
    /**
     * Clear all metadata
     */
    public void clearMetadata() {
        streamInfo.setValue(null);
        videoTitle.setValue(null);
        uploaderName.setValue(null);
        uploaderUrl.setValue(null);
        description.setValue(null);
        viewCount.setValue(null);
        likeCount.setValue(null);
        thumbnailUrl.setValue(null);
        uploadDate.setValue(null);
        availableQualities.setValue(null);
        metadataLoaded.setValue(false);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Reset all states
     */
    public void reset() {
        playQueue.setValue(null);
        currentItem.setValue(null);
        playerState.setValue(0);
        isPlaying.setValue(false);
        currentPosition.setValue(0L);
        duration.setValue(0L);
        queueIndex.setValue(0);
        queueSize.setValue(0);
        isLoading.setValue(false);
        loadingStatus.setValue(null);
        errorMessage.setValue(null);
        queueFinished.setValue(false);
        clearMetadata();
    }
    
    /**
     * Check if queue has next item
     */
    public boolean hasNext() {
        Integer index = queueIndex.getValue();
        Integer size = queueSize.getValue();
        return index != null && size != null && index < size - 1;
    }
    
    /**
     * Check if queue has previous item
     */
    public boolean hasPrevious() {
        Integer index = queueIndex.getValue();
        return index != null && index > 0;
    }
    
    /**
     * Get progress percentage (0-100)
     */
    public int getProgressPercentage() {
        Long pos = currentPosition.getValue();
        Long dur = duration.getValue();
        
        if (pos == null || dur == null || dur == 0) {
            return 0;
        }
        
        return (int) ((pos * 100) / dur);
    }
    
    /**
     * Format time in mm:ss or HH:mm:ss format
     */
    public String formatTime(long milliseconds) {
        if (milliseconds < 0) {
            return "00:00";
        }
        
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, remainingSeconds);
        } else {
            return String.format("%02d:%02d", minutes, remainingSeconds);
        }
    }
    
    /**
     * Get formatted current position
     */
    public String getFormattedPosition() {
        Long pos = currentPosition.getValue();
        return formatTime(pos != null ? pos : 0);
    }
    
    /**
     * Get formatted duration
     */
    public String getFormattedDuration() {
        Long dur = duration.getValue();
        return formatTime(dur != null ? dur : 0);
    }
    
    /**
     * Format view count with K/M/B suffix
     */
    public String getFormattedViewCount() {
        Long count = viewCount.getValue();
        if (count == null || count == 0) {
            return "0 views";
        }
        return formatCount(count) + " views";
    }
    
    /**
     * Format like count with K/M/B suffix
     */
    public String getFormattedLikeCount() {
        Long count = likeCount.getValue();
        if (count == null || count == 0) {
            return "0";
        }
        return formatCount(count);
    }
    
    /**
     * Format large numbers with K/M/B suffix
     */
    private String formatCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format("%.1fK", count / 1000.0);
        } else if (count < 1000000000) {
            return String.format("%.1fM", count / 1000000.0);
        } else {
            return String.format("%.1fB", count / 1000000000.0);
        }
    }
    
    /**
     * Check if metadata is available
     */
    public boolean hasMetadata() {
        Boolean loaded = metadataLoaded.getValue();
        return loaded != null && loaded;
    }
    
    /**
     * Get quality display text
     */
    public String getQualityDisplayText() {
        String quality = selectedQualityId.getValue();
        return quality != null ? quality : "Auto";
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // ViewModel is being destroyed, clean up if needed
    }
}