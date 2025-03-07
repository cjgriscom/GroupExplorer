package io.chandler.gap.graph;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

import org.jgrapht.Graph;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.File;

import io.chandler.gap.GapInterface;
import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class PlanarStudy {


	public static void main(String[] args) throws IOException {
		boolean generate = true;
		boolean filter = true;
		int order = -1;
		MemorySettings mem = MemorySettings.COMPACT;
		String[] conj = new String[] {
			"6p 3-cycles"
		};
		int filterCombination0 = 0, filterCombination1 = 0;
		String generator = Generators.m22;
		String groupName = "m22";
		File root = new File("PlanarStudy/" + groupName);
		root.mkdirs();

		if (generate) {
			boolean multithread = true;
			PrintStream[] filesOut = new PrintStream[conj.length];
			for (int i = 0; i < conj.length; i++) {
				filesOut[i] = new PrintStream(root.getAbsolutePath() + "/" + conj[i] + ".txt");
			}

			GroupExplorer g = new GroupExplorer(generator, mem, new HashSet<>(), new HashSet<>(), new HashSet<>(), multithread);
			Generators.exploreGroup(g, (state, description) -> {
				for (int i = 0; i < conj.length; i++) {
					if (description.equals(conj[i])) {
						String cycles = GroupExplorer.stateToNotation(state);
						filesOut[i].println(cycles);
					}
				}
			});
			order = g.order();

			// Close files
			for (int i = 0; i < conj.length; i++) {
				filesOut[i].close();
			}
		}

		if (filter) {
			GapInterface gap = new GapInterface();
			Scanner fileIn = new Scanner(new File(root.getAbsolutePath() + "/" + conj[filterCombination0] + ".txt"));
			String line0 = fileIn.nextLine();
			int[][][] gen0 = GroupExplorer.parseOperationsArr(line0);
			fileIn.close();
			int found = 0;
			int total = 0;
			File file = new File(root.getAbsolutePath() + "/" + conj[filterCombination1] + ".txt");
			String fileOutPath = root.getAbsolutePath() + "/" + conj[filterCombination0] + "-" + conj[filterCombination1] + "-filtered.txt";
			try (Scanner fileIn2 = new Scanner(file); PrintStream fileOut = new PrintStream(fileOutPath)) {
				while (fileIn2.hasNextLine()) {
					String line = fileIn2.nextLine();
					int[][][] gen1 = GroupExplorer.parseOperationsArr(line);
					int[][][] combinedGen = GroupExplorer.renumberGenerators_fast(new int[][][] {gen0[0], gen1[0]}, 300);

					String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedGen), 2).get(1).trim();
					
					if (size.equals(order+"")) {
						total++;
						if (checkPlanarity(combinedGen)) {
							fileOut.println(GroupExplorer.generatorsToString(combinedGen));
							found++;
						}
					}
				}
			}
			System.out.println("Found planar: " + found);
			System.out.println("Found non-planar: " + (total - found));
			System.out.println("Wrote: " + fileOutPath);
				
		}
	}

    /**
     * Checks if the graph constructed from combinedGen is planar.
     *
     * @param combinedGen A 3D array representing the generators of the graph.
     * @return true if the graph is planar, false otherwise.
     */
    public static boolean checkPlanarity(int[][][] combinedGen) {
        // Create a graph using JGraphT
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

        // Add vertices and edges from combinedGen
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices first
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                // Add edges to form a complete cycle
                for (int i = 0; i < polygon.length; i++) {
                    graph.addEdge(polygon[i], polygon[(i+1)%polygon.length]);
                }
            }
        }

        // Check planarity using Boyer-Myrvold algorithm
        BoyerMyrvoldPlanarityInspector<Integer, DefaultEdge> inspector = new BoyerMyrvoldPlanarityInspector<>(graph);
        return inspector.isPlanar();
    }

	public static void generate3CyclesFile() throws IOException {
		PrintStream fileOut11 = new PrintStream("hs-9p-11-cycles.txt");
		PrintStream fileOut10 = new PrintStream("hs-10p-10-cycles.txt");
		PrintStream fileOut7 = new PrintStream("hs-14p-7-cycles.txt");
		PrintStream fileOut5 = new PrintStream("hs-19p-5-cycles.txt");
		PrintStream fileOut3 = new PrintStream("hs-30p-3-cycles.txt");
		PrintStream fileOut2_full = new PrintStream("hs-50p-2-cycles.txt");
		PrintStream fileOut2 = new PrintStream("hs-40p-2-cycles.txt");

		GroupExplorer g = new GroupExplorer(Generators.hs, MemorySettings.COMPACT, new HashSet<>(), new HashSet<>(), new HashSet<>(), false);
		Generators.exploreGroup(g, (state, description) -> {
			if (description.equals("30p 3-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut3.println(cycles);
			} else if (description.equals("19p 5-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut5.println(cycles);
			} else if (description.equals("14p 7-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut7.println(cycles);
			} else if (description.equals("10p 10-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut10.println(cycles);
			} else if (description.equals("9p 11-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut11.println(cycles);
			} else if (description.equals("40p 2-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut2.println(cycles);
			} else if (description.equals("50p 2-cycles")) {
				String cycles = GroupExplorer.stateToNotation(state);
				fileOut2_full.println(cycles);
			}
		});

		fileOut11.close();
		fileOut10.close();
		fileOut7.close();
		fileOut5.close();
		fileOut3.close();
		fileOut2.close();
		fileOut2_full.close();
	}
	public static void filter3CyclesFile() throws IOException {
		GapInterface gap = new GapInterface();
		
		Scanner fileIn = new Scanner(new File("hs-30p-3-cycles.txt"));
		PrintStream fileOut = new PrintStream("hs-30p-3-cycles-filtered.txt");
		String line0 = fileIn.nextLine();
		int[][][] gen0 = GroupExplorer.parseOperationsArr(line0);

		while (fileIn.hasNextLine()) {
			String line = fileIn.nextLine();
			int[][][] gen1 = GroupExplorer.parseOperationsArr(line);
			Generator combined = Generator.combine(new Generator(gen0), new Generator(gen1));
			int[][][] combinedGen = GroupExplorer.renumberGenerators_fast(combined.generator());

			String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combinedGen), 2).get(1).trim();
			System.out.println(size);
			if (size.equals("44352000")) {
				fileOut.println(GroupExplorer.generatorsToString(combinedGen));
			}
			
		}
		fileOut.close();
		fileIn.close();
	}
}
