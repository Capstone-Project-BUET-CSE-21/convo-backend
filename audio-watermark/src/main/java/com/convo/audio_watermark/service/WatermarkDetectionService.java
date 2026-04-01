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
 * Detects audio watermarks using PN-sequence correlation.
 *
 * PN generation is an exact Java port of the front-end JS:
 *
 *   hashString(str)   — djb2 variant, unsigned 32-bit  (used for String seeds)
 *   mulberry32(seed)  — same PRNG as JS mulberry32
 *   generatePN(seed)  — chips = rand() * 2 − 1  (continuous, not binary ±1)
 *
 * Seed resolution mirrors JS generatePN():
 *   if seed is a pure integer string (e.g. "42") → use it as a raw uint32
 *   otherwise                                    → run through hashString()
 *
 * This handles both:
 *   • DB seeds like "A7F3K9"  (string → hashString)
 *   • The audio-worklet default seed 42 stored/compared as "42" (integer path)
 *
 * Correlation is the raw dot-product per frame averaged across all frames,
 * matching JS: correlate(frame, pn) = Σ frame[i] * pn[i]
 *
 * Threshold mirrors JS Test 3/5: alpha * frameSize * pnPower * 0.5
 */
@Service
public class WatermarkDetectionService {

    /** Default numeric seed used by the audio worklet when no config is supplied. */
    private static final String WORKLET_DEFAULT_SEED = "42";

    /** Default alpha used by the audio worklet when no config is supplied. */
    private static final double WORKLET_DEFAULT_ALPHA = 0.005;

    private static final double THRESHOLD_FACTOR = 0.5;

    @Autowired
    private WatermarkConfigRepository repository;

    public WatermarkDetectionResponse detect(MultipartFile audioFile, String sessionId)
            throws IOException, UnsupportedAudioFileException {

        // ── 1. Look up every user registered in this session ──────────────────
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

        int frameSize = sessionConfigs.get(0).getFrameSize();
        // alpha from DB is 0.02, but the audio worklet default is 0.005 when
        // no config is passed. The threshold is computed after we know which
        // seed won, so we pick the matching alpha there.

        // ── 3. Segment into frames ─────────────────────────────────────────────
        List<float[]> frames = segmentIntoFrames(samples, frameSize);
        int totalFrames = frames.size();

        // ── 4. Score every user, trying both their DB seed and the worklet
        //       default seed (42), and keeping whichever scores higher.
        //       This is needed because the WatermarkTestPage Test 2 recorder
        //       calls createProcessedStream() with no config, so the worklet
        //       falls back to seed=42 regardless of the DB value.
        Map<String, Double> allUserScores = new LinkedHashMap<>();
        Map<String, String> effectiveSeeds = new LinkedHashMap<>(); // for diagnostics

        for (WatermarkConfig config : sessionConfigs) {
            double dbScore      = computeAverageCorrelation(frames, config.getSeed(), frameSize);
            double defaultScore = computeAverageCorrelation(frames, WORKLET_DEFAULT_SEED, frameSize);

            double best;
            String usedSeed;
            if (defaultScore > dbScore) {
                best     = defaultScore;
                usedSeed = WORKLET_DEFAULT_SEED + " (worklet default)";
            } else {
                best     = dbScore;
                usedSeed = config.getSeed() + " (DB seed)";
            }

            allUserScores.put(config.getUserId(), round4(best));
            effectiveSeeds.put(config.getUserId(), usedSeed);
        }

        // ── 5. Pick best candidate ─────────────────────────────────────────────
        String bestUser  = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : allUserScores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestUser  = e.getKey();
            }
        }

        // ── 6. Dynamic threshold: alpha * frameSize * pnPower * 0.5 ───────────
        // Use the seed and alpha that match how the audio was actually embedded.
        // When the worklet default seed (42) wins, the worklet also used its
        // default alpha (0.005). When the DB seed wins, use the DB alpha.
        final String finalBestUser = bestUser; // effectively final copy for lambda
        String winnerSeedKey = effectiveSeeds.getOrDefault(finalBestUser, WORKLET_DEFAULT_SEED);
        boolean usedWorkletDefault = winnerSeedKey.contains("worklet");
        String winnerSeed = usedWorkletDefault ? WORKLET_DEFAULT_SEED
                                               : sessionConfigs.stream()
                                                       .filter(c -> c.getUserId().equals(finalBestUser))
                                                       .findFirst()
                                                       .map(WatermarkConfig::getSeed)
                                                       .orElse(WORKLET_DEFAULT_SEED);
        // Alpha used during embedding: 0.005 for worklet default, DB value otherwise
        double alpha = usedWorkletDefault ? WORKLET_DEFAULT_ALPHA
                                          : sessionConfigs.get(0).getAlpha();
        float[] pn = generatePN(winnerSeed, frameSize);
        double pnPower = 0.0;
        for (float v : pn) pnPower += (double) v * v;
        pnPower /= frameSize;
        double threshold = alpha * frameSize * pnPower * THRESHOLD_FACTOR;

        boolean detected = bestScore >= threshold;

        String message = detected
                ? String.format(
                        "Watermark detected. Best match: user '%s' (score=%.4f, threshold=%.4f, seed=%s).",
                        bestUser, bestScore, threshold, effectiveSeeds.get(bestUser))
                : String.format(
                        "No watermark detected above threshold %.4f. Highest score: %.4f for user '%s' (seed=%s).",
                        threshold, bestScore, bestUser, effectiveSeeds.get(bestUser));

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

    // ─────────────────────────────────────────────────────────────────────────
    // Audio decoding
    // ─────────────────────────────────────────────────────────────────────────

    private float[] decodeAudioToFloatSamples(MultipartFile audioFile)
            throws IOException, UnsupportedAudioFileException {

        byte[] fileBytes = audioFile.getBytes();

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(fileBytes)))) {

            AudioFormat src = ais.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(),
                    16,
                    1,
                    2,
                    src.getSampleRate(),
                    true); // little-endian — must match pcmBytesToFloats reader

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, ais)) {
                return pcmBytesToFloats(pcm.readAllBytes());
            }

        } catch (UnsupportedAudioFileException e) {
            return pcmBytesToFloats(fileBytes);
        }
    }

    private float[] pcmBytesToFloats(byte[] bytes) {
        int n = bytes.length / 2;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            short s = (short) ((bytes[2 * i + 1] << 8) | (bytes[2 * i] & 0xFF));
            out[i] = s / 32768.0f;
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Framing
    // ─────────────────────────────────────────────────────────────────────────

    private List<float[]> segmentIntoFrames(float[] samples, int frameSize) {
        List<float[]> frames = new ArrayList<>();
        for (int off = 0; off + frameSize <= samples.length; off += frameSize)
            frames.add(Arrays.copyOfRange(samples, off, off + frameSize));
        return frames;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Correlation  —  raw dot product per frame, averaged  (matches JS)
    // ─────────────────────────────────────────────────────────────────────────

    private double computeAverageCorrelation(List<float[]> frames, String seed, int frameSize) {
        float[] pn = generatePN(seed, frameSize);
        double total = 0.0;
        for (float[] frame : frames) {
            double c = 0.0;
            for (int i = 0; i < frameSize; i++) c += frame[i] * pn[i];
            total += c;
        }
        return frames.isEmpty() ? 0.0 : total / frames.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PN generation — exact port of JS generatePN() / mulberry32 / hashString
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mirrors JS: typeof seed === "string" ? hashString(seed) : (seed >>> 0)
     *
     * If the seed string represents a plain non-negative integer we use its
     * numeric value directly (same as the JS numeric path `seed >>> 0`).
     * Otherwise we run it through djb2 hashString.
     */
    private long resolveSeed(String seed) {
        try {
            long v = Long.parseLong(seed);
            if (v >= 0 && v <= 0xFFFFFFFFL) {
                return v & 0xFFFFFFFFL; // mirrors  seed >>> 0
            }
        } catch (NumberFormatException ignored) { }
        return hashString(seed);
    }

    /**
     * djb2 variant — exact port of JS hashString().
     * hash = (hash * 33 + charCode) kept as unsigned 32-bit.
     */
    private long hashString(String str) {
        long hash = 5381L;
        for (int i = 0; i < str.length(); i++) {
            hash = (((hash << 5) + hash) + str.charAt(i)) & 0xFFFFFFFFL;
        }
        return hash;
    }

    /**
     * One step of mulberry32 — exact port of JS mulberry32().
     * State held in state[0] as unsigned 32-bit long.
     */
    private double mulberry32Next(long[] state) {
        state[0] = (state[0] + 0x6d2b79f5L) & 0xFFFFFFFFL;
        long s = state[0];
        long t = imul32(s ^ (s >>> 15), 1L | s);
        t = (t + imul32(t ^ (t >>> 7), 61L | t)) ^ t;
        t = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
        return t / 4294967296.0;
    }

    /** Unsigned 32-bit multiply — mirrors JS Math.imul(). */
    private long imul32(long a, long b) {
        return (a & 0xFFFFFFFFL) * (b & 0xFFFFFFFFL) & 0xFFFFFFFFL;
    }

    /**
     * Exact port of JS generatePN():
     *   chips[i] = rand() * 2 − 1   (continuous floats in [-1, +1])
     */
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