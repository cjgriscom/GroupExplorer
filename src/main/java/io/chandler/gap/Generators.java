package io.chandler.gap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.State;

public class Generators {
    // https://www.sciencedirect.com/science/article/pii/0012365X9390222F?ref=cra_js_challenge&fr=RR-1
    public static final String m12 = "[(1,2,3)(4,5,6)(7,8,9)(10,11,12),(3,4)(6,7)(9,10)(11,12)]";
    public static final String m11 = "[(1,2,3,4,5,6,7,8,9,10,11),(3,7,11,8)(4,10,5,6)]";

    public static final String m11_12pt = "[(1,6)(2,9)(5,7)(8,10),(1,6,7,4)(2,8)(3,9)(5,11,12,10)]";
    
    public static final String q8 = "[(1,2,4,7)(3,6,8,5),(1,3,4,8)(2,5,7,6)]";
    
    public static final String l2_8 = "[(1,3,2)(4,5,6)(7,8,9),(1,2)(3,4)(6,7)(8,9)]";
    public static final String ree3 = "[(1,2,3)(5,6,7),(2,4,5)(6,8,9)]";
    public static final String a9 = "[(1,2,3)(4,5,6),(2,4,3)(7,8,5),(2,7,5)(6,9,4)]";

    public static final String a5 = "[(1,2)(3,4),(1,3,5)]"; // PSL(2,4)
    public static final String a5_6pt = "[(2,4)(5,6),(1,2,3)(4,5,6)]"; // PSL(2,5)
    public static final String a5_10pt = "[(2,4)(3,5)(6,8)(7,10),(1,2,3)(4,6,7)(5,8,9)]";

    public static final String m10_a6 = "[(2,3)(4,6)(5,7)(8,9),(1,2)(3,4,7,9,10,8,6,5)]";

    public static final String a8_15pts = "[(1,2,4)(3,6,9)(5,8,12)(7,10,14)(11,13,15),(1,3,7,11,8,13,15)(2,5,6,10,9,14,4)]";

    // M12:2 - 24 point representation
    public static final String m12_2 = "[(1,4)(2,17)(3,15)(5,18)(6,19)(7,12)(8,10)(9,21)(11,13)(14,16)(20,23)(22,24),(2,18,23)(3,19,14)(4,11,21)(5,10,16)(7,22,13)(17,24,20)]";

    public static final String _2_m12 = "[(1,2)(3,5)(7,10)(9,11)(13,16)(14,18)(19,21)(23,24),(1,3,6,9,13,17)(2,4,7,11,15,19)(5,8,12,16,20,22)(10,14,18,21,23,24)]";

    // https://web.archive.org/web/20021231011543/http://www.mat.bham.ac.uk/atlas/html/M20.html

    // M20 = 2^4:A5 as permutations on 16 points - primitive (2-transitive)
    public static final String m20_16pt = "[(1,2,4,3)(5,10,15,9)(6,11,16,12)(7,8,14,13),(2,5,6)(3,7,8)(4,9,10)(11,14,16)(12,15,13)]";

    // M20 = 2^4:A5 as permutations on 20 points - the natural representation
    // [(1,2,4,3)(5,11,7,12)(6,13)(8,14)(9,15,10,16)(17,19,20,18),(2,5,6)(3,7,8)(4,9,10)(11,17,12)(13,16,18)(14,15,19)]
    
    // M20 = 2a.M20 = 2^(1+4):A5 on 12 points
    public static final String m20_12pt = "[(4,7,6,8)(9,12,11,10),(1,2,4)(3,5,6)(7,9,10)(8,11,12)]";
    public static final String m20b_12pt = "[(1,2)(3,5)(4,7,6,8)(9,12,11,10),(1,3,4)(2,5,6)(7,9,10)(8,11,12)]";

    public static final String m20_24pt = "[(1,2)(3,5)(4,7)(6,8)(9,13,11,14)(10,15,12,16)(17,19)(18,20)(21,22)(23,24),(1,2,4)(3,5,6)(7,9,10)(8,11,12)(13,16,17)(14,15,18)(19,21,22)(20,23,24)]";

    // Mildly interesting - upper vertices on pentultimate with edge turn
    public static final String unk_upper_pentultimate_edge = "[(1,2,3,4,5)(6,7,8,9,10),(4,11)(5,3)(1,2)(6,7)]";

    // Alternating pentultimate
    public static final String alternating_pentultimate = "[(1,2,3,4,5)(6,7,8,9,10),(1,11,4,10,9)(2,8,12,6,3)]";


    // A6 10pt / Eliac: [ (1,2,3,4)(5,6,7,8), (1,5)(2,9)(4,10) ]
    public static final String eliac = "[(1,2,3,4)(5,6,7,8),(1,5)(2,9)(4,10)]";

    //https://brauer.maths.qmul.ac.uk/Atlas/misc/24A8/
    public static final String _24a8 = "[(1,3,17)(2,4,18)(5,9,26)(6,10,25)(7,11,28)(8,12,27)(13,23,16)(14,24,15)(19,22,30)(20,21,29),(3,5,7,9,11,13,15)(4,6,8,10,12,14,16)(17,19,21,23,25,27,29)(18,20,22,24,26,28,30)]";

    public static final String icosian_24pt = "[(1,2,5,4)(3,6,8,7)(9,13,11,14)(10,15,12,16)(17,19,18,20)(21,24,23,22),(1,3,2)(4,5,8)(6,9,10)(7,11,12)(13,16,17)(14,15,18)(19,21,22)(20,23,24)]";

    // This is order 660, PSL(2,11) ?
    public static final String l2_11 = "[(1,2,3,4,5,6,7,8,9,10,11),(12,1)(2,11)(3,6)(4,8)(5,9)(7,10)]";
    public static final String l2_13 = "[(1,12)(2,6)(3,4)(7,11)(9,10)(13,14),(1,6,11)(2,4,5)(7,8,10)(12,14,13)]";
    public static final String l2_23 = "[(1,22)(2,11)(3,15)(4,17)(5,9)(6,19)(7,13)(8,20)(10,16)(12,21)(14,18)(23,24),(1,11,21)(2,15,10)(3,17,14)(4,9,16)(5,19,8)(6,13,18)(7,20,12)(22,24,23)]";
    public static final String l2_59 = "[(1,30)(2,7)(3,53)(4,38)(5,15)(6,47)(8,11)(9,18)(10,40)(12,52)(13,50)(14,46)(16,20)(17,21)(19,28)(22,32)(23,49)(24,45)(25,43)(26,29)(27,55)(31,48)(33,57)(34,42)(35,36)(37,44)(39,41)(51,58)(54,56)(59,60),(1,33,20)(2,25,12)(3,7,46)(4,23,21)(5,49,34)(6,26,32)(8,36,18)(9,37,27)(10,17,30)(11,54,40)(13,19,39)(14,50,47)(15,28,35)(16,58,52)(22,41,24)(29,51,45)(31,56,53)(38,42,57)(43,44,55)(48,59,60)]";
    
    public static final String l2_11_11pt = "[(1,2,3,4,5,6,7,8,9,10,11),(2,4)(3,9)(5,10)(7,11)]";

    public static final String l3_4_m21 = "[(1,2)(4,6)(5,7)(8,12)(9,14)(10,15)(11,17)(13,19),(2,3,5,4)(6,8,13,9)(7,10,16,11)(12,18)(14,20,21,15)(17,19)]";
    public static final String l3_7 = "[(1,14)(2,51)(3,47)(4,53)(5,44)(6,19)(7,10)(8,16)(11,45)(12,28)(13,34)(17,27)(18,37)(20,29)(22,41)(23,32)(24,38)(26,35)(30,42)(31,55)(33,56)(36,52)(43,57)(48,54),(1,4,33)(2,54,35)(3,25,23)(5,28,31)(6,52,45)(7,22,46)(8,15,43)(9,47,48)(10,51,12)(11,30,41)(13,32,16)(14,57,27)(17,19,50)(18,29,40)(21,44,49)(24,37,26)(34,55,39)(38,53,56)]";

    public static final String l5_2 = "[(4,5)(6,8)(7,9)(16,19)(18,21)(23,25)(26,27)(30,31),(1,2,3,4,6)(5,7,10,13,16)(8,11,14,17,20)(9,12,15,18,22)(19,23,26,28,30)(21,24,27,29,25)]";

    public static final String sl2_7 = "[(1,2,4,3)(5,9,7,10)(6,11,8,12)(13,16,15,14),(2,5,6)(3,7,8)(9,13,14)(10,15,16)]";

    // PSL(3,2) / PSL(2,7)
    public static final String l2_7 = "[(1,2,3)(4,5,6),(7,1,3)(5,8,6),(1,2,8)(7,4,5)]";  
    public static final String psl_3_2 = "[(4,6,2)(5,3,1),(3,6,5)(4,2,7)]";  
    
    // Non-split extension 2^3.L3(2)
    public static final String psl_3_2_nonsplit_ext = "[(2,4)(3,5)(6,10)(7,11)(9,12)(13,14),(1,2,3)(4,6,7)(5,8,9)(10,13,12)]";

    public static final String psl_4_2_a8_nonsplit_ext = "[(1,3,17)(2,4,18)(5,9,26)(6,10,25)(7,11,28)(8,12,27)(13,23,16)(14,24,15)(19,22,30)(20,21,29),(3,5,7,9,11,13,15)(4,6,8,10,12,14,16)(17,19,21,23,25,27,29)(18,20,22,24,26,28,30)]";

    // PSL(3,3)
    // This was generated on PI as (V6L,V9L,V31R,V32R),(V5R,V8R,V20R)
    public static final String l_3_3 = "[(1,2,3)(4,5,6)(7,8,9)(10,11,12),(13,7,3)(2,10,6)(8,11,9)]";

    public static final String u3_3 = "[(2,3)(4,6)(5,8)(7,11)(9,13)(10,15)(12,14)(16,20)(17,22)(18,23)(24,27)(25,28),(1,2,4,7,12,17)(3,5,9,14,19,22)(6,10,13,18,24,23)(8,11,16,21,26,28)(20,25,27)]";

    //https://web.archive.org/web/20030301122101/http://www.mat.bham.ac.uk/atlas/html/WF4.html
    public static final String wf4 = "[(2,3)(4,6)(5,7)(8,10)(9,12)(11,13)(14,17)(15,19)(16,21)(18,20)(22,23),(1,2)(3,4,5)(6,8,11,10,7,9)(12,14,18,21,20,15)(13,16)(17,19,22)(23,24)]";
    
    public static final String sz8 = "[(1,2)(3,4)(5,7)(6,9)(8,12)(10,13)(11,15)(14,19)(16,21)(17,23)(18,25)(20,28)(22,31)(24,33)(26,35)(27,32)(29,37)(30,39)(34,43)(36,46)(38,48)(41,51)(42,44)(45,55)(47,50)(49,58)(52,60)(53,61)(54,59)(56,62)(57,63)(64,65)," + 
                                      "(1,3,5,8)(4,6,10,14)(7,11,16,22)(9,12,17,24)(13,18,26,36)(15,20,29,38)(19,27,31,28)(21,30,40,50)(23,32,41,52)(25,34,44,54)(33,42,53,43)(35,45,56,63)(37,47,51,46)(39,49,59,60)(48,57,55,58)(61,64,62,65)]";
    public static final String j1 = "[(1,262)(2,107)(3,21)(4,213)(5,191)(6,22)(7,133)(8,234)(9,232)(10,151)(11,139)(12,176)(13,202)(14,253)(15,222)(17,195)(18,206)(19,68)(20,55)(23,179)(24,217)(25,216)(26,256)(27,87)(28,70)(29,131)(30,44)(31,105)(32,170)(33,77)(34,104)(35,198)(36,137)(37,243)(38,56)(39,124)(40,223)(41,134)(43,174)(46,51)(47,128)(48,94)(49,250)(50,264)(52,183)(53,231)(54,115)(57,85)(58,233)(59,261)(60,95)(61,235)(62,177)(63,249)(64,91)(65,247)(66,155)(69,219)(71,237)(72,211)(73,84)(74,192)(75,130)(76,251)(79,260)(80,112)(81,193)(82,156)(83,242)(86,238)(88,143)(89,168)(90,148)(92,119)(93,212)(96,150)(97,199)(98,140)(99,189)(100,180)(101,147)(102,111)(103,159)(106,162)(108,194)(109,166)(110,200)(113,120)(114,141)(116,182)(117,181)(118,225)(121,254)(122,125)(123,146)(126,208)(127,221)(129,210)(132,255)(136,175)(138,207)(142,240)(144,172)(145,185)(149,224)(152,169)(153,241)(154,190)(157,214)(158,161)(160,236)(163,239)(164,229)(165,230)(167,188)(171,258)(173,186)(178,245)(184,205)(187,228)(197,203)(201,252)(209,248)(215,259)(218,246)(220,227)(257,263)(265,266),(1,146,21)(2,132,82)(4,156,166)(5,242,253)(6,107,28)(7,125,76)(8,245,130)(9,174,42)(10,241,244)(11,264,63)(12,248,234)(13,36,44)(14,116,128)(15,47,25)(16,178,112)(17,170,110)(18,197,74)(19,233,180)(20,121,96)(22,228,155)(23,48,173)(24,201,187)(26,136,190)(27,212,94)(29,175,52)(30,77,32)(31,237,34)(33,226,90)(35,129,54)(37,161,114)(38,232,87)(39,219,192)(40,78,159)(41,139,71)(43,211,251)(45,222,240)(46,97,135)(49,70,131)(50,153,200)(51,186,209)(53,203,216)(55,169,64)(56,140,230)(57,260,118)(58,91,243)(59,199,227)(60,108,164)(61,208,101)(62,206,106)(65,103,66)(67,95,205)(68,73,225)(69,151,113)(72,221,152)(75,143,202)(79,217,254)(80,93,122)(81,181,252)(83,258,126)(84,163,177)(85,154,213)(86,182,196)(88,133,215)(89,117,247)(92,191,160)(99,229,263)(100,138,188)(102,194,157)(105,149,184)(109,123,193)(111,137,183)(115,238,235)(119,167,147)(120,134,189)(124,185,265)(127,218,261)(141,231,210)(142,239,236)(144,224,249)(145,158,220)(148,214,172)(150,250,259)(162,257,256)(165,179,246)(176,195,266)(198,204,207)(223,262,255)]";
    public static final String j2 = "[(1,84)(2,20)(3,48)(4,56)(5,82)(6,67)(7,55)(8,41)(9,35)(10,40)(11,78)(12,100)(13,49)(14,37)(15,94)(16,76)(17,19)(18,44)(21,34)(22,85)(23,92)(24,57)(25,75)(26,28)(27,64)(29,90)(30,97)(31,38)(32,68)(33,69)(36,53)(39,61)(42,73)(43,91)(45,86)(46,81)(47,89)(50,93)(51,96)(52,72)(54,74)(58,99)(59,95)(60,63)(62,83)(65,70)(66,88)(71,87)(77,98)(79,80),(1,80,22)(2,9,11)(3,53,87)(4,23,78)(5,51,18)(6,37,24)(8,27,60)(10,62,47)(12,65,31)(13,64,19)(14,61,52)(15,98,25)(16,73,32)(17,39,33)(20,97,58)(21,96,67)(26,93,99)(28,57,35)(29,71,55)(30,69,45)(34,86,82)(38,59,94)(40,43,91)(42,68,44)(46,85,89)(48,76,90)(49,92,77)(50,66,88)(54,95,56)(63,74,72)(70,81,75)(79,100,83)]";
    public static final String j2_2 = "[(1,88)(2,22)(4,5)(6,98)(7,66)(8,24)(9,20)(10,75)(12,74)(13,39)(14,45)(15,58)(16,46)(17,55)(18,96)(19,48)(21,94)(23,76)(26,42)(27,65)(29,51)(30,86)(31,70)(32,71)(34,78)(35,93)(36,56)(37,87)(38,67)(40,83)(41,59)(43,73)(47,100)(49,72)(50,77)(52,95)(54,84)(57,82)(61,91)(62,64)(69,81)(79,97)(89,99),(1,59,75,7,64)(2,84,69,93,9)(3,78,51,10,30)(4,14,20,67,8)(5,99,43,32,46)(6,54,89,11,90)(12,33,21,56,35)(13,58,87,49,45)(15,18,76,94,37)(16,50,88,79,27)(17,70,52,82,38)(19,91,41,73,62)(22,55,23,65,86)(24,61,29,98,63)(25,81,96,83,48)(26,68,77,100,44)(28,92,97,57,34)(31,36,72,85,47)(39,95,60,66,42)(40,80,74,71,53)]";
    public static final String hs = "[(1,60)(2,72)(3,81)(4,43)(5,11)(6,87)(7,34)(9,63)(12,46)(13,28)(14,71)(15,42)(16,97)(18,57)(19,52)(21,32)(23,47)(24,54)(25,83)(26,78)(29,89)(30,39)(33,61)(35,56)(37,67)(44,76)(45,88)(48,59)(49,86)(50,74)(51,66)(53,99)(55,75)(62,73)(65,79)(68,82)(77,92)(84,90)(85,98)(94,100),(1,86,13,10,47)(2,53,30,8,38)(3,40,48,25,17)(4,29,92,88,43)(5,98,66,54,65)(6,27,51,73,24)(7,83,16,20,28)(9,23,89,95,61)(11,42,46,91,32)(12,14,81,55,68)(15,90,31,56,37)(18,69,45,84,76)(19,59,79,35,93)(21,22,64,39,100)(26,58,96,85,77)(33,52,94,75,44)(34,62,87,78,50)(36,82,60,74,72)(41,80,70,49,67)(57,63,71,99,97)]";
    public static final String m22 = "[(1,13)(2,8)(3,16)(4,12)(6,22)(7,17)(9,10)(11,14),(1,22,3,21)(2,18,4,13)(5,12)(6,11,7,15)(8,14,20,10)(17,19)]";
    public static final String m22_77pt = "[(3,5)(4,6)(8,12)(9,13)(11,15)(14,20)(16,19)(17,22)(18,23)(21,25)(24,30)(26,32)(27,34)(28,35)(29,37)(31,39)(33,41)(36,46)(38,48)(42,53)(43,44)(45,56)(49,59)(51,61)(58,67)(60,65)(62,66)(68,74)(69,72)(70,71)(73,76)(75,77),(1,2,4,3)(5,7,11,8)(6,9,14,10)(12,16,21,17)(13,18,24,19)(22,26,33,27)(23,28,36,29)(25,31)(30,38)(32,40)(34,42,54,43)(35,44,55,45)(37,47)(39,49,60,50)(41,51,46,52)(48,57,66,58)(53,62,71,63)(56,64,72,65)(59,68,73,67)(61,69,75,70)]";
    public static final String m23 = "[(1,2)(3,4)(7,8)(9,10)(13,14)(15,16)(19,20)(21,22),(1,16,11,3)(2,9,21,12)(4,5,8,23)(6,22,14,18)(13,20)(15,17)]";
    public static final String m24 = "[(1,4)(2,7)(3,17)(5,13)(6,9)(8,15)(10,19)(11,18)(12,21)(14,16)(20,24)(22,23),(1,4,6)(2,21,14)(3,9,15)(5,18,10)(13,17,16)(19,24,23)]";
    public static void main(String[] args) {
        HashSet<State> states = new HashSet<>();
        GroupExplorer g = new GroupExplorer(ree3, MemorySettings.COMPACT, states, new HashSet<>(), new HashSet<>(), true);
        exploreGroup(g, (state, description) -> { });

        HashSet<State> disjoint1 = new HashSet<>();
        HashSet<State> disjoint2 = new HashSet<>();
        HashSet<State> disjoint3 = new HashSet<>();
        HashSet<State> disjoint4 = new HashSet<>();

        ArrayList<State> shuffledStates = new ArrayList<>(states);
        Collections.shuffle(shuffledStates);

        // Find combinations of 2 states that generate a group of order 504
        for (State state1 : shuffledStates) {
            for (State state2 : shuffledStates) {
                if (state1.equals(state2)) continue;
                Generator generator = Generator.combine(new Generator(GroupExplorer.stateToCycles(state1.state())), new Generator(GroupExplorer.stateToCycles(state2.state())));
                HashSet<State> newstates = new HashSet<>();
                GroupExplorer g2 = new GroupExplorer(GroupExplorer.generatorsToString(generator.generator()), MemorySettings.COMPACT, newstates, new HashSet<>(), new HashSet<>(), true);
                g2.exploreStates(false,null);
                if (g2.order() == 504/3) {
                    System.out.println("Found generator: " + generator);
                    if (disjoint1.isEmpty() || disjoint1.containsAll(newstates)) {
                        System.out.println("Adding to disjoint1");
                        disjoint1.addAll(newstates);
                    } else if (disjoint2.isEmpty() || disjoint2.containsAll(newstates)) {
                        System.out.println("Adding to disjoint2");
                        disjoint2.addAll(newstates);
                    } else if (disjoint3.isEmpty() || disjoint3.containsAll(newstates)) {
                        System.out.println("Adding to disjoint3");
                        disjoint3.addAll(newstates);
                    } else if (disjoint4.isEmpty() || disjoint4.containsAll(newstates)) {
                        System.out.println("Adding to disjoint4");
                        disjoint4.addAll(newstates);
                    } else {
                        throw new RuntimeException("Found generator: " + generator + " with " + newstates.size() + " states");
                    }
                }
            }
        }
        
    }

    public static void exploreGroup(GroupExplorer gap,
            BiConsumer<int[], String> peekCyclesAndDescriptions) {

        long nPermutations = 1;
        for (int i = 1; i <= gap.nElements; i++) {
            nPermutations *= i;
        }

        HashMap<String, Integer> cycleDescriptions = new HashMap<>();

        int iterations = gap.exploreStates(true, (states, depth) -> {
            for (int[] state : states) {
                String cycleDescription = GroupExplorer.describeState(gap.nElements, state);
                if (peekCyclesAndDescriptions != null) peekCyclesAndDescriptions.accept(state, cycleDescription);
                cycleDescriptions.merge(cycleDescription, 1, Integer::sum);
            }
        });
        
        System.out.println("Elements: " + gap.nElements);
        System.out.println("Total unique permutations: " + nPermutations);
        System.out.println("Total group permutations: " + gap.order());

        System.out.println("Subset: 1/" + ((double)nPermutations / gap.order()));
        System.out.println("Iterations: " + iterations);

        printCycleDescriptions(cycleDescriptions);

    }

    public static void printCycleDescriptions(HashMap<String, Integer> cycleDescriptions) {


        // Print sorted cycle descriptions
        System.out.println("Cycle structure frequencies:");
        cycleDescriptions.entrySet().stream()
            .sorted((e1, e2) -> {
                int comp = Integer.compare(e2.getValue(), e1.getValue()); // Sort by frequency descending
                if (comp == 0) {
                    return e1.getKey().compareTo(e2.getKey()); // If frequencies are equal, sort alphabetically
                }
                return comp;
            })
            .forEach(entry -> System.out.println(entry.getValue() + ": " + entry.getKey()));

    }

}
