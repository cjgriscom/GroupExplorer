package io.chandler.gap.util;

import javafx.geometry.Point3D;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

public class MeshUtil {

	/**
	 * Creates a copy of the provided MeshView.
	 *
	 * @param originalMesh The original MeshView to copy.
	 * @return A new MeshView instance cloned from the original.
	 */
	public static MeshView copyMeshView(MeshView originalMesh) {
		// Clone the mesh
		TriangleMesh clonedMesh = new TriangleMesh();
		clonedMesh.getPoints().addAll(((TriangleMesh) originalMesh.getMesh()).getPoints());
		clonedMesh.getTexCoords().addAll(((TriangleMesh) originalMesh.getMesh()).getTexCoords());
		clonedMesh.getFaces().addAll(((TriangleMesh) originalMesh.getMesh()).getFaces());

		// Create a new MeshView with the cloned mesh
		MeshView copiedMesh = new MeshView(clonedMesh);

		// Copy transforms from the original mesh
		copiedMesh.getTransforms().addAll(originalMesh.getTransforms());

		// Copy material and other properties if necessary
		copiedMesh.setMaterial(originalMesh.getMaterial());
		copiedMesh.setCullFace(originalMesh.getCullFace());

		return copiedMesh;
	}

	/**
	 * Rotates the provided MeshView around the specified axis and angle.
	 *
	 * @param mesh  The MeshView to rotate.
	 * @param axis  The rotation axis as a Point3D.
	 * @param angle The rotation angle in degrees.
	 * @return The rotated MeshView.
	 */
	public static MeshView rotateMeshView(MeshView mesh, Point3D axis, double angle) {
		// Rotate around the axis passing through the origin
		mesh.getTransforms().add(0, new Rotate(angle, 0, 0, 0, axis));
		return mesh;
	}

	/**
	 * Creates a rotated copy of the provided MeshView by copying and rotating it.
	 *
	 * @param originalMesh The original MeshView to copy and rotate.
	 * @param axis         The rotation axis as a Point3D.
	 * @param angle        The rotation angle in degrees.
	 * @return A new rotated MeshView instance.
	 */
	public static MeshView copyAndRotateMeshView(MeshView originalMesh, Point3D axis, double angle) {
		MeshView copiedMesh = copyMeshView(originalMesh);
		MeshView rotatedMesh = rotateMeshView(copiedMesh, axis, angle);
		return rotatedMesh;
	}
}
