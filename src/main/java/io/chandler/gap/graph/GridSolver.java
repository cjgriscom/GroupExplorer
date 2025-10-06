package io.chandler.gap.graph;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.chandler.gap.GroupExplorer;

public class GridSolver {
	final int MAX_SOLUTIONS = 10000;
	final long TIMEOUT_MS = 30000L; // 30 seconds

	boolean DONT_VALIDATE = true;
	boolean DELETE_FILES = true;

	private final Graph<Integer, DefaultEdge> graph;

	public static void main(String[] args) {
		// 'snake' / straight line test
		String genSnake = "[(1,2)(3,4)(5,6)(7,8),(2,3)(4,5)(6,7)]";

		// u4_2 - optimal is 8x4
		String gen_u4_2 = "[(1,24)(2,15)(4,16)(6,11)(7,13)(8,18)(9,27)(10,19)(17,21)(23,25),(1,18)(2,16)(3,17)(6,21)(7,14)(8,26)(9,10)(13,25)(15,22)(20,27),(5,19)(8,22)(11,23)(13,20)(15,26)(25,27),(1,25)(5,12)(7,8)(13,18)(14,26)(23,24)]";

		// o2m2_2 - really struggles but works at 10x13
		String gen_o2m2_2 = "[(1,68)(2,58)(3,55)(4,51)(5,106)(7,96)(8,30)(9,98)(10,95)(11,42)(12,15)(13,88)(14,45)(16,107)(18,77)(19,47)(20,108)(21,44)(22,118)(23,71)(24,114)(26,53)(27,56)(28,104)(29,111)(31,32)(33,62)(34,69)(35,60)(36,78)(37,93)(38,63)(39,113)(40,112)(41,76)(43,94)(46,80)(48,90)(49,115)(50,64)(52,83)(54,66)(57,89)(59,102)(61,92)(67,110)(70,86)(72,117)(73,103)(74,119)(75,82)(79,100)(81,99)(85,97)(87,91)(101,109),(1,77)(3,18)(4,74)(6,102)(8,62)(10,110)(14,30)(15,66)(17,72)(24,73)(26,101)(28,42)(31,48)(33,43)(34,50)(35,111)(36,70)(40,92)(45,94)(55,57)(58,98)(59,65)(63,82)(68,89)(76,107)(80,118)(81,85)(84,117),(1,8)(2,44)(3,50)(4,68)(5,97)(6,92)(7,16)(9,13)(11,21)(12,69)(14,26)(15,76)(17,58)(18,34)(19,78)(20,27)(22,115)(23,96)(24,65)(25,87)(28,118)(30,101)(31,117)(32,108)(33,81)(36,82)(37,53)(38,60)(40,102)(41,56)(42,80)(43,85)(46,79)(47,104)(48,84)(52,113)(54,114)(59,73)(62,77)(63,70)(64,95)(66,107)(67,103)(71,105)(72,98)(74,89)(83,109)(86,106)(88,99)(90,91)(100,116)(112,119)]";

		// J2_2 - a known 12x10 solution
		String genJ = "[(1,99)(2,86)(3,17)(5,23)(6,94)(8,47)(9,58)(10,45)(11,97)(13,18)(14,37)(15,81)(19,26)(21,62)(22,89)(25,69)(27,100)(28,49)(29,92)(30,75)(31,74)(33,84)(34,82)(35,71)(36,90)(39,54)(41,66)(42,67)(43,83)(44,91)(46,57)(48,53)(50,95)(51,77)(55,93)(56,76)(59,61)(63,65)(68,98)(70,79)(72,73)(78,85)(87,88),(1,90)(2,70)(3,73)(4,9)(5,7)(6,87)(8,96)(10,24)(11,42)(12,33)(13,100)(14,38)(15,27)(16,61)(17,95)(18,39)(19,29)(20,77)(21,71)(22,86)(23,43)(25,98)(26,47)(28,45)(30,84)(31,40)(32,69)(34,58)(35,88)(36,55)(37,72)(41,54)(44,82)(46,80)(48,76)(49,93)(50,68)(51,56)(52,66)(53,92)(57,99)(59,81)(60,64)(62,79)(63,83)(65,91)(67,74)(75,78)(85,97)(89,94),(1,92)(2,82)(3,33)(4,22)(6,52)(8,96)(9,86)(10,81)(12,73)(13,28)(14,38)(15,27)(17,95)(18,55)(19,80)(20,97)(21,75)(23,64)(24,59)(25,79)(29,46)(30,68)(31,74)(32,65)(34,58)(35,51)(36,39)(40,67)(41,76)(43,60)(44,70)(45,100)(48,54)(49,93)(50,84)(53,90)(56,88)(57,99)(62,98)(66,87)(69,91)(71,78)(77,85)]";
		
		String gen = gen_o2m2_2;



		///////////////////////////////////////////////////////
		int[][][] genOps = GroupExplorer.parseOperationsArr(gen);
		Graph<Integer, DefaultEdge> graph = PlanarStudyRepeated.buildGraphFromCombinedGen(genOps, false);
		GridSolver solver = new GridSolver(graph);
		
		List<Map<Integer, int[]>> coords;

		List<int[]> areas = new ArrayList<>();
		areas.add(new int[]{10, 13});
		
		for (int[] area : areas) {
			// Try
			System.out.println("Trying " + area[0] + "x" + area[1]);

			coords = solver.solveAll(area[0], area[1], GroupExplorer.parseOperationsArr(gen)[0][0], false);

			int i = 0;
			for (Map<Integer, int[]> coord : coords) {
				System.out.println("Solution " + i + ":");
				System.out.println(printGrid(coord));
				i++;
			}

			System.out.println("Found " + coords.size() + " solutions");
		}
	}

	public GridSolver(Graph<Integer, DefaultEdge> graph) {
		this.graph = graph;
	}

	/**
	 * Finds all possible grid embeddings of the graph.
	 * @return List of all valid grid coordinate mappings
	 */
	public java.util.List<Map<Integer, int[]>> solveAll(int w, int h, int[] fixedPair, boolean allSolutions) {
		// Try VF3L to get all solutions
		if (graph.vertexSet().size() >= 1) {
			Graph<Integer, DefaultEdge> pattern = toUndirected(graph);
			int n = pattern.vertexSet().size();
			
			// Quick prechecks
			int maxDeg = 0;
			for (Integer v : pattern.vertexSet()) {
				int d = pattern.degreeOf(v);
				if (d > maxDeg) maxDeg = d;
			}
			if (maxDeg > 4) return java.util.Collections.emptyList();
			if (!isBipartite(pattern)) return java.util.Collections.emptyList();

			System.out.println("Trying grid " + w + "x" + h + " for all solutions...");
			java.util.List<Map<Integer, int[]>> solutions = tryVF3PAllSolutions(pattern, w, h, fixedPair, allSolutions);
			if (!solutions.isEmpty()) {
				return solutions;
			}
		}
		
		return java.util.Collections.emptyList();
	}

	public static java.util.List<Map<Integer, int[]>> solveAll(int w, int h, int[] fixedPair, boolean allSolutions, Graph<Integer, DefaultEdge> graph) {
		return new GridSolver(graph).solveAll(w,h, fixedPair, allSolutions);
	}

	/**
	 * Reconstructs the grid from a solution mapping.
	 * Returns a 2D array where grid[y][x] contains the vertex ID at that position,
	 * or -1 if the cell is empty.
	 * 
	 * @param solution A mapping from vertex IDs to [x, y] coordinates
	 * @return 2D grid array with vertex IDs at their positions
	 */
	public static int[][] reconstructGrid(Map<Integer, int[]> solution) {
		if (solution == null || solution.isEmpty()) {
			return new int[0][0];
		}

		// Find the grid dimensions
		int maxX = 0, maxY = 0;
		for (int[] coords : solution.values()) {
			maxX = Math.max(maxX, coords[0]);
			maxY = Math.max(maxY, coords[1]);
		}

		// Create grid (add 1 because coordinates are 0-indexed)
		int width = maxX + 1;
		int height = maxY + 1;
		int[][] grid = new int[height][width];

		// Initialize with -1 (empty cells)
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				grid[y][x] = -1;
			}
		}

		// Place vertices in the grid
		for (Map.Entry<Integer, int[]> entry : solution.entrySet()) {
			int vertex = entry.getKey();
			int[] coords = entry.getValue();
			int x = coords[0];
			int y = coords[1];
			grid[y][x] = vertex;
		}

		return grid;
	}

	/**
	 * Prints a visual representation of the grid with vertex IDs.
	 * 
	 * @param solution A mapping from vertex IDs to [x, y] coordinates
	 * @return String representation of the grid
	 */
	public static String printGrid(Map<Integer, int[]> solution) {
		int[][] grid = reconstructGrid(solution);
		if (grid.length == 0) {
			return "Empty grid";
		}

		StringBuilder sb = new StringBuilder();
		int height = grid.length;
		int width = grid[0].length;

		// Find the maximum number of digits needed for display
		int maxDigits = 1;
		for (int[] row : grid) {
			for (int val : row) {
				if (val > 0) {
					maxDigits = Math.max(maxDigits, String.valueOf(val).length());
				}
			}
		}
		maxDigits = Math.max(maxDigits, 2); // At least 2 chars for empty cells

		// Print grid
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int vertex = grid[y][x];
				String cell;
				if (vertex == -1) {
					cell = ".";
				} else {
					cell = String.valueOf(vertex);
				}
				// Right-align cells
				sb.append(String.format("%" + (maxDigits + 1) + "s", cell));
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	private java.util.List<Map<Integer, int[]>> tryVF3PAllSolutions(Graph<Integer, DefaultEdge> pattern, int w, int h, int[] fixedPair, boolean allSolutions) {
		

		java.util.List<Map<Integer, int[]>> solutions = new java.util.ArrayList<>();
		try {
			// Create temp files for pattern and target graphs
			java.io.File patternFile = java.io.File.createTempFile("pattern_", ".grf");
			java.io.File targetFile = java.io.File.createTempFile("target_", ".grf");

			// Export pattern graph (input graph) with mapping
			Map<Integer, Integer> patternToZeroIndexed = new HashMap<>();
			exportGraphToVF3Format(pattern, patternFile, patternToZeroIndexed);
			
			// Build and export grid graph
			Graph<Integer, DefaultEdge> host = buildGridGraph(w, h);
			Map<Integer, Integer> hostToZeroIndexed = new HashMap<>();
			exportGraphToVF3Format(host, targetFile, hostToZeroIndexed);

			// Use VF3L (lightweight sequential) with -s to get all mappings
			String vf3lPath = System.getProperty("user.home") + "/Programming/vf3lib/bin/vf3";
			
			ProcessBuilder pb = new ProcessBuilder(
				vf3lPath,
				patternFile.getAbsolutePath(),  // pattern graph (positional arg 1)
				targetFile.getAbsolutePath(),   // target graph (positional arg 2)
				allSolutions ? "-s" : "-F",
				"-u",  // undirected
				"-e",  // Use edge-induced (monomorphism)
				"-s"   // print solutions (no -F, get all solutions)
			);

			System.out.println(String.join(" ", pb.command()));
			
			pb.redirectErrorStream(true);
			Process process = pb.start();
			long startTime = System.currentTimeMillis();

			if (DELETE_FILES) {
			patternFile.deleteOnExit();
			targetFile.deleteOnExit();
			}

			// Capture output and parse all solutions
			try (java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					// Check timeout
					if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
						System.out.println("VF3L timeout reached (" + (TIMEOUT_MS/1000) + "s), stopping...");
						process.destroy();
						break;
					}
					
					if (line.startsWith("0,")) {
						// This is a solution line, parse it
						Map<Integer, int[]> result = parseSolution(line, pattern, patternToZeroIndexed, hostToZeroIndexed, w);
						if (result != null && validateEmbedding(pattern, result, fixedPair)) {
							solutions.add(result);
							if (solutions.size() % 100 == 0) {
								System.out.println("VF3L found " + solutions.size() + " valid solutions so far...");
							}
							
							// Check if we've reached the max solutions limit
							if (solutions.size() >= MAX_SOLUTIONS) {
								System.out.println("VF3L reached max solutions limit (" + MAX_SOLUTIONS + "), stopping...");
								process.destroy();
								break;
							}
						}
					}
				}
			}

			// Try to wait for process completion, but with timeout
			try {
				if (process.isAlive()) {
					process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
					if (process.isAlive()) {
						process.destroyForcibly();
					}
				}
			} catch (InterruptedException e) {
				process.destroyForcibly();
			}

			if (DELETE_FILES) {
			Files.deleteIfExists(patternFile.toPath());
			Files.deleteIfExists(targetFile.toPath());
			}

			System.out.println("VF3L found total of " + solutions.size() + " valid solutions!");
		} catch (Exception e) {
			System.err.println("VF3L error: " + e.getMessage());
			e.printStackTrace();
		}
		return solutions;
	}

	private Map<Integer, int[]> parseSolution(String solutionLine, Graph<Integer, DefaultEdge> pattern,
			Map<Integer, Integer> patternToZeroIndexed, Map<Integer, Integer> hostToZeroIndexed, int w) {
		// Solution format: "0,0:1,2:2,5:3,12:"
		// Each pair is patternNode,targetNode
		Map<Integer, int[]> result = new HashMap<>();
		
		// Create reverse mapping from 0-indexed back to original IDs
		Map<Integer, Integer> zeroIndexedToPattern = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : patternToZeroIndexed.entrySet()) {
			zeroIndexedToPattern.put(e.getValue(), e.getKey());
		}
		
		Map<Integer, Integer> zeroIndexedToHost = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : hostToZeroIndexed.entrySet()) {
			zeroIndexedToHost.put(e.getValue(), e.getKey());
		}
		
		String[] pairs = solutionLine.split(":");
		for (String pair : pairs) {
			if (pair.trim().isEmpty()) continue;
			String[] parts = pair.split(",");
			if (parts.length != 2) continue;
			
			int patternIdx = Integer.parseInt(parts[0].trim());
			int hostIdx = Integer.parseInt(parts[1].trim());
			
			Integer patternVertex = zeroIndexedToPattern.get(patternIdx);
			Integer hostVertex = zeroIndexedToHost.get(hostIdx);
			
			if (patternVertex != null && hostVertex != null) {
				int x = hostVertex % w;
				int y = hostVertex / w;
				result.put(patternVertex, new int[]{x, y});
			}
		}
		
		return result.size() == pattern.vertexSet().size() ? result : null;
	}

	private void exportGraphToVF3Format(Graph<Integer, DefaultEdge> g, java.io.File file, 
			Map<Integer, Integer> outMapping) throws java.io.IOException {
		// VF3 text format requires 0-indexed sequential node IDs:
		// Line 1: number of nodes
		// Lines 2..n+1: node_id node_attribute (node_id must be 0,1,2,...,n-1)
		// Then for each node: 
		//   - line with number of outgoing edges
		//   - lines with "source target" (one per edge)
		
		try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
			java.util.List<Integer> vertices = new java.util.ArrayList<>(g.vertexSet());
			java.util.Collections.sort(vertices);
			
			// Create mapping from original vertex IDs to 0-indexed IDs
			outMapping.clear();
			for (int i = 0; i < vertices.size(); i++) {
				outMapping.put(vertices.get(i), i);
			}
			
			pw.println(vertices.size());
			
			// Node attributes - use 0-indexed IDs
			for (int i = 0; i < vertices.size(); i++) {
				pw.println(i + " 1");
			}
			
			// Edges for each node (using 0-indexed IDs)
			for (int i = 0; i < vertices.size(); i++) {
				Integer v = vertices.get(i);
				Set<DefaultEdge> edges = g.edgesOf(v);
				java.util.List<Integer> neighbors = new java.util.ArrayList<>();
				for (DefaultEdge e : edges) {
					Integer other = Graphs.getOppositeVertex(g, e, v);
					//if (other > v) { // Only write each undirected edge once (from lower to higher ID)
						neighbors.add(outMapping.get(other));
					//}
				}
				
				pw.println(neighbors.size());
				for (Integer neighbor : neighbors) {
					pw.println(i + " " + neighbor);
				}

				if (false) { // DEBUG
					System.out.println(
						"EXPORT " + file.getName()
						+ " orig=" + v + " idx=" + i
						+ " deg=" + neighbors.size()
						+ " neighbors=" + neighbors
					);
				}
			}
		}
	}

	private Graph<Integer, DefaultEdge> toUndirected(Graph<Integer, DefaultEdge> g) {
		SimpleGraph<Integer, DefaultEdge> und = new SimpleGraph<>(DefaultEdge.class);
		for (Integer v : g.vertexSet()) und.addVertex(v);
		for (DefaultEdge e : g.edgeSet()) {
			Integer a = g.getEdgeSource(e);
			Integer b = g.getEdgeTarget(e);
			if (!und.containsEdge(a, b) && !und.containsEdge(b, a)) und.addEdge(a, b);
		}
		return und;
	}

	private Graph<Integer, DefaultEdge> buildGridGraph(int w, int h) {
		SimpleGraph<Integer, DefaultEdge> grid = new SimpleGraph<>(DefaultEdge.class);
		int n = w * h;
		for (int id = 0; id < n; id++) grid.addVertex(id);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int id = y * w + x;
				if (x + 1 < w) grid.addEdge(id, id + 1);
				if (y + 1 < h) grid.addEdge(id, id + w);
			}
		}
		return grid;
	}

	private boolean isBipartite(Graph<Integer, DefaultEdge> g) {
		Map<Integer, Integer> color = new HashMap<>();
		Queue<Integer> q = new ArrayDeque<>();
		for (Integer s : g.vertexSet()) {
			if (color.containsKey(s)) continue;
			color.put(s, 0);
			q.add(s);
			while (!q.isEmpty()) {
				Integer u = q.remove();
				for (DefaultEdge e : g.edgesOf(u)) {
					Integer v = Graphs.getOppositeVertex(g, e, u);
					Integer cv = color.get(v);
					if (cv == null) {
						color.put(v, 1 - color.get(u));
						q.add(v);
					} else if (cv.intValue() == color.get(u)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean validateEmbedding(Graph<Integer, DefaultEdge> g, Map<Integer, int[]> placement, int[] fixedPair) {
		if (DONT_VALIDATE) return true;
		for (DefaultEdge e : g.edgeSet()) {
			Integer a = g.getEdgeSource(e);
			Integer b = g.getEdgeTarget(e);
			int[] pa = placement.get(a);
			int[] pb = placement.get(b);
			if (pa == null || pb == null) return false;
			int dist = Math.abs(pa[0] - pb[0]) + Math.abs(pa[1] - pb[1]);
			if (dist != 1) return false;
		}

		// this helps a tiny bit but it would be better to check reflection and rotation vs. other solutions
		// check that fixed pair is in a correct order
		int[] fixed = placement.get(fixedPair[0]);
		int[] fixed2 = placement.get(fixedPair[1]);
		if (fixed[0] > fixed2[0] || fixed[1] > fixed2[1]) {
			return false;
		}

		return true;
	}
}