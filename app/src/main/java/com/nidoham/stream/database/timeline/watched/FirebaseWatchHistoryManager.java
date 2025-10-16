package com.nidoham.stream.database.timeline.watched;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.nidoham.stream.database.timeline.watched.model.WatchHistory;
import com.nidoham.stream.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FirebaseWatchHistoryManager {
    private static final String TIMELINE_NODE = "timeline";
    private static final String WATCH_HISTORY_NODE = "watch_history";
    private static final int MAX_HISTORY_LIMIT = 50;
    
    private final String userId;
    private final DatabaseReference historyRef;

    public FirebaseWatchHistoryManager() {
        this.userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        this.historyRef = database.getReference(TIMELINE_NODE)
                .child(WATCH_HISTORY_NODE)
                .child(userId);
    }

    public void insertOrUpdateHistory(long currentDuration, @NonNull PlayQueueItem queue) {
        if (queue == null || queue.getUrl() == null) {
            return;
        }

        getHistoryByUrl(queue.getUrl(), new SingleHistoryCallback() {
            @Override
            public void onSuccess(@Nullable WatchHistory existingHistory) {
                if (existingHistory != null) {
                    updateHistoryEntry(existingHistory.getHistoryId(), currentDuration);
                } else {
                    createNewHistoryEntry(currentDuration, queue);
                }
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                createNewHistoryEntry(currentDuration, queue);
            }
        });
    }

    private void createNewHistoryEntry(long currentDuration, @NonNull PlayQueueItem queue) {
        String historyId = historyRef.push().getKey();
        if (historyId == null) {
            return;
        }

        WatchHistory history = WatchHistory.fromPlayQueueItem(queue);
        history.setHistoryId(historyId);
        history.setLastDuration(currentDuration);
        history.setStandByTime(System.currentTimeMillis());

        historyRef.child(historyId).setValue(history);
    }

    private void updateHistoryEntry(@NonNull String historyId, long currentDuration) {
        historyRef.child(historyId).child("lastDuration").setValue(currentDuration);
        historyRef.child(historyId).child("standByTime").setValue(System.currentTimeMillis());
    }

    public void removeFromHistory(@NonNull String historyId) {
        if (historyId == null || historyId.isEmpty()) {
            return;
        }
        historyRef.child(historyId).removeValue();
    }

    public void clearAllHistory() {
        historyRef.removeValue();
    }

    public void getWatchHistory(@NonNull HistoryCallback callback) {
        Query query = historyRef.orderByChild("standByTime")
                .limitToLast(MAX_HISTORY_LIMIT);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<WatchHistory> historyList = new ArrayList<>();
                
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    WatchHistory history = childSnapshot.getValue(WatchHistory.class);
                    if (history != null) {
                        historyList.add(history);
                    }
                }

                Collections.reverse(historyList);
                callback.onSuccess(historyList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getHistoryByUrl(@NonNull String videoUrl, @NonNull SingleHistoryCallback callback) {
        Query query = historyRef.orderByChild("url").equalTo(videoUrl).limitToFirst(1);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                        WatchHistory history = childSnapshot.getValue(WatchHistory.class);
                        callback.onSuccess(history);
                        return;
                    }
                }
                callback.onSuccess(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void addHistoryListener(@NonNull ValueEventListener listener) {
        Query query = historyRef.orderByChild("standByTime").limitToLast(MAX_HISTORY_LIMIT);
        query.addValueEventListener(listener);
    }

    public void removeHistoryListener(@NonNull ValueEventListener listener) {
        historyRef.removeEventListener(listener);
    }

    @NonNull
    public DatabaseReference getHistoryRef() {
        return historyRef;
    }

    public interface HistoryCallback {
        void onSuccess(@NonNull List<WatchHistory> historyList);
        void onError(@NonNull String errorMessage);
    }

    public interface SingleHistoryCallback {
        void onSuccess(@Nullable WatchHistory history);
        void onError(@NonNull String errorMessage);
    }
}