package io.chandler.gap.graph.layoutalgos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public abstract class LayoutAlgo {
	
	// Perform the layout
	public abstract void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph, EnumMap<LayoutAlgoArg, Double> args);

	public abstract LayoutAlgoArg[] getArgs(); // Return the arguments that this algo requires

	public abstract Map<Integer, double[]> getResult(); // Return the result of the layout
	
	public abstract Double getFitOut(); // If this algo supports it, return the fit out of the layout

	protected static Random cloneRandom(Random random) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(random);
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
			return (Random) ois.readObject();
		} catch (Exception e) { throw new RuntimeException(e); }
	}

	public void norm2D(Map<Integer, double[]> positions, double boxSize) {
		// Normalize the positions for 2D rendering.
		double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
		for (double[] pos : positions.values()) {
			minX = Math.min(minX, pos[0]);
			maxX = Math.max(maxX, pos[0]);
			minY = Math.min(minY, pos[1]);
			maxY = Math.max(maxY, pos[1]);
		}
		double maxScale = Math.max(maxX - minX, maxY - minY);
		for (double[] pos : positions.values()) {
			if (maxX - minX > 0)	
				pos[0] = (pos[0] - minX) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
			else
				pos[0] = 0.5 * boxSize;
			if (maxY - minY > 0)
				pos[1] = (pos[1] - minY) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
			else
				pos[1] = 0.5 * boxSize;
		}
	}

	public void norm3D(Map<Integer, double[]> positions, double boxSize) {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        for (double[] pos : positions.values()) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
            minZ = Math.min(minZ, pos[2]);
            maxZ = Math.max(maxZ, pos[2]);
        }
		double maxScale = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        for (double[] pos : positions.values()) {
            if (maxX - minX > 0)
                pos[0] = (pos[0] - minX) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            else
                pos[0] = 0.5 * boxSize;
            if (maxY - minY > 0)
                pos[1] = (pos[1] - minY) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            else
                pos[1] = 0.5 * boxSize;
            if (maxZ - minZ > 0)
                pos[2] = (pos[2] - minZ) / maxScale * 0.8 * boxSize + 0.1 * boxSize;
            else
                pos[2] = 0.5 * boxSize;
        }
	}
}