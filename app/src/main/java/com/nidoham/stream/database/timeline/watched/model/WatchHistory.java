package com.nidoham.stream.database.timeline.watched.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.nidoham.stream.player.playqueue.PlayQueueItem;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class WatchHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private String historyId = "";
    @NonNull
    private String userId = getCurrentUserId();
    @NonNull
    private String title = "";
    @NonNull
    private String url = "";
    @NonNull
    private String uploader = "";
    @NonNull
    private String uploaderUrl = "";
    private long duration = 0L;
    private long lastDuration = 0L;
    private long standByTime = System.currentTimeMillis();
    @Nullable
    private List<Image> thumbnails;
    @Nullable
    private StreamType streamType;

    public WatchHistory() {}

    public WatchHistory(
            @NonNull String historyId,
            @NonNull String userId,
            @NonNull String title,
            @NonNull String url,
            @NonNull String uploader,
            @NonNull String uploaderUrl,
            long duration,
            long lastDuration,
            long standByTime,
            @Nullable List<Image> thumbnails,
            @Nullable StreamType streamType) {
        this.historyId = historyId;
        this.userId = userId;
        this.title = title;
        this.url = url;
        this.uploader = uploader;
        this.uploaderUrl = uploaderUrl;
        this.duration = duration;
        this.lastDuration = lastDuration;
        this.standByTime = standByTime;
        this.thumbnails = thumbnails;
        this.streamType = streamType;
    }

    @NonNull
    public static WatchHistory fromPlayQueueItem(@NonNull PlayQueueItem queue) {
        WatchHistory history = new WatchHistory();
        history.title = queue.getTitle();
        history.url = queue.getUrl();
        history.uploader = queue.getUploader();
        history.uploaderUrl = queue.getUploaderUrl();
        history.duration = queue.getDuration();
        history.standByTime = System.currentTimeMillis();
        history.thumbnails = queue.getThumbnails();
        history.streamType = queue.getStreamType();
        history.userId = getCurrentUserId();
        return history;
    }

    @NonNull
    private static String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "";
    }

    @NonNull
    public String getHistoryId() { return historyId; }
    public void setHistoryId(@NonNull String historyId) { this.historyId = historyId; }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public String getTitle() { return title; }
    public void setTitle(@NonNull String title) { this.title = title; }

    @NonNull
    public String getUrl() { return url; }
    public void setUrl(@NonNull String url) { this.url = url; }

    @NonNull
    public String getUploader() { return uploader; }
    public void setUploader(@NonNull String uploader) { this.uploader = uploader; }

    @NonNull
    public String getUploaderUrl() { return uploaderUrl; }
    public void setUploaderUrl(@NonNull String uploaderUrl) { this.uploaderUrl = uploaderUrl; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public long getLastDuration() { return lastDuration; }
    public void setLastDuration(long lastDuration) { this.lastDuration = lastDuration; }

    public long getStandByTime() { return standByTime; }
    public void setStandByTime(long standByTime) { this.standByTime = standByTime; }

    @Nullable
    public List<Image> getThumbnails() { return thumbnails; }
    public void setThumbnails(@Nullable List<Image> thumbnails) { this.thumbnails = thumbnails; }

    @Nullable
    public StreamType getStreamType() { return streamType; }
    public void setStreamType(@Nullable StreamType streamType) { this.streamType = streamType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WatchHistory)) return false;
        WatchHistory that = (WatchHistory) o;
        return duration == that.duration &&
                lastDuration == that.lastDuration &&
                standByTime == that.standByTime &&
                historyId.equals(that.historyId) &&
                userId.equals(that.userId) &&
                title.equals(that.title) &&
                url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(historyId, userId, title, url, duration, lastDuration, standByTime);
    }

    @NonNull
    @Override
    public String toString() {
        return "WatchHistory{" +
                "historyId='" + historyId + '\'' +
                ", userId='" + userId + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", uploader='" + uploader + '\'' +
                ", duration=" + duration +
                ", lastDuration=" + lastDuration +
                ", standByTime=" + standByTime +
                ", streamType=" + streamType +
                '}';
    }
}