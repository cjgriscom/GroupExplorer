package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;

public class Icosahedron extends Solid {
    private static final String STL_PATH = "stl/Anim_DodotCenter.STL";
    private static final float STL_SCALE_ABOUT_ORIGIN = 0.05f;
    private static final int STL_VERTEX_INDEX = 12;

    public Icosahedron() {
        super(20);
    }
    
    @Override
    public Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception {
        MeshView mesh = loadStlFile(STL_PATH, STL_SCALE_ABOUT_ORIGIN);
        return new Pair<>(STL_VERTEX_INDEX, mesh);
    }

	@Override
	public int getPosOrNegFaceFromGenerator(int[] groupNotation) {
		for (int i = 0; i < Dodecahedron.vertexFaces.length; i++) {
			int[] face = Dodecahedron.vertexFaces[i];
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
        return CycleInverter.invertArray(Dodecahedron.vertexFaces[i]);
    }

    @Override
    protected float[] getPoints() {
        float[] points = {
                0.01f, 13.32f, -26.66f,
                25.36f, 13.31f, -8.22f,
                15.65f, 13.32f, 21.58f,
                -15.68f, 13.31f, 21.56f,
                -25.35f, 13.31f, -8.25f,
                -0.02f, -13.34f, 26.65f,
                -25.34f, -13.34f, 8.23f,
                -15.64f, -13.37f, -21.56f,
                15.68f, -13.35f, -21.54f,
                25.3f, -13.36f, 8.34f,
                -0.03f, -29.8f, -0.02f,
                -0.05f, 29.8f, 0.03f
            };

        // Scale
        for (int i = 0; i < points.length; i++) {
            points[i] /= 29.8 / 2;
        }
        return points;
    }

}
