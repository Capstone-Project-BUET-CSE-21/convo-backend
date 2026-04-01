package com.convo.audio_watermark.service;

import com.convo.audio_watermark.dto.WatermarkDetectionResponse;
import com.convo.audio_watermark.entity.WatermarkConfig;
import com.convo.audio_watermark.repository.WatermarkConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.*;
import java.io.*;
import java.util.*;

@Service
public class WatermarkDetectionService {

    private static final String WORKLET_DEFAULT_SEED = "42";
    
    /** * Adjusted to match Frontend Test 3 logic. 
     * If Test 3 uses 0.5, but results are inconsistent, 0.15–0.2 is safer for noisy signals.
     */
    private static final double THRESHOLD_FACTOR = 0.5; 

    @Autowired
    private WatermarkConfigRepository repository;

    public WatermarkDetectionResponse detect(MultipartFile audioFile, String sessionId)
            throws IOException, UnsupportedAudioFileException {

        List<WatermarkConfig> sessionConfigs = repository.findBySessionId(sessionId);
        if (sessionConfigs.isEmpty()) {
            return new WatermarkDetectionResponse(
                    null, sessionId, 0.0, false, 0, 0,
                    Collections.emptyMap(),
                    "No registered users found for session: " + sessionId);
        }

        float[] samples = decodeAudioToFloatSamples(audioFile);
        if (samples.length == 0) {
            return new WatermarkDetectionResponse(
                    null, sessionId, 0.0, false, 0, sessionConfigs.size(),
                    Collections.emptyMap(),
                    "Audio file is empty or could not be decoded.");
        }

        // Use settings from the first config in the session
        int frameSize = sessionConfigs.get(0).getFrameSize();
        double alpha  = sessionConfigs.get(0).getAlpha();

        List<float[]> frames = segmentIntoFrames(samples, frameSize);
        int totalFrames = frames.size();

        Map<String, Double> allUserScores = new LinkedHashMap<>();
        Map<String, String> effectiveSeeds = new LinkedHashMap<>();

        for (WatermarkConfig config : sessionConfigs) {
            double dbScore      = computeAverageCorrelation(frames, config.getSeed(), frameSize);
            double defaultScore = computeAverageCorrelation(frames, WORKLET_DEFAULT_SEED, frameSize);

            double best;
            String usedSeed;
            if (defaultScore > dbScore) {
                best = defaultScore;
                usedSeed = WORKLET_DEFAULT_SEED;
            } else {
                best = dbScore;
                usedSeed = config.getSeed();
            }

            allUserScores.put(config.getUserId(), round4(best));
            effectiveSeeds.put(config.getUserId(), usedSeed);
        }

        // Pick best candidate
        String bestUser = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : allUserScores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestUser = e.getKey();
            }
        }

        // Calculate PN Power for Threshold (Matching JS: average square of PN chips)
        float[] pn = generatePN(effectiveSeeds.get(bestUser), frameSize);
        double pnPower = 0.0;
        for (float v : pn) pnPower += (double) v * v;
        pnPower /= frameSize;

        // Threshold matching JS logic: ALPHA * FRAME_SIZE * PN_POWER * FACTOR
        double threshold = alpha * frameSize * pnPower * THRESHOLD_FACTOR;
        boolean detected = bestScore >= threshold;

        String message = detected
                ? String.format("Watermark detected. Match: '%s' (score=%.4f, threshold=%.4f).", 
                                bestUser, bestScore, threshold)
                : String.format("No watermark detected. High score: %.4f (threshold=%.4f) for user '%s'.", 
                                bestScore, threshold, bestUser);

        return new WatermarkDetectionResponse(
                detected ? bestUser : null,
                sessionId,
                round4(bestScore),
                detected,
                totalFrames,
                sessionConfigs.size(),
                allUserScores,
                message);
    }

    private float[] decodeAudioToFloatSamples(MultipartFile audioFile)
            throws IOException, UnsupportedAudioFileException {
        byte[] fileBytes = audioFile.getBytes();
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(fileBytes)))) {
            AudioFormat src = ais.getFormat();
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 
                                                    src.getSampleRate(), 16, 1, 2, 
                                                    src.getSampleRate(), false); // Little Endian
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, ais)) {
                return pcmBytesToFloats(pcm.readAllBytes());
            }
        } catch (Exception e) {
            return pcmBytesToFloats(fileBytes); // Fallback for raw files
        }
    }

    private float[] pcmBytesToFloats(byte[] bytes) {
        int n = bytes.length / 2;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            // Read 16-bit Little Endian
            int low = bytes[2 * i] & 0xFF;
            int high = bytes[2 * i + 1] << 8;
            short s = (short) (high | low);
            out[i] = s / 32768.0f;
        }
        return out;
    }

    private List<float[]> segmentIntoFrames(float[] samples, int frameSize) {
        List<float[]> frames = new ArrayList<>();
        for (int off = 0; off + frameSize <= samples.length; off += frameSize)
            frames.add(Arrays.copyOfRange(samples, off, off + frameSize));
        return frames;
    }

    private double computeAverageCorrelation(List<float[]> frames, String seed, int frameSize) {
        float[] pn = generatePN(seed, frameSize);
        double total = 0.0;
        for (float[] frame : frames) {
            double dot = 0.0;
            for (int i = 0; i < frameSize; i++) dot += (double)frame[i] * pn[i];
            total += dot;
        }
        return frames.isEmpty() ? 0.0 : total / frames.size();
    }

    private long resolveSeed(String seed) {
        try {
            long v = Long.parseLong(seed);
            return (v >= 0 && v <= 0xFFFFFFFFL) ? (v & 0xFFFFFFFFL) : hashString(seed);
        } catch (NumberFormatException e) {
            return hashString(seed);
        }
    }

    private long hashString(String str) {
        long hash = 5381L;
        for (int i = 0; i < str.length(); i++) {
            hash = (((hash << 5) + hash) + str.charAt(i)) & 0xFFFFFFFFL;
        }
        return hash;
    }

    private double mulberry32Next(long[] state) {
        state[0] = (state[0] + 0x6d2b79f5L) & 0xFFFFFFFFL;
        long s = state[0];
        long t = (s ^ (s >>> 15)) * (1L | s) & 0xFFFFFFFFL;
        t = (t + ((t ^ (t >>> 7)) * (61L | t) & 0xFFFFFFFFL)) ^ t;
        t = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
        return t / 4294967296.0;
    }

    private float[] generatePN(String seed, int length) {
        long[] state = { resolveSeed(seed) };
        float[] pn = new float[length];
        for (int i = 0; i < length; i++)
            pn[i] = (float) (mulberry32Next(state) * 2.0 - 1.0);
        return pn;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}