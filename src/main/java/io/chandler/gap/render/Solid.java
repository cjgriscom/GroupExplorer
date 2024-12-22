package io.chandler.gap.render;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.ardor3d.extension.model.stl.StlDataStore;
import com.ardor3d.extension.model.stl.StlGeometryStore;
import com.ardor3d.extension.model.stl.StlImporter;
import com.ardor3d.math.Vector3;
import com.ardor3d.util.resource.URLResourceSource;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.util.Pair;

public abstract class Solid extends Group {
    private final List<MeshView> l;
	private final int nFaces;

    public Solid(int nFaces) {
        this.nFaces = nFaces;
        this.getChildren().addAll(l = createMesh());
    }

	public List<MeshView> getVertexMeshObjects() {
		TreeMap<Integer, MeshView> m = new TreeMap<>();
		try {
			Pair<Integer, MeshView> src = loadVertexMeshAndIndex();
			if (src == null) return null;
			m.put(src.getKey(), src.getValue());

			// TODO

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return new ArrayList<>(m.values());
	}

	public abstract Pair<Integer, MeshView> loadVertexMeshAndIndex() throws Exception;

	/**
	 * Translates the group notation into a face number, 1-indexed,
	 *   and negative if the cycle is inverted.
	 */
	public abstract int getPosOrNegFaceFromGenerator(int[] groupNotation);
	
	public List<MeshView> getMeshViews() {
		return l;
	}

	protected abstract int[] getFaceVertices(int i);
	protected abstract float[] getPoints();

    protected List<MeshView> createMesh() {
        List<MeshView> icosaGroup = new ArrayList<>();

        // Get the base geometry data
        float[] allPoints = getPoints();
        float[] texCoords = RenderUtil.calculateTextureCoordinates(allPoints);

        // Create individual faces
        for (int i = 0; i < nFaces; i++) {
            int[] faceVertices = getFaceVertices(i);

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

	/**
	 * Utility method to load an STL file and return a MeshView.
	 * Can be used by derived classes to include STL models.
	 *
	 * @param filePath The path to the STL file.
	 * @return The MeshView representing the loaded STL model.
	 * @throws Exception If loading fails.
	 */
	protected MeshView loadStlFile(String filePath, float scale) throws Exception {
		// Load the STL file using StlImporter
		URLResourceSource r = new URLResourceSource(getClass().getResource("/" + filePath));
		StlGeometryStore stl = new StlImporter().load(r);
		StlDataStore data = stl.getDataStore();
		
		// Debug output: Print first few vertices
		System.out.println("\nSTL File Debug Output for: " + filePath);
		System.out.println("Total vertices: " + data.getVertices().size());
		
		// Print first triangle's vertices
		if (data.getVertices().size() >= 3) {
			System.out.println("\nFirst triangle coordinates:");
			for (int i = 0; i < 3; i++) {
				Vector3 v = data.getVertices().get(i);
				System.out.printf("v%d: (%.6f, %.6f, %.6f)%n", i, v.getX(), v.getY(), v.getZ());
			}
		}
		
		// Print min/max coordinates to understand the model's bounds
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		
		for (Vector3 v : data.getVertices()) {
			minX = Math.min(minX, v.getX());
			minY = Math.min(minY, v.getY());
			minZ = Math.min(minZ, v.getZ());
			maxX = Math.max(maxX, v.getX());
			maxY = Math.max(maxY, v.getY());
			maxZ = Math.max(maxZ, v.getZ());
		}
		
		System.out.println("\nModel bounds:");
		System.out.printf("X: %.6f to %.6f (range: %.6f)%n", minX, maxX, maxX - minX);
		System.out.printf("Y: %.6f to %.6f (range: %.6f)%n", minY, maxY, maxY - minY);
		System.out.printf("Z: %.6f to %.6f (range: %.6f)%n", minZ, maxZ, maxZ - minZ);
		
		// Create a JavaFX TriangleMesh
		TriangleMesh mesh = new TriangleMesh();
		
		// Add points to the mesh
		for (Vector3 vertex : data.getVertices()) {
			mesh.getPoints().addAll((float) vertex.getX() * scale, (float) vertex.getY() * 0.05f, (float) vertex.getZ() * 0.05f); // Apply scale here
		}
		
		// Since JavaFX TriangleMesh requires texture coordinates, add a dummy coordinate
		mesh.getTexCoords().addAll(0, 0);
		
		// Add faces to the mesh
		// Each face in STL has three vertices; JavaFX uses triangular faces with three point indices
		for (int i = 0; i < data.getVertices().size(); i += 3) {
			// JavaFX TriangleMesh faces are defined as p0/t0, p1/t0, p2/t0
			// We use 0 for all texture indices as we're not using textures
			mesh.getFaces().addAll(
				i, 0,
				i + 1, 0,
				i + 2, 0
			);
		}
		
		// Create and return the MeshView
		MeshView meshView = new MeshView(mesh);
		meshView.setCullFace(CullFace.NONE); // Optional: Disable back-face culling
		meshView.setMaterial(new PhongMaterial(Color.GRAY)); // Optional: Set a material
		
		return meshView;
	}

}
