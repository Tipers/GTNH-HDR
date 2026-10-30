package com.gtnhhdr.client;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * GPU time of one pass per frame, from GL timestamp queries in a 4-frame ring: results are read 4 frames later and
 * only when available, so measuring never stalls the CPU. Off (zero GL calls) unless {@link #enabled}.
 */
public final class HdrTimer {

    public static final HdrTimer ENCODE = new HdrTimer();
    public static final HdrTimer FINAL_PASS = new HdrTimer();

    /** Info overlay or test run is on. */
    public static volatile boolean enabled;

    private static final int RING = 4;
    private int[] queries;
    private int slot;
    private boolean open;
    private final boolean[] pending = new boolean[RING];

    private double sumMs;
    private int count;
    private double lastAvgMs = -1;

    private HdrTimer() {}

    public void begin() {
        if (!enabled || open) return;
        if (queries == null) {
            queries = new int[RING * 2];
            for (int i = 0; i < queries.length; i++) queries[i] = GL15.glGenQueries();
        }
        collect(slot);
        GL33.glQueryCounter(queries[slot * 2], GL33.GL_TIMESTAMP);
        open = true;
    }

    public void end() {
        if (!open) return;
        GL33.glQueryCounter(queries[slot * 2 + 1], GL33.GL_TIMESTAMP);
        pending[slot] = true;
        open = false;
        slot = (slot + 1) % RING;
    }

    private void collect(int s) {
        if (!pending[s]) return;
        final int qEnd = queries[s * 2 + 1];
        if (GL15.glGetQueryObjecti(qEnd, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
            pending[s] = false; // too slow to come back; drop rather than wait
            return;
        }
        final long t0 = GL33.glGetQueryObjectui64(queries[s * 2], GL15.GL_QUERY_RESULT);
        final long t1 = GL33.glGetQueryObjectui64(qEnd, GL15.GL_QUERY_RESULT);
        pending[s] = false;
        if (t1 > t0) {
            sumMs += (t1 - t0) / 1e6;
            if (++count >= 120) {
                lastAvgMs = sumMs / count;
                sumMs = 0;
                count = 0;
            }
        }
    }

    /** Average over the last 120 measured frames, or -1 if not measured yet. */
    public double averageMs() {
        return lastAvgMs;
    }

    public void reset() {
        sumMs = 0;
        count = 0;
        lastAvgMs = -1;
    }
}
