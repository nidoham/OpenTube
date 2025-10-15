package com.nidoham.opentube;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// নতুন RxStreamInfoExtractor ক্লাস ইমপোর্ট করা হয়েছে
import com.nidoham.stream.data.RxStreamInfoExtractor;

import com.nidoham.opentube.databinding.ActivityExperimentBinding;

import com.nidoham.stream.stream.StreamSelector;
import com.nidoham.stream.stream.StreamSelector.QualityOption;


import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

// RxJava ইমপোর্ট করা হয়েছে
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class ExperimentActivity extends AppCompatActivity {

    private ActivityExperimentBinding binding;
    private StreamSelector selector;
    private boolean isExtracting = false;
    
    private int selectedVideoId = -1;
    private int selectedAudioId = -1;

    // লাইফসাইকেল ম্যানেজমেন্টের জন্য CompositeDisposable যোগ করা হয়েছে
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExperimentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // StreamInfoExtractor.getInstance() এর আর প্রয়োজন নেই

        binding.buttonLoadStream.setOnClickListener(v -> extractStream());
        binding.buttonPlay.setOnClickListener(v -> playStream());
        binding.buttonDownload.setOnClickListener(v -> downloadStream());
        
        // Spinner listeners আগের মতোই থাকছে
        setupSpinnerListeners();
    }

    private void setupSpinnerListeners() {
        binding.spinnerVideoQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selector != null) {
                    List<QualityOption> options = selector.getVideoQualities();
                    if (position < options.size()) {
                        selectedVideoId = options.get(position).id;
                        updateStreamInfo();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        binding.spinnerAudioQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selector != null) {
                    List<QualityOption> options = selector.getAudioQualities();
                    if (position < options.size()) {
                        selectedAudioId = options.get(position).id;
                        updateStreamInfo();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void extractStream() {
        if (isExtracting) return;

        String url = binding.editTextYoutubeUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "Enter a YouTube URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // RxStreamInfoExtractor ব্যবহার করে স্ট্রিম এক্সট্র্যাক্ট করা হচ্ছে
        Disposable disposable = RxStreamInfoExtractor.extract(url)
                .doOnSubscribe(d -> {
                    // onLoading() এর কাজ এখানে করা হচ্ছে
                    isExtracting = true;
                    binding.buttonLoadStream.setEnabled(false);
                    binding.streamInfoTextView.setText("Extracting stream data...");
                    binding.qualityCard.setVisibility(View.GONE);
                })
                .doFinally(() -> {
                    // অপারেশন সফল হোক বা ব্যর্থ, সবশেষে এটি কল হবে
                    isExtracting = false;
                    binding.buttonLoadStream.setEnabled(true);
                })
                .subscribe(
                        // onSuccess
                        info -> {
                            selector = new StreamSelector(info);
                            
                            List<QualityOption> videoOptions = selector.getVideoQualities();
                            List<QualityOption> audioOptions = selector.getAudioQualities();

                            if (!videoOptions.isEmpty() || !audioOptions.isEmpty()) {
                                binding.qualityCard.setVisibility(View.VISIBLE);
                                populateSpinners(videoOptions, audioOptions);
                                updateStreamInfo();
                            } else {
                                binding.streamInfoTextView.setText("No video or audio streams available");
                                binding.qualityCard.setVisibility(View.GONE);
                            }
                        },
                        // onError
                        error -> {
                            binding.streamInfoTextView.setText("Error: " + error.getMessage());
                            Toast.makeText(ExperimentActivity.this,
                                "Extraction failed: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        }
                );
        
        // ডিসপোজেবলটি CompositeDisposable-এ যোগ করা হচ্ছে যাতে onDestroy-তে এটি বাতিল করা যায়
        compositeDisposable.add(disposable);
    }

    private void populateSpinners(List<QualityOption> videoOptions, List<QualityOption> audioOptions) {
        // Populate video spinner
        if (!videoOptions.isEmpty()) {
            ArrayAdapter<QualityOption> videoAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, videoOptions
            );
            videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerVideoQuality.setAdapter(videoAdapter);
            selectedVideoId = videoOptions.get(0).id;
        }
        
        // Populate audio spinner
        if (!audioOptions.isEmpty()) {
            ArrayAdapter<QualityOption> audioAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, audioOptions
            );
            audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerAudioQuality.setAdapter(audioAdapter);
            selectedAudioId = audioOptions.get(0).id;
        }
    }
    
    @Deprecated
    private void updateStreamInfo() {
        // এই মেথডটি অপরিবর্তিত থাকছে
        if (selector == null) return;
        
        StringBuilder infoText = new StringBuilder();
        
        if (selectedVideoId != -1) {
            VideoStream video = selector.getVideo(selectedVideoId);
            if (video != null) {
                infoText.append("=== VIDEO STREAM ===\n")
                      .append("Resolution: ").append(video.getResolution()).append("\n")
                      .append("Format: ").append(video.getFormat().getName()).append("\n")
                      .append("URL: ").append(video.getUrl()).append("\n\n");
            }
        }
        
        if (selectedAudioId != -1) {
            AudioStream audio = selector.getAudio(selectedAudioId);
            if (audio != null) {
                infoText.append("=== AUDIO STREAM ===\n")
                      .append("Bitrate: ").append(audio.getAverageBitrate()).append(" kbps\n")
                      .append("Format: ").append(audio.getFormat().getName()).append("\n")
                      .append("URL: ").append(audio.getUrl()).append("\n");
            }
        }
        
        if (infoText.length() > 0) {
            binding.streamInfoTextView.setText(infoText.toString());
        }
    }
    
    private void playStream() {
        // এই মেথডটি অপরিবর্তিত থাকছে
        if (selector == null || (selectedVideoId == -1 && selectedAudioId == -1)) {
            Toast.makeText(this, "No stream selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Play functionality - to be implemented", Toast.LENGTH_SHORT).show();
    }
    
    private void downloadStream() {
        // এই মেথডটি অপরিবর্তিত থাকছে
        if (selector == null || (selectedVideoId == -1 && selectedAudioId == -1)) {
            Toast.makeText(this, "No stream selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Download functionality - to be implemented", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // মেমোরি লিক এড়ানোর জন্য সমস্ত সাবস্ক্রিপশন বাতিল করা হচ্ছে
        compositeDisposable.clear();
        binding = null;
    }
}