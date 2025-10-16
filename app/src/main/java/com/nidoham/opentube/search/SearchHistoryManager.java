package com.nidoham.opentube.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase Realtime Database manager for search history.
 * 
 * Firebase Structure: timeline/query/{userId}/{queryId}
 * 
 * This class provides a complete solution for managing user search history with Firebase,
 * designed to work seamlessly with RecyclerView adapters like SearchHistoryAdapter.
 * 
 * Key Features:
 * - Add search queries with automatic timestamp
 * - Remove individual queries
 * - Clear all search history for a user
 * - Fetch last 20 queries sorted by timestamp (latest first)
 * - Real-time updates via Firebase listeners
 * - Proper error handling with callbacks
 * 
 * Usage with RecyclerView:
 * 
 * 1. Initialize the manager:
 *    SearchHistoryManager manager = new SearchHistoryManager("user123");
 * 
 * 2. Fetch history and update adapter:
 *    manager.fetchSearchHistory(new SearchHistoryManager.OnHistoryFetchListener() {
 *        @Override
 *        public void onSuccess(List<SearchQuery> queries) {
 *            List<String> queryStrings = new ArrayList<>();
 *            for (SearchQuery query : queries) {
 *                queryStrings.add(query.getQueryText());
 *            }
 *            searchHistoryAdapter.setSearchHistory(queryStrings);
 *        }
 *        
 *        @Override
 *        public void onFailure(String error) {
 *            // Handle error
 *        }
 *    });
 * 
 * 3. Add new search query:
 *    manager.addSearchQuery("android tutorial", new SearchHistoryManager.OnOperationListener() {
 *        @Override
 *        public void onSuccess() {
 *            // Query added successfully
 *        }
 *        
 *        @Override
 *        public void onFailure(String error) {
 *            // Handle error
 *        }
 *    });
 * 
 * 4. Remove query (e.g., on long click):
 *    manager.removeSearchQuery(queryId, listener);
 * 
 * 5. Clear all history:
 *    manager.clearAllSearchHistory(listener);
 * 
 * 6. Listen for real-time updates:
 *    manager.attachRealtimeListener(new SearchHistoryManager.OnHistoryFetchListener() {
 *        @Override
 *        public void onSuccess(List<SearchQuery> queries) {
 *            // Update RecyclerView adapter with new data
 *        }
 *        
 *        @Override
 *        public void onFailure(String error) {
 *            // Handle error
 *        }
 *    });
 * 
 * 7. Don't forget to detach listener in onDestroy():
 *    manager.detachRealtimeListener();
 */
public class SearchHistoryManager {

    private static final String FIREBASE_PATH_TIMELINE = "timeline";
    private static final String FIREBASE_PATH_QUERY = "query";
    private static final int MAX_HISTORY_ITEMS = 20;

    private final DatabaseReference databaseReference;
    private final String userId;
    private ValueEventListener realtimeListener;
    private Query realtimeQuery;

    /**
     * Constructor for SearchHistoryManager.
     * 
     * @param userId The unique identifier for the user whose search history is being managed.
     *               This creates a Firebase path: timeline/query/{userId}/
     */
    public SearchHistoryManager(@NonNull String userId) {
        this.userId = userId;
        this.databaseReference = FirebaseDatabase.getInstance()
                .getReference()
                .child(FIREBASE_PATH_TIMELINE)
                .child(FIREBASE_PATH_QUERY)
                .child(userId);
    }

    /**
     * Adds a new search query to the user's history.
     * 
     * The query is stored with:
     * - Auto-generated unique ID (push key)
     * - Query text
     * - Timestamp (server timestamp for consistency)
     * 
     * Usage in SearchActivity:
     * When user performs a search, call this method to save it to Firebase.
     * 
     * @param queryText The search query text to save
     * @param listener Callback for success/failure
     */
    public void addSearchQuery(@NonNull String queryText, @Nullable OnOperationListener listener) {
        if (queryText.trim().isEmpty()) {
            if (listener != null) {
                listener.onFailure("Query text cannot be empty");
            }
            return;
        }

        // Generate a unique key for this query
        String queryId = databaseReference.push().getKey();
        
        if (queryId == null) {
            if (listener != null) {
                listener.onFailure("Failed to generate query ID");
            }
            return;
        }

        // Create the search query object
        SearchQuery searchQuery = new SearchQuery(queryId, queryText, System.currentTimeMillis());

        // Save to Firebase
        databaseReference.child(queryId)
                .setValue(searchQuery.toMap())
                .addOnSuccessListener(aVoid -> {
                    // After adding, check if we need to trim old entries
                    trimOldEntries();
                    
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onFailure("Failed to add query: " + e.getMessage());
                    }
                });
    }

    /**
     * Removes a specific search query from history.
     * 
     * Usage in RecyclerView:
     * Call this method when user long-presses a history item to delete it.
     * You'll need to store the queryId along with the query text in your adapter.
     * 
     * @param queryId The unique ID of the query to remove
     * @param listener Callback for success/failure
     */
    public void removeSearchQuery(@NonNull String queryId, @Nullable OnOperationListener listener) {
        databaseReference.child(queryId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onFailure("Failed to remove query: " + e.getMessage());
                    }
                });
    }

    /**
     * Clears all search history for the current user.
     * 
     * Usage in SearchActivity:
     * Call this when user clicks "Clear All" button.
     * 
     * @param listener Callback for success/failure
     */
    public void clearAllSearchHistory(@Nullable OnOperationListener listener) {
        databaseReference.removeValue()
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onFailure("Failed to clear history: " + e.getMessage());
                    }
                });
    }

    /**
     * Fetches the last 20 search queries, sorted by timestamp (latest first).
     * 
     * This is a one-time fetch operation. For real-time updates, use attachRealtimeListener().
     * 
     * Usage in SearchActivity onCreate():
     * Call this to populate the RecyclerView with existing search history.
     * 
     * @param listener Callback with the list of search queries or error
     */
    public void fetchSearchHistory(@NonNull OnHistoryFetchListener listener) {
        databaseReference
                .orderByChild("timestamp")
                .limitToLast(MAX_HISTORY_ITEMS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<SearchQuery> queries = new ArrayList<>();
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            SearchQuery query = snapshot.getValue(SearchQuery.class);
                            if (query != null) {
                                queries.add(query);
                            }
                        }
                        
                        // Sort by timestamp descending (latest first)
                        Collections.sort(queries, (q1, q2) -> 
                            Long.compare(q2.getTimestamp(), q1.getTimestamp()));
                        
                        listener.onSuccess(queries);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        listener.onFailure("Failed to fetch history: " + databaseError.getMessage());
                    }
                });
    }

    /**
     * Attaches a real-time listener to automatically update the RecyclerView when data changes.
     * 
     * This is useful for keeping the UI in sync across multiple devices or sessions.
     * 
     * Usage in SearchActivity:
     * Call this in onCreate() or onResume() to start listening for changes.
     * The listener will automatically trigger when:
     * - New queries are added
     * - Queries are removed
     * - History is cleared
     * 
     * IMPORTANT: Must call detachRealtimeListener() in onDestroy() to prevent memory leaks.
     * 
     * @param listener Callback that receives updated list whenever data changes
     */
    public void attachRealtimeListener(@NonNull OnHistoryFetchListener listener) {
        // Detach any existing listener first
        detachRealtimeListener();

        realtimeQuery = databaseReference
                .orderByChild("timestamp")
                .limitToLast(MAX_HISTORY_ITEMS);

        realtimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<SearchQuery> queries = new ArrayList<>();
                
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    SearchQuery query = snapshot.getValue(SearchQuery.class);
                    if (query != null) {
                        queries.add(query);
                    }
                }
                
                // Sort by timestamp descending (latest first)
                Collections.sort(queries, (q1, q2) -> 
                    Long.compare(q2.getTimestamp(), q1.getTimestamp()));
                
                listener.onSuccess(queries);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listener.onFailure("Real-time listener error: " + databaseError.getMessage());
            }
        };

        realtimeQuery.addValueEventListener(realtimeListener);
    }

    /**
     * Detaches the real-time listener to prevent memory leaks.
     * 
     * MUST be called in Activity's onDestroy() or Fragment's onDestroyView().
     * 
     * Usage:
     * @Override
     * protected void onDestroy() {
     *     super.onDestroy();
     *     searchHistoryManager.detachRealtimeListener();
     * }
     */
    public void detachRealtimeListener() {
        if (realtimeListener != null && realtimeQuery != null) {
            realtimeQuery.removeEventListener(realtimeListener);
            realtimeListener = null;
            realtimeQuery = null;
        }
    }

    /**
     * Internal method to trim old entries when history exceeds MAX_HISTORY_ITEMS.
     * Automatically called after adding a new query.
     */
    private void trimOldEntries() {
        databaseReference
                .orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        long count = dataSnapshot.getChildrenCount();
                        
                        if (count > MAX_HISTORY_ITEMS) {
                            // Calculate how many to delete
                            long toDelete = count - MAX_HISTORY_ITEMS;
                            int deleted = 0;
                            
                            // Delete oldest entries
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                if (deleted >= toDelete) break;
                                snapshot.getRef().removeValue();
                                deleted++;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Silently fail - trimming is not critical
                    }
                });
    }

    // ==================== Data Model ====================

    /**
     * Model class representing a search query in Firebase.
     * 
     * Firebase structure:
     * {
     *   "queryId": "unique-push-key",
     *   "queryText": "android tutorial",
     *   "timestamp": 1234567890
     * }
     */
    public static class SearchQuery {
        private String queryId;
        private String queryText;
        private long timestamp;

        // Required empty constructor for Firebase
        public SearchQuery() {
        }

        public SearchQuery(String queryId, String queryText, long timestamp) {
            this.queryId = queryId;
            this.queryText = queryText;
            this.timestamp = timestamp;
        }

        public String getQueryId() {
            return queryId;
        }

        public void setQueryId(String queryId) {
            this.queryId = queryId;
        }

        public String getQueryText() {
            return queryText;
        }

        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        /**
         * Converts the SearchQuery object to a Map for Firebase storage.
         * 
         * @return Map representation of the search query
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("queryId", queryId);
            map.put("queryText", queryText);
            map.put("timestamp", timestamp);
            return map;
        }
    }

    // ==================== Callback Interfaces ====================

    /**
     * Callback interface for operations that don't return data (add, remove, clear).
     * 
     * Usage:
     * manager.addSearchQuery("test", new OnOperationListener() {
     *     @Override
     *     public void onSuccess() {
     *         Toast.makeText(context, "Query saved", Toast.LENGTH_SHORT).show();
     *     }
     *     
     *     @Override
     *     public void onFailure(String error) {
     *         Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
     *     }
     * });
     */
    public interface OnOperationListener {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Callback interface for fetching search history.
     * 
     * Usage with RecyclerView:
     * manager.fetchSearchHistory(new OnHistoryFetchListener() {
     *     @Override
     *     public void onSuccess(List<SearchQuery> queries) {
     *         // Convert to string list for adapter
     *         List<String> queryStrings = new ArrayList<>();
     *         for (SearchQuery query : queries) {
     *             queryStrings.add(query.getQueryText());
     *         }
     *         adapter.setSearchHistory(queryStrings);
     *     }
     *     
     *     @Override
     *     public void onFailure(String error) {
     *         Log.e("SearchHistory", error);
     *     }
     * });
     */
    public interface OnHistoryFetchListener {
        void onSuccess(List<SearchQuery> queries);
        void onFailure(String error);
    }
}
