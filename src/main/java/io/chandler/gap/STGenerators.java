package io.chandler.gap;

import static io.chandler.gap.Generators.printCycleDescriptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.VertexColorSearch.ColorMapping;
import io.chandler.gap.cache.LongStateCache;
import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;

public class STGenerators {

	public static void main(String[] args) {
		vertexColorSearchST();
		//includeOctCaseTest();
	}

	public static final String tet1Symm = "[(1,4,7)(8,2,5)(9,3,6)(10,12,11)]";
	public static final String tet2Symm = "[(7,8,9)(2,4,11)(1,6,10)(3,5,12)]";
    private static void vertexColorSearchST() {
		Generator tet1Gen = new Generator(GroupExplorer.parseOperationsArr(tet1Symm));
        Generator tet2Gen = new Generator(GroupExplorer.parseOperationsArr(tet2Symm));
        int[][][] stSymm = Generator.combine(tet1Gen, tet2Gen).generator();

        VertexColorSearch vcs = new VertexColorSearch(stSymm, 12, Dodecahedron_STDual::getFacesFromVertex, Dodecahedron_STDual::getMatchingVertexFromFaces);

        for (ColorMapping c : vcs.searchForGenerators()) {
            int[] axes = c.axesSubgroup.vertex1Positions;
            if (axes.length >= 2) {
                int colors = (int) Arrays.stream(c.getVertexToColorMap()).distinct().count();
                
                // Pick the two large axis mappings
                System.out.println(c.axesSubgroup.order + " " + Arrays.toString(c.axesSubgroup.vertex1Positions));

                int[] vertices = c.axesSubgroup.vertex1Positions;

                int[][] cyclesUnified = new int[axes.length][];
                for (int i = 0; i < vertices.length; i++) {
                    cyclesUnified[i] = Dodecahedron_STDual.getFacesFromVertex(vertices[i]);
                }
                
                // Select partitions of vertices

                Generator gUnified = Generator.combine(
                    new Generator(new int[][][] {cyclesUnified}),
                    new Generator(stSymm)
                );
                System.out.println("Generating unified group with " + colors + " colors");
                checkGenerator(false, 11, gUnified);

				if (axes.length % 2 == 0) {
					// Split
					System.out.println("Splitting " + axes.length + " axes");
					List<int[]> combinations = Permu.generateCombinations(vertices.length, vertices.length / 2);

					System.out.println("Generating split groups : " + combinations.size() + " combinations");

					for (int[] combination : combinations) {
						HashSet<Integer> verticesB = new HashSet<>();
						for (int v : vertices) verticesB.add(v);
						int[][] cyclesSplit = new int[axes.length][];
						for (int i = 0; i < combination.length; i++) {
							int vertex = axes[combination[i]];
							verticesB.remove(vertex);
							cyclesSplit[i] = Dodecahedron_STDual.getFacesFromVertex(vertex);
						}
						int i = 0;
						for (int vertex : verticesB) {
							cyclesSplit[combination.length + i] = CycleInverter.invertArray(Dodecahedron_STDual.getFacesFromVertex(vertex));
							i++;
						}
	
						//System.out.println(cyclesSplit.length);
						
						Generator gSplit = Generator.combine(
							new Generator(new int[][][]{cyclesSplit}),
							new Generator(stSymm)
						);
	
						System.out.println("Generating split group with " + colors + " colors");
						checkGenerator(false, 8, gSplit);
					}
				}

            }
        }
    }

	// This is another symmetric option that color search doesn't find
	// It generates the same alternating group when unified
	// When split and patterned over 3d 180 symmetry ... let's find out
	public static final String tetAsymGen = "[(10,11,12)(4,6,8)(1,2,3)(7,8,9)]";

	public static final String threeDSymm = "[(9,11)(8,10)(7,12)(2,6)(4,3)(1,5),(6,12)(11,5)(8,3)(10,4)(1,9)(2,7)]";


    private static void includeOctCaseTest() {
		Generator tetAsymGenG = new Generator(GroupExplorer.parseOperationsArr(tetAsymGen));
        int[][][] symm3d = new Generator(GroupExplorer.parseOperationsArr(threeDSymm)).generator();

		int[][] cyclesUnified = tetAsymGenG.generator()[0];
		
		// Select partitions of vertices

		Generator gUnified = Generator.combine(
			new Generator(new int[][][] {cyclesUnified}),
			new Generator(symm3d)
		);
		System.out.println("Generating unified group");
		checkGenerator(false, 11, gUnified);

		
		// Split
		System.out.println("Splitting " + cyclesUnified.length + " axes");
		List<int[]> combinations = Permu.generateCombinations(cyclesUnified.length, cyclesUnified.length / 2);

		System.out.println("Generating split groups : " + combinations.size() + " combinations");

		for (int[] combination : combinations) {
			int[][] cyclesSplit = cyclesUnified.clone();
			for (int i : combination) {
				cyclesSplit[i] = CycleInverter.invertArray(cyclesSplit[i]);
			}

			//System.out.println(cyclesSplit.length);
			
			Generator gSplit = Generator.combine(
				new Generator(new int[][][]{cyclesSplit}),
				new Generator(symm3d)
			);


			System.out.println("Generating split group");
			Dodecahedron_STDual.printVertexGeneratorNotations(new int[][][]{cyclesSplit});
			checkGenerator(false, 10, gSplit);

			// Undo
			for (int i : combination) {
				cyclesSplit[i] = CycleInverter.invertArray(cyclesSplit[i]);
			}
		}
		
		
    }


    private static void checkGenerator(boolean debug, int transitivityMax, Generator g) {
        for (int transitivity = transitivityMax; transitivity <= transitivityMax; transitivity++) {

            if (debug) System.out.println("Checking transitivity " + transitivity);
            Set<State> stateCache = new LongStateCache(transitivity,12);
            ArrayList<String> results = new ArrayList<>();
            HashMap<Integer, List<String>> smallGroupGenerators = new HashMap<>();

            checkGenerator(debug, g, results, smallGroupGenerators, stateCache);

            if (results.size() > 0 || smallGroupGenerators.size() > 0) {

                System.out.println("Found generator at transitivity " + transitivity);
                for (Map.Entry<Integer, List<String>> e : smallGroupGenerators.entrySet()) {
                    System.out.println("Order " + e.getKey() + ":");
                    for (String genString : e.getValue()) {
                        System.out.println(genString);
                    }
                }
                for (String genString : results) {
                    int[][] cycles = GroupExplorer.parseOperations(genString).get(0);
                    Dodecahedron_STDual.printVertexGeneratorNotations((new int[][][] {cycles}));
                    System.out.println(genString);
                }

                break;
            }
        }
    }
    private static void checkGenerator(boolean debug, Generator g, List<String> lgGroupResults, Map<Integer, List<String>> smallGroupGenerators, Set<State> cache) {
        cache = new ParityStateCache(new HashSet<>(), cache);
        debug = true;
        String genString = GroupExplorer.generatorsToString(g.generator());
        GroupExplorer candidate = new GroupExplorer(
            genString,
            MemorySettings.FASTEST, cache);
            

        ArrayList<String> depthPeek = new ArrayList<>();
        int startCheckingRatioIncreaseAtOrder = 313692;/// 739215;
        int limit = 30400000/5;
        int[] stateCount = new int[2];
        int iters = -2;

        //System.out.println(genString);
        
        double lastRatio = 0;
        candidate.initIterativeExploration();
        while (iters == -2) {
            int[] depthA = new int[] {0};
            try {
                iters = candidate.iterateExploration(debug, limit, (states, depth) -> {
                    stateCount[0] = stateCount[1];
                    stateCount[1] += states.size();
                    depthA[0] = depth;
                });
            } catch (ParityStateCache.StateRejectedException e) {
                // Heuristic 1:
                //    If the 7-transitive cache is too small we've generated a non-M12 group
                
                // Fail because this can't happen for valid M12 generators
                iters = -2;
                //System.out.println("M12 cache is different");
                break;
            }
            int depth = depthA[0];
            double ratio = depth < 2 ? 10000 : stateCount[1]/(double)stateCount[0];
            if (depth > 5 ) depthPeek.add(ratio+" " + stateCount[1] + ",");

            //System.out.println(stateCountB[0] + " " + stateCount[1]);

            // Heuristic 2: If the ratio of states is increasing, it's not going to converge quickly
            boolean isDecreasing = ratio - lastRatio < 0.01;
            if (!isDecreasing && stateCount[1] > startCheckingRatioIncreaseAtOrder) {
                //iters = -2;
                System.out.println("Ratio rate increase");
                //break;
            }
            
            
            
            lastRatio = ratio;
        }

        if (iters == -2) {
            /*if (depthPeek.size() > 4) {
                System.out.println("Reject " + iters + " Last iter states: " + depthPeek.get(depthPeek.size() - 1) + " Depth: " + depthPeek.size());
                System.out.println(depthPeek.toString());
            }*/
        } else if (iters == -1) {
            System.out.println("Iters: " + iters + " Last iter states: " + depthPeek.get(depthPeek.size() - 1) + " Depth: " + depthPeek.size());
            System.out.println( GroupExplorer.generatorsToString(g.generator()));
            System.out.println(depthPeek.toString());
            lgGroupResults.add(GroupExplorer.generatorsToString(g.generator()));

            
            HashMap<String, Integer> cycleDescriptions = new HashMap<>();
            for (State state : cache) {
                String cycleDescription = GroupExplorer.describeState(candidate.nElements, state.state());
                cycleDescriptions.merge(cycleDescription, 1, Integer::sum);
                if (cycleDescriptions.size() % 100_000 == 0) {
                    System.out.println("Processed " + cycleDescriptions.size() + " / " + cache.size() + " states");
                }
            }

            printCycleDescriptions(cycleDescriptions);
            
        } else if (candidate.order() > 1) {
            // Add genString to smallGroupGenerators
            List<String> gens = smallGroupGenerators.computeIfAbsent(candidate.order(), k -> Collections.synchronizedList(new ArrayList<String>()));
            gens.add(genString);
            System.out.println("Found order " + (candidate.order()) + ": " + GroupExplorer.generatorsToString(g.generator()));
        }
        

    }
}
