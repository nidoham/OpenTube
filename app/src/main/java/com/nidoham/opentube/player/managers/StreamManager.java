package com.nidoham.opentube.player.managers;

import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.player.streams.StreamInfoCallback;
import com.nidoham.flowtube.player.streams.StreamInfoExtractor;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages stream extraction, caching, and quality selection.
 * Uses LRU cache to prevent unlimited memory growth.
 */
public class StreamManager {
    private static final String TAG = "StreamManager";
    private static final int MAX_CACHE_SIZE = 50; // Maximum cached streams
    private static final int MAX_RETRIES = 3;
    
    private final LruCache<String, StreamData> streamCache;
    private final StreamInfoExtractor streamInfoExtractor;
    
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicBoolean isExtracting = new AtomicBoolean(false);
    
    private String qualityPreference = "720p";
    private StreamExtractionListener extractionListener;
    
    /**
     * Callback interface for stream extraction events
     */
    public interface StreamExtractionListener {
        void onExtractionStarted(String url);
        void onExtractionProgress(String message, int attempt);
        void onExtractionSuccess(StreamInfo streamInfo, StreamData streamData);
        void onExtractionError(String error, Exception exception);
    }
    
    /**
     * Data class to hold stream information
     */
    public static class StreamData {
        public final StreamInfo streamInfo;
        public final List<VideoStream> videoStreams;
        public final List<AudioStream> audioStreams;
        public String selectedVideoUrl;
        public String selectedAudioUrl;
        public String currentQuality;
        
        public StreamData(@NonNull StreamInfo info) {
            this.streamInfo = info;
            this.videoStreams = info.getVideoOnlyStreams();
            this.audioStreams = info.getAudioStreams();
        }
        
        public void selectQuality(@NonNull String qualityPref) {
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
        
        public boolean hasValidStreams() {
            return selectedVideoUrl != null && selectedAudioUrl != null;
        }
    }
    
    public StreamManager() {
        this.streamCache = new LruCache<String, StreamData>(MAX_CACHE_SIZE) {
            @Override
            protected void entryRemoved(boolean evicted, String key, 
                                       StreamData oldValue, StreamData newValue) {
                if (evicted) {
                    Log.d(TAG, "Cache evicted: " + key);
                }
            }
            
            @Override
            protected int sizeOf(String key, StreamData value) {
                return 1; // Count each entry as 1
            }
        };
        
        this.streamInfoExtractor = StreamInfoExtractor.getInstance();
    }
    
    /**
     * Extract stream information with retry logic
     */
    public void extractStreamInfo(@NonNull String videoUrl) {
        if (isExtracting.getAndSet(true)) {
            Log.w(TAG, "Extraction already in progress");
            return;
        }
        
        retryCount.set(0);
        extractStreamInfoInternal(videoUrl);
    }
    
    private void extractStreamInfoInternal(String videoUrl) {
        if (extractionListener != null) {
            extractionListener.onExtractionStarted(videoUrl);
        }
        
        streamInfoExtractor.extractStreamInfo(videoUrl, new StreamInfoCallback() {
            @Override
            public void onLoading() {
                int attempt = retryCount.get() + 1;
                Log.d(TAG, "Extracting stream info (attempt " + attempt + "): " + videoUrl);
                
                if (extractionListener != null) {
                    extractionListener.onExtractionProgress(
                        "Loading stream data...", 
                        attempt
                    );
                }
            }
            
            @Override
            public void onSuccess(StreamInfo streamInfo) {
                isExtracting.set(false);
                
                StreamData streamData = new StreamData(streamInfo);
                streamData.selectQuality(qualityPreference);
                
                streamCache.put(videoUrl, streamData);
                
                Log.d(TAG, "Stream extraction successful: " + streamInfo.getName());
                
                if (extractionListener != null) {
                    extractionListener.onExtractionSuccess(streamInfo, streamData);
                }
            }
            
            @Override
            public void onError(Exception error) {
                int currentRetry = retryCount.incrementAndGet();
                
                if (currentRetry < MAX_RETRIES) {
                    Log.w(TAG, "Extraction failed, retrying... Attempt " + currentRetry, error);
                    
                    if (extractionListener != null) {
                        extractionListener.onExtractionProgress(
                            "Retrying...", 
                            currentRetry + 1
                        );
                    }
                    
                    // Retry after delay
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> extractStreamInfoInternal(videoUrl), 2000);
                } else {
                    isExtracting.set(false);
                    String errorMsg = "Failed to extract stream after " + MAX_RETRIES + 
                                    " attempts: " + error.getMessage();
                    Log.e(TAG, errorMsg, error);
                    
                    if (extractionListener != null) {
                        extractionListener.onExtractionError(errorMsg, error);
                    }
                }
            }
        });
    }
    
    /**
     * Get cached stream data
     */
    @Nullable
    public StreamData getCachedStream(@NonNull String url) {
        return streamCache.get(url);
    }
    
    /**
     * Change quality for cached stream
     */
    public boolean changeQuality(@NonNull String url, @NonNull String newQuality) {
        StreamData streamData = streamCache.get(url);
        if (streamData == null) {
            return false;
        }
        
        qualityPreference = newQuality;
        streamData.selectQuality(newQuality);
        return streamData.hasValidStreams();
    }
    
    /**
     * Get available qualities for a stream
     */
    @Nullable
    public List<String> getAvailableQualities(@NonNull String url) {
        StreamData streamData = streamCache.get(url);
        if (streamData == null || streamData.videoStreams == null) {
            return null;
        }
        
        List<String> qualities = new ArrayList<>();
        for (VideoStream stream : streamData.videoStreams) {
            String quality = stream.getHeight() + "p";
            if (!qualities.contains(quality)) {
                qualities.add(quality);
            }
        }
        return qualities;
    }
    
    // Getters and setters
    public String getQualityPreference() {
        return qualityPreference;
    }
    
    public void setQualityPreference(String quality) {
        this.qualityPreference = quality;
    }
    
    public void setExtractionListener(StreamExtractionListener listener) {
        this.extractionListener = listener;
    }
    
    public boolean isExtracting() {
        return isExtracting.get();
    }
    
    /**
     * Clear cache and release resources
     */
    public void release() {
        streamCache.evictAll();
        
        if (streamInfoExtractor != null) {
            streamInfoExtractor.shutdown();
        }
        
        extractionListener = null;
        isExtracting.set(false);
        retryCount.set(0);
        
        Log.d(TAG, "StreamManager released");
    }
}
