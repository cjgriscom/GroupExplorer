package io.chandler.gap;

import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

public class BubbloidSearchCrammed {
	private static int[][] vertexEdges = Cube.vertexEdges; // 1x1x1 vertex edges
	private static int[][] edgeVertices = Cube.edgeVertices;

	// Now we need to know which pairs of 4 vertices are bisected by each plane


	// Faces 1-3 represent X, Y, Z planes
	private static int[] verticesXPositive = Cube.getVerticesOfFaceClockwise(1);
	private static int[] verticesXNegative = Cube.getVerticesOfFaceClockwise(6);
	private static int[] verticesYPositive = Cube.getVerticesOfFaceClockwise(2);
	private static int[] verticesYNegative = Cube.getVerticesOfFaceClockwise(4);
	private static int[] verticesZPositive = Cube.getVerticesOfFaceClockwise(3);
	private static int[] verticesZNegative = Cube.getVerticesOfFaceClockwise(5);


	private static char checkEdgePlane(int cubeEdge, int[] polarityOut) {
		// Determine the plane of the edge
		int[] vertices = edgeVertices[cubeEdge];
		if (contains(verticesXPositive, vertices[0]) && contains(verticesXNegative, vertices[1])) { // Negative X
			// This edge is bisected by the X plane
			polarityOut[0] = -1;
			return 'x';
		} else if (contains(verticesXPositive, vertices[1]) && contains(verticesXNegative, vertices[0])) { // Positive X
			// This edge is bisected by the X plane
			polarityOut[0] = 1;
			return 'x';
		} else if (contains(verticesYPositive, vertices[0]) && contains(verticesYNegative, vertices[1])) { // Negative Y
			// This edge is bisected by the Y plane
			polarityOut[0] = -1;
			return 'y';
		} else if (contains(verticesYPositive, vertices[1]) && contains(verticesYNegative, vertices[0])) { // Positive Y
			// This edge is bisected by the Y plane
			polarityOut[0] = 1;
			return 'y';
		} else if (contains(verticesZPositive, vertices[0]) && contains(verticesZNegative, vertices[1])) { // Negative Z
			// This edge is bisected by the Z plane
			polarityOut[0] = -1;
			return 'z';
		} else if (contains(verticesZPositive, vertices[1]) && contains(verticesZNegative, vertices[0])) { // Positive Z
			// This edge is bisected by the Z plane
			polarityOut[0] = 1;
			return 'z';
		}
		throw new RuntimeException("Edge " + cubeEdge + " does not bisect any plane");
	}

	public static int[][][] generateAxes(int xDepth, int yDepth, int zDepth, int reduceDepth) {
		int totalEdges = (xDepth + yDepth + zDepth) * 4;
		int maxVertexDepth = Math.min(xDepth, Math.min(yDepth, zDepth)) - reduceDepth;

		// Now construct the edge indices
		int newIndex = 1;
		int[][] newEdgeIndices = new int[totalEdges][];

		for (int i = 0; i < 12; i++) {
			// Determine the plane of the edge
			int originalEdge = i;
			int[] polarityOut = new int[1];
			char plane = checkEdgePlane(originalEdge, polarityOut);
			if (plane == 'x') {
				newEdgeIndices[i] = new int[xDepth];
			} else if (plane == 'y') {
				newEdgeIndices[i] = new int[yDepth];
			} else if (plane == 'z') {
				newEdgeIndices[i] = new int[zDepth];
			}

			if (polarityOut[0] == -1) {
				for (int j = 0; j < newEdgeIndices[i].length; j++) newEdgeIndices[i][j] = newIndex++;
			} else {
				for (int j = 0; j < newEdgeIndices[i].length; j++) newEdgeIndices[i][newEdgeIndices[i].length - j - 1] = newIndex++;
			}
		}
		
		// Construct the turning axes
		int[][][] axes = new int[8][maxVertexDepth][3];
		for (int i = 0; i < 8; i++) {
			// Get the clockwise edges of the vertex
			int[] edges = vertexEdges[i];
			// For each edge
			int k = 0;
			for (int cubeEdge : edges) {
				cubeEdge--; // Source is 1-indexed

				// Get the polarity of the vertex on that edge plane
				char edgePlane = checkEdgePlane(cubeEdge, new int[1]);
				int polarity = 0;
				if (edgePlane == 'x') {
					if (contains(verticesXPositive, i+1)) polarity = 1;
					else if (contains(verticesXNegative, i+1)) polarity = -1;
					else throw new RuntimeException("Vertex " + i + " is not on the X plane");
				} else if (edgePlane == 'y') {
					if (contains(verticesYPositive, i+1)) polarity = 1;
					else if (contains(verticesYNegative, i+1)) polarity = -1;
					else throw new RuntimeException("Vertex " + i + " is not on the Y plane");
				} else if (edgePlane == 'z') {
					if (contains(verticesZPositive, i+1)) polarity = 1;
					else if (contains(verticesZNegative, i+1)) polarity = -1;
					else throw new RuntimeException("Vertex " + i + " is not on the Z plane");
				}

				if (polarity == -1) {
					for (int j = 0; j < maxVertexDepth; j++) axes[i][j][k] = newEdgeIndices[cubeEdge][j];
				} else {
					for (int j = 0; j < maxVertexDepth; j++) axes[i][j][k] = newEdgeIndices[cubeEdge][newEdgeIndices[cubeEdge].length - j - 1];
				}
				k++;
			}
		}
		return axes;
	}

	public static void main(String[] args) throws Exception {
		GapInterface gap = new GapInterface();

		int xDepth = 2;
		int yDepth = 2;
		int zDepth = 2;

		int reduceDepth = 0;

		int totalEdges = (xDepth + yDepth + zDepth) * 4;
		int maxVertexDepth = Math.min(xDepth, Math.min(yDepth, zDepth)) - reduceDepth;
		
		int[][][] axes = generateAxes(xDepth, yDepth, zDepth, reduceDepth);

		System.out.println("Generating axes for " + xDepth + "x" + yDepth + "x" + zDepth);
		System.out.println("Total edges: " + totalEdges);
		System.out.println("Max vertex depth: " + maxVertexDepth);
		System.out.println("Axes:");
		//System.out.println(GroupExplorer.generatorsToString(axes));

		System.out.println("Merge 1 and 2");
		for (int[][] x : axes) {
			for (int[] y : x) {
				for (int zi = 0; zi < y.length; zi++) {
					if (y[zi] == 2) y[zi] = 1;
				}
			}
		}
		int off = 11;
		System.out.println("Merge "+(2+off*2)+" and "+(1+off*2));
		for (int[][] x : axes) {
			for (int[] y : x) {
				for (int zi = 0; zi < y.length; zi++) {
					if (y[zi] == 2+off*2) y[zi] = 1+off*2;
				}
			}
		}

		int[] axisChoicesA = {1, 3, 5, 7};
		int[] axisChoicesB = {2, 4, 6, 8};


		int axesInGroupA = 3, axesInGroupB = 3;

		System.out.println("Searching for groups with " + axesInGroupA + " axes in group A and " + axesInGroupB + " axes in group B");

		for (int[] groupA : Permu.generateCombinations(4 * maxVertexDepth, axesInGroupA * maxVertexDepth)) {
			next:for (int[] groupB : Permu.generateCombinations(4 * maxVertexDepth, axesInGroupB * maxVertexDepth)) {

				for (int bitmaskA = 0; bitmaskA < (1 << (4-1)); bitmaskA++) {
					for (int bitmaskB = 0; bitmaskB < (1 << (4-1)); bitmaskB++) {
						int[][][] generator = new int[2][][];
						generator[0] = new int[axesInGroupA*maxVertexDepth][3];
						int ai = 0;
						for (int a = 0; a < groupA.length; a++) {
							int subIndex = groupA[a] / maxVertexDepth;
							int axisIndex = axisChoicesA[subIndex]-1;
							int axisDepth = groupA[a] % maxVertexDepth;
							generator[0][ai] = axes[axisIndex][axisDepth].clone();
							// Reverse if bitmask is set
							if ((bitmaskA & (1 << subIndex)) != 0) {
								generator[0][ai] = CycleInverter.invertArray(generator[0][ai]);
							}
							ai++;
						}
						generator[1] = new int[axesInGroupB*maxVertexDepth][3];
						int bi = 0;
						for (int b = 0; b < groupB.length; b++) {
							int subIndex = groupB[b] / maxVertexDepth;
							int axisIndex = axisChoicesB[subIndex]-1;
							int axisDepth = groupB[b] % maxVertexDepth;
							generator[1][bi] = axes[axisIndex][axisDepth].clone();
							// Reverse if bitmask is set
							if ((bitmaskB & (1 << subIndex)) != 0) {
								generator[1][bi] = CycleInverter.invertArray(generator[1][bi]);
							}
							bi++;
						}

						// Ensure disjoint
						for (int[][] x : generator) {
							int[] used = new int[100];
							for (int[] y : x) {
								for (int z : y) used[z]++;
							}
							for (int i = 1; i < used.length; i++) {
								if (used[i] > 1) continue next;
							}
						}

						Graph<Integer, DefaultEdge> graph = buildGraphFromLine(GroupExplorer.generatorsToString(generator));
						// Check if graph is connected
						ConnectivityInspector<Integer, DefaultEdge> inspector = new ConnectivityInspector<>(graph);
						if (!inspector.isConnected()) continue next;
						

						List<String> results = gap.runGapCommands(GroupExplorer.generatorsToString(generator), 3);
						String description = results.get(2);

						if (!description.contains("A")) {
							System.out.println("Not an A group");
							Thread.sleep(1000);
						}
						String size = results.get(1);
						System.out.println(String.format("%s [%s] %s - %s",
								description,
								size,
								"directions A" + Integer.toBinaryString(bitmaskA<<1) + " directions B" + Integer.toBinaryString(bitmaskB<<1),
								GroupExplorer.generatorsToString(generator)));

					}
				}
			}
		}

	}

	private static Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices first and update vertex frequency count.
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                // Add edges to form a complete cycle and update edge frequency count.
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    graph.addEdge(a, b);
                }
            }
        }
        return graph;
    }

	private static boolean contains(int[] array, int value) {
		for (int i = 0; i < array.length; i++) {
			if (array[i] == value) {
				return true;
			}
		}
		return false;
	}
}
