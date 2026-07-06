package io.chandler.gap.graph.flatten;

import io.chandler.gap.GroupExplorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ColoredGraph {
    private final List<ColoredEdge> edges;
    private final int[] vertices;
    private final Map<Integer, List<ColoredEdge>> edgesByVertex;
    private final int colorCount;

    public ColoredGraph(int[][][] generators) {
        colorCount = generators.length;
        Set<Integer> vertexSet = new TreeSet<>();
        List<ColoredEdge> edgeList = new ArrayList<>();
        Set<String> seenByColor = new HashSet<>();

        for (int color = 0; color < generators.length; color++) {
            seenByColor.clear();
            for (int[] cycle : generators[color]) {
                if (cycle.length == 0) {
                    continue;
                }
                if (cycle.length == 1) {
                    vertexSet.add(cycle[0]);
                    continue;
                }
                for (int i = 0; i < cycle.length; i++) {
                    int a = cycle[i];
                    int b = cycle[(i + 1) % cycle.length];
                    vertexSet.add(a);
                    vertexSet.add(b);
                    int u = Math.min(a, b);
                    int v = Math.max(a, b);
                    String key = u + "," + v;
                    if (seenByColor.add(key)) {
                        edgeList.add(new ColoredEdge(u, v, color));
                    }
                }
            }
        }

        edges = Collections.unmodifiableList(edgeList);
        vertices = vertexSet.stream().mapToInt(Integer::intValue).toArray();
        edgesByVertex = new HashMap<>();
        for (ColoredEdge edge : edges) {
            edgesByVertex.computeIfAbsent(edge.u, k -> new ArrayList<>()).add(edge);
            edgesByVertex.computeIfAbsent(edge.v, k -> new ArrayList<>()).add(edge);
        }
        for (List<ColoredEdge> list : edgesByVertex.values()) {
            list.sort((a, b) -> {
                int cmp = Integer.compare(a.u, b.u);
                if (cmp != 0) {
                    return cmp;
                }
                cmp = Integer.compare(a.v, b.v);
                if (cmp != 0) {
                    return cmp;
                }
                return Integer.compare(a.color, b.color);
            });
        }
    }

    public static ColoredGraph fromGapString(String gapString) {
        return new ColoredGraph(GroupExplorer.parseOperationsArr(gapString));
    }

    public List<ColoredEdge> edges() {
        return edges;
    }

    public int[] vertices() {
        return Arrays.copyOf(vertices, vertices.length);
    }

    public int vertexCount() {
        return vertices.length;
    }

    public List<ColoredEdge> edgesOf(int vertex) {
        List<ColoredEdge> list = edgesByVertex.get(vertex);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    public int colorCount() {
        return colorCount;
    }

    public static boolean isDuplicatePair(ColoredEdge a, ColoredEdge b) {
        return a.u == b.u && a.v == b.v && a.color != b.color;
    }
}
