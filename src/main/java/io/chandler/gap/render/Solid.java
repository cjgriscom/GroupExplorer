package io.chandler.gap.render;

import java.util.List;

import javafx.scene.Group;
import javafx.scene.shape.MeshView;

public abstract class Solid extends Group {
    private final List<MeshView> l;

    public Solid() {
        this.getChildren().addAll(l = createMesh());
    }

	/**
	 * Translates the group notation into a face number, 1-indexed,
	 *   and negative if the cycle is inverted.
	 */
	public abstract int getPosOrNegFaceFromGenerator(int[] groupNotation);
	
    protected abstract List<MeshView> createMesh();

	public List<MeshView> getMeshViews() {
		return l;
	}

}
