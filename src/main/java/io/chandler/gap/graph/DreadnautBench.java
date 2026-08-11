package io.chandler.gap.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.PbinFile;

/**
 * Benchmark process dreadnaut vs in-process TLS libnauty on PBIN generator entries.
 *
 * <pre>
 * mvn -q exec:java -Dexec.mainClass=io.chandler.gap.graph.DreadnautBench \
 *   -Dexec.args="PlanarStudy/he2/...survivors.txt.pbin [limit]"
 * </pre>
 */
public final class DreadnautBench {

    private static final String DREADNAUT = "dreadnaut";
    private static final boolean USE_TRACES = true;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DreadnautBench <file.pbin> [limit]");
            System.exit(1);
        }
        String path = args[0];
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.println("=== dreadnaut / nauty canonical-hash benchmark ===");
        System.out.println("file: " + path);
        System.out.println("native libnauty_jni: " + (NautyNative.isAvailable()
                ? ("loaded (" + NautyNative.version() + "), TLS=" + NautyNative.hasTls())
                : "NOT LOADED"));
        System.out.println("default backend: " + DreadnautInterface.defaultBackend());
        System.out.println("useTraces=" + USE_TRACES + " (undirected; all-2-cycle gens)");

        List<Graph<Integer, DefaultEdge>> graphs = new ArrayList<>();
        try (PbinFile pf = PbinFile.open(path)) {
            int n = Math.min(pf.size(), limit);
            System.out.printf("loading %d / %d generators…%n", n, pf.size());
            for (int i = 0; i < n; i++) {
                int[][][] gen = GroupExplorer.parseOperationsArr(pf.get(i));
                graphs.add(PlanarStudy.buildGraphFromCombinedGen(gen, false));
            }
        }
        int nv = graphs.isEmpty() ? 0 : graphs.get(0).vertexSet().size();
        int ne = graphs.isEmpty() ? 0 : graphs.get(0).edgeSet().size();
        System.out.printf("sample graph: |V|=%d |E|=%d  (first entry)%n", nv, ne);

        DreadnautInterface process = new DreadnautInterface(
                DREADNAUT, USE_TRACES, DreadnautInterface.Backend.PROCESS);
        DreadnautInterface nativeIface = NautyNative.isAvailable()
                ? new DreadnautInterface(DREADNAUT, USE_TRACES, DreadnautInterface.Backend.NATIVE)
                : null;

        int check = Math.min(16, graphs.size());
        System.out.println("\n-- correctness (first " + check + ") --");
        int mismatches = 0;
        for (int i = 0; i < check; i++) {
            CanonicalGraphHash hp = CanonicalGraphHash.parse(
                    process.getCanonicalLabeling(graphs.get(i), false));
            if (nativeIface != null) {
                CanonicalGraphHash hn = CanonicalGraphHash.parse(
                        nativeIface.getCanonicalLabeling(graphs.get(i), false));
                if (!hp.equals(hn)) {
                    mismatches++;
                    System.out.println("MISMATCH @" + i + " process=" + hp + " native=" + hn);
                }
            }
        }
        if (nativeIface != null) {
            System.out.println(mismatches == 0
                    ? "OK: process dreadnaut At/z == native Traces/hashgraph_sg"
                    : ("FAIL: " + mismatches + " mismatches"));
        } else {
            System.out.println("skipped native compare (library not loaded)");
        }

        final int warmup = 1;
        final int iters = 3;
        System.out.printf("%n-- serial microbench: %d graphs, warmup=%d iters=%d --%n",
                graphs.size(), warmup, iters);

        benchSerial("process dreadnaut", process, graphs, warmup, iters);
        if (nativeIface != null) {
            benchSerial("native libnauty", nativeIface, graphs, warmup, iters);
        }

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        System.out.printf("%n-- parallel microbench: %d graphs, %d threads, iters=%d --%n",
                graphs.size(), threads, iters);
        // Process: one dreadnaut subprocess per task (PlanarStudy-style).
        benchParallel("process dreadnaut", () -> new DreadnautInterface(
                DREADNAUT, USE_TRACES, DreadnautInterface.Backend.PROCESS),
                graphs, threads, iters);
        if (nativeIface != null && NautyNative.hasTls()) {
            // Shared TLS-safe native iface across worker threads.
            benchParallel("native libnauty TLS", () -> nativeIface, graphs, threads, iters);
        } else if (nativeIface != null) {
            System.out.println("  (skip parallel native: library not TLS-enabled)");
        }
    }

    private static void benchSerial(String name, DreadnautInterface iface,
                                    List<Graph<Integer, DefaultEdge>> graphs,
                                    int warmup, int iters) {
        for (int w = 0; w < warmup; w++) {
            for (Graph<Integer, DefaultEdge> g : graphs) {
                iface.getCanonicalLabeling(g, false);
            }
        }
        long bestNs = Long.MAX_VALUE;
        long touch = 0;
        for (int it = 0; it < iters; it++) {
            long t0 = System.nanoTime();
            long local = 0;
            for (Graph<Integer, DefaultEdge> g : graphs) {
                String z = iface.getCanonicalLabeling(g, false);
                local += z.length() + z.charAt(2);
            }
            bestNs = Math.min(bestNs, System.nanoTime() - t0);
            touch = local;
        }
        printResult(name, bestNs, graphs.size(), touch);
    }

    @FunctionalInterface
    private interface IfaceFactory {
        DreadnautInterface get();
    }

    private static void benchParallel(String name, IfaceFactory factory,
                                      List<Graph<Integer, DefaultEdge>> graphs,
                                      int threads, int iters) throws Exception {
        // warmup
        parallelOnce(factory, graphs, threads);

        long bestNs = Long.MAX_VALUE;
        long touch = 0;
        for (int it = 0; it < iters; it++) {
            long t0 = System.nanoTime();
            touch = parallelOnce(factory, graphs, threads);
            bestNs = Math.min(bestNs, System.nanoTime() - t0);
        }
        printResult(name, bestNs, graphs.size(), touch);
    }

    private static long parallelOnce(IfaceFactory factory,
                                     List<Graph<Integer, DefaultEdge>> graphs,
                                     int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong touch = new AtomicLong();
        try {
            ThreadLocal<DreadnautInterface> local =
                    ThreadLocal.withInitial(factory::get);
            List<Future<?>> futures = new ArrayList<>(graphs.size());
            for (Graph<Integer, DefaultEdge> g : graphs) {
                futures.add(pool.submit(() -> {
                    String z = local.get().getCanonicalLabeling(g, false);
                    touch.addAndGet(z.length() + z.charAt(2));
                }));
            }
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        return touch.get();
    }

    private static void printResult(String name, long bestNs, int n, long touch) {
        double ms = bestNs / 1_000_000.0;
        double per = bestNs / (double) n / 1_000_000.0;
        System.out.printf("  %-22s  best=%8.1f ms  (%.2f ms/graph)  touch=%d%n",
                name, ms, per, touch);
    }

    private DreadnautBench() {}
}
