package io.chandler.gap;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.SamplePuzzleDepthDistribution.PuzzleDef;
import io.chandler.gap.cache.KeyframeStateCache;
import io.chandler.gap.weyl.WeylE8Quotient.Encoding;

/**
 * Compares Weyl(E8) compress hash variants by BFS depth, against FASTEST mode.
 */
public final class WeylE8HashBenchmark {

    private static final Path PUZZLES =
        Paths.get("/home/cjgriscom/Programming/GridExplorer/src/samplePuzzles.ts");

    static final class Variant {
        final String label;
        final MemorySettings mem;

        Variant(String label, MemorySettings mem) {
            this.label = label;
            this.mem = mem;
        }
    }

    static final class Result {
        final String label;
        final int total;
        final int delta;
        final int prefixLen;
        final int numLongs;

        Result(String label, int total, int delta, int prefixLen, int numLongs) {
            this.label = label;
            this.total = total;
            this.delta = delta;
            this.prefixLen = prefixLen;
            this.numLongs = numLongs;
        }
    }

    public static void main(String[] args) throws IOException {
        int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        KeyframeStateCache.KEYFRAMES_ENABLED = false;

        String generator = loadWeylE8Generator();
        int reference = bfsToDepth(generator, MemorySettings.FASTEST, maxDepth);

        List<Variant> variants = List.of(
            new Variant("baseline: pair-only, 2L (current)",
                MemorySettings.compressWeylE8(2, Encoding.PAIR_ONLY)),
            new Variant("O1: primary image (pair+parity), 2L",
                MemorySettings.compressWeylE8(2, Encoding.PRIMARY_IMAGE)),
            new Variant("O1: primary image, 3L",
                MemorySettings.compressWeylE8(3, Encoding.PRIMARY_IMAGE)),
            new Variant("O2: pair-only, 3L",
                MemorySettings.compressWeylE8(3, Encoding.PAIR_ONLY)),
            new Variant("O2: pair-only, 4L",
                MemorySettings.compressWeylE8(4, Encoding.PAIR_ONLY)),
            new Variant("O3: full 240-perm, 2L (old generic)",
                MemorySettings.compress(2)),
            new Variant("O3: full 240-perm, 3L",
                MemorySettings.compress(3)),
            new Variant("O3: full 240-perm, 4L",
                MemorySettings.compress(4))
        );

        List<Result> results = new ArrayList<>();
        System.out.printf("Weyl(E8) hash benchmark to depth %d%n", maxDepth);
        System.out.printf("FASTEST reference: %d states%n%n", reference);
        System.out.printf("%-42s %10s %10s %6s %5s%n", "Variant", "Total", "Delta", "Pref", "Longs");
        System.out.println("------------------------------------------------------------------------------");

        for (Variant variant : variants) {
            GroupExplorer gap = newGroupExplorer(generator, variant.mem);
            gap.initIterativeExploration();
            for (int d = 0; d < maxDepth; d++) {
                int ret = gap.iterateExploration(false, -1, null);
                if (ret >= 0) {
                    break;
                }
            }
            int total = gap.visitedStateCount();
            int prefixLen = gap.compressStateCache() != null
                ? gap.compressStateCache().prefixLen()
                : GroupExplorer.compressPrefixLength(240, variant.mem.compressBits);
            int numLongs = variant.mem.compressLongs();
            results.add(new Result(variant.label, total, reference - total, prefixLen, numLongs));
            System.out.printf("%-42s %10d %10d %6d %5d%n",
                variant.label, total, reference - total, prefixLen, numLongs);
        }

        results.sort(Comparator.comparingInt(r -> r.delta));
        System.out.println();
        System.out.println("Best (smallest delta from FASTEST):");
        Result best = results.get(0);
        System.out.printf("  %s -> total=%d, missing=%d%n", best.label, best.total, best.delta);

        Result baseline = null;
        for (Result r : results) {
            if (r.label.startsWith("baseline")) {
                baseline = r;
                break;
            }
        }
        if (baseline != null && best.delta < baseline.delta) {
            int recovered = baseline.total - best.total;
            System.out.printf("  Recovered %d states vs current baseline at depth %d%n",
                -recovered, maxDepth);
        }
    }

    private static GroupExplorer newGroupExplorer(String generator, MemorySettings mem) {
        if (mem.isCompress()) {
            return new GroupExplorer(
                generator,
                mem,
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(),
                false);
        }
        return new GroupExplorer(generator, mem);
    }

    private static int bfsToDepth(String generator, MemorySettings mem, int maxDepth) {
        GroupExplorer gap = newGroupExplorer(generator, mem);
        gap.initIterativeExploration();
        for (int d = 0; d < maxDepth; d++) {
            int ret = gap.iterateExploration(false, -1, null);
            if (ret >= 0) {
                break;
            }
        }
        return gap.visitedStateCount();
    }

    private static String loadWeylE8Generator() throws IOException {
        List<PuzzleDef> puzzles = SamplePuzzleDepthDistribution.parseSamplePuzzles(PUZZLES);
        for (PuzzleDef p : puzzles) {
            if ("weyl_e8".equals(p.id)) {
                return p.generator;
            }
        }
        throw new IllegalStateException("weyl_e8 not found");
    }
}
