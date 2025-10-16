package com.nidoham.opentube.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.nidoham.newpipe.image.ThumbnailExtractor;
import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Optimized PlayerViewModel - Production-ready state management
 * 
 * Improvements:
 * - Immutable LiveData exposure (encapsulation)
 * - MediatorLiveData for derived states
 * - Null-safe operations throughout
 * - Memory-efficient metadata handling
 * - Thread-safe state updates
 * - Proper resource cleanup
 * - Cached computed values
 * - Enhanced validation
 */
public class PlayerViewModel extends ViewModel {
    
    // ═══════════════════════════════════════════════════════════
    // PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<PlayQueue> _playQueue = new MutableLiveData<>();
    private final LiveData<PlayQueue> playQueue = _playQueue;
    
    private final MutableLiveData<PlayQueueItem> _currentItem = new MutableLiveData<>();
    private final LiveData<PlayQueueItem> currentItem = _currentItem;
    
    // ═══════════════════════════════════════════════════════════
    // Playback State
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<Integer> _playerState = new MutableLiveData<>(0);
    private final LiveData<Integer> playerState = _playerState;
    
    private final MutableLiveData<Boolean> _isPlaying = new MutableLiveData<>(false);
    private final LiveData<Boolean> isPlaying = _isPlaying;
    
    private final MutableLiveData<Long> _currentPosition = new MutableLiveData<>(0L);
    private final LiveData<Long> currentPosition = _currentPosition;
    
    private final MutableLiveData<Long> _duration = new MutableLiveData<>(0L);
    private final LiveData<Long> duration = _duration;
    
    // ═══════════════════════════════════════════════════════════
    // Queue Information
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<Integer> _queueIndex = new MutableLiveData<>(0);
    private final LiveData<Integer> queueIndex = _queueIndex;
    
    private final MutableLiveData<Integer> _queueSize = new MutableLiveData<>(0);
    private final LiveData<Integer> queueSize = _queueSize;
    
    // ═══════════════════════════════════════════════════════════
    // Quality Selection
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<String> _selectedQualityId = new MutableLiveData<>("720p");
    private final LiveData<String> selectedQualityId = _selectedQualityId;
    
    private final MutableLiveData<List<String>> _availableQualities = new MutableLiveData<>();
    private final LiveData<List<String>> availableQualities = _availableQualities;
    
    // ═══════════════════════════════════════════════════════════
    // Loading and Error State
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final LiveData<Boolean> isLoading = _isLoading;
    
    private final MutableLiveData<String> _loadingStatus = new MutableLiveData<>();
    private final LiveData<String> loadingStatus = _loadingStatus;
    
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final LiveData<String> errorMessage = _errorMessage;
    
    private final MutableLiveData<Boolean> _queueFinished = new MutableLiveData<>(false);
    private final LiveData<Boolean> queueFinished = _queueFinished;
    
    // ═══════════════════════════════════════════════════════════
    // Metadata Information
    // ═══════════════════════════════════════════════════════════
    
    private final MutableLiveData<StreamInfo> _streamInfo = new MutableLiveData<>();
    private final LiveData<StreamInfo> streamInfo = _streamInfo;
    
    private final MutableLiveData<String> _videoTitle = new MutableLiveData<>();
    private final LiveData<String> videoTitle = _videoTitle;
    
    private final MutableLiveData<String> _uploaderName = new MutableLiveData<>();
    private final LiveData<String> uploaderName = _uploaderName;
    
    private final MutableLiveData<String> _uploaderUrl = new MutableLiveData<>();
    private final LiveData<String> uploaderUrl = _uploaderUrl;
    
    private final MutableLiveData<String> _description = new MutableLiveData<>();
    private final LiveData<String> description = _description;
    
    private final MutableLiveData<Long> _viewCount = new MutableLiveData<>(0L);
    private final LiveData<Long> viewCount = _viewCount;
    
    private final MutableLiveData<Long> _likeCount = new MutableLiveData<>(0L);
    private final LiveData<Long> likeCount = _likeCount;
    
    private final MutableLiveData<String> _thumbnailUrl = new MutableLiveData<>();
    private final LiveData<String> thumbnailUrl = _thumbnailUrl;
    
    private final MutableLiveData<String> _uploadDate = new MutableLiveData<>();
    private final LiveData<String> uploadDate = _uploadDate;
    
    private final MutableLiveData<Boolean> _metadataLoaded = new MutableLiveData<>(false);
    private final LiveData<Boolean> metadataLoaded = _metadataLoaded;
    
    // ═══════════════════════════════════════════════════════════
    // Derived LiveData (Computed States)
    // ═══════════════════════════════════════════════════════════
    
    private final MediatorLiveData<Integer> progressPercentage = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> hasNextItem = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> hasPreviousItem = new MediatorLiveData<>();
    private final MediatorLiveData<String> formattedPosition = new MediatorLiveData<>();
    private final MediatorLiveData<String> formattedDuration = new MediatorLiveData<>();
    private final MediatorLiveData<String> formattedViewCount = new MediatorLiveData<>();
    private final MediatorLiveData<String> formattedLikeCount = new MediatorLiveData<>();
    private final MediatorLiveData<String> qualityDisplayText = new MediatorLiveData<>();
    
    // ═══════════════════════════════════════════════════════════
    // Cache for expensive computations
    // ═══════════════════════════════════════════════════════════
    
    private volatile long lastPositionUpdate = 0;
    private volatile String cachedFormattedPosition = "00:00";
    private static final long POSITION_UPDATE_THROTTLE_MS = 500;
    
    public PlayerViewModel() {
        super();
        initializeDerivedStates();
    }
    
    // ═══════════════════════════════════════════════════════════
    // Derived State Initialization
    // ═══════════════════════════════════════════════════════════
    
    private void initializeDerivedStates() {
        // Progress Percentage
        progressPercentage.addSource(_currentPosition, pos -> updateProgressPercentage());
        progressPercentage.addSource(_duration, dur -> updateProgressPercentage());
        
        // Has Next/Previous
        hasNextItem.addSource(_queueIndex, idx -> updateNavigationStates());
        hasNextItem.addSource(_queueSize, size -> updateNavigationStates());
        hasPreviousItem.addSource(_queueIndex, idx -> updateNavigationStates());
        
        // Formatted times
        formattedPosition.addSource(_currentPosition, pos -> {
            long now = System.currentTimeMillis();
            if (pos != null && now - lastPositionUpdate > POSITION_UPDATE_THROTTLE_MS) {
                cachedFormattedPosition = formatTime(pos);
                formattedPosition.setValue(cachedFormattedPosition);
                lastPositionUpdate = now;
            }
        });
        
        formattedDuration.addSource(_duration, dur -> 
            formattedDuration.setValue(formatTime(dur != null ? dur : 0L))
        );
        
        // Formatted counts
        formattedViewCount.addSource(_viewCount, count -> 
            formattedViewCount.setValue(formatViewCount(count))
        );
        
        formattedLikeCount.addSource(_likeCount, count -> 
            formattedLikeCount.setValue(formatLikeCount(count))
        );
        
        // Quality display
        qualityDisplayText.addSource(_selectedQualityId, quality -> 
            qualityDisplayText.setValue(quality != null && !quality.isEmpty() ? quality : "Auto")
        );
    }
    
    private void updateProgressPercentage() {
        Long pos = _currentPosition.getValue();
        Long dur = _duration.getValue();
        
        if (pos == null || dur == null || dur == 0) {
            progressPercentage.setValue(0);
            return;
        }
        
        int percentage = (int) ((pos * 100) / dur);
        progressPercentage.setValue(Math.min(100, Math.max(0, percentage)));
    }
    
    private void updateNavigationStates() {
        Integer idx = _queueIndex.getValue();
        Integer size = _queueSize.getValue();
        
        if (idx != null && size != null) {
            hasNextItem.setValue(idx < size - 1);
            hasPreviousItem.setValue(idx > 0);
        } else {
            hasNextItem.setValue(false);
            hasPreviousItem.setValue(false);
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<PlayQueue> getPlayQueue() {
        return playQueue;
    }
    
    @NonNull
    public LiveData<PlayQueueItem> getCurrentItem() {
        return currentItem;
    }
    
    @Nullable
    public PlayQueue getPlayQueueValue() {
        return _playQueue.getValue();
    }
    
    @Nullable
    public PlayQueueItem getCurrentItemValue() {
        return _currentItem.getValue();
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Playback State
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<Integer> getPlayerState() {
        return playerState;
    }
    
    @NonNull
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    @NonNull
    public LiveData<Long> getCurrentPosition() {
        return currentPosition;
    }
    
    @NonNull
    public LiveData<Long> getDuration() {
        return duration;
    }
    
    public int getPlayerStateValue() {
        Integer state = _playerState.getValue();
        return state != null ? state : 0;
    }
    
    public boolean isPlayingValue() {
        Boolean playing = _isPlaying.getValue();
        return playing != null && playing;
    }
    
    public long getCurrentPositionValue() {
        Long pos = _currentPosition.getValue();
        return pos != null ? pos : 0L;
    }
    
    public long getDurationValue() {
        Long dur = _duration.getValue();
        return dur != null ? dur : 0L;
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Queue Information
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<Integer> getQueueIndex() {
        return queueIndex;
    }
    
    @NonNull
    public LiveData<Integer> getQueueSize() {
        return queueSize;
    }
    
    public int getQueueIndexValue() {
        Integer idx = _queueIndex.getValue();
        return idx != null ? idx : 0;
    }
    
    public int getQueueSizeValue() {
        Integer size = _queueSize.getValue();
        return size != null ? size : 0;
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Quality Selection
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<String> getSelectedQualityId() {
        return selectedQualityId;
    }
    
    @NonNull
    public LiveData<List<String>> getAvailableQualities() {
        return availableQualities;
    }
    
    @Nullable
    public String getSelectedQualityValue() {
        return _selectedQualityId.getValue();
    }
    
    @Nullable
    public List<String> getAvailableQualitiesValue() {
        return _availableQualities.getValue();
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Loading and Error State
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    @NonNull
    public LiveData<String> getLoadingStatus() {
        return loadingStatus;
    }
    
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    @NonNull
    public LiveData<Boolean> getQueueFinished() {
        return queueFinished;
    }
    
    public boolean isLoadingValue() {
        Boolean loading = _isLoading.getValue();
        return loading != null && loading;
    }
    
    public boolean hasError() {
        String error = _errorMessage.getValue();
        return error != null && !error.isEmpty();
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Metadata Information
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<StreamInfo> getStreamInfo() {
        return streamInfo;
    }
    
    @NonNull
    public LiveData<String> getVideoTitle() {
        return videoTitle;
    }
    
    @NonNull
    public LiveData<String> getUploaderName() {
        return uploaderName;
    }
    
    @NonNull
    public LiveData<String> getUploaderUrl() {
        return uploaderUrl;
    }
    
    @NonNull
    public LiveData<String> getDescription() {
        return description;
    }
    
    @NonNull
    public LiveData<Long> getViewCount() {
        return viewCount;
    }
    
    @NonNull
    public LiveData<Long> getLikeCount() {
        return likeCount;
    }
    
    @NonNull
    public LiveData<String> getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    @NonNull
    public LiveData<String> getUploadDate() {
        return uploadDate;
    }
    
    @NonNull
    public LiveData<Boolean> getMetadataLoaded() {
        return metadataLoaded;
    }
    
    @Nullable
    public StreamInfo getStreamInfoValue() {
        return _streamInfo.getValue();
    }
    
    public boolean hasMetadata() {
        Boolean loaded = _metadataLoaded.getValue();
        return loaded != null && loaded;
    }
    
    // ═══════════════════════════════════════════════════════════
    // Getters - Derived States
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    public LiveData<Integer> getProgressPercentage() {
        return progressPercentage;
    }
    
    @NonNull
    public LiveData<Boolean> getHasNextItem() {
        return hasNextItem;
    }
    
    @NonNull
    public LiveData<Boolean> getHasPreviousItem() {
        return hasPreviousItem;
    }
    
    @NonNull
    public LiveData<String> getFormattedPosition() {
        return formattedPosition;
    }
    
    @NonNull
    public LiveData<String> getFormattedDuration() {
        return formattedDuration;
    }
    
    @NonNull
    public LiveData<String> getFormattedViewCount() {
        return formattedViewCount;
    }
    
    @NonNull
    public LiveData<String> getFormattedLikeCount() {
        return formattedLikeCount;
    }
    
    @NonNull
    public LiveData<String> getQualityDisplayText() {
        return qualityDisplayText;
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - PlayQueue and Current Item
    // ═══════════════════════════════════════════════════════════
    
    public void setPlayQueue(@Nullable PlayQueue queue) {
        _playQueue.setValue(queue);
        if (queue != null) {
            _queueSize.setValue(queue.size());
            _queueIndex.setValue(queue.getIndex());
        } else {
            _queueSize.setValue(0);
            _queueIndex.setValue(0);
        }
    }
    
    public void setCurrentItem(@Nullable PlayQueueItem item) {
        _currentItem.setValue(item);
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - Playback State
    // ═══════════════════════════════════════════════════════════
    
    public void setPlayerState(int state) {
        if (state < 0) return;
        _playerState.setValue(state);
        _isPlaying.setValue(state == 1);
    }
    
    public void updatePlaybackState(int state, boolean playing, long position, long dur) {
        if (state >= 0) {
            _playerState.setValue(state);
        }
        _isPlaying.setValue(playing);
        
        if (position >= 0) {
            _currentPosition.setValue(position);
        }
        if (dur >= 0) {
            _duration.setValue(dur);
        }
    }
    
    public void updatePosition(long position) {
        if (position >= 0) {
            _currentPosition.setValue(position);
        }
    }
    
    public void updateDuration(long dur) {
        if (dur >= 0) {
            _duration.setValue(dur);
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - Queue Information
    // ═══════════════════════════════════════════════════════════
    
    public void updateQueueInfo(int index, int size) {
        if (index >= 0 && size >= 0) {
            _queueIndex.setValue(index);
            _queueSize.setValue(size);
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - Quality Selection
    // ═══════════════════════════════════════════════════════════
    
    public void setSelectedQuality(@Nullable String qualityId) {
        _selectedQualityId.setValue(qualityId != null ? qualityId : "720p");
    }
    
    public void setAvailableQualities(@Nullable List<String> qualities) {
        _availableQualities.setValue(qualities != null ? 
            new ArrayList<>(qualities) : null);
    }
    
    public void setAvailableQualities(@Nullable String[] qualities) {
        if (qualities != null && qualities.length > 0) {
            _availableQualities.setValue(Arrays.asList(qualities));
        } else {
            _availableQualities.setValue(null);
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - Loading and Error State
    // ═══════════════════════════════════════════════════════════
    
    public void setLoading(boolean loading) {
        _isLoading.setValue(loading);
        if (!loading) {
            _loadingStatus.setValue(null);
        }
    }
    
    public void setLoadingStatus(@Nullable String status) {
        _loadingStatus.setValue(status);
        _isLoading.setValue(status != null && !status.isEmpty());
    }
    
    public void setLoadingMessage(@Nullable String message) {
        setLoadingStatus(message);
    }
    
    public void setError(@Nullable String error) {
        _errorMessage.setValue(error);
    }
    
    public void clearError() {
        _errorMessage.setValue(null);
    }
    
    public void setQueueFinished(boolean finished) {
        _queueFinished.setValue(finished);
    }
    
    // ═══════════════════════════════════════════════════════════
    // Setters - Metadata Information
    // ═══════════════════════════════════════════════════════════
    
    public void updateMetadata(@Nullable StreamInfo info) {
        if (info == null) {
            clearMetadata();
            return;
        }
        
        try {
            _streamInfo.setValue(info);
            _videoTitle.setValue(info.getName());
            _uploaderName.setValue(info.getUploaderName());
            _uploaderUrl.setValue(info.getUploaderUrl());
            
            String desc = info.getDescription() != null ? 
                info.getDescription().getContent() : "";
            _description.setValue(desc);
            
            _viewCount.setValue(Math.max(0, info.getViewCount()));
            _likeCount.setValue(Math.max(0, info.getLikeCount()));
            
            if (info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
                ThumbnailExtractor thumbnail = new ThumbnailExtractor(info.getThumbnails());
                _thumbnailUrl.setValue(thumbnail.getThumbnail());
            }
            
            _metadataLoaded.setValue(true);
        } catch (Exception e) {
            _metadataLoaded.setValue(false);
        }
    }
    
    public void updateMetadataFromBroadcast(
            @Nullable String title,
            @Nullable String uploader,
            @Nullable String uploaderUrlStr,
            @Nullable String desc,
            long views,
            long likes,
            @Nullable String thumbnail,
            @Nullable String date,
            @Nullable String[] qualities,
            @Nullable String currentQuality
    ) {
        _videoTitle.setValue(title);
        _uploaderName.setValue(uploader);
        _uploaderUrl.setValue(uploaderUrlStr);
        _description.setValue(desc);
        _viewCount.setValue(Math.max(0, views));
        _likeCount.setValue(Math.max(0, likes));
        _thumbnailUrl.setValue(thumbnail);
        _uploadDate.setValue(date);
        
        if (qualities != null && qualities.length > 0) {
            setAvailableQualities(qualities);
        }
        
        if (currentQuality != null && !currentQuality.isEmpty()) {
            _selectedQualityId.setValue(currentQuality);
        }
        
        _metadataLoaded.setValue(true);
    }
    
    public void setVideoTitle(@Nullable String title) {
        _videoTitle.setValue(title);
    }
    
    public void setUploaderName(@Nullable String name) {
        _uploaderName.setValue(name);
    }
    
    public void setUploaderUrl(@Nullable String url) {
        _uploaderUrl.setValue(url);
    }
    
    public void setDescription(@Nullable String desc) {
        _description.setValue(desc);
    }
    
    public void setViewCount(long count) {
        _viewCount.setValue(Math.max(0, count));
    }
    
    public void setLikeCount(long count) {
        _likeCount.setValue(Math.max(0, count));
    }
    
    public void setThumbnailUrl(@Nullable String url) {
        _thumbnailUrl.setValue(url);
    }
    
    public void setUploadDate(@Nullable String date) {
        _uploadDate.setValue(date);
    }
    
    public void clearMetadata() {
        _streamInfo.setValue(null);
        _videoTitle.setValue(null);
        _uploaderName.setValue(null);
        _uploaderUrl.setValue(null);
        _description.setValue(null);
        _viewCount.setValue(0L);
        _likeCount.setValue(0L);
        _thumbnailUrl.setValue(null);
        _uploadDate.setValue(null);
        _availableQualities.setValue(null);
        _metadataLoaded.setValue(false);
    }
    
    // ═══════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════
    
    public void reset() {
        _playQueue.setValue(null);
        _currentItem.setValue(null);
        _playerState.setValue(0);
        _isPlaying.setValue(false);
        _currentPosition.setValue(0L);
        _duration.setValue(0L);
        _queueIndex.setValue(0);
        _queueSize.setValue(0);
        _isLoading.setValue(false);
        _loadingStatus.setValue(null);
        _errorMessage.setValue(null);
        _queueFinished.setValue(false);
        clearMetadata();
    }
    
    public boolean hasNext() {
        Boolean hasNext = hasNextItem.getValue();
        return hasNext != null && hasNext;
    }
    
    public boolean hasPrevious() {
        Boolean hasPrev = hasPreviousItem.getValue();
        return hasPrev != null && hasPrev;
    }
    
    public int getProgressPercentageValue() {
        Integer progress = progressPercentage.getValue();
        return progress != null ? progress : 0;
    }
    
    @NonNull
    public String getFormattedPositionValue() {
        return cachedFormattedPosition;
    }
    
    @NonNull
    public String getFormattedDurationValue() {
        String formatted = formattedDuration.getValue();
        return formatted != null ? formatted : "00:00";
    }
    
    @NonNull
    public String getFormattedViewCountValue() {
        String formatted = formattedViewCount.getValue();
        return formatted != null ? formatted : "0 views";
    }
    
    @NonNull
    public String getFormattedLikeCountValue() {
        String formatted = formattedLikeCount.getValue();
        return formatted != null ? formatted : "0";
    }
    
    @NonNull
    public String getQualityDisplayTextValue() {
        String display = qualityDisplayText.getValue();
        return display != null ? display : "Auto";
    }
    
    // ═══════════════════════════════════════════════════════════
    // Formatting Utilities
    // ═══════════════════════════════════════════════════════════
    
    @NonNull
    private String formatTime(long milliseconds) {
        if (milliseconds < 0) {
            return "00:00";
        }
        
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds);
        }
    }
    
    @NonNull
    private String formatViewCount(@Nullable Long count) {
        if (count == null || count <= 0) {
            return "0 views";
        }
        return formatCount(count) + " views";
    }
    
    @NonNull
    private String formatLikeCount(@Nullable Long count) {
        if (count == null || count <= 0) {
            return "0";
        }
        return formatCount(count);
    }
    
    @NonNull
    private String formatCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            double value = count / 1000.0;
            return String.format(Locale.US, value % 1 == 0 ? "%.0fK" : "%.1fK", value);
        } else if (count < 1000000000) {
            double value = count / 1000000.0;
            return String.format(Locale.US, value % 1 == 0 ? "%.0fM" : "%.1fM", value);
        } else {
            double value = count / 1000000000.0;
            return String.format(Locale.US, value % 1 == 0 ? "%.0fB" : "%.1fB", value);
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Remove all sources from MediatorLiveData to prevent leaks
        progressPercentage.removeSource(_currentPosition);
        progressPercentage.removeSource(_duration);
        hasNextItem.removeSource(_queueIndex);
        hasNextItem.removeSource(_queueSize);
        hasPreviousItem.removeSource(_queueIndex);
        formattedPosition.removeSource(_currentPosition);
        formattedDuration.removeSource(_duration);
        formattedViewCount.removeSource(_viewCount);
        formattedLikeCount.removeSource(_likeCount);
        qualityDisplayText.removeSource(_selectedQualityId);
    }
}