package io.chandler.gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class WeylSearch {
	

	public static void main(String[] args) {
		findSL23(args);
	}


	/**
	 * SL(2,3) over tetrakis hexahedron (?)
	 * @param args
	 */
    public static void findSL23(String[] args) {
        System.out.println();
        GroupExplorer g = new GroupExplorer(Generators.wf4, MemorySettings.COMPACT, new HashSet<>(), new HashSet<>(), new HashSet<>(), true);
        
        ArrayList<int[]> states_a = new ArrayList<>();
        ArrayList<int[]> states_b = new ArrayList<>();
        
        String description_a = "8p 3-cycles";
        String description_b = "8p 3-cycles";

        // Pairs that work:
        //   Quadruple 6 and triple 8
        //   Quadruple 6 and quintuple 4


        // Pairs that don't work:
        //   Triple 8 and triple 8
        //   Quadruple 6 and quadruple 6
        
        Generators.exploreGroup(g, (state, description) -> {
            if (description.equals(description_a)) {
                states_a.add(state);
            }
            if (description.equals(description_b)) {
                states_b.add(state);
            }
        });

        System.out.println(description_a + ": " + states_a.size());
        System.out.println(description_b + ": " + states_b.size());

        // For each combination, get the group order

        int maxOrder = 0;

        for (int[] state_a : states_a) {
            for (int[] state_b : states_b) {
                Generator g2 = new Generator(GroupExplorer.stateToCycles(state_a));
                g2 = Generator.combine(g2, new Generator(GroupExplorer.stateToCycles(state_b)));
                String genS = GroupExplorer.generatorsToString(g2.generator());
                genS = GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators(g2.generator()));
                GroupExplorer ge2 = new GroupExplorer(genS, MemorySettings.COMPACT);
                ge2.exploreStates(false, null);
                maxOrder = Math.max(maxOrder, ge2.order());
                if (ge2.order() >= 24) {
                    int[][][] gParse = GroupExplorer.parseOperationsArr(genS);
                    boolean good = true;
                    int nGoodGroups = 0;
                    for (int i = 0; i < gParse[1].length; i++) {
                        int[] counts = new int[8];
                        for (int j = 0; j < gParse[1][i].length; j++) {
                            counts[(gParse[1][i][j] - 1) / 6]++;
                        }
                        for (int j = 0; j < counts.length; j++) {
                            if (counts[j] != 0 && counts[j] != 1) {
                                good = false;
                            }
                        }
                        if (good) nGoodGroups++;
                        if (good) System.out.print(Arrays.toString(gParse[1][i]));
                        System.out.print(",");
                    }
                    System.out.println();
                    if (nGoodGroups >= 8) {
                        System.out.println(ge2.order() + " " + genS);
                    }
                }
            }
        }

        System.out.println("Max order: " + maxOrder);
    }

}
