package io.chandler.gap.graph.flatten;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GridScorerHs176Test {

    private static final String HAND_SOLUTION = ""
            + "((x,x,x,x,x,x,x,x,122,34,76,151,167,85,x,x,x,x,x,x,x,x),\n"
            + "(x,x,114,x,58,99,27,168,155,54,11,97,6,51,83,7,x,x,x,x,x,x),\n"
            + "(x,x,105,x,4,52,70,126,x,145,131,x,98,136,36,89,x,x,x,x,x,x),\n"
            + "(19,100,40,109,16,128,175,35,37,65,71,61,73,44,66,x,x,x,x,x,x,x),\n"
            + "(x,x,57,32,103,161,69,171,x,x,x,156,112,x,x,149,x,x,x,x,x,x),\n"
            + "(137,12,106,28,88,144,3,107,x,x,x,x,94,x,x,130,150,x,110,84,148,x),\n"
            + "(5,75,41,141,113,82,22,1,142,120,x,x,170,119,18,56,92,166,165,2,133,38),\n"
            + "(9,72,17,158,78,104,157,46,43,93,x,x,123,50,13,121,60,163,14,68,152,153),\n"
            + "(25,90,118,67,140,10,91,174,x,x,x,x,55,x,x,42,31,x,124,64,45,x),\n"
            + "(x,x,29,30,173,132,77,159,x,x,x,102,138,x,x,129,x,x,x,x,x,x),\n"
            + "(74,39,108,134,8,135,147,160,115,125,20,154,86,143,48,x,x,x,x,x,x,x),\n"
            + "(x,x,95,x,172,176,47,79,x,49,62,x,164,101,24,111,x,x,x,x,x,x),\n"
            + "(x,x,162,x,117,139,116,169,26,127,33,23,21,80,15,53,x,x,x,x,x,x),\n"
            + "(x,x,x,x,x,x,x,x,81,146,59,63,87,96,x,x,x,x,x,x,x,x))";

    @Test
    void hs176GraphStructure() throws Exception {
        Path gapFile = Path.of("/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/hs_176/2026-06-25.txt");
        assumeTrue(Files.exists(gapFile), "GAP file missing: " + gapFile);

        String gapLine = Files.readString(gapFile).trim();
        ColoredGraph graph = ColoredGraph.fromGapString(gapLine);

        assertEquals(176, graph.vertexCount());
        assertEquals(244, graph.edges().size());

        int[] colorCounts = new int[3];
        for (ColoredEdge edge : graph.edges()) {
            colorCounts[edge.color]++;
        }
        assertArrayEquals(new int[] { 82, 82, 80 }, colorCounts);
    }

    @Test
    void hs176HandSolutionScoresLegal() throws Exception {
        Path gapFile = Path.of("/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/hs_176/2026-06-25.txt");
        assumeTrue(Files.exists(gapFile), "GAP file missing: " + gapFile);

        String gapLine = Files.readString(gapFile).trim();
        ColoredGraph graph = ColoredGraph.fromGapString(gapLine);
        GridState state = GridState.parseGridText(HAND_SOLUTION);

        ScoreReport report = GridScorer.score(graph, state);
        System.out.println(report.summary());

        if (!report.legal) {
            System.out.println("Violations:");
            report.violations.forEach(System.out::println);
        }

        assertTrue(report.legal, () -> "Expected legal solution, violations: " + report.violations);
        assertArrayEquals(new int[] { 0, 209, 24, 10, 0, 1 }, report.chebHistogram);
        assertEquals(7, report.otherAngleCount);
        assertEquals(22, report.junctions.size());
        assertEquals(69L, report.junctionPenalty);
        assertEquals(358L, report.edgePenalty);
        assertEquals(427L, report.totalPenalty);
    }

    @Test
    void gridTextRoundTripUpToTranslation() {
        GridState parsed = GridState.parseGridText(HAND_SOLUTION);
        GridState roundTrip = GridState.parseGridText(parsed.toGridText());

        Map<String, Integer> parsedByVertex = vertexPositions(parsed);
        Map<String, Integer> roundTripByVertex = vertexPositions(roundTrip);

        assertEquals(parsedByVertex.keySet(), roundTripByVertex.keySet());

        int dx = 0;
        int dy = 0;
        boolean deltaSet = false;
        for (int vertex : parsed.placedVertices()) {
            int[] p1 = parsed.get(vertex);
            int[] p2 = roundTrip.get(vertex);
            int ndx = p2[0] - p1[0];
            int ndy = p2[1] - p1[1];
            if (!deltaSet) {
                dx = ndx;
                dy = ndy;
                deltaSet = true;
            } else {
                assertEquals(dx, ndx);
                assertEquals(dy, ndy);
            }
        }
    }

    private static Map<String, Integer> vertexPositions(GridState state) {
        Map<String, Integer> map = new HashMap<>();
        for (int vertex : state.placedVertices()) {
            int[] pos = state.get(vertex);
            map.put(vertex + "@" + pos[0] + "," + pos[1], vertex);
        }
        return map;
    }
}
