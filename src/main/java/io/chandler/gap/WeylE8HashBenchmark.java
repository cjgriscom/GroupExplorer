package io.chandler.gap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.KeyframeStateCache;
import io.chandler.gap.weyl.WeylE8Quotient.Encoding;

/**
 * Compares Weyl(E8) compress hash variants by BFS depth, against FASTEST mode.
 */
public final class WeylE8HashBenchmark {

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
        return "[(1,4)(2,3)(7,8)(9,15)(10,16)(11,155)(12,156)(13,135)(14,136)(17,195)(18,196)(19,217)(20,218)(21,185)(22,186)(23,183)(24,184)(25,213)(26,214)(27,189)(28,190)(29,77)(30,78)(31,75)(32,76)(33,193)(34,194)(35,221)(36,222)(37,153)(38,154)(39,191)(40,192)(41,73)(42,74)(43,122)(44,121)(45,220)(46,219)(49,215)(50,216)(51,55)(52,56)(53,223)(54,224)(57,162)(58,161)(59,142)(60,141)(61,66)(62,65)(63,236)(64,235)(67,68)(69,233)(70,234)(71,229)(72,230)(79,163)(80,164)(81,165)(82,166)(83,113)(84,114)(85,169)(86,170)(87,125)(88,126)(89,98)(90,97)(91,117)(92,118)(93,111)(94,112)(95,167)(96,168)(99,109)(100,110)(101,197)(102,198)(103,173)(104,174)(105,203)(106,204)(107,108)(115,212)(116,211)(119,228)(120,227)(123,207)(124,208)(127,172)(128,171)(129,178)(130,177)(131,200)(132,199)(133,182)(134,181)(139,143)(140,144)(145,180)(146,179)(147,226)(148,225)(149,202)(150,201)(151,210)(152,209)(157,206)(158,205)(159,176)(160,175)(187,188)(231,239)(232,240),(3,103)(4,104)(5,87)(6,88)(7,167)(8,168)(9,203)(10,204)(11,147)(12,148)(13,149)(14,150)(19,201)(20,202)(23,162)(24,161)(27,199)(28,200)(29,191)(30,192)(31,193)(32,194)(33,177)(34,178)(35,179)(36,180)(49,75)(50,76)(51,94)(52,93)(63,97)(64,98)(65,81)(66,82)(67,95)(68,96)(85,86)(113,239)(114,240)(115,214)(116,213)(121,206)(122,205)(123,218)(124,217)(125,237)(126,238)(127,230)(128,229)(129,216)(130,215)(133,219)(134,220)(135,207)(136,208)(139,163)(140,164),(1,47)(2,48)(3,82)(4,81)(5,94)(6,93)(7,135)(8,136)(9,217)(10,218)(13,27)(14,28)(15,211)(16,212)(17,145)(18,146)(21,131)(22,132)(23,216)(24,215)(25,225)(26,226)(29,163)(30,164)(31,113)(32,114)(33,133)(34,134)(35,205)(36,206)(37,151)(38,152)(39,224)(40,223)(41,137)(42,138)(43,89)(44,90)(45,108)(46,107)(49,64)(50,63)(51,87)(52,88)(53,72)(54,71)(55,91)(56,92)(57,58)(59,74)(60,73)(61,105)(62,106)(65,104)(66,103)(69,77)(70,78)(75,98)(76,97)(79,80)(83,99)(84,100)(101,102)(109,196)(110,195)(111,153)(112,154)(115,238)(116,237)(117,221)(118,222)(119,174)(120,173)(121,180)(122,179)(123,204)(124,203)(125,213)(126,214)(129,162)(130,161)(139,191)(140,192)(141,171)(142,172)(143,183)(144,184)(149,199)(150,200)(155,165)(156,166)(159,181)(160,182)(167,207)(168,208)(169,228)(170,227)(177,219)(178,220)(185,231)(186,232)(187,233)(188,234)(189,197)(190,198)(193,239)(194,240)]";
    }
}
