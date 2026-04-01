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

/**
 * Detects audio watermarks using PN (pseudo-noise) sequence correlation.
 *
 * Detection pipeline:
 *  1. Decode uploaded audio to raw PCM samples (16-bit signed, mono)
 *  2. Segment PCM into frames matching the embedding frame size
 *  3. For every user registered in the session, regenerate their PN sequence
 *     from their unique seed using a seeded PRNG (same PRNG as the embedder)
 *  4. Compute the normalised cross-correlation between each frame and the PN sequence
 *  5. Average the per-frame correlations → one scalar score per user
 *  6. The user with the highest score wins; if that score exceeds the threshold,
 *     a watermark is declared detected
 */
@Service
public class WatermarkDetectionService {

    /** Correlation threshold above which a watermark is declared present. */
    private static final double DETECTION_THRESHOLD = 0.05;

    @Autowired
    private WatermarkConfigRepository repository;

    /**
     * Main entry point: detect whose watermark is embedded in the supplied audio file.
     *
     * @param audioFile the audio file to analyse (WAV preferred; will attempt
     *                  AudioInputStream conversion for other formats)
     * @param sessionId the session/meeting ID – used to look up registered users
     * @return a {@link WatermarkDetectionResponse} with the result and evaluation metrics
     */
    public WatermarkDetectionResponse detect(MultipartFile audioFile, String sessionId)
            throws IOException, UnsupportedAudioFileException {

        // ── 1. Load all users registered for this session ─────────────────────
        List<WatermarkConfig> sessionConfigs = repository.findBySessionId(sessionId);
        if (sessionConfigs.isEmpty()) {
            return new WatermarkDetectionResponse(
                    null, sessionId, 0.0, false, 0, 0,
                    Collections.emptyMap(),
                    "No registered users found for session: " + sessionId);
        }

        // ── 2. Decode audio → normalised float samples ─────────────────────────
        float[] samples = decodeAudioToFloatSamples(audioFile);
        if (samples.length == 0) {
            return new WatermarkDetectionResponse(
                    null, sessionId, 0.0, false, 0, sessionConfigs.size(),
                    Collections.emptyMap(),
                    "Audio file is empty or could not be decoded.");
        }

        // Use the frame size stored in the first config entry (all entries for a
        // session should share the same frameSize and alpha since they come from
        // the same WatermarkConfigService defaults).
        int frameSize = sessionConfigs.get(0).getFrameSize();

        // ── 3. Segment audio into frames ───────────────────────────────────────
        List<float[]> frames = segmentIntoFrames(samples, frameSize);
        int totalFrames = frames.size();

        // ── 4. Compute correlation score for each user ─────────────────────────
        Map<String, Double> allUserScores = new LinkedHashMap<>();

        for (WatermarkConfig config : sessionConfigs) {
            double score = computeCorrelationScore(frames, config.getSeed(), frameSize);
            allUserScores.put(config.getUserId(), score);
        }

        // ── 5. Identify best candidate ─────────────────────────────────────────
        String bestUser = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : allUserScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestUser = entry.getKey();
            }
        }

        boolean detected = bestScore >= DETECTION_THRESHOLD;

        // Round score to 4 decimal places for cleaner output
        double roundedScore = Math.round(bestScore * 10000.0) / 10000.0;

        // Round all scores map too
        Map<String, Double> roundedScores = new LinkedHashMap<>();
        allUserScores.forEach((uid, sc) ->
                roundedScores.put(uid, Math.round(sc * 10000.0) / 10000.0));

        String message = detected
                ? String.format("Watermark detected. Best match: user '%s' (score=%.4f, threshold=%.4f).",
                        bestUser, bestScore, DETECTION_THRESHOLD)
                : String.format("No watermark detected above threshold %.4f. Highest score: %.4f for user '%s'.",
                        DETECTION_THRESHOLD, bestScore, bestUser);

        return new WatermarkDetectionResponse(
                detected ? bestUser : null,
                sessionId,
                roundedScore,
                detected,
                totalFrames,
                sessionConfigs.size(),
                roundedScores,
                message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Decode an uploaded audio file to a normalised float array in [-1, +1].
     * Supports WAV and any format Java's AudioSystem can open.
     * For formats it cannot open natively (MP3 etc.) the raw PCM bytes are
     * interpreted directly as 16-bit little-endian signed integers.
     */
    private float[] decodeAudioToFloatSamples(MultipartFile audioFile)
            throws IOException, UnsupportedAudioFileException {

        byte[] fileBytes = audioFile.getBytes();

        try (InputStream rawStream = new ByteArrayInputStream(fileBytes);
             AudioInputStream ais = AudioSystem.getAudioInputStream(
                     new BufferedInputStream(rawStream))) {

            // Convert to a known PCM format: 16-bit signed mono, native byte order
            AudioFormat srcFormat = ais.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    srcFormat.getSampleRate(),
                    16,
                    1,           // mono – sum channels if stereo
                    2,           // 2 bytes per frame (16-bit mono)
                    srcFormat.getSampleRate(),
                    false);      // little-endian

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, ais)) {
                byte[] pcmBytes = pcmStream.readAllBytes();
                return pcmBytesToFloats(pcmBytes);
            }

        } catch (UnsupportedAudioFileException e) {
            // Fallback: treat raw bytes as 16-bit LE PCM directly
            // (handles cases where the AudioSystem has no spi for the container)
            return pcmBytesToFloats(fileBytes);
        }
    }

    /** Convert raw 16-bit little-endian signed PCM bytes to normalised floats. */
    private float[] pcmBytesToFloats(byte[] pcmBytes) {
        int numSamples = pcmBytes.length / 2;
        float[] samples = new float[numSamples];
        for (int i = 0; i < numSamples; i++) {
            // Combine two bytes into a signed 16-bit value (little-endian)
            short s = (short) ((pcmBytes[2 * i + 1] << 8) | (pcmBytes[2 * i] & 0xFF));
            samples[i] = s / 32768.0f;
        }
        return samples;
    }

    /**
     * Slice the sample array into non-overlapping frames of {@code frameSize}.
     * Any trailing samples that do not fill a complete frame are discarded
     * (consistent with typical embedding behaviour).
     */
    private List<float[]> segmentIntoFrames(float[] samples, int frameSize) {
        List<float[]> frames = new ArrayList<>();
        for (int offset = 0; offset + frameSize <= samples.length; offset += frameSize) {
            float[] frame = Arrays.copyOfRange(samples, offset, offset + frameSize);
            frames.add(frame);
        }
        return frames;
    }

    /**
     * Compute the average normalised cross-correlation between each audio frame
     * and the PN sequence derived from {@code seed}.
     *
     * <p>The PN sequence is regenerated here using exactly the same seeded PRNG
     * strategy as the front-end embedder: the seed string is converted to a
     * long via summing char codes (matching a common JS {@code seedrandom} port)
     * and fed into a linear congruential generator, producing ±1 chips.
     *
     * <p>Normalised correlation for a single frame f and PN sequence p:
     * <pre>
     *   corr = Σ(f_i * p_i) / (||f|| * ||p||)
     * </pre>
     * This is bounded in [-1, +1]; a random unrelated signal scores ≈ 0.
     */
    private double computeCorrelationScore(List<float[]> frames, String seed, int frameSize) {
        float[] pnSequence = generatePnSequence(seed, frameSize);
        double pnNorm = vectorNorm(pnSequence);

        if (pnNorm == 0.0) return 0.0;

        double totalCorrelation = 0.0;
        int validFrames = 0;

        for (float[] frame : frames) {
            double dot = dotProduct(frame, pnSequence);
            double frameNorm = vectorNorm(frame);

            if (frameNorm == 0.0) continue; // skip silent frames

            double normalisedCorr = dot / (frameNorm * pnNorm);
            totalCorrelation += normalisedCorr;
            validFrames++;
        }

        return validFrames > 0 ? totalCorrelation / validFrames : 0.0;
    }

    /**
     * Regenerate the PN sequence for a given seed string.
     *
     * <p>Algorithm mirrors the front-end JS embedder:
     * <ol>
     *   <li>Convert seed string → numeric seed via char-code summation
     *   <li>Use a seeded 48-bit LCG (same constants as {@code java.util.Random})
     *   <li>Each chip = nextDouble() >= 0.5 ? +1 : -1
     * </ol>
     */
    private float[] generatePnSequence(String seed, int length) {
        long numericSeed = 0;
        for (char c : seed.toCharArray()) {
            numericSeed += c;
        }

        // LCG matching java.util.Random internals for reproducibility
        Random rng = new Random(numericSeed);
        float[] pn = new float[length];
        for (int i = 0; i < length; i++) {
            pn[i] = rng.nextDouble() >= 0.5 ? 1.0f : -1.0f;
        }
        return pn;
    }

    /** Dot product of two equal-length float vectors. */
    private double dotProduct(float[] a, float[] b) {
        double sum = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** L2 norm of a float vector. */
    private double vectorNorm(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }
}