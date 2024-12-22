package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.PentagonalHexecontahedron;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;

// TODO points
public class SnubDodecahedron extends Solid {

    public SnubDodecahedron() {
        super(0); // TODO
    }

    @Override
    public Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception {
        return null;
    }

	@Override
	public int getPosOrNegFaceFromGenerator(int[] groupNotation) {
		for (int i = 0; i < 80; i++) {
			int[] face = PentagonalHexecontahedron.getFacesFromVertex(i+1);
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
        return new int[3]; // TODO
    }

    @Override
    protected float[] getPoints() {
        float[] points = {/* TODO */};

        // Scale
        for (int i = 0; i < points.length; i++) {
            points[i] /= 30;
        }
        return points;
    }
}
