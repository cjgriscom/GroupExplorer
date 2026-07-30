package io.chandler.gap.graph;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Compact key for a dreadnaut {@code z} canonical-graph certificate.
 * <p>
 * Wire form looks like {@code [T41186f80 12ed979 2a061bec]} or
 * {@code [N465fc7fa 1c46de86 6812455a]}: engine tag {@code T}/{@code N} plus three
 * 32-bit hex words (leading zeros omitted). Stored here as 13 bytes of payload
 * instead of a Java {@link String}.
 */
public final class CanonicalGraphHash implements Serializable, Comparable<CanonicalGraphHash> {
    private static final long serialVersionUID = 1L;

    /** Packed on-disk / stream size: 12 hash bytes. */
    public static final int PACKED_BYTES = 12;

    private final int w0;
    private final int w1;
    private final int w2;

    public CanonicalGraphHash(int w0, int w1, int w2) {
        this.w0 = w0;
        this.w1 = w1;
        this.w2 = w2;
    }

    public int w0() { return w0; }
    public int w1() { return w1; }
    public int w2() { return w2; }

    /** True if {@code s} looks like a dreadnaut {@code z} certificate. */
    public static boolean looksLike(String s) {
        if (s == null || s.length() < 8) return false;
        if (s.charAt(0) != '[') return false;
        char e = s.charAt(1);
        return (Character.isAlphabetic(e)) && s.charAt(s.length() - 1) == ']';
    }

    public static CanonicalGraphHash parse(String dreadnautZ) {
        if (dreadnautZ == null) {
            throw new IllegalArgumentException("canonical hash is null");
        }
        String s = dreadnautZ.trim();
        if (!looksLike(s)) {
            throw new IllegalArgumentException("Not a dreadnaut z hash: " + dreadnautZ);
        }
        String inner = s.substring(1, s.length() - 1).trim();
        String[] parts = inner.split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Expected 3 hex words in dreadnaut z hash, got " + parts.length + ": " + dreadnautZ);
        }
        int w0 = Integer.parseUnsignedInt(parts[0].substring(1), 16);
        int w1 = Integer.parseUnsignedInt(parts[1], 16);
        int w2 = Integer.parseUnsignedInt(parts[2], 16);
        return new CanonicalGraphHash(w0, w1, w2);
    }

    /** Reconstruct the dreadnaut {@code z} string (hex words without leading-zero padding). */
    @Override
    public String toString() {
        return "[?" // Engine intentionally omitted
            + Integer.toHexString(w0) + " "
            + Integer.toHexString(w1) + " "
            + Integer.toHexString(w2) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CanonicalGraphHash)) return false;
        CanonicalGraphHash other = (CanonicalGraphHash) o;
        return w0 == other.w0
            && w1 == other.w1
            && w2 == other.w2;
    }

    @Override
    public int hashCode() {
        int h = w0;
        h = 31 * h + w1;
        h = 31 * h + w2;
        return h;
    }

    @Override
    public int compareTo(CanonicalGraphHash o) {
        int c;
        c = Integer.compareUnsigned(this.w0, o.w0);
        if (c != 0) return c;
        c = Integer.compareUnsigned(this.w1, o.w1);
        if (c != 0) return c;
        return Integer.compareUnsigned(this.w2, o.w2);
    }

    public void writePacked(DataOutput out) throws IOException {
        out.writeInt(w0);
        out.writeInt(w1);
        out.writeInt(w2);
    }

    public static CanonicalGraphHash readPacked(DataInput in) throws IOException {
        return new CanonicalGraphHash(in.readInt(), in.readInt(), in.readInt());
    }
}
