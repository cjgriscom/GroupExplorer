package io.chandler.gap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.chandler.gap.GroupExplorer.Generator;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class GeneratorPairSearch {

	/*
	 * Find isomorphic pairs, pruning as they're created
	 * This is much slower than caching isomorphisms
	 *    but for large generators it's required
	 */
    public static ArrayList<IsomorphicGenerator> findGeneratorPairs_NoCache(
			AbstractGroupProperties group,
			int quickPruneOrder,
			int thoroughDiscardOrder,
			List<int[][]> generatorCandidates,
			List<int[][]> generatorCandidates2,
			boolean verbose) {
        
        System.out.println("Generator candidates: " + generatorCandidates.size());
        System.out.println("Generator candidates2: " + generatorCandidates2.size());

        // Make a list of generator pairs : src index
        //HashMap<Generator, Integer> generatorPairs = new HashMap<>();

        ArrayList<IsomorphicGenerator> generatorPairs = new ArrayList<>();
        int lastSize = 0;
        // Loop thru pairs of generator candidates
        for (int i = 0; i < generatorCandidates.size(); i++) {
            if (i % 10 == 0) System.out.println("Checking generator "+i+"/"+0+" of " + generatorCandidates.size() + " - " + generatorPairs.size() + " pairs found");
            int[][] aCycles = generatorCandidates.get(i);
            int j0 = generatorCandidates == generatorCandidates2 ? i + 1 : 0;
            for (int j = j0; j < generatorCandidates2.size(); j++) {
                int[][] bCycles = generatorCandidates2.get(j);

                if (!GroupExplorer.cyclesContainsAllElements(group.elements(), aCycles, bCycles)) continue;
                if (GroupExplorer.intersectionsMissing(aCycles, bCycles)) {
                    //System.out.println("Intersections missing");
                    continue;
                }

                int[][][] generator = new int[][][] { aCycles, bCycles };
                IsomorphicGenerator isomorphicGenerator = new IsomorphicGenerator(generator);
                boolean alreadyExists = false;
                for (IsomorphicGenerator existing : generatorPairs) {
                    if (existing.equals(isomorphicGenerator)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (alreadyExists) continue;

                String composite = "[" + GroupExplorer.cyclesToNotation(aCycles) + "," + GroupExplorer.cyclesToNotation(bCycles) + "]";
                
                boolean[] success = new boolean[]{false};
                int[] order = new int[]{0};

                try {
                    GroupExplorer compositeGAP = new GroupExplorer(composite, group.mem());
                    compositeGAP.exploreStates(false, (states, depth) -> {
                        order[0] += states.size();
                        if (order[0] > thoroughDiscardOrder) {
                            success[0] = true;
                            throw new RuntimeException();
                        }
                        //if (depth <= 14) System.out.print(depth + " " + states.size() + " ");

                    });
                    //System.out.println();

                } catch (RuntimeException e) {}

                if (success[0]) {
                    generatorPairs.add(isomorphicGenerator);
                } else {
                    //System.out.println("Failed to generate group: " + order[0]);
                }
                if (generatorPairs.size() > lastSize) {
                    if (verbose) System.out.println("Checking generator "+i+"/"+j+" of " + generatorCandidates.size() + " - " + generatorPairs.size() + " pairs found");
                    lastSize = generatorPairs.size();
                }
            }
        }

        System.out.println("Isomorphic Generator pairs: " + generatorPairs.size());
        return generatorPairs;
    }



    public static Map<Generator, Integer> findGeneratorPairs(AbstractGroupProperties group, List<int[][]> generatorCandidates, List<int[][]> generatorCandidates2) {
        return findGeneratorPairs(group, generatorCandidates, generatorCandidates2, false);
    }
    public static Map<Generator, Integer> findGeneratorPairs(AbstractGroupProperties group, List<int[][]> generatorCandidates, List<int[][]> generatorCandidates2, boolean verbose) {
        
        System.out.println("Generator candidates: " + generatorCandidates.size());
        System.out.println("Generator candidates2: " + generatorCandidates2.size());

        // Make a list of generator pairs : src index
        //HashMap<Generator, Integer> generatorPairs = new HashMap<>();

        Object2IntOpenHashMap<Generator> generatorPairs = new Object2IntOpenHashMap<>();
        int lastSize = 0;
        // Loop thru pairs of generator candidates
        for (int i = 0; i < generatorCandidates.size(); i++) {
            if (i % 10 == 0) System.out.println("Checking generator "+i+"/"+0+" of " + generatorCandidates.size() + " - " + generatorPairs.size() + " pairs found");
            int[][] aCycles = generatorCandidates.get(i);
            int j0 = generatorCandidates == generatorCandidates2 ? i + 1 : 0;
            for (int j = j0; j < generatorCandidates2.size(); j++) {
                int[][] bCycles = generatorCandidates2.get(j);

                if (!GroupExplorer.cyclesContainsAllElements(group.elements(), aCycles, bCycles)) continue;

                int[][][] generator = new int[][][] { aCycles, bCycles };
                generator = GroupExplorer.renumberGenerators(generator);
                if (generatorPairs.containsKey(new Generator(generator))) continue;

                String composite = "[" + GroupExplorer.cyclesToNotation(aCycles) + "," + GroupExplorer.cyclesToNotation(bCycles) + "]";
                GroupExplorer compositeGAP = new GroupExplorer(composite, group.mem());
                compositeGAP.exploreStates(false, null);
                int order = compositeGAP.order();
                if (order == group.order()) {
                    for (int[][][] iso : GroupExplorer.genIsomorphisms(generator)) {
                        generatorPairs.put(new Generator(iso), i * generatorCandidates.size() + j);
                    }
                }
                if (generatorPairs.size() > lastSize) {
                    if (verbose) System.out.println("Checking generator "+i+"/"+j+" of " + generatorCandidates.size() + " - " + generatorPairs.size() + " pairs found");
                    lastSize = generatorPairs.size();
                }
            }
        }

        System.out.println("Isomorphic Generator pairs: " + generatorPairs.size());
        return generatorPairs;
    }
   
}
