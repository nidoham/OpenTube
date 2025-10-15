package com.nidoham.flowtube.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable play queue item carrying essential metadata for a stream.
 */
public final class PlayQueueItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull private final String title;
    @NonNull private final String url;
    private final int serviceId;
    private final long duration;

    // Mark as transient since Image is not Serializable
    // Initialize with empty list to ensure non-null
    @NonNull private transient List<Image> thumbnails = Collections.emptyList();

    // Store serializable thumbnail URLs instead
    @NonNull private final List<String> thumbnailUrls;

    @NonNull private final String uploader;
    @Nullable private final String uploaderUrl;
    @NonNull private final StreamType streamType;

    private PlayQueueItem(@NonNull String title,
                          @NonNull String url,
                          int serviceId,
                          long duration,
                          @NonNull List<Image> thumbnails,
                          @NonNull String uploader,
                          @Nullable String uploaderUrl,
                          @NonNull StreamType streamType) {
        // Validate non-null parameters
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.serviceId = serviceId;
        this.duration = Math.max(0, duration); // Ensure non-negative duration
        
        // Safely handle thumbnails list
        List<Image> safeThumbnails = thumbnails != null ? thumbnails : Collections.emptyList();
        this.thumbnails = Collections.unmodifiableList(new ArrayList<>(safeThumbnails));
        this.thumbnailUrls = extractThumbnailUrls(safeThumbnails);
        
        this.uploader = Objects.requireNonNull(uploader, "uploader must not be null");
        this.uploaderUrl = uploaderUrl; // Nullable is OK
        this.streamType = Objects.requireNonNull(streamType, "streamType must not be null");
    }

    /**
     * Extract thumbnail URLs from Image objects for serialization.
     * Handles null images and null URLs safely.
     */
    @NonNull
    private List<String> extractThumbnailUrls(@NonNull List<Image> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> urls = new ArrayList<>();
        for (Image image : images) {
            if (image != null) {
                String url = image.getUrl();
                if (url != null && !url.trim().isEmpty()) {
                    urls.add(url);
                }
            }
        }
        return Collections.unmodifiableList(urls);
    }

    /**
     * Custom serialization to handle non-serializable Image objects
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // thumbnailUrls will be serialized automatically as it's not transient
    }

    /**
     * Custom deserialization - reconstruct thumbnails list from URLs.
     * Image objects cannot be fully reconstructed from URLs alone.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        
        // Initialize transient thumbnails field with empty list
        // Image objects cannot be reconstructed from URLs alone
        this.thumbnails = Collections.emptyList();
    }

    @NonNull
    public static PlayQueueItem from(@NonNull StreamInfo info) {
        Objects.requireNonNull(info, "info must not be null");
        
        return new PlayQueueItem(
                safeString(info.getName()),
                safeString(info.getUrl()),
                info.getServiceId(),
                info.getDuration(),
                safeList(info.getThumbnails()),
                safeString(info.getUploaderName()),
                info.getUploaderUrl(),
                safeStreamType(info.getStreamType())
        );
    }

    @NonNull
    public static PlayQueueItem from(@NonNull StreamInfoItem item) {
        Objects.requireNonNull(item, "item must not be null");
        
        return new PlayQueueItem(
                safeString(item.getName()),
                safeString(item.getUrl()),
                item.getServiceId(),
                item.getDuration(),
                safeList(item.getThumbnails()),
                safeString(item.getUploaderName()),
                item.getUploaderUrl(),
                safeStreamType(item.getStreamType())
        );
    }

    @NonNull
    private static String safeString(@Nullable String s) {
        return (s == null || s.trim().isEmpty()) ? "" : s;
    }

    @NonNull
    private static List<Image> safeList(@Nullable List<Image> list) {
        return (list == null || list.isEmpty()) ? Collections.emptyList() : list;
    }

    @NonNull
    private static StreamType safeStreamType(@Nullable StreamType type) {
        return type != null ? type : StreamType.NONE;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public int getServiceId() {
        return serviceId;
    }

    public long getDuration() {
        return duration;
    }

    /**
     * Returns an unmodifiable list of thumbnails.
     * Note: After deserialization, this will return an empty list since
     * Image objects cannot be reconstructed from URLs alone.
     */
    @NonNull
    public List<Image> getThumbnails() {
        return thumbnails != null ? thumbnails : Collections.emptyList();
    }

    /**
     * Returns thumbnail URLs as strings.
     * This is always available, even after deserialization.
     */
    @NonNull
    public List<String> getThumbnailUrls() {
        return thumbnailUrls != null ? thumbnailUrls : Collections.emptyList();
    }

    @NonNull
    public String getUploader() {
        return uploader;
    }

    @Nullable
    public String getUploaderUrl() {
        return uploaderUrl;
    }

    @NonNull
    public StreamType getStreamType() {
        return streamType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayQueueItem)) return false;
        
        PlayQueueItem that = (PlayQueueItem) o;
        return serviceId == that.serviceId
                && duration == that.duration
                && title.equals(that.title)
                && url.equals(that.url)
                && thumbnailUrls.equals(that.thumbnailUrls)
                && uploader.equals(that.uploader)
                && Objects.equals(uploaderUrl, that.uploaderUrl)
                && streamType == that.streamType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, url, serviceId, duration, thumbnailUrls, 
                uploader, uploaderUrl, streamType);
    }

    @Override
    public String toString() {
        return "PlayQueueItem{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", serviceId=" + serviceId +
                ", duration=" + duration +
                ", thumbnailUrlsCount=" + thumbnailUrls.size() +
                ", uploader='" + uploader + '\'' +
                ", uploaderUrl='" + uploaderUrl + '\'' +
                ", streamType=" + streamType +
                '}';
    }
}