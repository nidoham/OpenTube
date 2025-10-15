package com.nidoham.flowtube.player.streams;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamInfoExtractor {
    private static StreamInfoExtractor instance;
    private ExecutorService executorService = Executors.newFixedThreadPool(2);

    private StreamInfoExtractor() {}

    public static synchronized StreamInfoExtractor getInstance() {
        if (instance == null) {
            instance = new StreamInfoExtractor();
        }
        return instance;
    }

    // বাংলা: YouTube লিংক থেকে স্ট্রিম ইনফো এক্সট্র্যাক্ট করা
    public synchronized void extractStreamInfo(String youtubeUrl, StreamInfoCallback callback) {
        if (youtubeUrl == null || youtubeUrl.isBlank()) {
            postError(callback, new IllegalArgumentException("YouTube URL cannot be null or empty"));
            return;
        }

        // বাংলা: যদি থ্রেডপুল বন্ধ হয়ে থাকে, নতুন করে তৈরি করো
        if (executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newFixedThreadPool(2);
        }

        callback.onLoading();
        executorService.execute(() -> {
            try {
                StreamInfo streamInfo = StreamInfo.getInfo(NewPipe.getService("YouTube"), youtubeUrl);
                postSuccess(callback, streamInfo);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    // বাংলা: সফলভাবে ডেটা পেলে মেইন থ্রেডে পাঠানো
    private static void postSuccess(StreamInfoCallback callback, StreamInfo streamInfo) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> callback.onSuccess(streamInfo));
    }

    // বাংলা: কোনো এরর হলে মেইন থ্রেডে পাঠানো
    private static void postError(StreamInfoCallback callback, Exception error) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> callback.onError(error));
    }

    // বাংলা: থ্রেডপুল বন্ধ করা (প্রয়োজনে)
    public synchronized void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}