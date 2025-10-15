package com.nidoham.newpipe.image;

import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import org.schabi.newpipe.extractor.Image;

public class ThumbnailExtractor {
    private final List<Image> thumbnails;
    
    public ThumbnailExtractor(List<Image> thumbnails) {
        this.thumbnails = thumbnails != null ? thumbnails : List.of();
    }

    /**
     * Gets the highest quality thumbnail URL based on resolution and quality estimation
     * @return URL of the best thumbnail, or empty string if no thumbnails available
     */
    public String getThumbnail() {
        if (thumbnails.isEmpty()) {
            return "";
        }
        
        return thumbnails.stream()
                .max(Comparator.comparingInt(this::calculateScore)
                        .thenComparing(this::compareResolutionLevels))
                .map(Image::getUrl)
                .orElse("");
    }
    
    /**
     * Alternative method to get thumbnail by specific resolution level preference
     * @param preferredLevel The preferred resolution level
     * @return URL of the best matching thumbnail
     */
    public String getThumbnailByLevel(Image.ResolutionLevel preferredLevel) {
        if (thumbnails.isEmpty()) {
            return "";
        }
        
        return thumbnails.stream()
                .filter(image -> image.getEstimatedResolutionLevel() == preferredLevel)
                .max(Comparator.comparingInt(this::calculateScore))
                .map(Image::getUrl)
                .orElseGet(() -> getThumbnail()); // Fallback to default selection
    }
    
    /**
     * Gets thumbnail with fallback strategy - tries high quality first, then falls back
     * @return URL of the best available thumbnail
     */
    public String getThumbnailWithFallback() {
        if (thumbnails.isEmpty()) {
            return "";
        }
        
        // Try to get high resolution first
        String thumbnail = getThumbnailByLevel(Image.ResolutionLevel.HIGH);
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        
        // Fallback to medium resolution
        thumbnail = getThumbnailByLevel(Image.ResolutionLevel.MEDIUM);
        if (!thumbnail.isEmpty()) {
            return thumbnail;
        }
        
        // Final fallback to any available thumbnail
        return getThumbnail();
    }
    
    /**
     * Calculates a weighted score for image quality assessment
     */
    private int calculateScore(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Basic area calculation
        int areaScore = width * height;
        
        // Additional weight for resolution level
        int levelWeight = switch (image.getEstimatedResolutionLevel()) {
            case HIGH -> 10000;
            case MEDIUM -> 5000;
            case LOW -> 1000;
            default -> 0;
        };
        
        // Prefer images with reasonable aspect ratio (not too extreme)
        double aspectRatio = (double) width / height;
        int aspectBonus = (aspectRatio > 0.5 && aspectRatio < 2.0) ? 1000 : 0;
        
        return areaScore + levelWeight + aspectBonus;
    }
    
    /**
     * Compares resolution levels for ordering
     */
    private int compareResolutionLevels(Image img1, Image img2) {
        return Integer.compare(
            getResolutionLevelPriority(img1.getEstimatedResolutionLevel()),
            getResolutionLevelPriority(img2.getEstimatedResolutionLevel())
        );
    }
    
    /**
     * Assigns priority values to resolution levels
     */
    private int getResolutionLevelPriority(Image.ResolutionLevel level) {
        return switch (level) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            default -> 0;
        };
    }
    
    /**
     * Utility method to check if thumbnails are available
     */
    public boolean hasThumbnails() {
        return !thumbnails.isEmpty();
    }
    
    /**
     * Gets the number of available thumbnails
     */
    public int getThumbnailCount() {
        return thumbnails.size();
    }
}