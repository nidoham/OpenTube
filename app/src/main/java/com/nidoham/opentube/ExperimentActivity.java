package com.nidoham.opentube;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nidoham.flowtube.player.media.StreamSelector;
import com.nidoham.flowtube.player.media.StreamSelector.QualityOption;
import com.nidoham.flowtube.player.streams.StreamInfoExtractor;
import com.nidoham.flowtube.player.streams.StreamInfoCallback;
import com.nidoham.opentube.databinding.ActivityExperimentBinding;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class ExperimentActivity extends AppCompatActivity {

    private ActivityExperimentBinding binding;
    private StreamInfoExtractor extractor;
    private StreamSelector selector;
    private boolean isExtracting = false;
    
    private int selectedVideoId = -1;
    private int selectedAudioId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExperimentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        extractor = StreamInfoExtractor.getInstance();

        binding.buttonLoadStream.setOnClickListener(v -> extractStream());
        binding.buttonPlay.setOnClickListener(v -> playStream());
        binding.buttonDownload.setOnClickListener(v -> downloadStream());
        
        // Set up spinner listeners
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

        isExtracting = true;
        binding.buttonLoadStream.setEnabled(false);
        binding.streamInfoTextView.setText("Loading stream information...");

        extractor.extractStreamInfo(url, new StreamInfoCallback() {
            @Override
            public void onLoading() {
                runOnUiThread(() -> {
                    binding.streamInfoTextView.setText("Extracting stream data...");
                });
            }

            @Override
            public void onSuccess(StreamInfo info) {
                runOnUiThread(() -> {
                    isExtracting = false;
                    binding.buttonLoadStream.setEnabled(true);
                    
                    selector = new StreamSelector(info);
                    
                    // Get quality options
                    List<QualityOption> videoOptions = selector.getVideoQualities();
                    List<QualityOption> audioOptions = selector.getAudioQualities();

                    if (!videoOptions.isEmpty() || !audioOptions.isEmpty()) {
                        // Show quality selection card
                        binding.qualityCard.setVisibility(View.VISIBLE);
                        
                        // Populate video spinner
                        if (!videoOptions.isEmpty()) {
                            ArrayAdapter<QualityOption> videoAdapter = new ArrayAdapter<>(
                                ExperimentActivity.this,
                                android.R.layout.simple_spinner_item,
                                videoOptions
                            );
                            videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            binding.spinnerVideoQuality.setAdapter(videoAdapter);
                            selectedVideoId = videoOptions.get(0).id;
                        }
                        
                        // Populate audio spinner
                        if (!audioOptions.isEmpty()) {
                            ArrayAdapter<QualityOption> audioAdapter = new ArrayAdapter<>(
                                ExperimentActivity.this,
                                android.R.layout.simple_spinner_item,
                                audioOptions
                            );
                            audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            binding.spinnerAudioQuality.setAdapter(audioAdapter);
                            selectedAudioId = audioOptions.get(0).id;
                        }
                        
                        // Update info display
                        updateStreamInfo();
                    } else {
                        binding.streamInfoTextView.setText("No video or audio streams available");
                        binding.qualityCard.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    isExtracting = false;
                    binding.buttonLoadStream.setEnabled(true);
                    binding.streamInfoTextView.setText("Error: " + error.getMessage());
                    Toast.makeText(ExperimentActivity.this, 
                        "Extraction failed: " + error.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    @Deprecated
    private void updateStreamInfo() {
        if (selector == null) return;
        
        StringBuilder info = new StringBuilder();
        
        // Video stream info
        if (selectedVideoId != -1) {
            VideoStream video = selector.getVideo(selectedVideoId);
            if (video != null) {
                info.append("=== VIDEO STREAM ===\n");
                info.append("Resolution: ").append(video.getResolution()).append("\n");
                info.append("Format: ").append(video.getFormat().getName()).append("\n");
                info.append("URL: ").append(video.getUrl()).append("\n\n");
            }
        }
        
        // Audio stream info
        if (selectedAudioId != -1) {
            AudioStream audio = selector.getAudio(selectedAudioId);
            if (audio != null) {
                info.append("=== AUDIO STREAM ===\n");
                info.append("Bitrate: ").append(audio.getAverageBitrate()).append(" kbps\n");
                info.append("Format: ").append(audio.getFormat().getName()).append("\n");
                info.append("URL: ").append(audio.getUrl()).append("\n");
            }
        }
        
        if (info.length() > 0) {
            binding.streamInfoTextView.setText(info.toString());
        }
    }
    
    private void playStream() {
        if (selector == null || (selectedVideoId == -1 && selectedAudioId == -1)) {
            Toast.makeText(this, "No stream selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // TODO: Implement play functionality
        Toast.makeText(this, "Play functionality - to be implemented", Toast.LENGTH_SHORT).show();
    }
    
    private void downloadStream() {
        if (selector == null || (selectedVideoId == -1 && selectedAudioId == -1)) {
            Toast.makeText(this, "No stream selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // TODO: Implement download functionality
        Toast.makeText(this, "Download functionality - to be implemented", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}