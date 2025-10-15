package com.nidoham.flowtube.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*------------------------------------------------------------
 * 🔹 PlaylistQueue ক্লাস
 * এই ক্লাস PlayQueue এর wrapper — এটি streams নিয়ন্ত্রণ করে।
 * কাজ: বর্তমান stream, পরবর্তী stream, shuffle, repeat, remove, ইত্যাদি।
 *------------------------------------------------------------*/
public class PlaylistQueue implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PlayQueue playQueue;
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;

    /*------------------------------------------------------------
     * 🔸 কনস্ট্রাক্টর — বিদ্যমান PlayQueue ইনজেক্ট করা
     *-----------------------------------------------------------*/
    public PlaylistQueue(@NonNull PlayQueue playQueue) {
        Objects.requireNonNull(playQueue, "playQueue must not be null");
        this.playQueue = playQueue;
    }

    /*------------------------------------------------------------
     * 🔹 PlayQueue অ্যাক্সেস করা (যদি direct access দরকার হয়)
     *-----------------------------------------------------------*/
    @NonNull
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    /*------------------------------------------------------------
     * 🔹 বর্তমান স্ট্রিম ফেরত দেয়
     *-----------------------------------------------------------*/
    @Nullable
    public PlayQueueItem getCurrentStream() {
        return playQueue.getItem();
    }

    /*------------------------------------------------------------
     * 🔹 বর্তমান index ফেরত দেয়
     *-----------------------------------------------------------*/
    public int getCurrentIndex() {
        return playQueue.getIndex();
    }

    /*------------------------------------------------------------
     * 🔹 সব streams এর list ফেরত দেয়
     *-----------------------------------------------------------*/
    @NonNull
    public List<PlayQueueItem> getAllStreams() {
        return playQueue.getStreams();
    }

    /*------------------------------------------------------------
     * 🔹 queue এর size
     *-----------------------------------------------------------*/
    public int size() {
        return playQueue.size();
    }

    /*------------------------------------------------------------
     * 🔹 queue খালি কিনা চেক করা
     *-----------------------------------------------------------*/
    public boolean isEmpty() {
        return playQueue.isEmpty();
    }

    /*------------------------------------------------------------
     * 🔹 পরবর্তী স্ট্রিমে যাওয়া (repeat/shuffle অনুজায়ী)
     *-----------------------------------------------------------*/
    @Nullable
    public PlayQueueItem moveToNext() {
        if (shuffleEnabled) {
            return moveToRandomStream();
        }
        
        playQueue.next();
        return playQueue.getItem();
    }

    /*------------------------------------------------------------
     * 🔹 আগের স্ট্রিমে যাওয়া
     *-----------------------------------------------------------*/
    @Nullable
    public PlayQueueItem moveToPrevious() {
        if (shuffleEnabled && repeatEnabled) {
            // Shuffle + repeat mode এ random stream
            return moveToRandomStream();
        }
        
        playQueue.previous();
        return playQueue.getItem();
    }

    /*------------------------------------------------------------
     * 🔹 নির্দিষ্ট index এ যাওয়া
     *-----------------------------------------------------------*/
    @Nullable
    public PlayQueueItem moveToIndex(int index) {
        playQueue.setIndex(index);
        return playQueue.getItem();
    }

    /*------------------------------------------------------------
     * 🔹 পরবর্তী stream দেখা (index পরিবর্তন না করে)
     *-----------------------------------------------------------*/
    @Nullable
    public PlayQueueItem peekNext() {
        if (shuffleEnabled) {
            return null; // Shuffle mode এ পরবর্তী item নির্ধারিত নয়
        }
        
        int nextIndex = playQueue.getIndex() + 1;
        if (nextIndex >= playQueue.size()) {
            if (repeatEnabled) {
                nextIndex = 0;
            } else {
                return null;
            }
        }
        return playQueue.getItem(nextIndex);
    }

    /*------------------------------------------------------------
     * 🔹 পরবর্তী stream তালিকা (upcoming list)
     *-----------------------------------------------------------*/
    @NonNull
    public List<PlayQueueItem> getUpcomingStreams() {
        List<PlayQueueItem> list = playQueue.getStreams();
        int current = playQueue.getIndex();
        if (current + 1 >= list.size()) return new ArrayList<>();
        return new ArrayList<>(list.subList(current + 1, list.size()));
    }

    /*------------------------------------------------------------
     * 🔹 র‌্যান্ডম স্ট্রিমে যাওয়া (shuffle মোড)
     *-----------------------------------------------------------*/
    @Nullable
    private PlayQueueItem moveToRandomStream() {
        List<PlayQueueItem> list = playQueue.getStreams();
        if (list.isEmpty()) return null;
        
        int currentIndex = playQueue.getIndex();
        int randomIndex;
        
        // যদি একাধিক item থাকে, তাহলে বর্তমান item বাদে random নির্বাচন করা
        if (list.size() > 1) {
            do {
                randomIndex = (int) (Math.random() * list.size());
            } while (randomIndex == currentIndex);
        } else {
            randomIndex = 0;
        }
        
        playQueue.setIndex(randomIndex);
        return list.get(randomIndex);
    }

    /*------------------------------------------------------------
     * 🔹 Shuffle মোড টগল করা
     *-----------------------------------------------------------*/
    public void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
    }

    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    /*------------------------------------------------------------
     * 🔹 Repeat মোড টগল করা
     *-----------------------------------------------------------*/
    public void toggleRepeat() {
        repeatEnabled = !repeatEnabled;
    }

    public void setRepeatEnabled(boolean enabled) {
        repeatEnabled = enabled;
    }

    public boolean isRepeatEnabled() {
        return repeatEnabled;
    }

    /*------------------------------------------------------------
     * 🔹 পুরো লিস্ট shuffle করা (manual)
     *-----------------------------------------------------------*/
    public void shuffleNow() {
        List<PlayQueueItem> list = new ArrayList<>(playQueue.getStreams());
        PlayQueueItem currentItem = getCurrentStream();
        
        Collections.shuffle(list);
        
        // বর্তমান item কে প্রথমে রাখা (যাতে playback বিরক্ত না হয়)
        if (currentItem != null && list.contains(currentItem)) {
            list.remove(currentItem);
            list.add(0, currentItem);
            playQueue.replaceAll(list, 0);
        } else {
            playQueue.replaceAll(list, 0);
        }
    }

    /*------------------------------------------------------------
     * 🔹 Stream যোগ করা
     *-----------------------------------------------------------*/
    public void append(@NonNull List<PlayQueueItem> items) {
        playQueue.append(items);
    }

    public void append(@NonNull PlayQueueItem item) {
        playQueue.append(Collections.singletonList(item));
    }

    /*------------------------------------------------------------
     * 🔹 নির্দিষ্ট position এ insert করা
     *-----------------------------------------------------------*/
    public void insert(int position, @NonNull List<PlayQueueItem> items) {
        playQueue.insert(position, items);
    }

    public void insert(int position, @NonNull PlayQueueItem item) {
        playQueue.insert(position, Collections.singletonList(item));
    }

    /*------------------------------------------------------------
     * 🔹 Stream মুছে ফেলা
     *-----------------------------------------------------------*/
    public void remove(int index) {
        playQueue.remove(index);
    }

    /*------------------------------------------------------------
     * 🔹 Stream move করা
     *-----------------------------------------------------------*/
    public void move(int from, int to) {
        playQueue.move(from, to);
    }

    /*------------------------------------------------------------
     * 🔹 সব stream clear করা
     *-----------------------------------------------------------*/
    public void clear() {
        playQueue.clear();
    }

    /*------------------------------------------------------------
     * 🔹 প্লেলিস্ট রিফ্রেশ করা (যেমন fetch নতুন stream)
     *-----------------------------------------------------------*/
    public void refresh() {
        playQueue.fetch(); // 🔸 PlayQueue-এর fetch() future implementation এর জন্য
    }

    /*------------------------------------------------------------
     * 🔹 Queue complete কিনা (wrap around হবে কিনা)
     *-----------------------------------------------------------*/
    public boolean isComplete() {
        return playQueue.isComplete();
    }

    /*------------------------------------------------------------
     * 🔹 তথ্য ফেরত দেয় (debug/log purpose)
     *-----------------------------------------------------------*/
    @NonNull
    @Override
    public String toString() {
        PlayQueueItem current = getCurrentStream();
        return "PlaylistQueue{" +
                "current=" + (current != null ? current.getTitle() : "null") +
                ", index=" + playQueue.getIndex() +
                ", shuffle=" + shuffleEnabled +
                ", repeat=" + repeatEnabled +
                ", total=" + playQueue.size() +
                ", complete=" + playQueue.isComplete() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaylistQueue)) return false;
        PlaylistQueue that = (PlaylistQueue) o;
        return shuffleEnabled == that.shuffleEnabled &&
                repeatEnabled == that.repeatEnabled &&
                playQueue.equals(that.playQueue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playQueue, shuffleEnabled, repeatEnabled);
    }
}