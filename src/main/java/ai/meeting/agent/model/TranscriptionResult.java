package ai.meeting.agent.model;

import java.util.ArrayList;
import java.util.List;

public class TranscriptionResult {
    private final String text;
    private final double confidence;
    private final List<SpeakerSegment> segments;

    public TranscriptionResult(String text, double confidence) {
        this.text = text;
        this.confidence = confidence;
        this.segments = new ArrayList<>();
    }

    public TranscriptionResult(String text, double confidence, List<SpeakerSegment> segments) {
        this.text = text;
        this.confidence = confidence;
        this.segments = segments != null ? segments : new ArrayList<>();
    }

    public String getText() {
        return text;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<SpeakerSegment> getSegments() {
        return segments;
    }

    public static class SpeakerSegment {
        private final String text;
        private final String speaker;
        private final double startTime;
        private final double endTime;
        private final double confidence;

        public SpeakerSegment(String text, String speaker, double startTime, double endTime, double confidence) {
            this.text = text;
            this.speaker = speaker;
            this.startTime = startTime;
            this.endTime = endTime;
            this.confidence = confidence;
        }

        // Getters
        public String getText() { return text; }
        public String getSpeaker() { return speaker; }
        public double getStartTime() { return startTime; }
        public double getEndTime() { return endTime; }
        public double getConfidence() { return confidence; }
    }
}
