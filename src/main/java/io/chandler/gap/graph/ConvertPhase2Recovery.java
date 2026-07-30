package io.chandler.gap.graph;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Offline converter: old Phase 2 recovery files (String dreadnaut hashes) → compact
 * {@link Phase2RecoveryState} / {@link CanonicalGraphHashSet} format.
 * <p>
 * Usage:
 * <pre>
 *   java ... io.chandler.gap.graph.ConvertPhase2Recovery path/to/phase2-recovery.ser
 *   java ... io.chandler.gap.graph.ConvertPhase2Recovery in.ser out.ser
 * </pre>
 * When only one path is given, writes {@code <path>.compact.ser} then atomically
 * replaces the original (a {@code .bak} copy is kept).
 * <p>
 * Conversion still needs enough heap to deserialize the old String-backed HashSet
 * once; strings are removed as they are compacted. Run with a large {@code -Xmx}
 * if the original run OOMed, then resume PlanarStudy against the compact file.
 */
public final class ConvertPhase2Recovery {
    private static final String LEGACY_CLASS =
        "io.chandler.gap.graph.PlanarStudy$Phase2RecoveryState";

    private ConvertPhase2Recovery() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: ConvertPhase2Recovery <recovery.ser> [out.ser]");
            System.exit(1);
        }
        File inFile = new File(args[0]);
        if (!inFile.isFile()) {
            throw new IOException("Not a file: " + inFile.getAbsolutePath());
        }

        boolean inplace = args.length == 1;
        File outFile = inplace
            ? new File(inFile.getAbsolutePath() + ".compact.ser")
            : new File(args[1]);

        System.out.println("Reading " + inFile.getAbsolutePath() + " (" + inFile.length() + " bytes)");
        ConversionResult result = convert(inFile, outFile);
        System.out.println("Wrote " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");
        System.out.println("  groupName=" + result.state.groupName
            + " baseFileName=" + result.state.baseFileName
            + " round=" + result.state.round
            + " candidate=" + result.state.candidateIndex
            + " isoCache=" + result.state.canonicalGraphs.size()
            + " currentCandidates=" + result.state.currentCandidates.size()
            + " newCandidates=" + result.state.newCandidates.size()
            + " accepted=" + result.state.acceptedCount);
        System.out.println("  source=" + result.source
            + " hashesConverted=" + result.hashesConverted
            + " ratio=" + String.format("%.2f", (double) outFile.length() / Math.max(1L, inFile.length())));

        if (inplace) {
            File bak = new File(inFile.getAbsolutePath() + ".bak");
            Files.copy(inFile.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(outFile.toPath(), inFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(outFile.toPath(), inFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Replaced original; backup at " + bak.getAbsolutePath());
        }
    }

    static ConversionResult convert(File inFile, File outFile) throws IOException, ClassNotFoundException {
        Phase2RecoveryState state;
        String source;
        int hashesConverted;

        try (ObjectInputStream ois = new LegacyResolvingObjectInputStream(
                new BufferedInputStream(new FileInputStream(inFile)))) {
            Object obj = ois.readObject();
            if (obj instanceof Phase2RecoveryState) {
                state = (Phase2RecoveryState) obj;
                source = "already-compact";
                hashesConverted = state.canonicalGraphs.size();
            } else if (obj instanceof LegacyPhase2RecoveryState) {
                LegacyPhase2RecoveryState legacy = (LegacyPhase2RecoveryState) obj;
                int expected = legacy.canonicalGraphs == null ? 16 : legacy.canonicalGraphs.size();
                CanonicalGraphHashSet hashes = new CanonicalGraphHashSet(expected);
                hashesConverted = absorbLegacyHashes(hashes, legacy.canonicalGraphs);
                state = new Phase2RecoveryState(
                    legacy.groupName,
                    legacy.baseFileName,
                    legacy.round,
                    legacy.candidateIndex,
                    legacy.roundCount,
                    legacy.acceptedCount,
                    hashes,
                    legacy.currentCandidates != null ? legacy.currentCandidates : new ArrayList<>(),
                    legacy.newCandidates != null ? legacy.newCandidates : new ArrayList<>());
                source = "legacy-string-cache";
            } else {
                throw new IOException("Unexpected recovery object type: " +
                    (obj == null ? "null" : obj.getClass().getName()));
            }
        }

        File tmp = new File(outFile.getAbsolutePath() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmp)))) {
            oos.writeObject(state);
            oos.flush();
        }
        try {
            Files.move(tmp.toPath(), outFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return new ConversionResult(state, source, hashesConverted);
    }

    /** Convert and drop legacy entries one-by-one to free String memory sooner. */
    private static int absorbLegacyHashes(CanonicalGraphHashSet dest, HashSet<Object> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return 0;
        }
        int added = 0;
        Iterator<Object> it = legacy.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            CanonicalGraphHash h;
            if (o instanceof CanonicalGraphHash) {
                h = (CanonicalGraphHash) o;
            } else if (o instanceof String) {
                h = CanonicalGraphHash.parse((String) o);
            } else {
                throw new IllegalArgumentException(
                    "Unexpected canonical hash element type: " +
                        (o == null ? "null" : o.getClass().getName()));
            }
            if (dest.add(h)) {
                added++;
            }
            it.remove();
        }
        legacy.clear();
        return added;
    }

    static final class ConversionResult {
        final Phase2RecoveryState state;
        final String source;
        final int hashesConverted;

        ConversionResult(Phase2RecoveryState state, String source, int hashesConverted) {
            this.state = state;
            this.source = source;
            this.hashesConverted = hashesConverted;
        }
    }

    /**
     * Layout matching PlanarStudy's original nested recovery class (uid 1).
     * Field names/types must stay compatible for deserialization.
     */
    @SuppressWarnings("serial")
    static final class LegacyPhase2RecoveryState implements Serializable {
        private static final long serialVersionUID = 1L;

        String groupName;
        String baseFileName;
        int round;
        int candidateIndex;
        int roundCount;
        int acceptedCount;
        HashSet<Object> canonicalGraphs;
        ArrayList<int[][][]> currentCandidates;
        ArrayList<int[][][]> newCandidates;
    }

    private static final class LegacyResolvingObjectInputStream extends ObjectInputStream {
        LegacyResolvingObjectInputStream(java.io.InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            ObjectStreamClass desc = super.readClassDescriptor();
            // Java 17+ rejects resolveClass() to a differently named type; swap the
            // descriptor so the local LegacyPhase2RecoveryState name matches.
            if (LEGACY_CLASS.equals(desc.getName())) {
                return ObjectStreamClass.lookup(LegacyPhase2RecoveryState.class);
            }
            return desc;
        }
    }
}
