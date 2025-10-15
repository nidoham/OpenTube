package com.nidoham.flowtube.player.media;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamSelector {
    private final Map<Integer, VideoStream> videoMap = new HashMap<>();
    private final Map<Integer, AudioStream> audioMap = new HashMap<>();
    
    public StreamSelector(StreamInfo streamInfo) {
        indexStreams(streamInfo.getVideoOnlyStreams(), streamInfo.getAudioStreams());
    }
    
    private void indexStreams(List<VideoStream> videos, List<AudioStream> audios) {
        int videoId = 1;
        for (VideoStream stream : videos) {
            videoMap.put(videoId++, stream);
        }
        
        int audioId = 1;
        for (AudioStream stream : audios) {
            audioMap.put(audioId++, stream);
        }
    }
    
    public List<QualityOption> getVideoQualities() {
        List<QualityOption> options = new ArrayList<>();
        for (Map.Entry<Integer, VideoStream> entry : videoMap.entrySet()) {
            VideoStream stream = entry.getValue();
            options.add(new QualityOption(entry.getKey(), stream.getResolution(), stream.getFormat().getName()));
        }
        return options;
    }
    
    public List<QualityOption> getAudioQualities() {
        List<QualityOption> options = new ArrayList<>();
        for (Map.Entry<Integer, AudioStream> entry : audioMap.entrySet()) {
            AudioStream stream = entry.getValue();
            String quality = stream.getAverageBitrate() + " kbps";
            options.add(new QualityOption(entry.getKey(), quality, stream.getFormat().getName()));
        }
        return options;
    }
    
    public VideoStream getVideo(int id) {
        return videoMap.get(id);
    }
    
    public AudioStream getAudio(int id) {
        return audioMap.get(id);
    }
    
    public static class QualityOption {
        public final int id;
        public final String quality;
        public final String format;
        
        public QualityOption(int id, String quality, String format) {
            this.id = id;
            this.quality = quality;
            this.format = format;
        }
        
        @Override
        public String toString() {
            return quality + " (" + format + ")";
        }
    }
}