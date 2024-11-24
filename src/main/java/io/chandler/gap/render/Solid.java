package io.chandler.gap.render;

import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.Group;
import javafx.scene.shape.MeshView;

public abstract class Solid extends Group {
    
    public Solid() {
        this.getChildren().addAll(createMesh());
    }

    protected abstract List<MeshView> createMesh();

	public List<MeshView> getMeshViews() {
		return this.getChildren().stream().filter(MeshView.class::isInstance).map(MeshView.class::cast).collect(Collectors.toList());
	}

}
