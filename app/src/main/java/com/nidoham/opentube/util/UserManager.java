package com.nidoham.opentube.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * UserManager - Manages user identification and authentication
 * 
 * Uses Firebase Authentication for user management and tracking.
 * Provides consistent user ID across app sessions and devices.
 */
public class UserManager {
    
    private static final String PREFS_NAME = "opentube_user_prefs";
    private static final String KEY_LAST_USER_ID = "last_user_id";
    
    private static UserManager instance;
    private final SharedPreferences prefs;
    private final FirebaseAuth auth;
    
    private UserManager(@NonNull Context context) {
        prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        
        // Cache the last known user ID
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            cacheUserId(user.getUid());
        }
    }
    
    public static synchronized UserManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }
    
    /**
     * Get the current user ID from Firebase Auth
     * Falls back to cached ID if user is not authenticated
     * 
     * @return User ID string, never null
     */
    @NonNull
    public String getUserId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            cacheUserId(uid);
            return uid;
        }
        
        // Return cached ID or empty string as fallback
        return prefs.getString(KEY_LAST_USER_ID, "");
    }
    
    /**
     * Get the current Firebase user
     * 
     * @return FirebaseUser or null if not authenticated
     */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
    
    /**
     * Check if user is authenticated with Firebase
     * 
     * @return true if user is logged in
     */
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Sign in anonymously with Firebase
     * Useful for tracking users without requiring account creation
     * 
     * @param callback Result callback
     */
    public void signInAnonymously(@NonNull AuthCallback callback) {
        auth.signInAnonymously()
            .addOnSuccessListener(authResult -> {
                FirebaseUser user = authResult.getUser();
                if (user != null) {
                    cacheUserId(user.getUid());
                    callback.onSuccess(user.getUid());
                } else {
                    callback.onFailure(new Exception("User is null after sign in"));
                }
            })
            .addOnFailureListener(callback::onFailure);
    }
    
    /**
     * Sign out from Firebase
     */
    public void signOut() {
        auth.signOut();
    }
    
    /**
     * Get Firebase Auth instance for advanced operations
     * 
     * @return FirebaseAuth instance
     */
    @NonNull
    public FirebaseAuth getAuth() {
        return auth;
    }
    
    /**
     * Cache the user ID locally for offline access
     */
    private void cacheUserId(@NonNull String userId) {
        prefs.edit().putString(KEY_LAST_USER_ID, userId).apply();
    }
    
    /**
     * Callback interface for authentication operations
     */
    public interface AuthCallback {
        void onSuccess(@NonNull String userId);
        void onFailure(@NonNull Exception e);
    }
}