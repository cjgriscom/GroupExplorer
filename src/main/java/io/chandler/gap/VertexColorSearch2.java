package io.chandler.gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class VertexColorSearch2 {

	public static VertexColorSearch2 pentagonalIcositrahedron_3D_180() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(1);
			ge.applyOperation(1);
			accept.run();
			ge.applyOperation(0);
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			3, 8, new Generator(GroupExplorer.parseOperationsArr(CubicGenerators.cubicPISymmetries3)), patternFacesAboutSymm,
			24, PentagonalIcositrahedron::getFacesFromVertex, PentagonalIcositrahedron::getMatchingVertexFromFaces);
	}
	public static VertexColorSearch2 pentagonalIcositrahedron_3D_180_32Vertices() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(1);
			ge.applyOperation(1);
			accept.run();
			ge.applyOperation(0);
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			4, 8, new Generator(GroupExplorer.parseOperationsArr(CubicGenerators.cubicPISymmetries3)), patternFacesAboutSymm,
			32, PentagonalIcositrahedron::getFacesFromVertex, PentagonalIcositrahedron::getMatchingVertexFromFaces);
	}

	public static VertexColorSearch2 pentagonalHexecontahedron_3D_180_60vertices() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(1);
			accept.run();
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			4, 15, new Generator(PHGenerators.get3D_180Symm()), patternFacesAboutSymm,
			60, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);
	}

	public static VertexColorSearch2 pentagonalHexecontahedron_3D_180_80vertices() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(1);
			accept.run();
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			4, 20, new Generator(PHGenerators.get3D_180Symm()), patternFacesAboutSymm,
			80, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);
	}


	public static VertexColorSearch2 pentagonalHexecontahedron_Threefold_60vertices() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			3, 20, new Generator(GroupExplorer.parseOperationsArr(PHGenerators.triPHSymmetryF1)), patternFacesAboutSymm,
			60, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);
	}
	public static VertexColorSearch2 pentagonalHexecontahedron_Threefold_80vertices() {
		BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm = (ge, accept) -> {
			ge.resetElements(true);
			accept.run();
			ge.applyOperation(0);
			accept.run();
			ge.applyOperation(0);
			accept.run();
		};
		return new VertexColorSearch2(
			3, 20, new Generator(GroupExplorer.parseOperationsArr(PHGenerators.triPHSymmetryF1)), patternFacesAboutSymm,
			80, PentagonalHexecontahedron::getFacesFromVertex, PentagonalHexecontahedron::getMatchingVertexFromFaces);
	}

    public static void main(String[] args) {
        VertexColorSearch2 vcs = VertexColorSearch2.pentagonalHexecontahedron_3D_180_60vertices();
		System.out.println(Arrays.toString(vcs.patternVerticesAboutSymm(1)));

		
    }
    
	final Generator symmGenerator;

    final int order;
    final int vertexCount;
    final int selections;

	final Function<Integer, int[]> getFacesFromVertex;
	final Function<int[], Integer> getMatchingVertexFromFaces;

	final BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm;

	final int[][][] symmArr;
    final String symm;
    final ThreadLocal<GroupExplorer> ge;

    final int[][]collidingVertices;

	private final int[][] patternVerticesCache;
	
	public VertexColorSearch2(int symmOrder, int selections, Generator symmGenerator, BiConsumer<GroupExplorer, Runnable> patternFacesAboutSymm, int totalVertexCount, Function<Integer, int[]> getFacesFromVertex, Function<int[], Integer> getMatchingVertexFromFaces) {
		this.order = symmOrder;
		this.symmGenerator = symmGenerator;
		this.patternFacesAboutSymm = patternFacesAboutSymm;
		this.vertexCount = totalVertexCount;
		this.getFacesFromVertex = getFacesFromVertex;
		this.getMatchingVertexFromFaces = getMatchingVertexFromFaces;
		this.selections = selections;

		this.symmArr = this.symmGenerator.generator();
		this.symm = GroupExplorer.generatorsToString(symmArr);
		this.ge = ThreadLocal.withInitial(() -> new GroupExplorer(symm, MemorySettings.FASTEST, new HashSet<>()));

		collidingVertices = generateCollidingVertices();
		patternVerticesCache = new int[vertexCount + 1][];
	}

	// TODO make this configurable
    public int[][] patternFacesAboutSymm(int[] faceIndices) {
        int[][] facesPatterned = new int[order][faceIndices.length];
		int[] s = new int[1];
		GroupExplorer ge = this.ge.get();
		patternFacesAboutSymm.accept(ge, () -> {
			for (int i = 0; i < faceIndices.length; i++) facesPatterned[s[0]][i] = ge.elements[faceIndices[i] - 1];
			s[0]++;
		});

		if (s[0] != order) throw new RuntimeException("Patterned faces not equal to order");
        
		return facesPatterned;
    }

	// Second generator is without the symm group
	public void forEachGeneratorWithInversions(BiConsumer<Generator, Generator> consumer) {
		for (List<Integer> selection : generateAllSelections()) {
			int[][] cycles = new int[selection.size()][];
			for (int i = 0; i < selection.size(); i++) {
				int[] faces = getFacesFromVertex.apply(selection.get(i));
				cycles[i] = faces;
			}

			boolean[][] fixedCycleIndices = new boolean[1][cycles.length];
			fixedCycleIndices[0][0] = true;

			List<int[][][]> invCyclesList = CycleInverter.generateInvertedCycles(fixedCycleIndices, new int[][][] {cycles});
			System.out.println(cycles.length + " " + invCyclesList.size());
			for (int[][][] cyclesInverted : invCyclesList) {
				consumer.accept(Generator.combine(new Generator(symmArr), new Generator(cyclesInverted)), new Generator(cyclesInverted));
			}

		}
	}

	private boolean verifyAllSelectionsCoverage() {
		for (List<Integer> selection : allSelections) {
			HashSet<Integer> totalVertices = new HashSet<>();
			for (int vertex : selection) {
				for (int i : patternVerticesAboutSymm(vertex)) totalVertices.add(i);
			}
			if (totalVertices.size() != vertexCount) return false;
		}
		return true;
	}

    private List<List<Integer>> allSelections = new ArrayList<>();

	public List<List<Integer>> generateAllSelections() {
		if (allSelections.size() > 0) return allSelections;
        List<Integer> availableVertices = new ArrayList<>();
        for (int i = 1; i <= vertexCount; i++) {
			// Pattern the vertex about symmetry and if its ID changes, add it
			int[] pattern = patternVerticesAboutSymm(i);
			HashSet<Integer> patternSet = new HashSet<>();
			for (int j = 0; j < pattern.length; j++) {
				patternSet.add(pattern[j]);
			}
			if (patternSet.size() == pattern.length) availableVertices.add(i);
        }
		System.out.println("Available vertices: " + availableVertices.size());
        generateSelectionsRecursive(new ArrayList<>(), availableVertices, selections);
        return allSelections;
    }

	public void filterOutIdenticalGenerators() {
		HashMap<String, List<Integer>> uniqueGenerators = new HashMap<>();
		for (List<Integer> selection : allSelections) {
			int[] selectionArr = selection.stream().mapToInt(Integer::intValue).toArray();
			int[][] selectionArrPatterned = new int[order][selectionArr.length];
			for (int i = 0; i < selectionArr.length; i++) {
				int[] pattern = patternVerticesAboutSymm(selectionArr[i]);
				for (int j = 0; j < pattern.length; j++) {
					selectionArrPatterned[j][i] = pattern[j];
				}
			}
			boolean isUnique = true;
			for (int[] patterned : selectionArrPatterned) {
				Arrays.sort(patterned);
				if (uniqueGenerators.containsKey(Arrays.toString(patterned))) {
					isUnique = false;
				}
			}
			if (isUnique) {
				uniqueGenerators.put(Arrays.toString(selectionArr), selection);
			}
		}
		allSelections = new ArrayList<>(uniqueGenerators.values());
	}

	long startTime = System.currentTimeMillis();
	long lastPrintTime = System.currentTimeMillis();
    private void generateSelectionsRecursive(List<Integer> currentSelection, List<Integer> availableVertices, int remainingSelections) {
		if (remainingSelections == 0) {
            allSelections.add(new ArrayList<>(currentSelection));
			if (System.currentTimeMillis() - lastPrintTime > 1000) {
				System.out.println(allSelections.size() + " " + (System.currentTimeMillis() - startTime) / 1000. + "s");
				lastPrintTime = System.currentTimeMillis();
			}
            return;
        }

        for (int i = 0; i < availableVertices.size(); i++) {
            int vertex = availableVertices.get(i);
            currentSelection.add(vertex);
            
            List<Integer> newAvailableVertices = new ArrayList<>(availableVertices.subList(i + 1, availableVertices.size()));
            // Remove vertices patterned about symmetry
            int[] patternedVertices = patternVerticesAboutSymm(vertex);
            for (int patternedVertex : patternedVertices) {
                newAvailableVertices.remove(Integer.valueOf(patternedVertex));
            }

            // Remove colliding vertices
            int[] collidingVertices = getCollidingVertices(vertex);
            for (int collidingVertex : collidingVertices) {
                newAvailableVertices.remove(Integer.valueOf(collidingVertex));
            }

			if (currentSelection.size() == 20) {
			System.out.println("Current selection: " + currentSelection.size() + " new available vertices: " + newAvailableVertices.size());
			}

            generateSelectionsRecursive(currentSelection, newAvailableVertices, remainingSelections - 1);
            currentSelection.remove(currentSelection.size() - 1);
        }
		if (currentSelection.size() <= minDepth) {
			System.out.println("Finshed depth " + currentSelection.size());
			minDepth = currentSelection.size();
		}
    }
	int minDepth = 20;

    private int[] getCollidingVertices(int vertex) {
        return this.collidingVertices[vertex];
    }

    private int[] patternVerticesAboutSymm(int vertexIndex) {
		if (patternVerticesCache[vertexIndex] != null) return patternVerticesCache[vertexIndex];
        int[] verticesPatterned = new int[order];
        int[] faces = getFacesFromVertex.apply(vertexIndex);
        int[][] facesPatterned = patternFacesAboutSymm(faces);
        for (int j = 0; j < facesPatterned.length; j++) {
            verticesPatterned[j] = getMatchingVertexFromFaces.apply(facesPatterned[j]);
        }
		patternVerticesCache[vertexIndex] = verticesPatterned;
        return verticesPatterned;
    }


    private int[][] generateCollidingVertices() {
		HashMap<Integer, int[]> result = new HashMap<>();	
        for (int v = 1; v <= vertexCount; v++) {
            HashSet<Integer> collidingVertices = new HashSet<>();
            int[] faces = getFacesFromVertex.apply(v);
            for (int v2 = 1; v2 <= vertexCount; v2++) {
                if (v2 == v) continue;
                int[] faces2 = getFacesFromVertex.apply(v2);
                for (int i = 0; i < faces2.length; i++) {
                    for (int j = 0; j < faces.length; j++) {
                        if (faces[j] == faces2[i]) {
                            collidingVertices.add(v2);
                        }
                    }
                }
            }
            result.put(v, collidingVertices.stream().mapToInt(Integer::intValue).toArray());
        }

		// Convert result to an array
		int[][] resultArray = new int[result.size() + 1][];
		int i = 1;
		for (int[] vertices : result.values()) {
			resultArray[i++] = vertices;
		}
		return resultArray;
    }
}
