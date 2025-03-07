package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;

public class Icosidodecahedron extends Solid {
    // Edges of a dodecahedron, where vertices are 1-20 and faces are 21-32

    public static void main(String[] args) {
        // TODO somethings broken
        Icosidodecahedron i = new Icosidodecahedron();
        for (int f = 0; f < 32; f++) {
            System.out.println(Arrays.toString(i.getFaceVertices(f)));
        }
    }

    public Icosidodecahedron() {
        super(0); // TODO
    }
    
    @Override
    public Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception {
        return null;
    }

	@Override
	public int getPosOrNegFaceFromGenerator(int[] groupNotation) {
		for (int i = 0; i < 32; i++) {
			int[] face = getFaceVertices(i);
			for (int j = 0; j < face.length; j++) {
				if (Arrays.equals(face, groupNotation)) {
					return (i + 1);
				} else if (Arrays.equals(CycleInverter.invertArray(face), groupNotation)) {
					return -(i + 1);
				}
				ArrayRotator.rotateRight(face);
			}
		}
		throw new RuntimeException("No match found for " + Arrays.toString(groupNotation));
	}

    @Override
    public int[] getFaceVertices(int i) {
        i += 1;
        if (i <= 20) {
            return Dodecahedron.vertexEdges[i-1].clone();
        } else {
            int face = i - 20;
            int[] vertices = Dodecahedron.getVerticesOfFaceClockwise(face);
            int[] edges = new int[5];
            for (int e = 0; e < 5; e++) {
                int v0 = vertices[(e+4) % 5];
                int v1 = vertices[e];
                int edgeIdx = -1;
                for (int j = 0; j < Dodecahedron.vertexEdges.length; j++) {
                    if (Dodecahedron.edgeVertices[j][0] == v0 && Dodecahedron.edgeVertices[j][1] == v1) {
                        edgeIdx = j;
                        break;
                    } else if (Dodecahedron.edgeVertices[j][0] == v1 && Dodecahedron.edgeVertices[j][1] == v0) {
                        edgeIdx = j;
                        break;
                    }
                }
                if (edgeIdx == -1) {
                    throw new RuntimeException("No edge found for " + v0 + " " + v1);
                }
                edges[e] = edgeIdx+1;
            }
            return edges;
        }
    }

    @Override
    protected float[] getPoints() {
        float[] points = {  };

        return points;
    }

}
