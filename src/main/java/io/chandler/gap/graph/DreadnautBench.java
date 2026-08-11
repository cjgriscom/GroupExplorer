package io.chandler.gap.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

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

        List<int[][][]> gens = new ArrayList<>();
        List<Graph<Integer, DefaultEdge>> graphs = new ArrayList<>();
        try (PbinFile pf = PbinFile.open(path)) {
            int n = Math.min(pf.size(), limit);
            System.out.printf("loading %d / %d generators…%n", n, pf.size());
            for (int i = 0; i < n; i++) {
                int[][][] gen = GroupExplorer.parseOperationsArr(pf.get(i));
                gens.add(gen);
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

        int check = Math.min(16, gens.size());
        System.out.println("\n-- correctness (first " + check + ") --");
        int mismatches = 0;
        for (int i = 0; i < check; i++) {
            CanonicalGraphHash hp = CanonicalGraphHash.parse(
                    process.getCanonicalLabeling(gens.get(i), false));
            if (nativeIface != null) {
                CanonicalGraphHash hnGraph = CanonicalGraphHash.parse(
                        nativeIface.getCanonicalLabeling(graphs.get(i), false));
                CanonicalGraphHash hnGen = CanonicalGraphHash.parse(
                        nativeIface.getCanonicalLabeling(gens.get(i), false));
                if (!hp.equals(hnGraph) || !hp.equals(hnGen)) {
                    mismatches++;
                    System.out.println("MISMATCH @" + i
                            + " process=" + hp
                            + " nativeGraph=" + hnGraph
                            + " nativeGen=" + hnGen);
                }
            }
        }
        if (nativeIface != null) {
            System.out.println(mismatches == 0
                    ? "OK: process == native(graph) == native(gen→JNI build)"
                    : ("FAIL: " + mismatches + " mismatches"));
        } else {
            System.out.println("skipped native compare (library not loaded)");
        }

        final int warmup = 1;
        final int iters = 3;
        final int n = gens.size();
        System.out.printf("%n-- serial microbench: %d entries, warmup=%d iters=%d --%n",
                n, warmup, iters);

        bench("process (gen)", warmup, iters, n, i -> {
            String z = process.getCanonicalLabeling(gens.get(i), false);
            return z.length() + z.charAt(2);
        });

        if (nativeIface != null) {
            bench("native (JGraphT)", warmup, iters, n, i -> {
                String z = nativeIface.getCanonicalLabeling(graphs.get(i), false);
                return z.length() + z.charAt(2);
            });
            bench("native (gen→JNI)", warmup, iters, n, i -> {
                String z = nativeIface.getCanonicalLabeling(gens.get(i), false);
                return z.length() + z.charAt(2);
            });
        }

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        System.out.printf("%n-- parallel microbench: %d entries, %d threads, iters=%d --%n",
                n, threads, iters);

        ThreadLocal<DreadnautInterface> processTl = ThreadLocal.withInitial(
                () -> new DreadnautInterface(DREADNAUT, USE_TRACES, DreadnautInterface.Backend.PROCESS));
        benchParallel("process (gen)", threads, iters, n, i -> {
            String z = processTl.get().getCanonicalLabeling(gens.get(i), false);
            return z.length() + z.charAt(2);
        });

        if (nativeIface != null && NautyNative.hasTls()) {
            benchParallel("native (gen→JNI TLS)", threads, iters, n, i -> {
                String z = nativeIface.getCanonicalLabeling(gens.get(i), false);
                return z.length() + z.charAt(2);
            });
        } else if (nativeIface != null) {
            System.out.println("  (skip parallel native: library not TLS-enabled)");
        }
    }

    private static void bench(String name, int warmup, int iters, int n,
                              ToLongFunction<Integer> work) {
        for (int w = 0; w < warmup; w++) {
            for (int i = 0; i < n; i++) work.applyAsLong(i);
        }
        long bestNs = Long.MAX_VALUE;
        long touch = 0;
        for (int it = 0; it < iters; it++) {
            long t0 = System.nanoTime();
            long local = 0;
            for (int i = 0; i < n; i++) local += work.applyAsLong(i);
            bestNs = Math.min(bestNs, System.nanoTime() - t0);
            touch = local;
        }
        printResult(name, bestNs, n, touch);
    }

    private static void benchParallel(String name, int threads, int iters, int n,
                                      ToLongFunction<Integer> work) throws Exception {
        runParallel(threads, n, work); // warmup
        long bestNs = Long.MAX_VALUE;
        long touch = 0;
        for (int it = 0; it < iters; it++) {
            long t0 = System.nanoTime();
            touch = runParallel(threads, n, work);
            bestNs = Math.min(bestNs, System.nanoTime() - t0);
        }
        printResult(name, bestNs, n, touch);
    }

    private static long runParallel(int threads, int n, ToLongFunction<Integer> work)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong touch = new AtomicLong();
        try {
            List<Future<?>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> touch.addAndGet(work.applyAsLong(idx))));
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
        System.out.printf("  %-24s  best=%8.1f ms  (%.2f ms/graph)  touch=%d%n",
                name, ms, per, touch);
    }

    private DreadnautBench() {}
}
