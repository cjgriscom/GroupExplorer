package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.Cube;
import io.chandler.gap.CycleInverter;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;

public class Cuboctahedron extends Solid {
    // Edges of a cube, where vertices are 1-8 and faces are 9-14

    public Cuboctahedron() {
        super(0); // TODO
    }
    
    @Override
    public Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception {
        return null;
    }

	@Override
	public int getPosOrNegFaceFromGenerator(int[] groupNotation) {
		for (int i = 0; i < 14; i++) {
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
        if (i <= 8) {
            return Cube.vertexEdges[i-1].clone();
        } else {
            int face = i - 8;
            int[] vertices = Cube.getVerticesOfFaceClockwise(face);
            int[] edges = new int[4];
            for (int e = 0; e < 4; e++) {
                int v0 = vertices[(e+3) % 4];
                int v1 = vertices[e];
                int edgeIdx = -1;
                for (int j = 0; j < Cube.vertexEdges.length; j++) {
                    if (Cube.edgeVertices[j][0] == v0 && Cube.edgeVertices[j][1] == v1) {
                        edgeIdx = j;
                        break;
                    } else if (Cube.edgeVertices[j][0] == v1 && Cube.edgeVertices[j][1] == v0) {
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
