package com.nidoham.opentube.util.count;

import java.text.DecimalFormat;

public class NumberUtils {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.#");

    /**
     * Formats a long count into a user-friendly string with K, M, B suffixes.
     * For example: 1234 -> 1.2K, 1234567 -> 1.2M.
     * This method provides the base magnitude formatting without any specific type suffix (like "views").
     *
     * @param count The number to format.
     * @return A user-friendly formatted string (e.g., "1.2K", "5.6M", "123").
     */
    private static String formatMagnitudeOnlyCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        }

        double value;
        String suffix;

        if (count < 1_000_000) { // Thousands
            value = count / 1000.0;
            suffix = "K";
        } else if (count < 1_000_000_000) { // Millions
            value = count / 1_000_000.0;
            suffix = "M";
        } else { // Billions
            value = count / 1_000_000_000.0;
            suffix = "B";
        }

        return DECIMAL_FORMAT.format(value) + suffix;
    }

    /**
     * Formats a count specifically for video views, adding " views" as a suffix.
     * Example: 1234 -> "1.2K views", 123 -> "123 views".
     *
     * @param count The number of views.
     * @return A formatted string with " views" suffix.
     */
    public static String formatViewsCount(long count) {
        return formatMagnitudeOnlyCount(count) + " views";
    }

    /**
     * Formats a count specifically for channel subscribers.
     * It uses K, M, B suffixes for large numbers but does not add any additional suffix like " subscribers".
     * Example: 1234 -> "1.2K", 123 -> "123".
     *
     * @param count The number of subscribers.
     * @return A formatted string suitable for subscriber counts.
     */
    public static String formatSubscriberCount(long count) {
        return formatMagnitudeOnlyCount(count);
    }

    /**
     * Formats a count specifically for likes.
     * It uses K, M, B suffixes for large numbers but does not add any additional suffix.
     * Example: 1234 -> "1.2K", 123 -> "123".
     *
     * @param count The number of likes.
     * @return A formatted string suitable for like counts.
     */
    public static String formatLikesCount(long count) {
        return formatMagnitudeOnlyCount(count);
    }
}