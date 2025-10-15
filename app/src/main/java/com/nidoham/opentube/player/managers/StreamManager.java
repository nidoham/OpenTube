package com.nidoham.opentube.player.managers;

import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.stream.data.RxStreamInfoExtractor; // নতুন ইমপোর্ট

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers; // নতুন ইমপোর্ট
import io.reactivex.rxjava3.core.Flowable; // নতুন ইমপোর্ট
import io.reactivex.rxjava3.disposables.CompositeDisposable; // নতুন ইমপোর্ট
import io.reactivex.rxjava3.disposables.Disposable; // নতুন ইমপোর্ট

/**
 * Manages stream extraction, caching, and quality selection using RxJava.
 * Uses LRU cache to prevent unlimited memory growth and handles retries declaratively.
 */
public class StreamManager {
    private static final String TAG = "StreamManager";
    private static final int MAX_CACHE_SIZE = 50; // Maximum cached streams
    private static final int MAX_RETRIES = 3;
    
    private final LruCache<String, StreamData> streamCache;
    private final CompositeDisposable compositeDisposable;
    
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
        this.compositeDisposable = new CompositeDisposable();
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
    }
    
    /**
     * Extract stream information with declarative retry logic using RxJava
     */
    public void extractStreamInfo(@NonNull String videoUrl) {
        if (isExtracting.get()) {
            Log.w(TAG, "Extraction already in progress for another URL");
            return;
        }

        Disposable disposable = RxStreamInfoExtractor.extract(videoUrl)
                .retryWhen(errors -> {
                    AtomicInteger counter = new AtomicInteger();
                    return errors.flatMap(error -> {
                        if (counter.incrementAndGet() > MAX_RETRIES) {
                            return Flowable.error(error); // Retries exhausted, emit error
                        }
                        
                        Log.w(TAG, "Extraction failed, retrying... Attempt " + counter.get(), error);
                        if (extractionListener != null) {
                            // Ensure listener is called on the main thread
                            new android.os.Handler(Looper.getMainLooper()).post(() -> 
                                extractionListener.onExtractionProgress("Retrying...", counter.get() + 1));
                        }
                        
                        // Wait 2 seconds before retrying
                        return Flowable.timer(2, TimeUnit.SECONDS);
                    });
                })
                .observeOn(AndroidSchedulers.mainThread()) // Ensure results are on the main thread
                .doOnSubscribe(d -> {
                    isExtracting.set(true);
                    if (extractionListener != null) {
                        extractionListener.onExtractionStarted(videoUrl);
                    }
                })
                .doFinally(() -> isExtracting.set(false)) // Always reset the flag
                .subscribe(
                    // onSuccess
                    streamInfo -> {
                        StreamData streamData = new StreamData(streamInfo);
                        streamData.selectQuality(qualityPreference);
                        
                        streamCache.put(videoUrl, streamData);
                        
                        Log.d(TAG, "Stream extraction successful: " + streamInfo.getName());
                        if (extractionListener != null) {
                            extractionListener.onExtractionSuccess(streamInfo, streamData);
                        }
                    },
                    // onError
                    error -> {
                        String errorMsg = "Failed to extract stream after " + MAX_RETRIES + 
                                        " attempts: " + error.getMessage();
                        Log.e(TAG, errorMsg, error);
                        if (extractionListener != null) {
                            extractionListener.onExtractionError(errorMsg, (Exception) error);
                        }
                    }
                );
        
        compositeDisposable.add(disposable);
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
     * Clear cache and release resources, including active RxJava subscriptions
     */
    public void release() {
        streamCache.evictAll();
        compositeDisposable.clear(); // Cancel all active subscriptions
        
        extractionListener = null;
        isExtracting.set(false);
        
        Log.d(TAG, "StreamManager released");
    }
}