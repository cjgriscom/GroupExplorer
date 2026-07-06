package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class GridScorer {
    private GridScorer() {
    }

    public static ScoreReport score(ColoredGraph graph, GridState state) {
        List<String> violations = new ArrayList<>();
        int[] chebHistogram = new int[6];
        int axisCount = 0;
        int diag45Count = 0;
        int otherAngleCount = 0;
        long edgePenaltySum = 0;

        Map<String, Integer> cellOccupant = new HashMap<>();
        for (int vertex : graph.vertices()) {
            if (!state.isPlaced(vertex)) {
                violations.add("H1: vertex " + vertex + " is not placed");
            }
        }

        for (int vertex : state.placedVertices()) {
            int[] pos = state.get(vertex);
            String key = pos[0] + "," + pos[1];
            Integer existing = cellOccupant.put(key, vertex);
            if (existing != null) {
                violations.add("H1: vertices " + existing + " and " + vertex + " share cell (" + pos[0] + "," + pos[1] + ")");
            }
        }

        List<ColoredEdge> edges = graph.edges();
        int edgeCount = edges.size();
        int[] ax = new int[edgeCount];
        int[] ay = new int[edgeCount];
        int[] bx = new int[edgeCount];
        int[] by = new int[edgeCount];
        int[] dxArr = new int[edgeCount];
        int[] dyArr = new int[edgeCount];

        for (int i = 0; i < edgeCount; i++) {
            ColoredEdge edge = edges.get(i);
            ax[i] = state.posX(edge.u);
            ay[i] = state.posY(edge.u);
            bx[i] = state.posX(edge.v);
            by[i] = state.posY(edge.v);
            dxArr[i] = bx[i] - ax[i];
            dyArr[i] = by[i] - ay[i];

            int adx = Math.abs(dxArr[i]);
            int ady = Math.abs(dyArr[i]);
            int cheb = Math.max(adx, ady);
            if (cheb < 1 || cheb > GridGeometry.MAX_CHEB) {
                violations.add("H2: edge " + edge + " has Chebyshev distance " + cheb);
            } else {
                chebHistogram[cheb]++;
                edgePenaltySum += GridGeometry.edgePenalty(dxArr[i], dyArr[i]);
                if (Math.min(adx, ady) == 0) {
                    axisCount++;
                } else if (adx == ady) {
                    diag45Count++;
                } else {
                    otherAngleCount++;
                }
            }
        }

        for (int i = 0; i < edgeCount; i++) {
            ColoredEdge edge = edges.get(i);
            int[][] interior = GridGeometry.interiorLatticePoints(dxArr[i], dyArr[i]);
            for (int[] offset : interior) {
                int ix = ax[i] + offset[0];
                int iy = ay[i] + offset[1];
                String key = ix + "," + iy;
                Integer occupant = cellOccupant.get(key);
                if (occupant != null) {
                    violations.add("H3: vertex " + occupant + " lies on interior of edge " + edge
                            + " at (" + ix + "," + iy + ")");
                }
            }
        }

        Map<String, Set<Integer>> junctionEdgeIndices = new TreeMap<>();

        for (int i = 0; i < edgeCount; i++) {
            for (int j = i + 1; j < edgeCount; j++) {
                ColoredEdge e1 = edges.get(i);
                ColoredEdge e2 = edges.get(j);
                int relation = GridGeometry.segRelation(ax[i], ay[i], bx[i], by[i], ax[j], ay[j], bx[j], by[j]);

                switch (relation) {
                    case GridGeometry.DISJOINT:
                        break;
                    case GridGeometry.SHARED_ENDPOINT_ONLY:
                        if (!sharedEndpointIsCommonVertex(e1, e2, ax[i], ay[i], bx[i], by[i], ax[j], ay[j], bx[j], by[j])) {
                            violations.add("H4: edges " + e1 + " and " + e2
                                    + " share endpoint geometrically but not as a common graph vertex");
                        }
                        break;
                    case GridGeometry.OVERLAP:
                        if (!ColoredGraph.isDuplicatePair(e1, e2)) {
                            violations.add("H4: edges " + e1 + " and " + e2 + " overlap without being duplicate pairs");
                        }
                        break;
                    case GridGeometry.CROSS:
                        if (e1.color != e2.color) {
                            violations.add("H4: edges " + e1 + " and " + e2 + " cross with different colors");
                        } else {
                            long[] m1 = GridGeometry.midpoint2(ax[i], ay[i], bx[i], by[i]);
                            long[] m2 = GridGeometry.midpoint2(ax[j], ay[j], bx[j], by[j]);
                            if (m1[0] != m2[0] || m1[1] != m2[1]) {
                                violations.add("H4: edges " + e1 + " and " + e2
                                        + " cross with same color but unequal midpoints");
                            } else {
                                String junctionKey = e1.color + ":" + m1[0] + "," + m1[1];
                                junctionEdgeIndices.computeIfAbsent(junctionKey, k -> new LinkedHashSet<>()).add(i);
                                junctionEdgeIndices.get(junctionKey).add(j);
                            }
                        }
                        break;
                    default:
                        violations.add("H4: edges " + e1 + " and " + e2 + " have unknown relation " + relation);
                        break;
                }
            }
        }

        List<ScoreReport.Junction> junctions = new ArrayList<>();
        long junctionPenaltySum = 0;
        for (Map.Entry<String, Set<Integer>> entry : junctionEdgeIndices.entrySet()) {
            Set<Integer> indices = entry.getValue();
            if (indices.size() < 2) {
                continue;
            }
            List<ColoredEdge> junctionEdges = new ArrayList<>();
            for (int index : indices) {
                junctionEdges.add(edges.get(index));
            }
            junctionEdges.sort(Comparator.comparingInt((ColoredEdge e) -> e.u)
                    .thenComparingInt(e -> e.v)
                    .thenComparingInt(e -> e.color));

            String[] parts = entry.getKey().split(":");
            int color = Integer.parseInt(parts[0]);
            String[] midpointParts = parts[1].split(",");
            long mx2 = Long.parseLong(midpointParts[0]);
            long my2 = Long.parseLong(midpointParts[1]);

            int k = junctionEdges.size();
            junctionPenaltySum += GridGeometry.JUNCTION_BASE_PENALTY
                    + GridGeometry.JUNCTION_EXTRA_EDGE_PENALTY * (k - 2);
            junctions.add(new ScoreReport.Junction(color, mx2, my2, junctionEdges));
        }

        junctions.sort(Comparator.comparingInt((ScoreReport.Junction j) -> j.color)
                .thenComparingLong(j -> j.mx2)
                .thenComparingLong(j -> j.my2));

        boolean legal = violations.isEmpty();
        long totalPenalty = edgePenaltySum + junctionPenaltySum;

        return new ScoreReport(
                legal,
                totalPenalty,
                edgePenaltySum,
                junctionPenaltySum,
                violations,
                chebHistogram,
                axisCount,
                diag45Count,
                otherAngleCount,
                junctions);
    }

    private static boolean sharedEndpointIsCommonVertex(
            ColoredEdge e1,
            ColoredEdge e2,
            int ax1,
            int ay1,
            int bx1,
            int by1,
            int ax2,
            int ay2,
            int bx2,
            int by2) {
        return commonEndpointVertex(e1, e2, ax1, ay1, bx1, by1, ax2, ay2, bx2, by2) != null;
    }

    private static Integer commonEndpointVertex(
            ColoredEdge e1,
            ColoredEdge e2,
            int ax1,
            int ay1,
            int bx1,
            int by1,
            int ax2,
            int ay2,
            int bx2,
            int by2) {
        int[][] endpoints = {
                { e1.u, ax1, ay1 },
                { e1.v, bx1, by1 },
                { e2.u, ax2, ay2 },
                { e2.v, bx2, by2 }
        };
        for (int i = 0; i < endpoints.length; i++) {
            for (int j = i + 1; j < endpoints.length; j++) {
                if (samePoint(endpoints[i][1], endpoints[i][2], endpoints[j][1], endpoints[j][2])) {
                    if (endpoints[i][0] == endpoints[j][0]) {
                        return endpoints[i][0];
                    }
                }
            }
        }
        return null;
    }

    private static boolean samePoint(int x1, int y1, int x2, int y2) {
        return x1 == x2 && y1 == y2;
    }
}
