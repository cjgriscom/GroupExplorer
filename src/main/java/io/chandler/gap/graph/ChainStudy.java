package io.chandler.gap.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.tkk.ics.jbliss.AbstractGraph;
import io.chandler.gap.GapInterface;
import io.chandler.gap.GroupExplorer;

/**
 * Exhaustively generates "chain" generators built from two polygon types.
 *
 * <p>A configuration is specified by:
 * <ul>
 *   <li>{@code shape1Sides}: number of vertices of shape type 1 (e.g. 3 for a triangle, 2 for a line).</li>
 *   <li>{@code shape2Sides}: number of vertices of shape type 2.</li>
 *   <li>{@code numShape1}: how many copies of shape type 1 appear in the chain.</li>
 *   <li>{@code numShape2}: how many copies of shape type 2 appear in the chain.</li>
 * </ul>
 *
 * <p>The chain is a sequence of {@code numShape1 + numShape2} shapes. For every adjacent pair
 * in this sequence we choose a single vertex of the left shape and a single vertex of the
 * right shape to be identified (shared). This produces a connected graph whose edges are
 * the sides of the polygons.
 *
 * <p>The output for each realization is a pair of generators:
 * <ul>
 *   <li>Generator 1: disjoint cycles, one for each occurrence of shape type 1.</li>
 *   <li>Generator 2: disjoint cycles, one for each occurrence of shape type 2.</li>
 * </ul>
 *
 * <p>For every adjacency pattern we enumerate all directed versions by choosing, independently
 * for each polygon instance, whether its cycle runs "forwards" or "backwards".
 *
 * <p>For each distinct generator pair we call {@link GapInterface#runGapCommands(String, int)}
 * to obtain the group size and {@code StructureDescription(g)} and print them.
 */
public class ChainStudy {

	static {
		loadJBliss();
	}
		
	private static void loadJBliss() {
		System.out.println(System.getProperty("os.name"));
		System.out.println(System.getProperty("os.arch"));
		// Check if we're on linux amd64
		if (System.getProperty("os.name").toLowerCase().contains("linux") && System.getProperty("os.arch").toLowerCase().contains("amd64")) {
			File jbliss = new File("lib/libjbliss.so");
			if (!jbliss.exists()) {
				System.out.println("JBliss library not found.");
				System.exit(1);
			}
			System.load(jbliss.getAbsolutePath());
		// Check if we're on windows amd64
		} else if (System.getProperty("os.name").toLowerCase().startsWith("windows") && System.getProperty("os.arch").toLowerCase().contains("amd64")) {
			File jbliss = new File("lib/jbliss.dll");

			if (!jbliss.exists()) {
				System.out.println("JBliss library not found.");
				System.exit(1);
			}
			System.load(jbliss.getAbsolutePath());
		} else {
			System.out.println("JBliss library not found. Only Linux / Windows x86_64 are pre-compiled.");
			System.exit(1);
		}
		
	}

    public static void main(String[] args) throws IOException {
        // --------------------------------------------------------
        // Configuration (can be overridden by command‑line args)
        // --------------------------------------------------------
        int shape1Sides = 3;   // e.g. 3 = triangle, 2 = line, 4 = square
        int shape2Sides = 3;   // second polygon type
        int numShape1   =3;   // number of type‑1 shapes in the chain
        int numShape2   =2;   // number of type‑2 shapes in the chain

        int maxResults  = 5000; // safety cap to avoid combinatorial explosion

        if (args.length >= 4) {
            try {
                shape1Sides = Integer.parseInt(args[0]);
                shape2Sides = Integer.parseInt(args[1]);
                numShape1   = Integer.parseInt(args[2]);
                numShape2   = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: ChainStudy [shape1Sides shape2Sides numShape1 numShape2 [maxResults]]");
                System.err.println("Falling back to default configuration.");
            }
        }
        if (args.length >= 5) {
            try {
                maxResults = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid maxResults argument; using default " + maxResults);
            }
        }

        if (shape1Sides < 2 || shape2Sides < 2) {
            throw new IllegalArgumentException("Shapes must have at least 2 sides.");
        }
        if (numShape1 < 0 || numShape2 < 0) {
            throw new IllegalArgumentException("Number of shapes must be non‑negative.");
        }
        if (numShape1 + numShape2 == 0) {
            System.out.println("Nothing to do: total number of shapes is zero.");
            return;
        }

        System.out.println("ChainStudy configuration:");
        System.out.println("  Shape 1 sides: " + shape1Sides);
        System.out.println("  Shape 2 sides: " + shape2Sides);
        System.out.println("  #Shape 1:      " + numShape1);
        System.out.println("  #Shape 2:      " + numShape2);
        System.out.println("  Max results:   " + maxResults);
        System.out.println();

        int[] sides = new int[]{shape1Sides, shape2Sides};
        
        // We restrict to a single alternating chain:
        //   A1 – B1 – A2 – B2 – ...,
        // where B is either A or A-1. When B = A-1 the chain ends with an A.
        if (!(numShape2 == numShape1 || numShape2 == numShape1 - 1)) {
            System.out.println("For ChainStudy we require numShape2 == numShape1 or numShape2 == numShape1 - 1.");
            System.out.println("Nothing to do for this configuration.");
            return;
        }

        int chainLength = numShape1 + numShape2;
        int[] types = new int[chainLength];
        // 0 = shape1 (A), 1 = shape2 (B), alternating A,B,A,B,...
        for (int i = 0; i < chainLength; i++) {
            types[i] = (i % 2 == 0) ? 0 : 1;
        }

        System.out.println("Using single alternating type sequence of length " + chainLength + ".");

        GapInterface gap = new GapInterface();
        try {
            Set<String> seenGenerators = new HashSet<>();
            int[] counter = new int[]{0};

            exploreAdjacencyPatterns(types, sides, gap, maxResults, counter, seenGenerators);

            System.out.println();
            System.out.println("Total distinct generators tested: " + counter[0]);
        } finally {
            gap.close();
        }
    }

    /**
     * For a fixed type sequence, enumerate all ways to choose a single shared vertex
     * for each adjacent pair in the chain. We assign global vertex IDs lazily and
     * backtrack over choices.
     */
    private static void exploreAdjacencyPatterns(int[] types,
                                                 int[] sides,
                                                 GapInterface gap,
                                                 int maxResults,
                                                 int[] counter,
                                                 Set<String> seenGenerators) throws IOException {
        int nShapes = types.length;

        // globalVertices[shapeIndex][localVertexIndex] = global vertex id (0 = unassigned)
        int[][] globalVertices = new int[nShapes][];
        for (int i = 0; i < nShapes; i++) {
            globalVertices[i] = new int[sides[types[i]]];
        }

        backtrackAdjacency(0, types, sides, globalVertices, 1, gap, maxResults, counter, seenGenerators);
    }

    /**
     * Recursive backtracking over adjacency choices. At step {@code edgeIndex} we connect
     * shape {@code edgeIndex} to shape {@code edgeIndex+1} by choosing a single vertex
     * of each to be identified.
     */
    private static void backtrackAdjacency(int edgeIndex,
                                           int[] types,
                                           int[] sides,
                                           int[][] globalVertices,
                                           int nextVertexId,
                                           GapInterface gap,
                                           int maxResults,
                                           int[] counter,
                                           Set<String> seenGenerators) throws IOException {
        int nShapes = types.length;
        if (counter[0] >= maxResults) return;

        if (nShapes == 1) {
            // Degenerate case: only one polygon, no adjacency edges.
            finishAndEmit(types, sides, clone2D(globalVertices), nextVertexId, gap, maxResults, counter, seenGenerators);
            return;
        }

        if (edgeIndex >= nShapes - 1) {
            finishAndEmit(types, sides, clone2D(globalVertices), nextVertexId, gap, maxResults, counter, seenGenerators);
            return;
        }

        int leftShape  = edgeIndex;
        int rightShape = edgeIndex + 1;
        int leftSides  = sides[types[leftShape]];
        int rightSides = sides[types[rightShape]];

        for (int u = 0; u < leftSides; u++) {
            for (int v = 0; v < rightSides; v++) {
                int[][] gv = clone2D(globalVertices);
                int nextId = nextVertexId;

                int leftId  = gv[leftShape][u];
                int rightId = gv[rightShape][v];

                if (leftId == 0 && rightId == 0) {
                    // New shared vertex for this adjacency.
                    int id = nextId++;
                    gv[leftShape][u]  = id;
                    gv[rightShape][v] = id;
                } else if (leftId == 0 && rightId != 0) {
                    gv[leftShape][u] = rightId;
                } else if (leftId != 0 && rightId == 0) {
                    gv[rightShape][v] = leftId;
                } else {
                    // Both already assigned: they must agree, otherwise conflict.
                    if (leftId != rightId) {
                        continue;
                    }
                }

                backtrackAdjacency(edgeIndex + 1, types, sides, gv, nextId,
                        gap, maxResults, counter, seenGenerators);

                if (counter[0] >= maxResults) return;
            }
        }
    }

    /**
     * Once all adjacency edges have been processed, assign fresh IDs to remaining
     * free vertices and enumerate all directed versions of the cycles.
     */
    private static void finishAndEmit(int[] types,
                                      int[] sides,
                                      int[][] globalVertices,
                                      int nextVertexId,
                                      GapInterface gap,
                                      int maxResults,
                                      int[] counter,
                                      Set<String> seenGenerators) throws IOException {
        // Assign IDs to any still‑unassigned local vertices.
        for (int i = 0; i < globalVertices.length; i++) {
            int[] verts = globalVertices[i];
            for (int j = 0; j < verts.length; j++) {
                if (verts[j] == 0) {
                    verts[j] = nextVertexId++;
                }
            }
        }

        int nShapes = types.length;
        int totalOrientations = 1 << nShapes; // 2^(#shapes)

        // For each assignment of orientation bits we build the two generators.
        for (int mask = 0; mask < totalOrientations; mask++) {
            if (counter[0] >= maxResults) return;

            List<int[]> cycles1 = new ArrayList<>();
            List<int[]> cycles2 = new ArrayList<>();

            for (int i = 0; i < nShapes; i++) {
                int type = types[i];          // 0 or 1
                int len  = sides[type];
                int[] local = globalVertices[i];

                boolean forward = ((mask >> i) & 1) == 0;
                int[] cycle = new int[len];
                if (forward) {
                    for (int k = 0; k < len; k++) {
                        cycle[k] = local[k];
                    }
                } else {
                    for (int k = 0; k < len; k++) {
                        cycle[k] = local[len - 1 - k];
                    }
                }

                if (type == 0) {
                    cycles1.add(cycle);
                } else {
                    cycles2.add(cycle);
                }
            }

            int[][] gen1 = cycles1.toArray(new int[cycles1.size()][]);
            int[][] gen2 = cycles2.toArray(new int[cycles2.size()][]);

            // Enforce that cycles in each generator are disjoint: no vertex may appear
            // in more than one cycle of the same generator.
            if (!cyclesAreDisjoint(gen1) || !cyclesAreDisjoint(gen2)) {
                continue;
            }

            int[][][] generators = new int[][][]{gen1, gen2};

			AbstractGraph<Integer> graph = PlanarStudy.buildJblissGraphFromCombinedGen(generators, true);
			String check = PlanarStudy.getCanonicalGraph(graph);

			if (!seenGenerators.add(check)) {
				continue; // skip duplicates
			}

            String genStr = GroupExplorer.generatorsToString(generators);

            List<String> out = gap.runGapCommands(genStr, 3);

			if (out == null || out.size() < 3) {
				throw new RuntimeException("Unexpected gap output: " + out);
			}

            counter[0]++;
            System.out.println("Generator " + counter[0] + ": " + genStr);
            System.out.println("  Size: " + out.get(1).trim());
            System.out.println("  Structure: " + out.get(2).trim());
            System.out.println();

            if (counter[0] >= maxResults) return;
        }
    }

    /**
     * Returns true if, within the given array of cycles, every vertex label appears
     * in at most one cycle. This matches the requirement that cycles in a single
     * generator notation are disjoint.
     */
    private static boolean cyclesAreDisjoint(int[][] cycles) {
        Set<Integer> seen = new HashSet<>();
        for (int[] cycle : cycles) {
            for (int v : cycle) {
                if (!seen.add(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] clone2D(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i].clone();
        }
        return dst;
    }
}


