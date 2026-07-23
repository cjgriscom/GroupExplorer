package io.chandler.gap.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JNI bridge to libcongestion_cuda.so (FP32 spring layout + congestion scoring).
 */
public final class CongestionCuda {

    private static boolean loaded;
    private static boolean available;

    static {
        loaded = tryLoad();
        available = loaded && nativeInit();
    }

    private CongestionCuda() {}

    public static boolean isAvailable() {
        return available;
    }

    public static String deviceName() {
        return available ? nativeDeviceName() : "unavailable";
    }

    public static void shutdown() {
        if (available) {
            nativeShutdown();
            available = false;
        }
    }

    /**
     * Batched GPU evaluation. {@code scoresOut} length must be
     * numGraphs * numCheckpoints * numSeeds * numRotations.
     */
    public static int evaluateBatch(
            int numGraphs,
            int numSeeds,
            int nVertices,
            int[] graphEdgeOffsets,
            int[] graphEdgeCounts,
            int[] edgeI,
            int[] edgeJ,
            byte[] adjacency,
            float[] initialPos,
            int[] checkpoints,
            float[] thresholds,
            float[] rotMatrices,
            int numRotations,
            int maxCheckpoint,
            float[] scoresOut) {
        return nativeEvaluateBatch(
                numGraphs, numSeeds, nVertices,
                graphEdgeOffsets, graphEdgeCounts,
                edgeI, edgeJ, adjacency,
                initialPos, checkpoints, thresholds,
                rotMatrices, numRotations, maxCheckpoint,
                scoresOut);
    }

    private static boolean tryLoad() {
        try {
            System.loadLibrary("congestion_cuda");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
        }
        Path[] candidates = new Path[] {
                Paths.get("native/congestion_cuda/libcongestion_cuda.so"),
                Paths.get("lib/libcongestion_cuda.so"),
        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                System.load(p.toAbsolutePath().toString());
                return true;
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
        return false;
    }

    private static native boolean nativeInit();
    private static native void nativeShutdown();
    private static native String nativeDeviceName();
    private static native int nativeEvaluateBatch(
            int numGraphs, int numSeeds, int nVertices,
            int[] graphEdgeOffsets, int[] graphEdgeCounts,
            int[] edgeI, int[] edgeJ, byte[] adjacency,
            float[] initialPos, int[] checkpoints,
            float[] thresholds,
            float[] rotMatrices, int numRotations, int maxCheckpoint,
            float[] scoresOut);
}
