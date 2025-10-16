package com.nidoham.opentube.auth;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * User model class for Firebase Database
 * Inspired by YouTube user structure with basic user-related fields only
 */
@IgnoreExtraProperties
public class User implements Parcelable {
    
    private String userId;
    private String username;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String coverImageUrl;
    private String bio;
    private String status; // e.g., "active", "inactive", "suspended"
    private long online; // Timestamp of last online status or 0 if offline
    private long createdAt;
    private long lastActiveAt;
    private String phoneNumber;
    private boolean verified;
    private String channelUrl; // Custom URL for the user's channel
    private boolean banned;
    private String banReason;
    private long bannedAt;
    private String authType; // e.g., "email", "google", "facebook", "phone"
    private String accountType; // e.g., "standard", "creator", "premium"
    
    /**
     * Default no-argument constructor required for Firebase
     */
    public User() {
        // Required empty constructor for Firebase
    }
    
    /**
     * Parameterized constructor
     */
    public User(String userId, String username, String email, String displayName, 
                String profileImageUrl, String coverImageUrl, String bio, 
                String status, long online, long createdAt, long lastActiveAt,
                String phoneNumber, boolean verified, String channelUrl,
                boolean banned, String banReason, long bannedAt, String authType,
                String accountType) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.coverImageUrl = coverImageUrl;
        this.bio = bio;
        this.status = status;
        this.online = online;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.phoneNumber = phoneNumber;
        this.verified = verified;
        this.channelUrl = channelUrl;
        this.banned = banned;
        this.banReason = banReason;
        this.bannedAt = bannedAt;
        this.authType = authType;
        this.accountType = accountType;
    }
    
    /**
     * Simplified constructor with essential fields
     */
    public User(String userId, String username, String email, String displayName) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.status = "active";
        this.online = 0; // 0 indicates offline
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
        this.verified = false;
        this.banned = false;
        this.authType = "email";
        this.accountType = "standard";
    }
    
    // Getters and Setters
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getProfileImageUrl() {
        return profileImageUrl;
    }
    
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
    
    public String getCoverImageUrl() {
        return coverImageUrl;
    }
    
    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public long getOnline() {
        return online;
    }
    
    public void setOnline(long online) {
        this.online = online;
    }
    
    /**
     * Check if user is currently online (timestamp within last 5 minutes)
     */
    @Exclude
    public boolean isOnline() {
        return online > 0 && (System.currentTimeMillis() - online) < 300000; // 5 minutes
    }
    
    /**
     * Set user as online with current timestamp
     */
    public void setOnlineNow() {
        this.online = System.currentTimeMillis();
    }
    
    /**
     * Set user as offline
     */
    public void setOffline() {
        this.online = 0;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public boolean isVerified() {
        return verified;
    }
    
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
    
    public String getChannelUrl() {
        return channelUrl;
    }
    
    public void setChannelUrl(String channelUrl) {
        this.channelUrl = channelUrl;
    }
    
    public boolean isBanned() {
        return banned;
    }
    
    public void setBanned(boolean banned) {
        this.banned = banned;
    }
    
    public String getBanReason() {
        return banReason;
    }
    
    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }
    
    public long getBannedAt() {
        return bannedAt;
    }
    
    public void setBannedAt(long bannedAt) {
        this.bannedAt = bannedAt;
    }
    
    public String getAuthType() {
        return authType;
    }
    
    public void setAuthType(String authType) {
        this.authType = authType;
    }
    
    public String getAccountType() {
        return accountType;
    }
    
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }
    
    // Parcelable implementation
    
    protected User(Parcel in) {
        userId = in.readString();
        username = in.readString();
        email = in.readString();
        displayName = in.readString();
        profileImageUrl = in.readString();
        coverImageUrl = in.readString();
        bio = in.readString();
        status = in.readString();
        online = in.readLong();
        createdAt = in.readLong();
        lastActiveAt = in.readLong();
        phoneNumber = in.readString();
        verified = in.readByte() != 0;
        channelUrl = in.readString();
        banned = in.readByte() != 0;
        banReason = in.readString();
        bannedAt = in.readLong();
        authType = in.readString();
        accountType = in.readString();
    }
    
    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }
        
        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(userId);
        dest.writeString(username);
        dest.writeString(email);
        dest.writeString(displayName);
        dest.writeString(profileImageUrl);
        dest.writeString(coverImageUrl);
        dest.writeString(bio);
        dest.writeString(status);
        dest.writeLong(online);
        dest.writeLong(createdAt);
        dest.writeLong(lastActiveAt);
        dest.writeString(phoneNumber);
        dest.writeByte((byte) (verified ? 1 : 0));
        dest.writeString(channelUrl);
        dest.writeByte((byte) (banned ? 1 : 0));
        dest.writeString(banReason);
        dest.writeLong(bannedAt);
        dest.writeString(authType);
        dest.writeString(accountType);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status='" + status + '\'' +
                ", online=" + online +
                ", verified=" + verified +
                ", banned=" + banned +
                ", authType='" + authType + '\'' +
                ", accountType='" + accountType + '\'' +
                '}';
    }
}