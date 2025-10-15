package com.nidoham.stream.data;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RxStreamInfoExtractor {

    /**
     * Extracts StreamInfo from a given YouTube URL using RxJava 3.
     * This method returns a Single that will emit the StreamInfo on success
     * or an error on failure.
     *
     * The operation is performed on an I/O thread, and the result is observed
     * on the Android main thread.
     *
     * @param youtubeUrl The URL of the YouTube video.
     * @return A {@link Single<StreamInfo>} that will emit the result.
     */
    public static Single<StreamInfo> extract(final String youtubeUrl) {
        return Single.fromCallable(() -> {
            if (youtubeUrl == null || youtubeUrl.isBlank()) {
                // ইনপুট ভ্যালিড না হলে এরর থ্রো করা হবে
                throw new IllegalArgumentException("YouTube URL cannot be null or empty");
            }
            // নেটওয়ার্ক অপারেশন, যা ব্লকিং হতে পারে
            return StreamInfo.getInfo(NewPipe.getService("YouTube"), youtubeUrl);
        })
        // কাজটি ব্যাকগ্রাউন্ড থ্রেডে (I/O) চালানোর জন্য Schedulers.io() ব্যবহার করা হয়েছে
        .subscribeOn(Schedulers.io())
        // ফলাফল UI/Main থ্রেডে পাওয়ার জন্য AndroidSchedulers.mainThread() ব্যবহার করা হয়েছে
        .observeOn(AndroidSchedulers.mainThread());
    }
}





/** Useage:
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class MyViewModel extends ViewModel { // অথবা যেকোনো ক্লাস

    // সব সাবস্ক্রিপশন এখানে যোগ করা হবে যাতে একসাথে ডিসপোজ করা যায়
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public void fetchStreamInfo(String url) {
        Disposable disposable = RxStreamInfoExtractor.extract(url)
                .doOnSubscribe(d -> {
                    // onLoading() এর কাজ এখানে করা হবে
                    // যেমন: লোডিং স্পিনার দেখানো
                    System.out.println("Loading stream info...");
                })
                .subscribe(
                        streamInfo -> {
                            // onSuccess(streamInfo) এর কাজ এখানে
                            System.out.println("Successfully fetched: " + streamInfo.getName());
                            // UI তে ডেটা দেখানো
                        },
                        error -> {
                            // onError(error) এর কাজ এখানে
                            System.err.println("An error occurred: " + error.getMessage());
                            // ইউজারকে এরর মেসেজ দেখানো
                        }
                );

        // লাইফসাইকেল ম্যানেজমেন্টের জন্য CompositeDisposable-এ যোগ করা
        compositeDisposable.add(disposable);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // ViewModel ধ্বংস হওয়ার সময় সব রিকোয়েস্ট বাতিল করা হবে
        compositeDisposable.clear();
    }
}
*/