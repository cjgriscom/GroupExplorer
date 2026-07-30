package io.chandler.gap.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConvertPhase2RecoveryTest {

    @TempDir
    File tmp;

    @Test
    void convertsLegacyStringCacheToCompact() throws Exception {
        File in = new File(tmp, "phase2-recovery.ser");
        File out = new File(tmp, "phase2-recovery-compact.ser");

        ConvertPhase2Recovery.LegacyPhase2RecoveryState legacy =
            new ConvertPhase2Recovery.LegacyPhase2RecoveryState();
        legacy.groupName = "testGroup";
        legacy.baseFileName = "np-2-cycles-2-cycles-2-cycles";
        legacy.round = 1;
        legacy.candidateIndex = 42;
        legacy.roundCount = 7;
        legacy.acceptedCount = 3;
        legacy.canonicalGraphs = new HashSet<>();
        legacy.canonicalGraphs.add("[T41186f80 12ed979 2a061bec]");
        legacy.canonicalGraphs.add("[N465fc7fa 1c46de86 6812455a]");
        legacy.currentCandidates = new ArrayList<>();
        legacy.currentCandidates.add(new int[][][] {{{1, 2}}, {{3, 4}}});
        legacy.newCandidates = new ArrayList<>();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(in))) {
            oos.writeObject(legacy);
        }

        ConvertPhase2Recovery.ConversionResult result = ConvertPhase2Recovery.convert(in, out);
        assertEquals("legacy-string-cache", result.source);
        assertEquals(2, result.hashesConverted);
        assertEquals("testGroup", result.state.groupName);
        assertEquals(42, result.state.candidateIndex);
        assertEquals(2, result.state.canonicalGraphs.size());
        assertTrue(result.state.canonicalGraphs.contains("[T41186f80 12ed979 2a061bec]"));
        assertTrue(out.length() > 0);
        assertTrue(out.length() < in.length() || in.length() < 500); // tiny fixtures may not shrink much

        // Re-run on compact output is idempotent.
        File out2 = new File(tmp, "again.ser");
        ConvertPhase2Recovery.ConversionResult again = ConvertPhase2Recovery.convert(out, out2);
        assertEquals("already-compact", again.source);
        assertEquals(2, again.state.canonicalGraphs.size());
    }

    @Test
    void compactStateRoundTripsThroughConverter() throws Exception {
        File in = new File(tmp, "compact-in.ser");
        File out = new File(tmp, "compact-out.ser");

        CanonicalGraphHashSet hashes = new CanonicalGraphHashSet();
        hashes.add("[T41186f80 12ed979 2a061bec]");
        Phase2RecoveryState state = new Phase2RecoveryState(
            "g", "base", 2, 9, 1, 0, hashes, new ArrayList<>(), new ArrayList<>());
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(in))) {
            oos.writeObject(state);
        }
        long before = Files.size(in.toPath());
        ConvertPhase2Recovery.ConversionResult result = ConvertPhase2Recovery.convert(in, out);
        assertEquals("already-compact", result.source);
        assertEquals(before, Files.size(out.toPath()));
    }
}
