package com.fongmi.android.tv.player.util;

import java.util.ArrayList;
import java.util.List;

public class AudioFingerprinter {

    private static final int WINDOW_SIZE = 1024;
    private static final int BANDS = 32;

    public static String generateFingerprint(byte[] pcmData) {
        if (pcmData == null || pcmData.length < WINDOW_SIZE * 2) return "";

        // 1. Convert byte[] (16-bit PCM) to float[]
        float[] samples = new float[pcmData.length / 2];
        for (int i = 0; i < samples.length; i++) {
            int low = pcmData[i * 2] & 0xff;
            int high = pcmData[i * 2 + 1];
            samples[i] = (short) ((high << 8) | low) / 32768f;
        }

        // 2. Windowing and FFT
        List<long[]> frameHashes = new ArrayList<>();
        int step = WINDOW_SIZE / 2; // 50% overlap
        for (int i = 0; i + WINDOW_SIZE <= samples.length; i += step) {
            float[] window = new float[WINDOW_SIZE];
            System.arraycopy(samples, i, window, 0, WINDOW_SIZE);
            applyHammingWindow(window);
            float[] magnitudes = computeFFTMagnitudes(window);
            frameHashes.add(generateFrameHash(magnitudes));
        }

        // 3. Aggregate hashes into a compact string
        return aggregate(frameHashes);
    }

    private static void applyHammingWindow(float[] window) {
        for (int n = 0; n < window.length; n++) {
            window[n] *= (0.54 - 0.46 * Math.cos(2 * Math.PI * n / (window.length - 1)));
        }
    }

    private static float[] computeFFTMagnitudes(float[] window) {
        int n = window.length;
        float[] real = new float[n];
        float[] imag = new float[n];
        System.arraycopy(window, 0, real, 0, n);

        // Simple Radix-2 FFT
        fft(real, imag);

        float[] magnitudes = new float[n / 2];
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return magnitudes;
    }

    private static void fft(float[] re, float[] im) {
        int n = re.length;
        if (n <= 1) return;

        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float tempRe = re[i]; re[i] = re[j]; re[j] = tempRe;
                float tempIm = im[i]; im[i] = im[j]; im[j] = tempIm;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len;
            float wlenRe = (float) Math.cos(ang);
            float wlenIm = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float wRe = 1;
                float wIm = 0;
                for (int j = 0; j < len / 2; j++) {
                    float uRe = re[i + j];
                    float uIm = im[i + j];
                    float vRe = re[i + j + len / 2] * wRe - im[i + j + len / 2] * wIm;
                    float vIm = re[i + j + len / 2] * wIm + im[i + j + len / 2] * wRe;
                    re[i + j] = uRe + vRe;
                    im[i + j] = uIm + vIm;
                    re[i + j + len / 2] = uRe - vRe;
                    im[i + j + len / 2] = uIm - vIm;
                    float tmpRe = wRe * wlenRe - wIm * wlenIm;
                    wIm = wRe * wlenIm + wIm * wlenRe;
                    wRe = tmpRe;
                }
            }
        }
    }

    private static long[] generateFrameHash(float[] magnitudes) {
        // Divide magnitudes into BANDS
        float[] bandEnergies = new float[BANDS];
        int binsPerBand = magnitudes.length / BANDS;
        for (int i = 0; i < BANDS; i++) {
            float sum = 0;
            for (int j = 0; j < binsPerBand; j++) {
                sum += magnitudes[i * binsPerBand + j];
            }
            bandEnergies[i] = sum;
        }

        // Simple bit generation based on energy gradient
        long hash = 0;
        for (int i = 0; i < BANDS - 1; i++) {
            if (bandEnergies[i] > bandEnergies[i + 1]) {
                hash |= (1L << i);
            }
        }
        return new long[]{hash};
    }

    private static String aggregate(List<long[]> frameHashes) {
        StringBuilder sb = new StringBuilder();
        for (long[] h : frameHashes) {
            sb.append(Long.toHexString(h[0])).append(",");
        }
        return sb.toString();
    }

    public static float compare(String fp1, String fp2) {
        if (fp1 == null || fp2 == null || fp1.isEmpty() || fp2.isEmpty()) return 0;
        String[] h1 = fp1.split(",");
        String[] h2 = fp2.split(",");

        int minLen = Math.min(h1.length, h2.length);
        if (minLen == 0) return 0;

        int totalBits = minLen * (BANDS - 1);
        int matchingBits = 0;

        for (int i = 0; i < minLen; i++) {
            try {
                long v1 = Long.parseLong(h1[i], 16);
                long v2 = Long.parseLong(h2[i], 16);
                matchingBits += (BANDS - 1) - Long.bitCount(v1 ^ v2);
            } catch (Exception ignored) {}
        }

        return (float) matchingBits / totalBits;
    }
}
