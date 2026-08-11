package io.chandler.gap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JNI bridge to {@code libpbin_factorial.so} — limb-based factorial-number-system
 * decode matching {@code pbin/pbin.cpp}.
 */
public final class PbinFactorialNative {

    private static final boolean AVAILABLE;

    static {
        AVAILABLE = tryLoad() && nativeAvailable();
    }

    private PbinFactorialNative() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static int[] factorialDecode(byte[] bigEndianUnsigned, int N, int k) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libpbin_factorial not loaded");
        }
        return nativeFactorialDecode(bigEndianUnsigned, N, k);
    }

    public static void factorialDecodeInto(byte[] bigEndianUnsigned, int N, int k,
                                           int[] out) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libpbin_factorial not loaded");
        }
        nativeFactorialDecodeInto(bigEndianUnsigned, N, k, out);
    }

    public static void factorialDecodeSliceInto(byte[] buf, int offset, int length,
                                                int N, int k, int[] out) {
        if (!AVAILABLE) {
            throw new IllegalStateException("libpbin_factorial not loaded");
        }
        nativeFactorialDecodeSliceInto(buf, offset, length, N, k, out);
    }

    private static boolean tryLoad() {
        try {
            System.loadLibrary("pbin_factorial");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
        }
        Path[] candidates = new Path[] {
                Paths.get("native/pbin_factorial/libpbin_factorial.so"),
                Paths.get("lib/libpbin_factorial.so"),
        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            try {
                System.load(p.toAbsolutePath().toString());
                return true;
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
        return false;
    }

    private static native boolean nativeAvailable();

    private static native int[] nativeFactorialDecode(byte[] bytes, int N, int k);

    private static native void nativeFactorialDecodeInto(byte[] bytes, int N, int k,
                                                         int[] out);

    private static native void nativeFactorialDecodeSliceInto(
            byte[] bytes, int offset, int length, int N, int k, int[] out);
}
