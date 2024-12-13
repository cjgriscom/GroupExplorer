package io.chandler.gap;

import java.util.Arrays;
import java.util.HashMap;

public class PentagonalHexecontahedron {
	static final int[][] phfaceToDodecahedronFaceVertex = new int[60][];

	// Faces listed in clockwise order
	static final int[][] phverticesToPhfaces = new int[60+20][];


	private static final HashMap<Integer, Integer> faceIVertexIToPhfaceI = new HashMap<>();
	private static int getPhfaceBetweenDodecahedronFaceAndVertex(int face, int vertex) {
		return faceIVertexIToPhfaceI.get(vertex*100 + face) + 1;
	}

	static {
		// Generate pentagonal icositrahedron geometries
		int phface = 0;
		for (int vertexI = 0; vertexI < 20; vertexI++) {
			phverticesToPhfaces[60 + vertexI] = new int[3];
			for (int faceI = 0; faceI < 3; faceI++) { // 3 faces per vertex
				int vertex = vertexI + 1;
				int face = Dodecahedron.vertexFaces[vertexI][faceI];
				//System.out.println(face + " " + vertex + " " + phface);
				faceIVertexIToPhfaceI.put(vertex*100 + face, phface);
				phfaceToDodecahedronFaceVertex[phface] = new int[]{face, vertex};
				phverticesToPhfaces[60 + vertexI][faceI] = phface + 1;
				phface++;
			}
		}

		// One ph vertex per face
		for (phface = 0; phface < 60; phface++) {
			int face = phfaceToDodecahedronFaceVertex[phface][0];
			int vertex = phfaceToDodecahedronFaceVertex[phface][1];

			// Get the dodec face clockwise
			int dodecfaceright = Dodecahedron.getClockwiseFace(vertex, face);
			int dodecvertexleft = Dodecahedron.getCounterclockwiseVertex(face, vertex);

			phverticesToPhfaces[phface] = new int[] {
				phface + 1,
				getPhfaceBetweenDodecahedronFaceAndVertex(face, dodecvertexleft),
				getPhfaceBetweenDodecahedronFaceAndVertex(dodecfaceright, vertex)
			};
		}


	}

	public static int[] getFacesFromVertex(int vertex) {
		return phverticesToPhfaces[vertex - 1];
	}

	public static void main(String[] args) {

		for (int i = 1; i <= 20; i++) {
			System.out.println("V" + i + " F" + Arrays.toString(Dodecahedron.vertexFaces[i-1]));
		}

		for (int i = 1; i <= 60; i++) {
			System.out.println(i + ": V" + phfaceToDodecahedronFaceVertex[i-1][1] + " F" + phfaceToDodecahedronFaceVertex[i-1][0]);
		}



		// Label generator

		String[] gens = new String[] {
			"[(4,1,5)(9,22,8)(12,2,10)(15,24,14)(18,3,16)(21,23,20)]",
			"[(1,10,2)(6,19,5)(9,11,7)(13,12,14)(18,20,17)(23,21,22)]",
			"[(2,16,3)(4,8,6)(11,7,10)(15,17,13)(19,18,20)(22,9,24)]",
			"[(3,5,1)(7,4,8)(12,14,11)(17,13,16)(21,6,19)(24,15,23)]"
		};

		for (String gen : gens) {
			printVertexGeneratorNotations(GroupExplorer.parseOperationsArr(gen));
		}

		
	}
	public static void printVertexGeneratorNotations(int[][][] g0) {
		// Each inner array corresponds to something in getFacesFromVertex(1 - 80)

		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		for (int a = 0; a < g0.length; a++) {
			if (a != 0) sb.append(",");
			sb.append("(");
			boolean first = true;
			for (int b = 0; b < g0[a].length; b++) {
				int[] cycle = g0[a][b].clone();
				
				int vert = getMatchingVertexFromFaces(cycle);
				
				if (!first) sb.append(",");
				first = false;
				if (vert == 0) {
					//throw new RuntimeException("No match found for " + Arrays.toString(cycle));
					sb.append(Arrays.toString(cycle));
				} else if (vert > 0) {
					sb.append("V").append(vert).append("R");
				} else {
					sb.append("V").append(-vert).append("L"); 
				}
			}
			sb.append(")");
		}

		sb.append("]");
		System.out.println(sb.toString());
	}

	public static int getMatchingVertexFromFaces(int[] src) {
		for (int i = 1; i <= 60+20; i++) {
			int[] faces = getFacesFromVertex(i).clone();
			
			for (int j = 0; j < faces.length; j++) {
				if (Arrays.equals(faces, src)) return i;
				ArrayRotator.rotateRight(faces);
			}

			faces = CycleInverter.invertArray(faces);
			for (int j = 0; j < faces.length; j++) {
				if (Arrays.equals(faces, src)) return -i;
				ArrayRotator.rotateRight(faces);
			}
			
		}
		return 0;
	}
}
