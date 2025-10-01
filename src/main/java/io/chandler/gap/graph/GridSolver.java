package io.chandler.gap.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;

public class GridSolver {
	private final Graph<Integer, DefaultEdge> graph;
	private final Map<Integer, int[]> coords = new HashMap<>();
	private final Set<Long> occupied = new HashSet<>();

	public static void main(String[] args) {
		String gen = "[(1,24)(2,15)(4,16)(6,11)(7,13)(8,18)(9,27)(10,19)(17,21)(23,25),(1,18)(2,16)(3,17)(6,21)(7,14)(8,26)(9,10)(13,25)(15,22)(20,27),(5,19)(8,22)(11,23)(13,20)(15,26)(25,27),(1,25)(5,12)(7,8)(13,18)(14,26)(23,24)]";
		int[][][] genOps = GroupExplorer.parseOperationsArr(gen);
		Graph<Integer, DefaultEdge> graph = PlanarStudyRepeated.buildGraphFromCombinedGen(genOps, false);
		GridSolver solver = new GridSolver(graph);
		Map<Integer, int[]> coords = solver.solve();
		
		System.out.println(coords);

	}

	public GridSolver(Graph<Integer, DefaultEdge> graph) {
		this.graph = graph;
	}

	public Map<Integer, int[]> solve() {
        if (!graph.containsVertex(1)) {
            throw new RuntimeException("Vertex 1 not present in graph.");
        }
		place(1, 0, 0);
		if (backtrack()) {
			return coords;
		}
		throw new RuntimeException("Failed to place all vertices on a grid with adjacent edges.");
	}

	public static Map<Integer, int[]> solve(Graph<Integer, DefaultEdge> graph) {
		return new GridSolver(graph).solve();
	}

	private boolean backtrack() {
		if (coords.size() == graph.vertexSet().size()) {
			return validateAllEdges();
		}

		Integer v = nextUnplacedWithPlacedNeighbor();
		if (v == null) {
			// No frontier vertex found; graph assumed connected, so this is failure
			return false;
		}

		// Order candidates by minimal bounding-box area among N,S,E,W relative to the first placed neighbor
		Integer anchor = firstPlacedNeighbor(v);
		if (anchor == null) return false; // should not happen
		int[] ap = coords.get(anchor);
		int[][] order = new int[][] {
			{ap[0], ap[1] - 1}, // N
			{ap[0], ap[1] + 1}, // S
			{ap[0] + 1, ap[1]}, // E
			{ap[0] - 1, ap[1]}  // W
		};

		long[] areas = new long[order.length];
		for (int i = 0; i < order.length; i++) {
			areas[i] = boundingAreaWith(order[i][0], order[i][1]);
		}
		// Stable sort by area (small n=4); tie-breaker preserves N,S,E,W order
		for (int i = 0; i < order.length; i++) {
			for (int j = i + 1; j < order.length; j++) {
				if (areas[j] < areas[i]) {
					long tmpA = areas[i]; areas[i] = areas[j]; areas[j] = tmpA;
					int[] tmp = order[i]; order[i] = order[j]; order[j] = tmp;
				}
			}
		}

		for (int[] p : order) {
			int x = p[0], y = p[1];
			if (isOccupied(x, y)) continue;
			if (!fitsAllPlacedNeighbors(v, x, y)) continue;

			place(v, x, y);
			if (backtrack()) return true;
			unplace(v, x, y);
		}

		return false;
	}

	private long boundingAreaWith(int x, int y) {
		int minX = x, maxX = x, minY = y, maxY = y;
		for (int[] p : coords.values()) {
			if (p[0] < minX) minX = p[0];
			if (p[0] > maxX) maxX = p[0];
			if (p[1] < minY) minY = p[1];
			if (p[1] > maxY) maxY = p[1];
		}
		long width = (long)maxX - (long)minX + 1L;
		long height = (long)maxY - (long)minY + 1L;
		return width * height;
	}

	private Integer nextUnplacedWithPlacedNeighbor() {
		for (Integer v : graph.vertexSet()) {
			if (!coords.containsKey(v)) {
				for (Integer u : Graphs.neighborListOf(graph, v)) {
					if (coords.containsKey(u)) return v;
				}
			}
		}
		return null;
	}

	private Integer firstPlacedNeighbor(Integer v) {
		for (Integer u : Graphs.neighborListOf(graph, v)) {
			if (coords.containsKey(u)) return u;
		}
		return null;
	}

	private boolean fitsAllPlacedNeighbors(Integer v, int x, int y) {
		for (Integer u : Graphs.neighborListOf(graph, v)) {
			if (coords.containsKey(u)) {
				int[] up = coords.get(u);
				if (manhattan(up[0], up[1], x, y) != 1) return false;
			}
		}
		return true;
	}

	private boolean validateAllEdges() {
		for (DefaultEdge e : graph.edgeSet()) {
			Integer a = graph.getEdgeSource(e);
			Integer b = graph.getEdgeTarget(e);
			int[] pa = coords.get(a);
			int[] pb = coords.get(b);
			if (pa == null || pb == null) return false;
			if (manhattan(pa[0], pa[1], pb[0], pb[1]) != 1) return false;
		}
		return true;
	}

	private int manhattan(int x1, int y1, int x2, int y2) {
		return Math.abs(x1 - x2) + Math.abs(y1 - y2);
	}

	private void place(Integer v, int x, int y) {
		coords.put(v, new int[]{x, y});
		occupied.add(key(x, y));
	}

	private void unplace(Integer v, int x, int y) {
		coords.remove(v);
		occupied.remove(key(x, y));
	}

	private boolean isOccupied(int x, int y) {
		return occupied.contains(key(x, y));
	}

	private long key(int x, int y) {
		return (((long) x) << 32) ^ (y & 0xffffffffL);
	}
}