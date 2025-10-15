package com.nidoham.flowtube.player.playqueue;

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
 * PlayQueue manages a list of streams and the index of the currently playing stream.
 * Thread-safety: all mutating operations synchronize on the internal lock object.
 */
public abstract class PlayQueue implements Serializable {

    private static final long serialVersionUID = 1L;

    // Mark as transient - will be reconstructed during deserialization
    @NonNull
    private transient AtomicInteger queueIndex;

    // Mark as transient - will be reconstructed during deserialization
    @NonNull
    private transient Object lock;

    // Store the actual index value for serialization
    private int indexValue;

    // Under lock for mutation; exposed as unmodifiable copy for reads
    @NonNull
    private List<PlayQueueItem> streams;

    /**
     * Create a PlayQueue starting at {@code index} and populated with {@code startWith}.
     * The initial index is normalized to a valid index for the provided list.
     */
    protected PlayQueue(final int index, @NonNull final List<PlayQueueItem> startWith) {
        Objects.requireNonNull(startWith, "startWith must not be null");
        this.streams = new ArrayList<>(startWith);
        final int sanitizedIndex = normalizeInitialIndex(index, this.streams.size());
        this.indexValue = sanitizedIndex;
        this.queueIndex = new AtomicInteger(sanitizedIndex);
        this.lock = new Object();
    }

    /**
     * Custom serialization to handle non-serializable fields
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        synchronized (lock) {
            // Update indexValue before serialization
            indexValue = queueIndex.get();
            out.defaultWriteObject();
        }
    }

    /**
     * Custom deserialization to reconstruct transient fields
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Reconstruct transient fields
        this.lock = new Object();
        this.queueIndex = new AtomicInteger(indexValue);
    }

    private static int normalizeInitialIndex(final int index, final int size) {
        if (size == 0) return 0;
        if (index < 0) return 0;
        return Math.min(index, size - 1);
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Abstract methods for completion and fetching
     //////////////////////////////////////////////////////////////////////////*/

    public abstract boolean isComplete();

    public abstract void fetch();

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

    public int indexOf(@NonNull final PlayQueueItem item) {
        Objects.requireNonNull(item, "item must not be null");
        synchronized (lock) {
            return streams.indexOf(item);
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

    /**
     * Returns an unmodifiable snapshot of the current streams.
     */
    @NonNull
    public List<PlayQueueItem> getStreams() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(streams));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Write operations (all synchronized on lock)
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Set index with boundary handling. If the queue is empty, index becomes 0.
     * If index is out-of-range and the queue is complete, wrap around using modulo.
     * If not complete and index exceeds last element, set to last element.
     */
    public void setIndex(final int index) {
        synchronized (lock) {
            if (streams.isEmpty()) {
                queueIndex.set(0);
                indexValue = 0;
                return;
            }
            int newIndex = index;
            if (newIndex < 0) {
                newIndex = 0;
            } else if (newIndex >= streams.size()) {
                newIndex = isComplete() ? mod(newIndex, streams.size()) : streams.size() - 1;
            }
            queueIndex.set(newIndex);
            indexValue = newIndex;
        }
    }

    private static int mod(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    public void offsetIndex(final int offset) {
        setIndex(getIndex() + offset);
    }

    /**
     * Append items to the end of the queue.
     */
    public void append(@NonNull final List<PlayQueueItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        synchronized (lock) {
            if (!items.isEmpty()) {
                streams.addAll(new ArrayList<>(items));
            }
        }
    }

    /**
     * Insert items at the given position. Clamp position to valid range [0, size].
     */
    public void insert(final int position, @NonNull final List<PlayQueueItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        synchronized (lock) {
            if (items.isEmpty()) return;
            final int pos = Math.max(0, Math.min(position, streams.size()));
            streams.addAll(pos, new ArrayList<>(items));
            final int current = queueIndex.get();
            if (pos <= current) {
                queueIndex.addAndGet(items.size());
                indexValue = queueIndex.get();
            }
        }
    }

    /**
     * Remove element at index and adjust the current index.
     */
    public void remove(final int index) {
        synchronized (lock) {
            if (index < 0 || index >= streams.size()) return;
            final int current = queueIndex.get();
            streams.remove(index);
            if (streams.isEmpty()) {
                queueIndex.set(0);
                indexValue = 0;
                return;
            }
            if (current > index) {
                queueIndex.decrementAndGet();
                indexValue = queueIndex.get();
            } else if (current == index) {
                final int newIndex = Math.min(index, streams.size() - 1);
                queueIndex.set(newIndex);
                indexValue = newIndex;
            } else if (queueIndex.get() >= streams.size()) {
                int newIndex = streams.size() - 1;
                queueIndex.set(newIndex);
                indexValue = newIndex;
            }
        }
    }

    /**
     * Move an item from source to target and adjust index consistently.
     */
    public void move(final int source, final int target) {
        synchronized (lock) {
            final int size = streams.size();
            if (source < 0 || source >= size || target < 0 || target >= size || source == target)
                return;

            final int current = queueIndex.get();

            PlayQueueItem item = streams.remove(source);
            streams.add(target, item);

            if (current == source) {
                queueIndex.set(target);
                indexValue = target;
            } else if (source < current && target >= current) {
                queueIndex.decrementAndGet();
                indexValue = queueIndex.get();
            } else if (source > current && target <= current) {
                queueIndex.incrementAndGet();
                indexValue = queueIndex.get();
            }
        }
    }

    /**
     * Replace the entire queue with a new list and optionally set the index.
     */
    public void replaceAll(@NonNull final List<PlayQueueItem> newStreams, final int newIndex) {
        Objects.requireNonNull(newStreams, "newStreams must not be null");
        synchronized (lock) {
            this.streams = new ArrayList<>(newStreams);
            final int sanitized = normalizeInitialIndex(newIndex, streams.size());
            queueIndex.set(sanitized);
            indexValue = sanitized;
        }
    }

    /**
     * Clear the queue and reset index to 0.
     */
    public void clear() {
        synchronized (lock) {
            streams.clear();
            queueIndex.set(0);
            indexValue = 0;
        }
    }

    /**
     * Advance to the next item. If queue is complete, wraps around; otherwise stops at last item.
     * @return the index after advancing
     */
    public int next() {
        synchronized (lock) {
            if (streams.isEmpty()) return 0;
            int next = queueIndex.get() + 1;
            if (next >= streams.size()) {
                next = isComplete() ? mod(next, streams.size()) : streams.size() - 1;
            }
            queueIndex.set(next);
            indexValue = next;
            return next;
        }
    }

    /**
     * Move to the previous item. If queue is complete, wraps around; otherwise stops at 0.
     * @return the index after moving
     */
    public int previous() {
        synchronized (lock) {
            if (streams.isEmpty()) return 0;
            int prev = queueIndex.get() - 1;
            if (prev < 0) {
                prev = isComplete() ? mod(prev, streams.size()) : 0;
            }
            queueIndex.set(prev);
            indexValue = prev;
            return prev;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
     // Comparison utilities
     //////////////////////////////////////////////////////////////////////////*/

    /**
     * Compares streams by serviceId and url equality.
     */
    public boolean equalStreams(@Nullable final PlayQueue other) {
        if (other == null) return false;
        synchronized (lock) {
            synchronized (other.lock) {
                if (this.size() != other.size()) return false;
                for (int i = 0; i < streams.size(); i++) {
                    final PlayQueueItem a = this.streams.get(i);
                    final PlayQueueItem b = other.streams.get(i);
                    if (a.getServiceId() != b.getServiceId() || !a.getUrl().equals(b.getUrl()))
                        return false;
                }
                return true;
            }
        }
    }

    public boolean equalStreamsAndIndex(@Nullable final PlayQueue other) {
        if (other == null) return false;
        return equalStreams(other) && other.getIndex() == this.getIndex();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayQueue)) return false;
        PlayQueue that = (PlayQueue) o;
        synchronized (lock) {
            synchronized (that.lock) {
                return queueIndex.get() == that.queueIndex.get() && streams.equals(that.streams);
            }
        }
    }

    @Override
    public int hashCode() {
        synchronized (lock) {
            return Objects.hash(queueIndex.get(), streams);
        }
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return "PlayQueue{" +
                    "index=" + queueIndex.get() +
                    ", size=" + streams.size() +
                    '}';
        }
    }
}