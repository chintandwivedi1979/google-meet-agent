package ai.meeting.agent.service;

import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

public class AudioCaptureService {
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private TargetDataLine microphone;
    private CompletableFuture<Void> captureTask;

    public String captureAudio(int durationSeconds) {
        long methodStartTime = System.currentTimeMillis();

        try {
            String fileName = "audio_capture_" + System.currentTimeMillis() + ".wav";

            // Configure high-quality audio format for better transcription
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000.0f, // Sample rate - 16kHz is optimal for speech recognition
                    16,       // Sample size in bits
                    1,        // Channels (mono)
                    2,        // Frame size
                    16000.0f, // Frame rate
                    false     // Big endian
            );

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                // Try to find an available line with similar format
                Mixer.Info[] mixers = AudioSystem.getMixerInfo();
                for (Mixer.Info mixerInfo : mixers) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        if (mixer.isLineSupported(info)) {
                            microphone = (TargetDataLine) mixer.getLine(info);
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (microphone == null) {
                    System.err.println("No suitable audio line found");
                    return null;
                }
            } else {
                microphone = (TargetDataLine) AudioSystem.getLine(info);
            }

            microphone.open(format);
            microphone.start();

            // Capture audio data
            ByteArrayOutputStream audioData = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000L);

            isCapturing.set(true);
            int totalBytesRead = 0;

            while (System.currentTimeMillis() < endTime && isCapturing.get()) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioData.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }

            microphone.stop();
            microphone.close();

            // Only save file if we captured some audio
            if (totalBytesRead > 0) {
                saveWavFile(fileName, audioData.toByteArray(), format);

                System.out.println("******** Audio capture completed in " + (System.currentTimeMillis() - methodStartTime) + " ms");
                return fileName;
            } else {
                System.out.println("No audio data captured");
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error capturing audio: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void saveWavFile(String fileName, byte[] audioData, AudioFormat format) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream audioInputStream = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());

        File file = new File(fileName);
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, file);
        System.out.println("Audio saved: " + fileName + " (" + audioData.length + " bytes)");
    }

    public void stop() {
        isCapturing.set(false);
        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
        }
        if (captureTask != null) {
            captureTask.cancel(true);
        }
    }

    public boolean isCapturing() {
        return isCapturing.get();
    }
}
