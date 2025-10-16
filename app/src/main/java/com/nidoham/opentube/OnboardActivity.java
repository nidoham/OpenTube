package com.nidoham.opentube;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.nidoham.opentube.auth.User;
import com.nidoham.opentube.databinding.ActivityOnboardBinding;

public class OnboardActivity extends AppCompatActivity {

    private static final String TAG = "OnboardActivity";
    private static final int RC_SIGN_IN = 9001;

    private ActivityOnboardBinding binding;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize View Binding
        binding = ActivityOnboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth and Database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Configure Google Sign-In
        configureGoogleSignIn();

        // Start animations
        startRingAnimations();

        // Set up click listener
        binding.btnGoogle.setOnClickListener(v -> signIn());
    }

    private void configureGoogleSignIn() {
        // Configure sign-in to request the user's ID, email address, and basic profile
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Required for Firebase Auth
                .requestEmail()
                .requestProfile()
                .build();

        // Build a GoogleSignInClient with the options specified by gso
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void startRingAnimations() {
        // Load animations
        Animation outerAnimation = AnimationUtils.loadAnimation(this, R.anim.ring_pulse_outer);
        Animation innerAnimation = AnimationUtils.loadAnimation(this, R.anim.ring_pulse_inner);

        // Start animations
        binding.outerRing.startAnimation(outerAnimation);
        binding.innerRing.startAnimation(innerAnimation);
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent()
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            
            // Signed in successfully, now authenticate with Firebase
            if (account != null) {
                Log.d(TAG, "Google sign in successful, authenticating with Firebase...");
                firebaseAuthWithGoogle(account);
            }
        } catch (ApiException e) {
            // Sign in failed
            Log.w(TAG, "Google sign in failed with code: " + e.getStatusCode());
            Toast.makeText(this, "Sign in failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        
                        if (firebaseUser != null) {
                            // Check if user exists in database
                            checkAndCreateUser(firebaseUser, acct);
                        }
                    } else {
                        // Sign in fails
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(OnboardActivity.this, "Authentication failed.", 
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkAndCreateUser(FirebaseUser firebaseUser, GoogleSignInAccount account) {
        String userId = firebaseUser.getUid();
        DatabaseReference userRef = mDatabase.child("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // User exists, update online status
                    Log.d(TAG, "User exists, updating online status");
                    userRef.child("online").setValue(System.currentTimeMillis());
                    userRef.child("lastActiveAt").setValue(System.currentTimeMillis());
                    
                    // Save to SharedPreferences
                    User existingUser = snapshot.getValue(User.class);
                    if (existingUser != null) {
                        saveUserToPreferences(existingUser);
                    }
                    
                    navigateToMainActivity();
                } else {
                    // New user, create user object
                    Log.d(TAG, "New user, creating account");
                    createNewUser(firebaseUser, account);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
                Toast.makeText(OnboardActivity.this, "Failed to load user data", 
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNewUser(FirebaseUser firebaseUser, GoogleSignInAccount account) {
        String userId = firebaseUser.getUid();
        String username = account.getEmail() != null ? 
                account.getEmail().split("@")[0] : "user" + userId.substring(0, 6);
        String email = account.getEmail();
        String displayName = account.getDisplayName();
        String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null;

        // Create User object
        User newUser = new User(userId, username, email, displayName);
        newUser.setProfileImageUrl(photoUrl);
        newUser.setAuthType("google");
        newUser.setAccountType("standard");
        newUser.setStatus("active");
        newUser.setOnlineNow();
        newUser.setVerified(false);
        newUser.setBanned(false);

        // Save to Firebase
        mDatabase.child("users").child(userId).setValue(newUser)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User created successfully in Firebase");
                    
                    // Save to SharedPreferences
                    saveUserToPreferences(newUser);
                    
                    // Navigate to main activity
                    navigateToMainActivity();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user: " + e.getMessage());
                    Toast.makeText(OnboardActivity.this, "Failed to create account", 
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserToPreferences(User user) {
        // Save to SharedPreferences
        getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit()
                .putString("user_id", user.getUserId())
                .putString("user_name", user.getDisplayName())
                .putString("username", user.getUsername())
                .putString("user_email", user.getEmail())
                .putString("user_photo", user.getProfileImageUrl())
                .putString("auth_type", user.getAuthType())
                .putBoolean("is_logged_in", true)
                .apply();
    }

    private void navigateToMainActivity() {
        // Navigate to your main activity
        Intent intent = new Intent(OnboardActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        // Check if user is already signed in with Firebase
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is already signed in, update online status and navigate
            Log.d(TAG, "User already signed in");
            mDatabase.child("users").child(currentUser.getUid())
                    .child("online").setValue(System.currentTimeMillis());
            navigateToMainActivity();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clear animations to prevent memory leaks
        if (binding != null) {
            binding.outerRing.clearAnimation();
            binding.innerRing.clearAnimation();
            binding = null;
        }
    }
}