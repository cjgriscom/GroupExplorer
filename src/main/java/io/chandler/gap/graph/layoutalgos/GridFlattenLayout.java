package io.chandler.gap.graph.layoutalgos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.graph.flatten.ColoredEdge;
import io.chandler.gap.graph.flatten.ColoredGraph;
import io.chandler.gap.graph.flatten.FlattenListener;
import io.chandler.gap.graph.flatten.FlattenParams;
import io.chandler.gap.graph.flatten.GridFlattenSolver;
import io.chandler.gap.graph.flatten.GridSnapper;
import io.chandler.gap.graph.flatten.GridState;
import io.chandler.gap.graph.flatten.Projection;
import io.chandler.gap.graph.flatten.SearchScore;

/**
 * CP-SAT grid flattening integrated as a visualizer layout algorithm.
 *
 * {@link #performLayout} returns quickly with an initial snapped grid (one
 * projection + {@link GridSnapper}); the full solver then runs on a background
 * thread and streams improvements into {@link #getResult()}.
 */
public class GridFlattenLayout extends LayoutAlgo {

    private volatile Map<Integer, double[]> result = new HashMap<>();
    private volatile Thread solverThread;
    private volatile String status = "";
    private volatile long bestScore = -1;
    private volatile boolean bestLegal;
    private volatile String runningKey;
    private volatile boolean cancelled;
    private double boxSize;

    @Override
    public void performLayout(double boxSize, String generator, Graph<Integer, DefaultEdge> graph,
                              EnumMap<LayoutAlgoArg, Double> args) {
        this.boxSize = boxSize;
        int iters = args.containsKey(LayoutAlgoArg.ITERS) ? args.get(LayoutAlgoArg.ITERS).intValue() : 5000;
        long seed = args.containsKey(LayoutAlgoArg.SEED) ? args.get(LayoutAlgoArg.SEED).longValue() : 42;
        int timeBudgetMin = args.containsKey(LayoutAlgoArg.TIME_BUDGET)
                ? args.get(LayoutAlgoArg.TIME_BUDGET).intValue() : 20;

        String runKey = generator + "|" + seed + "|" + iters + "|" + timeBudgetMin;
        if (runKey.equals(runningKey) && solverThread != null) {
            return;
        }

        cancelled = true;
        if (solverThread != null) {
            solverThread.interrupt();
        }

        ColoredGraph colored = ColoredGraph.fromGapString(generator);

        StringBuilder edgeData = new StringBuilder();
        for (ColoredEdge e : colored.edges()) {
            edgeData.append(e.u).append(',').append(e.v).append(';');
        }
        if (edgeData.length() > 0) {
            edgeData.setLength(edgeData.length() - 1);
        }

        status = "Flatten: computing spring seed...";
        Map<Integer, double[]> pos3d = JavaNetworkx.computeLayout(edgeData.toString(), iters, 3, seed);

        FlattenParams params = new FlattenParams();
        params.springIterations = iters;
        params.randomSeed = seed;
        params.totalTimeBudgetMillis = timeBudgetMin * 60L * 1000L;

        status = "Flatten: snapping initial grid...";
        FlattenParams snapSearch = new FlattenParams();
        snapSearch.randomSeed = seed;
        snapSearch.projectionSamples = 1;
        snapSearch.projectionTopK = 1;
        List<Projection.Candidate> cands = Projection.searchProjections(
                colored, pos3d, snapSearch, FlattenListener.CONSOLE);
        GridState initial = GridSnapper.snap(colored, cands.get(0).pos2d, params);
        SearchScore initScore = SearchScore.of(colored, initial, params);
        bestScore = initScore.total;
        bestLegal = initScore.isLegal();
        publishState(initial);
        status = String.format("Flatten: score=%d legal=%s running...", bestScore, bestLegal);

        runningKey = runKey;
        cancelled = false;

        Thread t = new Thread(() -> {
            try {
                FlattenListener listener = new FlattenListener() {
                    @Override public void onStatus(String message) {
                        status = "Flatten: " + message;
                    }
                    @Override public void onImprovement(GridState snapshot, long softScore, boolean legal) {
                        bestScore = softScore;
                        bestLegal = legal;
                        publishState(snapshot);
                        status = String.format("Flatten: score=%d legal=%s running...", softScore, legal);
                    }
                    @Override public boolean isCancelled() {
                        return cancelled;
                    }
                };
                GridFlattenSolver solver = new GridFlattenSolver(colored, params, listener);
                GridState finalState = solver.solve(pos3d);
                publishState(finalState);
                SearchScore fs = SearchScore.of(colored, finalState, params);
                bestScore = fs.total;
                bestLegal = fs.isLegal();
                status = String.format("Flatten: score=%d legal=%s done", bestScore, bestLegal);
            } catch (Exception ex) {
                status = "Flatten: error: " + ex.getMessage();
                ex.printStackTrace();
            }
        }, "grid-flatten-solver");
        t.setDaemon(true);
        solverThread = t;
        t.start();
    }

    private void publishState(GridState state) {
        Map<Integer, double[]> pos = new HashMap<>();
        for (int v : state.placedVertices()) {
            int[] p = state.get(v);
            pos.put(v, new double[]{p[0], p[1]});
        }
        norm2D(pos, boxSize);
        result = pos;
    }

    public boolean isRunning() {
        return solverThread != null && solverThread.isAlive();
    }

    public String getStatus() {
        return status;
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public LayoutAlgoArg[] getArgs() {
        return new LayoutAlgoArg[]{LayoutAlgoArg.ITERS, LayoutAlgoArg.SEED, LayoutAlgoArg.TIME_BUDGET};
    }

    @Override
    public Map<Integer, double[]> getResult() {
        return result;
    }

    @Override
    public Double getFitOut() {
        return null;
    }
}
