package ai.meeting.agent.service;

import ai.meeting.agent.model.TranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public class WhisperService {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String API_KEY = System.getenv("OPEN_API_KEY"); // Set this environment variable
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient;

    public WhisperService() {
        // Initialize OkHttp client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Transcribe audio using OpenAI Whisper API
     */
    public TranscriptionResult transcribe(String audioFilePath) {
        long startTime = System.currentTimeMillis();
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.err.println("⚠️ OpenAI API key not found. Falling back to local Whisper.");
            return transcribeLocal(audioFilePath);
        }

        try {
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                throw new FileNotFoundException("Audio file not found: " + audioFilePath);
            }

            System.out.println("🎤 Transcribing audio with OpenAI Whisper API...");

            // Create multipart request body
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFile.getName(),
                            RequestBody.create(audioFile, MediaType.parse("audio/wav")))
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "en")
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("temperature", "0")
                    .build();

            // Create request
            Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .post(requestBody)
                    .build();

            // Execute request
            TranscriptionResult result;
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    result = parseOpenAIResponse(responseBody);
                } else {
                    System.err.println("❌ OpenAI API error: " + response.code());
                    if (response.body() != null) {
                        System.err.println("Error details: " + response.body().string());
                    }
                    return transcribeLocal(audioFilePath);
                }
            }

            System.out.println("********* ✅ Transcription completed in " + (System.currentTimeMillis() - startTime) + " ms");
            return result;

        } catch (Exception e) {
            System.err.println("❌ Error with OpenAI Whisper API: " + e.getMessage());
            return transcribeLocal(audioFilePath);
        }
    }

    /**
     * Parse OpenAI API response to extract transcription and confidence
     */
    private TranscriptionResult parseOpenAIResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            String text = root.get("text").asText();

            double avgConfidence = 0.8; // Default confidence
            JsonNode segments = root.get("segments");

            if (segments != null && segments.isArray()) {
                double totalConfidence = 0.0;
                int segmentCount = 0;

                for (JsonNode segment : segments) {
                    if (segment.has("avg_logprob")) {
                        totalConfidence += Math.exp(segment.get("avg_logprob").asDouble());
                        segmentCount++;
                    }
                }

                if (segmentCount > 0) {
                    avgConfidence = totalConfidence / segmentCount;
                }
            }

            return new TranscriptionResult(text, Math.max(0.0, Math.min(1.0, avgConfidence)));

        } catch (Exception e) {
            System.err.println("❌ Error parsing OpenAI response: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fallback to local Whisper installation
     */
    private TranscriptionResult transcribeLocal(String audioFilePath) {
        try {
            System.out.println("🔄 Using local Whisper for transcription...");

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "whisper",
                    audioFilePath,
                    "--output_format", "json",
                    "--language", "en",
                    "--model", "base",
                    "--verbose", "False"
            );

            Process process = processBuilder.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Local Whisper transcription timed out");
            }

            String outputFile = audioFilePath.replace(".wav", ".json");
            if (new File(outputFile).exists()) {
                String jsonContent = Files.readString(new File(outputFile).toPath());
                new File(outputFile).delete();

                JsonNode root = objectMapper.readTree(jsonContent);
                String text = root.get("text").asText();

                return new TranscriptionResult(text.trim(), 0.8);
            }

        } catch (Exception e) {
            System.err.println("❌ Error with local Whisper: " + e.getMessage());
        }

        return null;
    }
}
