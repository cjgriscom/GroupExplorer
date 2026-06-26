package io.chandler.gap;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Random-access reader for PBIN files. Only one compressed block is held in
 * memory at a time; individual generators are decoded on demand.
 */
public final class PbinFile implements Closeable {

    private final RandomAccessFile raf;
    private final long fileLen;
    private final int M;
    private final int N;
    private final int blockSize;
    private final int compression;
    private final boolean bare;
    private final int[] offsets;
    private final int numBlocks;

    private int cachedBlock = -1;
    private String[] cachedLines;

    private PbinFile(RandomAccessFile raf, long fileLen, int M, int N,
                     int blockSize, int compression, boolean bare,
                     int[] offsets) {
        this.raf = raf;
        this.fileLen = fileLen;
        this.M = M;
        this.N = N;
        this.blockSize = blockSize;
        this.compression = compression;
        this.bare = bare;
        this.offsets = offsets;
        this.numBlocks = offsets.length;
    }

    public static PbinFile open(String filePath) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(filePath, "r");
        try {
            long fileLen = raf.length();
            if (fileLen < 7) throw new IOException("Not a PBIN file");

            byte[] header = new byte[7];
            raf.readFully(header);
            if (header[0] != 'P' || header[1] != 'B' || header[2] != 'I' || header[3] != 'N')
                throw new IOException("Not a PBIN file");
            if ((header[4] & 0xFF) != 0x01)
                throw new IOException("Unsupported PBIN version " + (header[4] & 0xFF));

            int flags       = header[5] & 0xFF;
            int compression = header[6] & 0xFF;
            boolean bare = (flags & 0x01) != 0;

            byte[] vbuf = new byte[15];
            raf.readFully(vbuf);
            int[] vp = {0};
            int blockSize = readVarint(vbuf, vp);
            int N         = readVarint(vbuf, vp);
            int M         = readVarint(vbuf, vp);

            long dirStart = 7 + vp[0];
            int numBlocks = (M + blockSize - 1) / blockSize;

            raf.seek(dirStart);
            byte[] dirBuf = new byte[numBlocks * 4];
            raf.readFully(dirBuf);
            int[] offsets = new int[numBlocks];
            for (int b = 0; b < numBlocks; b++)
                offsets[b] = readU32LE(dirBuf, b * 4);

            return new PbinFile(raf, fileLen, M, N, blockSize, compression, bare, offsets);
        } catch (IOException | RuntimeException e) {
            raf.close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    public int size() { return M; }

    public int getN() { return N; }

    public int getBlockSize() { return blockSize; }

    public String get(int index) throws IOException {
        if (index < 0 || index >= M)
            throw new IndexOutOfBoundsException("index " + index + ", size " + M);
        int b = index / blockSize;
        int off = index % blockSize;
        if (b != cachedBlock)
            loadBlock(b);
        return cachedLines[off];
    }

    private void loadBlock(int b) throws IOException {
        int bStart = offsets[b];
        int bEnd   = (b + 1 < numBlocks) ? offsets[b + 1] : (int) fileLen;

        raf.seek(bStart);
        byte[] blockData = new byte[bEnd - bStart];
        raf.readFully(blockData);

        byte[] payload = (compression == 1)
            ? zlibDecompress(blockData) : blockData;

        int[] bp = {0};
        int count = readVarint(payload, bp);
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++)
            sizes[i] = readVarint(payload, bp);

        cachedLines = new String[count];
        for (int i = 0; i < count; i++) {
            cachedLines[i] = decodeGenerator(payload, bp[0], sizes[i], N, bare);
            bp[0] += sizes[i];
        }
        cachedBlock = b;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    // ── varint ──────────────────────────────────────────────

    private static int readVarint(byte[] data, int[] pos) {
        int val = 0, shift = 0;
        while (true) {
            if (pos[0] >= data.length)
                throw new RuntimeException("Truncated varint");
            int b = data[pos[0]++] & 0xFF;
            val |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return val;
    }

    private static int readU32LE(byte[] data, int pos) {
        return (data[pos] & 0xFF)
             | ((data[pos + 1] & 0xFF) << 8)
             | ((data[pos + 2] & 0xFF) << 16)
             | ((data[pos + 3] & 0xFF) << 24);
    }

    // ── zlib ────────────────────────────────────────────────

    private static byte[] zlibDecompress(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] buf = new byte[8192];
        int cap = Math.max(compressed.length * 4, 8192);
        byte[] out = new byte[cap];
        int len = 0;
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0 && inflater.needsInput()) break;
                if (len + count > out.length) {
                    byte[] grown = new byte[out.length * 2];
                    System.arraycopy(out, 0, grown, 0, len);
                    out = grown;
                }
                System.arraycopy(buf, 0, out, len, count);
                len += count;
            }
        } catch (DataFormatException e) {
            throw new IOException("zlib decompression failed", e);
        } finally {
            inflater.end();
        }
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }

    // ── Fenwick tree ────────────────────────────────────────

    private static final class FenwickTree {
        private final int[] t;
        private final int n;
        private final int logn;

        FenwickTree(int n) {
            this.n = n;
            t = new int[n + 1];
            for (int i = 1; i <= n; i++) {
                t[i] += 1;
                int j = i + (i & -i);
                if (j <= n) t[j] += t[i];
            }
            int lg = 0;
            while ((1 << (lg + 1)) <= n) lg++;
            logn = lg;
        }

        int kth(int k) {
            int pos = 0, rem = k + 1;
            for (int i = logn; i >= 0; i--) {
                int nxt = pos + (1 << i);
                if (nxt <= n && t[nxt] < rem) {
                    rem -= t[nxt];
                    pos = nxt;
                }
            }
            return pos + 1;
        }

        void remove(int x) {
            for (int i = x; i <= n; i += i & -i) t[i] -= 1;
        }
    }

    private static int[] factorialDecode(BigInteger acc, int N, int k) {
        int[] indices = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            int remaining = N - i;
            BigInteger[] qr = acc.divideAndRemainder(BigInteger.valueOf(remaining));
            acc = qr[0];
            indices[i] = qr[1].intValue();
        }
        FenwickTree ft = new FenwickTree(N);
        int[] points = new int[k];
        for (int i = 0; i < k; i++) {
            points[i] = ft.kth(indices[i]);
            ft.remove(points[i]);
        }
        return points;
    }

    private static String decodeGenerator(byte[] raw, int start, int len,
                                          int N, boolean bare) {
        int end = start + len;
        int[] pos = {start};
        int ncs = readVarint(raw, pos);

        StringBuilder sb = new StringBuilder();
        boolean useBare = bare && ncs == 1;
        if (!useBare) sb.append('[');

        for (int s = 0; s < ncs; s++) {
            if (s > 0) sb.append(',');
            int ncyc = readVarint(raw, pos);
            int[] sizes = new int[ncyc];
            int total = 0;
            for (int j = 0; j < ncyc; j++) {
                sizes[j] = readVarint(raw, pos);
                total += sizes[j];
            }
            int biLen = readVarint(raw, pos);
            int[] points;
            if (total > 0 && biLen > 0) {
                if (pos[0] + biLen > end)
                    throw new RuntimeException("Truncated bigint in generator");
                byte[] biBytes = new byte[biLen];
                System.arraycopy(raw, pos[0], biBytes, 0, biLen);
                pos[0] += biLen;
                BigInteger packed = new BigInteger(1, biBytes);
                points = factorialDecode(packed, N, total);
            } else {
                pos[0] += biLen;
                points = new int[0];
            }

            int off = 0;
            for (int j = 0; j < ncyc; j++) {
                sb.append('(');
                for (int k = 0; k < sizes[j]; k++) {
                    if (k > 0) sb.append(',');
                    sb.append(points[off + k]);
                }
                sb.append(')');
                off += sizes[j];
            }
        }
        if (!useBare) sb.append(']');
        return sb.toString();
    }
}
