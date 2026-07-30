package io.chandler.gap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.WeylE8StateCache;
import io.chandler.gap.weyl.WeylE8Antipodes;
import io.chandler.gap.weyl.WeylE8Quotient;

class WeylE8StateCacheTest {

    private static String weylE8Generator;

    @BeforeAll
    static void loadGenerator() throws IOException {
        weylE8Generator = "[(1,4)(2,3)(7,8)(9,15)(10,16)(11,155)(12,156)(13,135)(14,136)(17,195)(18,196)(19,217)(20,218)(21,185)(22,186)(23,183)(24,184)(25,213)(26,214)(27,189)(28,190)(29,77)(30,78)(31,75)(32,76)(33,193)(34,194)(35,221)(36,222)(37,153)(38,154)(39,191)(40,192)(41,73)(42,74)(43,122)(44,121)(45,220)(46,219)(49,215)(50,216)(51,55)(52,56)(53,223)(54,224)(57,162)(58,161)(59,142)(60,141)(61,66)(62,65)(63,236)(64,235)(67,68)(69,233)(70,234)(71,229)(72,230)(79,163)(80,164)(81,165)(82,166)(83,113)(84,114)(85,169)(86,170)(87,125)(88,126)(89,98)(90,97)(91,117)(92,118)(93,111)(94,112)(95,167)(96,168)(99,109)(100,110)(101,197)(102,198)(103,173)(104,174)(105,203)(106,204)(107,108)(115,212)(116,211)(119,228)(120,227)(123,207)(124,208)(127,172)(128,171)(129,178)(130,177)(131,200)(132,199)(133,182)(134,181)(139,143)(140,144)(145,180)(146,179)(147,226)(148,225)(149,202)(150,201)(151,210)(152,209)(157,206)(158,205)(159,176)(160,175)(187,188)(231,239)(232,240),(3,103)(4,104)(5,87)(6,88)(7,167)(8,168)(9,203)(10,204)(11,147)(12,148)(13,149)(14,150)(19,201)(20,202)(23,162)(24,161)(27,199)(28,200)(29,191)(30,192)(31,193)(32,194)(33,177)(34,178)(35,179)(36,180)(49,75)(50,76)(51,94)(52,93)(63,97)(64,98)(65,81)(66,82)(67,95)(68,96)(85,86)(113,239)(114,240)(115,214)(116,213)(121,206)(122,205)(123,218)(124,217)(125,237)(126,238)(127,230)(128,229)(129,216)(130,215)(133,219)(134,220)(135,207)(136,208)(139,163)(140,164),(1,47)(2,48)(3,82)(4,81)(5,94)(6,93)(7,135)(8,136)(9,217)(10,218)(13,27)(14,28)(15,211)(16,212)(17,145)(18,146)(21,131)(22,132)(23,216)(24,215)(25,225)(26,226)(29,163)(30,164)(31,113)(32,114)(33,133)(34,134)(35,205)(36,206)(37,151)(38,152)(39,224)(40,223)(41,137)(42,138)(43,89)(44,90)(45,108)(46,107)(49,64)(50,63)(51,87)(52,88)(53,72)(54,71)(55,91)(56,92)(57,58)(59,74)(60,73)(61,105)(62,106)(65,104)(66,103)(69,77)(70,78)(75,98)(76,97)(79,80)(83,99)(84,100)(101,102)(109,196)(110,195)(111,153)(112,154)(115,238)(116,237)(117,221)(118,222)(119,174)(120,173)(121,180)(122,179)(123,204)(124,203)(125,213)(126,214)(129,162)(130,161)(139,191)(140,192)(141,171)(142,172)(143,183)(144,184)(149,199)(150,200)(155,165)(156,166)(159,181)(160,182)(167,207)(168,208)(169,228)(170,227)(177,219)(178,220)(185,231)(186,232)(187,233)(188,234)(189,197)(190,198)(193,239)(194,240)]";
    }

    @Test
    void antipodesAreConsecutivePairs() {
        for (int label = 1; label <= WeylE8Antipodes.NUM_POINTS; label++) {
            assertEquals(label, WeylE8Antipodes.partner(WeylE8Antipodes.partner(label)));
            assertTrue(WeylE8Antipodes.partner(label) == label + 1 || WeylE8Antipodes.partner(label) == label - 1);
        }
    }

    @Test
    void centralInvolutionMapsToQuotientIdentity() {
        int[] perm = new int[WeylE8Antipodes.NUM_POINTS];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = WeylE8Antipodes.partner(i + 1);
        }
        byte[] quotient = new byte[WeylE8Antipodes.NUM_PAIRS];
        WeylE8Quotient.encode(perm, quotient);
        for (int k = 0; k < quotient.length; k++) {
            assertEquals(k + 1, quotient[k] & 0xff);
        }
    }

    @Test
    void weylE8CompressUsesCustomCache() {
        GroupExplorer compress = new GroupExplorer(weylE8Generator, MemorySettings.compressWeylE8(2));
        assertTrue(compress.compressStateCache() instanceof WeylE8StateCache);
        assertEquals(WeylE8Antipodes.NUM_POINTS, compress.compressStateCache().nElements());
    }

    @Test
    void shallowBfsMatchesFastestMode() {
        GroupExplorer fastest = new GroupExplorer(weylE8Generator, MemorySettings.FASTEST);
        GroupExplorer compress = new GroupExplorer(
            weylE8Generator,
            MemorySettings.compressWeylE8(2),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            false);

        fastest.initIterativeExploration();
        compress.initIterativeExploration();

        for (int layer = 0; layer < 3; layer++) {
            fastest.iterateExploration(false, -1, null);
            compress.iterateExploration(false, -1, null);
            assertEquals(
                fastest.visitedStateCount(),
                compress.visitedStateCount(),
                "visited count at layer " + (layer + 1));
        }

        assertTrue(compress.compressStateCache().size() > 1);
    }

    @Test
    void tracePathReplaysState() {
        GroupExplorer compress = new GroupExplorer(
            weylE8Generator,
            MemorySettings.compressWeylE8(2),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            false);
        compress.initIterativeExploration();
        compress.iterateExploration(false, 10_000, null);

        WeylE8StateCache cache = (WeylE8StateCache) compress.compressStateCache();
        int[] identity = new int[WeylE8Antipodes.NUM_POINTS];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i + 1;
        }

        for (int id = 0; id < Math.min(cache.size(), 64); id++) {
            int[] path = cache.tracePath(id);
            int[] replay = identity.clone();
            for (int g : path) {
                replay = GroupExplorer.applyOperation(replay, compress.parsedOperations.get(g));
            }
            assertArrayEquals(cache.reconstruct(id), replay, "state id " + id);
        }
    }
}
