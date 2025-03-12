package networkx;

import java.util.*;

public class NetworkxLayout {

    public static Map<Integer, double[]> computeLayout(String edgeData, int iterations, int dim, long seed) {
        // Parse edge data (format: "u,v;u,v;...")
        Graph g = new Graph();
        String[] edgeStrs = edgeData.split(";");
        for (String edgeStr : edgeStrs) {
            edgeStr = edgeStr.trim();
            if (!edgeStr.isEmpty()) {
                String[] parts = edgeStr.split(",");
                if (parts.length == 2) {
                    try {
                        int u = Integer.parseInt(parts[0].trim());
                        int v = Integer.parseInt(parts[1].trim());
                        g.addEdge(u, v);
                    } catch (NumberFormatException ex) {
                        // Ignore invalid edges
                    }
                }
            }
        }
        
        Map<Integer, double[]> pos = SpringLayout.springLayout(g, iterations, dim, seed);
        
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double[] vec : pos.values()) {
            double x = vec[0];
            double y = vec[1];
            double z = (vec.length >= 3 ? vec[2] : 0.0);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }
        
        Map<Integer, double[]> normPos = new LinkedHashMap<>();
        for (Map.Entry<Integer, double[]> entry : pos.entrySet()) {
            double[] vec = entry.getValue();
            double x = vec[0];
            double y = vec[1];
            double z = (vec.length >= 3 ? vec[2] : 0.0);
            double normX = (maxX - minX > 0) ? (x - minX) / (maxX - minX) * 0.8 + 0.1 : 0.5;
            double normY = (maxY - minY > 0) ? (y - minY) / (maxY - minY) * 0.8 + 0.1 : 0.5;
            double normZ = (maxZ - minZ > 0) ? (z - minZ) / (maxZ - minZ) * 0.8 + 0.1 : 0.5;
            normPos.put(entry.getKey(), new double[] { normX, normY, normZ });
        }
        return normPos;
    }
} 