package com.nidoham.opentube.util.time;

public class TimeUtils {

    /**
     * Formats a given duration in milliseconds into a YouTube-like time string (e.g., "MM:SS" or "HH:MM:SS").
     *
     * @param millis The duration in milliseconds.
     * @return A formatted time string.
     */
    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}