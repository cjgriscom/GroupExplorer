package io.chandler.gap.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.chandler.gap.ArrayRotator;
import io.chandler.gap.CycleInverter;
import io.chandler.gap.PentagonalHexecontahedron;
import io.chandler.gap.PentagonalIcositrahedron;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

// TODO points
public class SnubDodecahedron extends Solid {

    @Override
    public MeshView loadVertexMesh() throws Exception {
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
    protected List<MeshView> createMesh() {
        List<MeshView> icosaGroup = new ArrayList<>();

        // Get the base geometry data
        float[] allPoints = SnubDodecahedronPoints.getSnubDodecahedronPoints();
        float[] texCoords = RenderUtil.calculateTextureCoordinates(allPoints);

        // Create individual faces
        for (int i = 0; i < 0; i++) {
            // Create a new mesh for this face
            TriangleMesh faceMesh = new TriangleMesh();
            faceMesh.setVertexFormat(VertexFormat.POINT_TEXCOORD);

            // Add only the points for this face
            float[] facePoints = new float[9]; // 3 vertices * 3 coordinates
            for (int j = 0; j < 3; j++) {
                int vertexIndex = PentagonalIcositrahedron.getFacesFromVertex(i+1)[2-j] - 1;
                System.arraycopy(allPoints, vertexIndex * 3, facePoints, j * 3, 3);
            }

            // Create face indices (always 0,1,2 since we only have 3 vertices)
            int[] faces = {0,0, 1,1, 2,2};

            faceMesh.getPoints().setAll(facePoints);
            faceMesh.getTexCoords().setAll(texCoords);
            faceMesh.getFaces().setAll(faces);

            MeshView faceMeshView = new MeshView(faceMesh);

            icosaGroup.add(faceMeshView);
            // Assuming faceMeshViews is managed elsewhere if needed
        }

        return icosaGroup;
    }

    private static class SnubDodecahedronPoints {
        public static float[] getSnubDodecahedronPoints() {
            float[] points = {/* TODO */};

            // Scale
            for (int i = 0; i < points.length; i++) {
                points[i] /= 30;
            }
            return points;
        }
    }
}
