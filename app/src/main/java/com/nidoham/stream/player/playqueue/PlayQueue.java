package com.nidoham.stream.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PlayQueue manages a list of streams, the current index, and playback modes like shuffle and repeat.
 * It is a thread-safe, concrete implementation for managing a media playlist.
 */
public class PlayQueue implements Serializable {

    private static final long serialVersionUID = 2L; // Updated version UID

    // Fields for queue state
    @NonNull private transient AtomicInteger queueIndex;
    @NonNull private transient Object lock;
    private int indexValue; // For serialization
    @NonNull private List<PlayQueueItem> streams;

    // Fields merged from PlaylistQueue and SimplePlayQueue
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false; // Replaces 'complete' flag logic

    /**
     * Create a PlayQueue.
     *
     * @param index       The starting index.
     * @param startWith   The initial list of items.
     * @param repeatEnabled If true, the queue will wrap around when navigating past the ends.
     */
    public PlayQueue(final int index, @NonNull final List<PlayQueueItem> startWith, final boolean repeatEnabled) {
        Objects.requireNonNull(startWith, "startWith must not be null");
        this.streams = new ArrayList<>(startWith);
        final int sanitizedIndex = normalizeInitialIndex(index, this.streams.size());
        this.indexValue = sanitizedIndex;
        this.queueIndex = new AtomicInteger(sanitizedIndex);
        this.lock = new Object();
        this.repeatEnabled = repeatEnabled;
    }

    /**
     * Convenience constructor starting at index 0.
     */
    public PlayQueue(@NonNull final List<PlayQueueItem> startWith, final boolean repeatEnabled) {
        this(0, startWith, repeatEnabled);
    }

    private static int normalizeInitialIndex(final int index, final int size) {
        if (size == 0) return 0;
        return Math.max(0, Math.min(index, size - 1));
    }

    // Custom serialization/deserialization to handle transient fields
    private void writeObject(ObjectOutputStream out) throws IOException {
        synchronized (lock) {
            indexValue = queueIndex.get();
            out.defaultWriteObject();
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new Object();
        this.queueIndex = new AtomicInteger(indexValue);
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Playlist Control (Shuffle/Repeat)
     //////////////////////////////////////////////////////////////////////////*/

    public void toggleShuffle() {
        synchronized (lock) {
            shuffleEnabled = !shuffleEnabled;
        }
    }

    public void setShuffleEnabled(boolean enabled) {
        synchronized (lock) {
            this.shuffleEnabled = enabled;
        }
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public void toggleRepeat() {
        synchronized (lock) {
            repeatEnabled = !repeatEnabled;
        }
    }

    public void setRepeatEnabled(boolean enabled) {
        synchronized (lock) {
            this.repeatEnabled = enabled;
        }
    }

    public boolean isRepeatEnabled() {
        return repeatEnabled;
    }

    /**
     * Shuffles the entire queue in place.
     * The currently playing item is moved to the start of the queue to ensure
     * continuous playback.
     */
    public void shuffleNow() {
        synchronized (lock) {
            if (streams.size() <= 1) return;
            PlayQueueItem currentItem = getItem();
            List<PlayQueueItem> list = new ArrayList<>(streams);
            Collections.shuffle(list);

            if (currentItem != null) {
                list.remove(currentItem);
                list.add(0, currentItem);
            }
            this.streams = list;
            queueIndex.set(0);
            indexValue = 0;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Navigation
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Advances to the next item in the queue.
     * If shuffle is enabled, moves to a random item.
     * If shuffle is disabled, moves to the next sequential item, wrapping around if repeat is enabled.
     *
     * @return the new index.
     */
    public int next() {
        synchronized (lock) {
            if (streams.isEmpty()) return 0;
            if (shuffleEnabled) {
                return moveToRandomStream();
            }

            int next = queueIndex.get() + 1;
            if (next >= streams.size()) {
                next = repeatEnabled ? 0 : streams.size() - 1;
            }
            queueIndex.set(next);
            indexValue = next;
            return next;
        }
    }

    /**
     * Moves to the previous item in the queue.
     * This method ignores shuffle mode and always moves to the previous sequential item,
     * wrapping around if repeat is enabled.
     *
     * @return the new index.
     */
    public int previous() {
        synchronized (lock) {
            if (streams.isEmpty()) return 0;
            int prev = queueIndex.get() - 1;
            if (prev < 0) {
                prev = repeatEnabled ? streams.size() - 1 : 0;
            }
            queueIndex.set(prev);
            indexValue = prev;
            return prev;
        }
    }

    private int moveToRandomStream() {
        // Assumes lock is held
        if (streams.size() <= 1) {
            queueIndex.set(0);
            indexValue = 0;
            return 0;
        }
        int currentIndex = queueIndex.get();
        int randomIndex;
        do {
            randomIndex = (int) (Math.random() * streams.size());
        } while (randomIndex == currentIndex);

        queueIndex.set(randomIndex);
        indexValue = randomIndex;
        return randomIndex;
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Read-only operations
     //////////////////////////////////////////////////////////////////////////*/

    public int getIndex() {
        return queueIndex.get();
    }

    @Nullable
    public PlayQueueItem getItem() {
        return getItem(getIndex());
    }

    @Nullable
    public PlayQueueItem getItem(final int index) {
        synchronized (lock) {
            if (index < 0 || index >= streams.size()) return null;
            return streams.get(index);
        }
    }

    /**
     * Peeks at the next item without changing the current index.
     * Returns null if shuffle is enabled, as the next item is non-deterministic.
     */
    @Nullable
    public PlayQueueItem peekNext() {
        synchronized (lock) {
            if (shuffleEnabled || streams.size() <= 1) return null;

            int nextIndex = getIndex() + 1;
            if (nextIndex >= streams.size()) {
                return repeatEnabled ? streams.get(0) : null;
            }
            return streams.get(nextIndex);
        }
    }

    /**
     * Returns a list of streams that are scheduled to play after the current one.
     */
    @NonNull
    public List<PlayQueueItem> getUpcomingStreams() {
        synchronized (lock) {
            final int current = getIndex();
            if (current + 1 >= streams.size()) return Collections.emptyList();
            return new ArrayList<>(streams.subList(current + 1, streams.size()));
        }
    }

    public int size() {
        synchronized (lock) {
            return streams.size();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return streams.isEmpty();
        }
    }

    @NonNull
    public List<PlayQueueItem> getStreams() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(streams));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Write operations
     //////////////////////////////////////////////////////////////////////////*/

    public void setIndex(final int index) {
        synchronized (lock) {
            if (streams.isEmpty()) {
                queueIndex.set(0);
                indexValue = 0;
                return;
            }
            int newIndex = index;
            if (newIndex < 0) {
                newIndex = repeatEnabled ? mod(newIndex, streams.size()) : 0;
            } else if (newIndex >= streams.size()) {
                newIndex = repeatEnabled ? mod(newIndex, streams.size()) : streams.size() - 1;
            }
            queueIndex.set(newIndex);
            indexValue = newIndex;
        }
    }

    private static int mod(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    public void append(@NonNull final List<PlayQueueItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        synchronized (lock) {
            streams.addAll(new ArrayList<>(items));
        }
    }

    public void insert(final int position, @NonNull final List<PlayQueueItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        synchronized (lock) {
            if (items.isEmpty()) return;
            final int pos = Math.max(0, Math.min(position, streams.size()));
            streams.addAll(pos, new ArrayList<>(items));
            if (pos <= getIndex()) {
                queueIndex.addAndGet(items.size());
                indexValue = queueIndex.get();
            }
        }
    }

    public void remove(final int index) {
        synchronized (lock) {
            if (index < 0 || index >= streams.size()) return;
            final int current = getIndex();
            streams.remove(index);
            if (streams.isEmpty()) {
                queueIndex.set(0);
                indexValue = 0;
                return;
            }
            if (current > index) {
                queueIndex.decrementAndGet();
            } else if (current >= streams.size()) {
                queueIndex.set(streams.size() - 1);
            }
            indexValue = queueIndex.get();
        }
    }

    public void move(final int source, final int target) {
        synchronized (lock) {
            final int size = streams.size();
            if (source < 0 || source >= size || target < 0 || target >= size || source == target) return;

            final int current = getIndex();
            PlayQueueItem item = streams.remove(source);
            streams.add(target, item);

            if (current == source) {
                queueIndex.set(target);
            } else if (source < current && target >= current) {
                queueIndex.decrementAndGet();
            } else if (source > current && target <= current) {
                queueIndex.incrementAndGet();
            }
            indexValue = queueIndex.get();
        }
    }

    public void replaceAll(@NonNull final List<PlayQueueItem> newStreams, final int newIndex) {
        Objects.requireNonNull(newStreams, "newStreams must not be null");
        synchronized (lock) {
            this.streams = new ArrayList<>(newStreams);
            final int sanitized = normalizeInitialIndex(newIndex, streams.size());
            queueIndex.set(sanitized);
            indexValue = sanitized;
        }
    }

    public void clear() {
        synchronized (lock) {
            streams.clear();
            queueIndex.set(0);
            indexValue = 0;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Overridden methods
     //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayQueue)) return false;
        PlayQueue that = (PlayQueue) o;
        synchronized (lock) {
            synchronized (that.lock) {
                return getIndex() == that.getIndex()
                        && shuffleEnabled == that.shuffleEnabled
                        && repeatEnabled == that.repeatEnabled
                        && streams.equals(that.streams);
            }
        }
    }

    @Override
    public int hashCode() {
        synchronized (lock) {
            return Objects.hash(getIndex(), streams, shuffleEnabled, repeatEnabled);
        }
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return "PlayQueue{" +
                    "index=" + getIndex() +
                    ", size=" + size() +
                    ", shuffle=" + shuffleEnabled +
                    ", repeat=" + repeatEnabled +
                    '}';
        }
    }
}