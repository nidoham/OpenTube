package com.nidoham.opentube.player.managers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nidoham.flowtube.player.playqueue.PlayQueue;
import com.nidoham.flowtube.player.playqueue.PlayQueueItem;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages play queue operations and navigation.
 * Handles queue state, item navigation, and callbacks.
 */
public class QueueManager {
    private static final String TAG = "QueueManager";
    
    private PlayQueue playQueue;
    private QueueListener queueListener;
    
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    /**
     * Callback interface for queue events
     */
    public interface QueueListener {
        void onQueueChanged(int index, int size);
        void onCurrentItemChanged(PlayQueueItem item);
        void onQueueFinished();
    }
    
    public QueueManager() {
    }
    
    /**
     * Set new play queue
     */
    public void setQueue(@NonNull PlayQueue queue) {
        this.playQueue = queue;
        currentIndex.set(queue.getIndex());
        
        Log.d(TAG, "Queue set with " + queue.size() + " items");
        
        if (queueListener != null) {
            queueListener.onQueueChanged(currentIndex.get(), queue.size());
            
            PlayQueueItem item = getCurrentItem();
            if (item != null) {
                queueListener.onCurrentItemChanged(item);
            }
        }
    }
    
    /**
     * Move to next item in queue
     */
    public boolean next() {
        if (playQueue == null) {
            Log.w(TAG, "Cannot move to next: Queue is null");
            return false;
        }
        
        if (currentIndex.get() < playQueue.size() - 1) {
            playQueue.next();
            currentIndex.incrementAndGet();
            
            Log.d(TAG, "Moved to next item: " + currentIndex.get());
            
            if (queueListener != null) {
                queueListener.onQueueChanged(currentIndex.get(), playQueue.size());
                
                PlayQueueItem item = getCurrentItem();
                if (item != null) {
                    queueListener.onCurrentItemChanged(item);
                }
            }
            
            return true;
        } else {
            Log.d(TAG, "Reached end of queue");
            
            if (queueListener != null) {
                queueListener.onQueueFinished();
            }
            
            return false;
        }
    }
    
    /**
     * Move to previous item in queue
     */
    public boolean previous() {
        if (playQueue == null) {
            Log.w(TAG, "Cannot move to previous: Queue is null");
            return false;
        }
        
        if (currentIndex.get() > 0) {
            playQueue.previous();
            currentIndex.decrementAndGet();
            
            Log.d(TAG, "Moved to previous item: " + currentIndex.get());
            
            if (queueListener != null) {
                queueListener.onQueueChanged(currentIndex.get(), playQueue.size());
                
                PlayQueueItem item = getCurrentItem();
                if (item != null) {
                    queueListener.onCurrentItemChanged(item);
                }
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Jump to specific index
     */
    public boolean jumpTo(int index) {
        if (playQueue == null || index < 0 || index >= playQueue.size()) {
            return false;
        }
        
        // Update queue index
        while (playQueue.getIndex() < index) {
            playQueue.next();
        }
        while (playQueue.getIndex() > index) {
            playQueue.previous();
        }
        
        currentIndex.set(index);
        
        if (queueListener != null) {
            queueListener.onQueueChanged(currentIndex.get(), playQueue.size());
            
            PlayQueueItem item = getCurrentItem();
            if (item != null) {
                queueListener.onCurrentItemChanged(item);
            }
        }
        
        return true;
    }
    
    // Getters
    @Nullable
    public PlayQueue getQueue() {
        return playQueue;
    }
    
    @Nullable
    public PlayQueueItem getCurrentItem() {
        return playQueue != null ? playQueue.getItem() : null;
    }
    
    public int getCurrentIndex() {
        return currentIndex.get();
    }
    
    public int getQueueSize() {
        return playQueue != null ? playQueue.size() : 0;
    }
    
    public boolean hasNext() {
        return playQueue != null && currentIndex.get() < playQueue.size() - 1;
    }
    
    public boolean hasPrevious() {
        return playQueue != null && currentIndex.get() > 0;
    }
    
    public boolean isEmpty() {
        return playQueue == null || playQueue.isEmpty();
    }
    
    public void setQueueListener(QueueListener listener) {
        this.queueListener = listener;
    }
    
    /**
     * Release resources
     */
    public void release() {
        playQueue = null;
        queueListener = null;
        currentIndex.set(0);
        
        Log.d(TAG, "QueueManager released");
    }
}
