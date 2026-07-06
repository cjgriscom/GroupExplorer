package io.chandler.gap.graph.flatten;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.chandler.gap.graph.layoutalgos.JavaNetworkx;

/**
 * Headless entry point for the grid flattening pipeline.
 *
 * Usage: FlattenCli <generator-file> [timeBudgetMinutes] [seed]
 *
 * The generator file's first non-empty line must be a GAP-style combined
 * generator, e.g. [(1,2)(3,4)...,(...),(...)].
 */
public class FlattenCli {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: FlattenCli <generator-file> [lineNumber] [timeBudgetMinutes] [seed]");
            System.err.println("       FlattenCli -g '<generator-string>' [timeBudgetMinutes] [seed]");
            System.exit(2);
        }

        String line;
        int argIdx = 1;
        if ("-g".equals(args[0])) {
            line = args[1];
            argIdx = 2;
        } else {
            List<String> lines = Files.readAllLines(Paths.get(args[0])).stream()
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            int lineNum = 1;
            int timeArgIdx = 1;
            if (args.length >= 2 && args[1].matches("\\d+")) {
                int n = Integer.parseInt(args[1]);
                // file line time [seed] only when the file has multiple lines
                if (lines.size() > 1 && n >= 1 && n <= lines.size() && args.length >= 3) {
                    lineNum = n;
                    timeArgIdx = 2;
                }
            }
            if (lineNum < 1 || lineNum > lines.size()) {
                throw new IllegalArgumentException("line " + lineNum + " out of range (file has " + lines.size() + " lines)");
            }
            line = lines.get(lineNum - 1);
            argIdx = timeArgIdx;
        }

        FlattenParams params = new FlattenParams();
        if (args.length > argIdx) params.totalTimeBudgetMillis = (long) (Double.parseDouble(args[argIdx]) * 60_000);
        if (args.length > argIdx + 1) params.randomSeed = Long.parseLong(args[argIdx + 1]);

        ColoredGraph graph = ColoredGraph.fromGapString(line);
        System.out.println("graph: " + graph.vertexCount() + " vertices, " + graph.edges().size() + " edges, "
                + graph.colorCount() + " colors");

        // 3D force-directed seed (Java port of networkx spring_layout)
        StringBuilder edgeData = new StringBuilder();
        for (ColoredEdge e : graph.edges()) edgeData.append(e.u).append(',').append(e.v).append(';');
        if (edgeData.length() > 0) edgeData.setLength(edgeData.length() - 1);
        System.out.println("running spring layout (" + params.springIterations + " iterations)...");
        Map<Integer, double[]> pos3d = JavaNetworkx.computeLayout(
                edgeData.toString(), params.springIterations, 3, params.randomSeed);

        FlattenListener listener = new FlattenListener() {
            long lastPrint = 0;
            @Override public void onStatus(String message) {
                System.out.println("[flatten] " + message);
            }
            @Override public void onImprovement(GridState snapshot, long softScore, boolean legal) {
                System.out.println("[flatten] improved: soft=" + softScore + " legal=" + legal);
                long now = System.currentTimeMillis();
                if (now - lastPrint > 15_000) {
                    lastPrint = now;
                    System.out.println(snapshot.toPrettyString());
                }
            }
            @Override public boolean isCancelled() { return false; }
        };

        GridFlattenSolver solver = new GridFlattenSolver(graph, params, listener);
        long t0 = System.currentTimeMillis();
        GridState result = solver.solve(pos3d);
        long elapsed = System.currentTimeMillis() - t0;

        System.out.println();
        int gw = result.maxX() - result.minX() + 1;
        int gh = result.maxY() - result.minY() + 1;
        System.out.println("=== result after " + (elapsed / 1000) + "s (" + gw + "x" + gh
                + " grid, " + String.format("%.0f%%", 100.0 * graph.vertexCount() / (gw * gh)) + " occupancy) ===");
        System.out.println(result.toPrettyString());
        System.out.println(result.toGridText());
        ScoreReport report = GridScorer.score(graph, result);
        System.out.println(report.summary());
        SearchScore ss = SearchScore.of(graph, result, params);
        System.out.println("search score: " + ss.brief());
        if (!ss.pairViolations.isEmpty() || !ss.passThroughs.isEmpty()) {
            ss.pairViolations.forEach(v -> System.out.println("  VIOL " + v));
            ss.passThroughs.forEach(v -> System.out.println("  VIOL " + v));
        }
    }
}
