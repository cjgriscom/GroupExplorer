package io.chandler.gap.graph.problems;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import org.jgrapht.Graph;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GapInterface;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.Permu;
import io.chandler.gap.graph.PlanarStudyRepeated;

public class M11GraphFilter {

    // This class finds M11 deep / slice puzzles with three axes on icosahedral symmetry


	// Not all of the results are valid due to collisions, but it can be visually verified

	//  Good test 3 results"
    //   * 3/16 - 11
    //   * 4/16 - 12



	// This one fits [(1,4,11)(2,10,9)(5,6,7),(1,3)(2,11)(4,6)(7,8),(3,10)(5,9)(6,8)(7,11)]
	// sike
	public static void main(String[] args) throws IOException {
		Scanner input = new Scanner(new File("PlanarStudyMulti/icosahedral_symm_12pt/6p 2-cycles.txt"));
		
		int[][] ico0 = GroupExplorer.parseOperationsArr(input.nextLine())[0];

		ArrayList<int[][]> icoX = new ArrayList<>();

		while (input.hasNextLine()) {
			String data = input.nextLine();
			int[][][] generator = GroupExplorer.parseOperationsArr(data);
			icoX.add(generator[0]);
		}

		GapInterface gap = new GapInterface();

		// Loop for ico1

		ArrayList<int[][][]> found = new ArrayList<>();
		ArrayList<Graph<Integer, DefaultEdge>> pairGraphs = new ArrayList<>();
		

		Permu.generateCombinations(6, 4).forEach(comb0 -> {
			int[][] filtered0 = filter(ico0, comb0);
			for (int[][] ico1 : icoX) {
				Permu.generateCombinations(6, 4).forEach(comb1 -> {
					int[][] filtered1 = filter(ico1, comb1);

					int[][][] combined0 = new int[2][][];
					combined0[0] = filtered0;
					combined0[1] = filtered1;

					String size = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combined0), 2).get(1).trim();
					if (Integer.parseInt(size) > 8000) return;


					for (int[][] ico2 : icoX) {
						if (ico1.equals(ico2)) continue;

						Permu.generateCombinations(6, 4).forEach(comb2 -> {
							// Filter 
							int[][] filtered2 = filter(ico2, comb2);

							int[][][] combined1 = new int[3][][];
							combined1[0] = filtered0;
							combined1[1] = filtered1;
							combined1[2] = filtered2;
							
							// Check size
							String size2 = gap.runGapSizeCommand(GroupExplorer.generatorsToString(combined1), 2).get(1).trim();
							if (Integer.parseInt(size2) != 7920) return;
							
							// Check if isomorph
							if (found.contains(combined1)) return;
							found.add(combined1);
							 // Check for isomorphic duplicates.
							 Graph<Integer, DefaultEdge> candGraph = PlanarStudyRepeated.buildGraphFromCombinedGen(combined1, false);
							 for (Graph<Integer, DefaultEdge> g : pairGraphs) {
								 VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
										 new VF2GraphIsomorphismInspector<>(candGraph, g);
								 if (inspector.isomorphismExists()) {
									 return;
								 }
							 }
							 pairGraphs.add(candGraph);
							//.out.println("Size: " + size2);
							System.out.println(GroupExplorer.generatorsToString(combined1));
						});
						
					}
				});
			}
		});
	}

	private static int[][] filter(int[][] ico, int[] indices) {
		int[][] filtered = new int[indices.length][];
		for (int i = 0; i < indices.length; i++) {
			filtered[i] = ico[indices[i]];
		}
		return filtered;
	}
}
