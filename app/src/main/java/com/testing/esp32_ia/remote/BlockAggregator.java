package com.testing.esp32_ia.remote;

import android.util.Log;

import com.testing.esp32_ia.StructuredBlock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class BlockAggregator {
    private final String TAG = "BlockAggregator";
    private static final int WINDOW_SIZE = 5;
    private static final int MIN_REPEATS = 3;

    private final Deque<StructuredBlock> window = new ArrayDeque<>();
    private final Map<String, Integer> seenCounts = new HashMap<>();

    private String lastSentFingerprint = null;

    public StructuredBlock process(StructuredBlock block) {

        // agregar a ventana
        window.addLast(block);
        if (window.size() > WINDOW_SIZE) {
            window.removeFirst();
        }

        // contar ocurrencias
        String fp = fingerprint(block);
        seenCounts.put(fp, seenCounts.getOrDefault(fp, 0) + 1);

        // verificar estabilidad
        int count = seenCounts.get(fp);
        Log.d(TAG, "FP=" + fingerprint(block) + " COUNT=" + count);
        if (count >= MIN_REPEATS) {

            StructuredBlock best = getBestOfFingerprint(fp);

            // evitar duplicados consecutivos
            if (!fp.equals(lastSentFingerprint)) {
                lastSentFingerprint = fp;
                resetCountsExcept(fp);
                return best; // ← ENVIAR
            }
        }

        return null; // no enviar aún
    }

    private StructuredBlock getBestOfFingerprint(String fp) {

        StructuredBlock best = null;
        int maxScore = -1;

        for (StructuredBlock b : window) {
            if (fingerprint(b).equals(fp)) {

                int s = score(b);

                if (s > maxScore) {
                    maxScore = s;
                    best = b;
                }
            }
        }
        return best;
    }

    private void resetCountsExcept(String keepFp) {
        seenCounts.clear();
        seenCounts.put(keepFp, 1);
    }
    private int score(StructuredBlock b) {
        int s = 0;

        if (b.question != null) s += b.question.length();
        s += b.optionsMap.size() * 20;

        // penalizar ruido
        if (b.raw != null && b.raw.length() > 0) {
            int noise = b.raw.length() - (b.cleaned != null ? b.cleaned.length() : 0);
            s -= noise / 5;
        }

        return s;
    }
    private String fingerprint(StructuredBlock b) {
        if (b.question == null) return "";

        String q = b.question
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();

        return q;
    }
}