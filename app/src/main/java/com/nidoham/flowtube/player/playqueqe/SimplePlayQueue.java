package com.nidoham.flowtube.player.playqueue;

import androidx.annotation.NonNull;
import java.util.List;

/*
 * 🔹 বাংলা মন্তব্য:
 * এই ক্লাস PlayQueue কে extend করে সাধারণ queue ব্যবহার করতে দেয়
 * isComplete() বলে দেবে কিউ ঘুরে আবার শুরু হবে কিনা
 * fetch() future use (যেমন: পরের পেজ load করার জন্য)
 */
public class SimplePlayQueue extends PlayQueue {

    private static final long serialVersionUID = 1L;

    private final boolean complete;

    /**
     * Create a SimplePlayQueue
     * @param complete true হলে queue শেষ হলে আবার শুরু থেকে শুরু হবে (loop)
     * @param index শুরুর index
     * @param items PlayQueueItem এর list
     */
    public SimplePlayQueue(boolean complete, int index, @NonNull List<PlayQueueItem> items) {
        super(index, items);
        this.complete = complete;
    }

    /**
     * Convenience constructor - শুরুতে index 0 থেকে শুরু হবে
     */
    public SimplePlayQueue(boolean complete, @NonNull List<PlayQueueItem> items) {
        this(complete, 0, items);
    }

    @Override
    public boolean isComplete() {
        // 🔸 যদি complete true হয় তবে queue ঘুরে যাবে (wrap around)
        return complete;
    }

    @Override
    public void fetch() {
        // 🔸 ভবিষ্যতে auto next page load করার logic এখানে রাখা যাবে
        // এখন empty implementation
    }

    @Override
    public String toString() {
        return "SimplePlayQueue{" +
                "complete=" + complete +
                ", index=" + getIndex() +
                ", size=" + size() +
                '}';
    }
}