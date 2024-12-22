package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.PentagonalIcositrahedron;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;

public class SnubCube extends Solid {

    public SnubCube() {
        super(32);
    }

    // TODO
	@Override
	public Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception {
		return null;
	}

	@Override
	public int getPosOrNegFaceFromGenerator(int[] groupNotation) {
		for (int i = 0; i < 32; i++) {
			int[] face = PentagonalIcositrahedron.getFacesFromVertex(i+1);
			for (int j = 0; j < 3; j++) {
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
    protected int[] getFaceVertices(int i) {
        int[] face = new int[3];
        face[0] = PentagonalIcositrahedron.getFacesFromVertex(i+1)[2];
        face[1] = PentagonalIcositrahedron.getFacesFromVertex(i+1)[1];
        face[2] = PentagonalIcositrahedron.getFacesFromVertex(i+1)[0];
        return face;
    }

    @Override
    protected float[] getPoints() {
        float[] points = {
                -22.95f, 42.21f, 12.48f,
                -12.48f, 22.95f, 42.21f,
                -42.21f, 12.48f, 22.95f,
                -12.48f, 42.21f, -22.95f,
                -42.21f, 22.95f, -12.48f,
                -22.95f, 12.48f, -42.21f,
                22.95f, 42.21f, -12.48f,
                12.48f, 22.95f, -42.21f,
                42.21f, 12.48f, -22.95f,
                12.48f, 42.21f, 22.95f,
                42.21f, 22.95f, 12.48f,
                22.95f, 12.48f, 42.21f,
                12.48f, -22.95f, 42.21f,
                42.21f, -12.48f, 22.95f,
                22.95f, -42.21f, 12.48f,
                -22.95f, -12.48f, 42.21f,
                -12.48f, -42.21f, 22.95f,
                -42.21f, -22.95f, 12.48f,
                -42.21f, -12.48f, -22.95f,
                -22.95f, -42.21f, -12.48f,
                -12.48f, -22.95f, -42.21f,
                22.95f, -12.48f, -42.21f,
                12.48f, -42.21f, -22.95f,
                42.21f, -22.95f, -12.48f};

        // Scale
        for (int i = 0; i < points.length; i++) {
            points[i] /= 30;
        }
        return points;
    
    }
}
