package io.chandler.gap.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

public class Icosahedron extends Solid {
    private static final String STL_PATH = "stl/Anim_DodotCenter.STL";
    private static final float STL_SCALE = 0.05f;
    private static final int STL_VERTEX_INDEX = 12;
    
    @Override
    public MeshView loadVertexMesh() throws Exception {
        return loadStlFile(STL_PATH, STL_SCALE);
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
    protected List<MeshView> createMesh() {
        List<MeshView> icosaGroup = new ArrayList<>();

        // Get the base geometry data
        float[] allPoints = IcosahedronPoints.getIcosahedronPoints();
        float[] texCoords = RenderUtil.calculateTextureCoordinates(allPoints);

        // Create individual faces
        for (int i = 0; i < 20; i++) {
            int[] faceVertices = CycleInverter.invertArray(Dodecahedron.vertexFaces[i]);

            // Create a new mesh for this face
            TriangleMesh faceMesh = new TriangleMesh();
            faceMesh.setVertexFormat(VertexFormat.POINT_TEXCOORD);

            // Add only the points for this face
            float[] facePoints = new float[9]; // 3 vertices * 3 coordinates
            for (int j = 0; j < 3; j++) {
                int vertexIndex = faceVertices[j] - 1;
                System.arraycopy(allPoints, vertexIndex * 3, facePoints, j * 3, 3);
            }

            // Create face indices (always 0,1,2 since we only have 3 vertices)
            int[] faces = {0,0, 1,1, 2,2};

            faceMesh.getPoints().setAll(facePoints);
            faceMesh.getTexCoords().setAll(texCoords);
            faceMesh.getFaces().setAll(faces);

            MeshView faceMeshView = new MeshView(faceMesh);

            icosaGroup.add(faceMeshView);
        }

        return icosaGroup;
    }

    private static class IcosahedronPoints {
        public static float[] getIcosahedronPoints() {
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
}
