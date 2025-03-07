package io.chandler.gap;

import java.util.Arrays;

public class Dodecahedron {
    private static final int[][] faceSymmetries = {
        /*1*/ {2,12,5,8,9},
        /*2*/ {1,9,10,3,12},
        /*3*/ {2,10,6,4,12},
        /*4*/ {7,5,12,3,6},
        /*5*/ {4,7,8,1,12},
        /*6*/ {7,4,3,10,11},
        /*7*/ {5,4,6,11,8},
        /*8*/ {1,5,7,11,9},
        /*9*/ {1,8,11,10,2},
        /*10*/ {2,9,11,6,3},
        /*11*/ {9,8,7,6,10},
        /*12*/ {1,2,3,4,5},
    };

    private static int vertexCount = 0;
    private static int edgeCount = 0;
    
    public static final int[][] vertexFaces = new int[20][3];
    public static int[][] edgeVertices = new int[30][2];
    public static final int[][] vertexEdges = new int[20][3];

    static {
        initializeVertexEdges();
        mapVertices();
        mapEdges();
        reorderVertexEdges();
    }

    public static void main(String[] args) {
        printVertexFaces();
        printEdges();
        printVertexEdges();

        int[][] symmetries = getEdgeSymmetriesAlongVertexAxis(1);
        for (int[] symmetry : symmetries) {
            System.out.println(Arrays.toString(symmetry));
        }
    }

    public static int getCounterclockwiseVertex(int face, int vertex) {
        // Find the index of the given face in the vertex's faces
        int faceIndex = -1;
        for (int i = 0; i < 3; i++) {
            if (vertexFaces[vertex - 1][i] == face) {
                faceIndex = i;
                break;
            }
        }
        
        if (faceIndex == -1) {
            throw new IllegalArgumentException("The given vertex is not on the given face");
        }
        
        // Get the next face in clockwise order
        int nextFace = vertexFaces[vertex - 1][(faceIndex + 1) % 3];
        
        // Find the vertex that shares both the current face and the next face
        for (int i = 0; i < vertexCount; i++) {
            if (i + 1 != vertex && // Skip the current vertex
                containsFace(vertexFaces[i], face) && 
                containsFace(vertexFaces[i], nextFace)) {
                return i + 1; // Return the vertex index (1-based)
            }
        }
        
        // This should never happen in a valid dodecahedron
        throw new IllegalStateException("Could not find the clockwise vertex");
    }

    public static int getClockwiseFace(int vertex, int face) {
        // Find index of face in vertexFaces
        int index = 0;
        for (int i = 0; i < 3; i++) {
            if (vertexFaces[vertex - 1][i] == face) {
                index = i;
                break;
            }
        }
        index++; // Clockwise face's index
        index %= 3;
        return vertexFaces[vertex - 1][index];
    }

    private static int getOpposingVertex(int vertex, int edge) {
        int vertex0_0 = edgeVertices[edge-1][0];
        int vertex0_1 = edgeVertices[edge-1][1];
        int vertex0 = vertex0_0 == vertex ? vertex0_1 : vertex0_0;
        return vertex0;
    }

    public static int getClockwiseEdge(int vertex, int edge) {
        // Find index of edge in vertexEdges
        int index = 0;
        for (int i = 0; i < 3; i++) {
            if (vertexEdges[vertex - 1][i] == edge) {
                index = i;
                break;
            }
        }
        index++; // Clockwise edge's index
        index %= 3;
        return vertexEdges[vertex - 1][index];
    }

    public static int getCounterclockwiseEdge(int vertex, int edge) {
        // Find index of edge in vertexEdges
        int index = 0;
        for (int i = 0; i < 3; i++) {
            if (vertexEdges[vertex - 1][i] == edge) {
                index = i;
                break;
            }
        }
        index += 2; // Counterclockwise edge's index
        index %= 3;
        return vertexEdges[vertex - 1][index];
    }

    public static int[] getVerticesOfFaceClockwise(int face) {
        // We'll collect the vertices of the face in clockwise order
        int[] vertices = new int[5];
        
        // Find one vertex that belongs to the face
        int startVertex = -1;
        for (int vertex = 1; vertex <= 20; vertex++) {
            if (containsFace(vertexFaces[vertex - 1], face)) {
                startVertex = vertex;
                break;
            }
        }
        
        if (startVertex == -1) {
            throw new IllegalArgumentException("Face " + face + " does not exist.");
        }
        
        // Traverse the face to get all vertices
        vertices[0] = startVertex;
        for (int i = 1; i < 5; i++) {
            vertices[i] = getCounterclockwiseVertex(face, vertices[i - 1]);
        }
        
        // Verify that we have returned to the starting vertex after a full cycle
        int nextVertex = getCounterclockwiseVertex(face, vertices[4]);
        if (nextVertex != startVertex) {
            throw new IllegalStateException("Vertices do not form a closed loop on face " + face);
        }

        // Reverse the array to make it clockwise
        vertices = CycleInverter.invertArray(vertices);
        
        return vertices;
    }

    public static int[][] getEdgeSymmetriesAlongVertexAxis(int vertex) {
        return getVertAndEdgeSymmetriesAlongVertexAxis(vertex)[0];
    }

    public static int[][] getVertexSymmetriesAlongVertexAxis(int vertex) {
        return getVertAndEdgeSymmetriesAlongVertexAxis(vertex)[1];
    }

    private static int[][][] getVertAndEdgeSymmetriesAlongVertexAxis(int vertex) {
        int[][] symmetries = new int[10][3]; // Edge
        int[][] vertexSymmetries = new int[6][3]; // Vertex

        for (int i = 0; i < 3; i++) {
            // Trace various paths down the edges to find all 10 symmetries
            int edge0 = vertexEdges[vertex - 1][i];
            int vertex0 = getOpposingVertex(vertex, edge0);
            symmetries[0][i] = edge0;
            vertexSymmetries[0][i] = vertex0;
            int edge1L = getCounterclockwiseEdge(vertex0, edge0);
            int edge1R = getClockwiseEdge(vertex0, edge0);
            symmetries[1][i] = edge1L;
            symmetries[2][i] = edge1R;
            int vertex1L = getOpposingVertex(vertex0, edge1L);
            int vertex1R = getOpposingVertex(vertex0, edge1R);
            vertexSymmetries[1][i] = vertex1L;
            vertexSymmetries[2][i] = vertex1R;
            int edge2 = getCounterclockwiseEdge(vertex1L, edge1L);
            symmetries[3][i] = edge2;
            int edge3L = getClockwiseEdge(vertex1L, edge1L);
            int edge3R = getCounterclockwiseEdge(vertex1R, edge1R);
            symmetries[4][i] = edge3L;
            symmetries[5][i] = edge3R;
            int vertex2L = getOpposingVertex(vertex1L, edge3L);
            int vertex2R = getOpposingVertex(vertex1R, edge3R);
            vertexSymmetries[3][i] = vertex2L;
            vertexSymmetries[4][i] = vertex2R;
            int edge4 = getClockwiseEdge(vertex2L, edge3L);
            symmetries[6][i] = edge4;
            int edge5L = getCounterclockwiseEdge(vertex2L, edge3L);
            int edge5R = getClockwiseEdge(vertex2R, edge3R);
            symmetries[7][i] = edge5L;
            symmetries[8][i] = edge5R;
            int vertex3L = getOpposingVertex(vertex2L, edge5L);
            vertexSymmetries[5][i] = vertex3L;
            int edge6 = getClockwiseEdge(vertex3L, edge5L);
            symmetries[9][i] = edge6;
        }

        return new int[][][] {symmetries, vertexSymmetries};
    }



    /////////////////


    private static void initializeVertexEdges() {
        for (int[] vertexEdge : vertexEdges) {
            Arrays.fill(vertexEdge, 0); // Use 0 to indicate no edge
        }
    }

    private static void mapVertices() {
        for (int i = 0; i < faceSymmetries.length; i++) {
            for (int j = 0; j < 5; j++) {
                int face1 = i + 1;
                int face2 = faceSymmetries[i][j];
                int face3 = faceSymmetries[i][(j + 1) % 5];
                addVertex(face1, face2, face3);
            }
        }
    }

    private static void addVertex(int face1, int face2, int face3) {
        int[] orderedFaces = new int[3];
        orderedFaces[0] = face1;
        
        if (getNextFace(face1, face2) == face3) {
            orderedFaces[1] = face2;
            orderedFaces[2] = face3;
        } else {
            orderedFaces[1] = face3;
            orderedFaces[2] = face2;
        }
        
        for (int[] vertex : vertexFaces) {
            if (isCyclicPermutation(vertex, orderedFaces)) {
                return; // Vertex already exists
            }
        }
        
        vertexFaces[vertexCount++] = orderedFaces;
    }

    private static void mapEdges() {
        for (int face = 1; face <= 12; face++) {
            int[] adjacentFaces = faceSymmetries[face - 1];
            for (int i = 0; i < 5; i++) {
                int nextFace = adjacentFaces[i];
                int v1 = getVertexIndex(face, nextFace, adjacentFaces[(i + 1) % 5]);
                int v2 = getVertexIndex(face, nextFace, adjacentFaces[(i + 4) % 5]);
                addEdge(v1, v2);
            }
        }
    }

    private static void addEdge(int v1, int v2) {
        if (v1 == 0 || v2 == 0) {
            return;
        }

        if (v1 > v2) {
            int temp = v1;
            v1 = v2;
            v2 = temp;
        }
        
        for (int i = 0; i < edgeCount; i++) {
            if (edgeVertices[i][0] == v1 && edgeVertices[i][1] == v2) {
                return; // Edge already exists
            }
        }
        
        int edgeIndex = ++edgeCount;
        edgeVertices[edgeIndex - 1] = new int[]{v1, v2};
        addEdgeToVertex(v1, edgeIndex);
        addEdgeToVertex(v2, edgeIndex);
    }

    private static void addEdgeToVertex(int vertex, int edgeIndex) {
        for (int i = 0; i < 3; i++) {
            if (vertexEdges[vertex - 1][i] == 0) {
                vertexEdges[vertex - 1][i] = edgeIndex;
                break;
            }
        }
    }

    private static void reorderVertexEdges() {
        for (int v = 1; v <= vertexCount; v++) {
            int[] faces = vertexFaces[v - 1];
            int[] orderedEdges = new int[3];
            int edgeIndex = 0;

            for (int i = 0; i < 3; i++) {
                int face1 = faces[i];
                int face2 = faces[(i + 1) % 3];
                for (int e : vertexEdges[v - 1]) {
                    if (e != 0 && connectsFaces(e, v, face1, face2)) {
                        orderedEdges[edgeIndex++] = e;
                        break;
                    }
                }
            }

            vertexEdges[v - 1] = orderedEdges;
        }
    }

    private static boolean connectsFaces(int edgeIndex, int vertex, int face1, int face2) {
        int[] edge = edgeVertices[edgeIndex - 1];
        int otherVertex = (edge[0] == vertex) ? edge[1] : edge[0];
        int[] otherFaces = vertexFaces[otherVertex - 1];
        return (containsFace(otherFaces, face1) && containsFace(otherFaces, face2));
    }

    private static boolean containsFace(int[] faces, int face) {
        for (int f : faces) {
            if (f == face) return true;
        }
        return false;
    }

    private static int getNextFace(int currentFace, int nextFace) {
        int[] adjacentFaces = faceSymmetries[currentFace - 1];
        for (int i = 0; i < 5; i++) {
            if (adjacentFaces[i] == nextFace) {
                return adjacentFaces[(i + 1) % 5];
            }
        }
        return -1; // This should never happen
    }

    private static int getVertexIndex(int face1, int face2, int face3) {
        int[] faces = {face1, face2, face3};
        for (int i = 0; i < vertexCount; i++) {
            if (containsSameFaces(vertexFaces[i], faces)) {
                return i + 1;
            }
        }
        return 0; // This should never happen
    }

    private static boolean containsSameFaces(int[] vertex, int[] faces) {
        return Arrays.stream(vertex).allMatch(face -> Arrays.stream(faces).anyMatch(f -> f == face));
    }

    private static boolean isCyclicPermutation(int[] arr1, int[] arr2) {
        if (arr1.length != arr2.length) {
            return false;
        }
        for (int i = 0; i < arr1.length; i++) {
            if (Arrays.equals(arr1, new int[]{arr2[i], arr2[(i+1)%3], arr2[(i+2)%3]})) {
                return true;
            }
        }
        return false;
    }

    private static void printVertexFaces() {
        System.out.println("Vertex mappings:");
        for (int i = 0; i < vertexCount; i++) {
            System.out.printf("Vertex %2d: [%2d, %2d, %2d]%n", 
                i + 1, vertexFaces[i][0], vertexFaces[i][1], vertexFaces[i][2]);
        }
        System.out.println("Total vertices: " + vertexCount);
    }

    private static void printEdges() {
        System.out.println("\nEdges:");
        for (int i = 0; i < edgeCount; i++) {
            System.out.printf("Edge %2d: [%2d, %2d]%n", i + 1, edgeVertices[i][0], edgeVertices[i][1]);
        }
        System.out.println("Total edges: " + edgeCount);
    }

    private static void printVertexEdges() {
        System.out.println("\nVertex Edges:");
        for (int i = 0; i < vertexCount; i++) {
            System.out.printf("Vertex %2d: [%2d, %2d, %2d]%n", 
                i + 1, vertexEdges[i][0], vertexEdges[i][1], vertexEdges[i][2]);
        }
    }
}