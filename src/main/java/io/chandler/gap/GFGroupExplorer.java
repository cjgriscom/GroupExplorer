package io.chandler.gap;

import static io.chandler.gap.GFGenerator.GF2_8x8_Cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.util.TimeEstimator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Explores matrix groups generated over GF(2) using {@link GFGenerator} data.
 * <p>
 * This implementation mirrors the breadth-first exploration strategy used by
 * {@link GroupExplorer} but operates on {@link GF2_8x8_Cache} states instead
 * of permutation cycles. Only the first two generator matrices (indices 0 and 1)
 * are utilised, corresponding to the bitstring encoding expected by
 * {@link GF2_8x8_Cache}.
 */
public class GFGroupExplorer implements AbstractGroupProperties {

    private final byte[][] generatorZero;
    private final byte[][] generatorOne;
    private final long encodedGeneratorZero;
    private final long encodedGeneratorOne;
    private final int field;
    private final GF2_8x8_Cache identity;

    private final Deque<GF2_8x8_Cache> frontier = new ArrayDeque<>();
    private final Set<Long> visited;

    private boolean explored;
    private boolean multithread = false;

    private static final int BATCH_SIZE = 300_000;
    private static final int PARALLEL_THRESHOLD = 10_000;

    public static void main(String[] args) throws Exception {
        runGenerator(Generators.l2_8_gf28, Generators.l2_8, "test_l2_8", 504);
        //runGenerator(Generators.o8m2_gf28, Generators.o8m2, "o8m2", 197406720L);
        //runGenerator(Generators.o8m2_2_gf28, Generators.o8m2_2, "o8m2_2", 197406720L*2);
        runGenerator(Generators.o8p2_gf28, Generators.o8p2, "o8p2", 174182400L);
        runGenerator(Generators.o8p2_gf28, Generators.o8p2_135, "o8p2_135", 174182400L);


        int[] a = {2,1,5,7,3,10,4,13,15,6,18,20,8,23,9,26,28,11,31,12,33,35,14,38,39,16,42,17,45,46,19,49,21,41,22,54,37,24,25,57,34,27,61,62,29,30,65,66,32,50,67,69,70,36,73,75,40,78,80,82,43,44,85,86,47,48,51,89,52,53,92,94,55,97,56,99,101,58,103,59,104,60,106,107,63,64,87,110,68,108,105,71,112,72,115,96,74,117,76,119,77,116,79,81,91,83,84,90,113,88,111,93,109,126,95,102,98,121,100,124,118,130,127,120,131,114,123,133,129,122,125,134,128,132,135};
        int[] b = {3,4,6,8,9,11,12,14,16,17,19,21,22,24,25,27,29,30,1,32,34,36,37,2,40,41,43,44,38,47,48,50,51,52,53,55,56,46,39,58,59,60,5,63,64,10,61,20,26,31,68,7,71,72,74,76,77,79,81,57,83,84,75,87,65,66,88,90,91,33,93,95,96,13,98,100,102,78,15,92,49,105,18,108,69,109,106,80,85,70,104,111,113,114,107,116,112,28,118,23,120,42,121,89,122,123,119,97,124,125,67,62,35,127,101,128,129,82,54,103,115,99,45,126,132,131,133,130,110,73,86,117,135,134,94};

        String aNot = GroupExplorer.stateToNotation(a);
        String bNot = GroupExplorer.stateToNotation(b);
        System.out.println("[" + aNot + "," + bNot + "]");
    }

	public static void runGenerator(GFGenerator generator, String permGen, String groupName, long orderEst) throws Exception {
		GFGroupExplorer explorer = new GFGroupExplorer(generator);

		File root = new File("PlanarStudyMulti/" + groupName);
		root.mkdirs();
		PrintStream cycles2Out = new PrintStream(new FileOutputStream("PlanarStudyMulti/" + groupName + "/2-cycles.txt"));
		PrintStream cycles3Out = new PrintStream(new FileOutputStream("PlanarStudyMulti/" + groupName + "/3-cycles.txt"));

        int[][][] gen = GroupExplorer.parseOperationsArr(permGen);
        int[][][] genReversed = CycleInverter.deepCopy(gen);
        genReversed[0] = GroupExplorer.reverseOperation(genReversed[0]);
        genReversed[1] = GroupExplorer.reverseOperation(genReversed[1]);
        int[][][] genWithReverses = new int[][][] { gen[0], gen[1], genReversed[0], genReversed[1]};
        String permGenWithReverses = GroupExplorer.generatorsToString(genWithReverses);

        ThreadLocal<GroupExplorer> groupExplorerLocal = ThreadLocal.withInitial(() -> new GroupExplorer(permGenWithReverses, MemorySettings.FASTEST));
        ThreadLocal<boolean[][]> prevStateLocal = ThreadLocal.withInitial(() -> new boolean[1][]);
        ConcurrentLinkedQueue<HashMap<String, Long>> directories = new ConcurrentLinkedQueue<>();
        ThreadLocal<HashMap<String, Long>> directoryLocal = ThreadLocal.withInitial(() -> {HashMap<String, Long> map = new HashMap<>(); directories.add(map); return map;});

        explorer.explore(resultsBatch -> {
            resultsBatch.parallelStream().forEach(result -> {
                boolean[][] prevState = prevStateLocal.get();
                GroupExplorer groupExplorer = groupExplorerLocal.get();
                
                HashMap<String, Long> directory = directoryLocal.get();

                boolean[] prevOps = prevState[0] == null ? new boolean[0] : prevState[0];
                boolean[] ops = result.operations();

                // Find common prefix length
                int minLen = Math.min(prevOps.length, ops.length);
                int common = 0;
                while (common < minLen && prevOps[common] == ops[common]) {
                    common++;
                }

                int nOpsWithBacktrack = prevOps.length - common + ops.length - common;
                int nOpsWithoutBacktrack = ops.length;

                if (nOpsWithBacktrack <= nOpsWithoutBacktrack) {
                    // Backtrack from prevOps to the common prefix using inverse generators (2,3)
                    for (int i = prevOps.length - 1; i >= common; i--) {
                        int inverseIndex = prevOps[i] ? 3 : 2; // inverse of 1 is 3; inverse of 0 is 2
                        groupExplorer.applyOperation(inverseIndex, false);
                    }

                    // Apply forward from the common prefix to reach ops
                    for (int i = common; i < ops.length; i++) {
                        int forwardIndex = ops[i] ? 1 : 0;
                        groupExplorer.applyOperation(forwardIndex, false);
                    }
                } else {
                    // Reset and apply forward
                    groupExplorer.resetElements(false);
                    for (int i = 0; i < ops.length; i++) {
                        int forwardIndex = ops[i] ? 1 : 0;
                        groupExplorer.applyOperation(forwardIndex, false);
                    }
                }

                // Save this op sequence as the previous state for this thread
                prevState[0] = ops;

                int[] state = groupExplorer.peekState();
                int[][] cycles = GroupExplorer.stateToCycles(state);
                String key = GroupExplorer.describeCycles(state.length, cycles);
                Long curDir = directory.get(key);
                if (curDir == null) curDir = 0L;
                curDir++;
                directory.put(key, curDir);
                
                int maxCycleLength = 0;
                for (int[] cycle : cycles) {
                    maxCycleLength = Math.max(maxCycleLength, cycle.length);
                }
                if (maxCycleLength == 2) {
                    if (result.getOrder() != 2) throw new RuntimeException("Order is not 2");
                    synchronized (cycles2Out) {
                        cycles2Out.println(GroupExplorer.cyclesToNotation(cycles));
                    }
                }
                if (maxCycleLength == 3) {
                    synchronized (cycles3Out) {
                        cycles3Out.println(GroupExplorer.cyclesToNotation(cycles));
                    }
                }

                prevState[0] = ops;
            });
            
		}, orderEst);
		System.out.println(explorer.order());


        HashMap<String, Long> directory = new HashMap<>();

        // Consolidate directories
        HashMap<String, Long> d = directories.poll();
        while (d != null) {
            for (Map.Entry<String, Long> entry : d.entrySet()) {
                directory.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            d = directories.poll();
        }
        
		PrintStream dir = new PrintStream(new FileOutputStream("PlanarStudyMulti/" + groupName + "/dir.txt"));
		directory.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.forEach(entry -> dir.println(entry.getValue() + ": " + entry.getKey()));
		dir.close();

		cycles2Out.close();
		cycles3Out.close();


	}

    public GFGroupExplorer(GFGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("GFGenerator must not be null");
        }
        if (generator.matrices.length != 2) {
            throw new IllegalArgumentException("GFGenerator must include exactly two generator matrices");
        }
        require8x8(generator.matrices[0]);
        require8x8(generator.matrices[1]);

        this.field = generator.field;
        this.generatorZero = generator.matrices[0];
        this.generatorOne = generator.matrices[1];
        this.identity = GF2_8x8_Cache.identity();
        this.encodedGeneratorZero = GF2_8x8_Cache.encode(generatorZero);
        this.encodedGeneratorOne  = GF2_8x8_Cache.encode(generatorOne);
        this.visited = Collections.synchronizedSet(new ObjectOpenHashSet<>());

        enqueue(identity, null, null, null);
    }

    private static void require8x8(byte[][] matrix) {
        int size = GF2_8x8_Cache.size();
        if (matrix.length != size) {
            throw new IllegalArgumentException("Generator matrices must be " + size + "x" + size);
        }
        for (byte[] row : matrix) {
            if (row == null || row.length != size) {
                throw new IllegalArgumentException("Generator matrices must be " + size + "x" + size);
            }
        }
    }

    private void enqueue(GF2_8x8_Cache cache,
                         Consumer<? super Collection<GF2_8x8_Cache>> consumer,
                         Collection<GF2_8x8_Cache> buffer,
                         Collection<GF2_8x8_Cache> consumerBuffer) {
        if (visited.add(cache.value)) {
            if (buffer != null) {
                buffer.add(cache);
            } else {
                frontier.addLast(cache);
            }
            if (consumerBuffer != null) {
                consumerBuffer.add(cache);
            }
        }
    }


    public void explore(Consumer<? super Collection<GF2_8x8_Cache>> consumer) {
        explore(consumer, -1);
    }

    public void explore(Consumer<? super Collection<GF2_8x8_Cache>> consumer, long orderEst) {
        TimeEstimator timeEstimator = orderEst > 0 ? new TimeEstimator(orderEst) : null;
        if (explored) {
            return;
        }
        // Ensure the initial identity element is delivered to the consumer exactly once
        if (consumer != null) {
            consumer.accept(Collections.singletonList(identity));
        }
        while (!frontier.isEmpty()) {
            if (timeEstimator != null) {
                timeEstimator.checkProgressEstimate(visited.size(), null);
            }
            int batchSize = Math.min(frontier.size(), BATCH_SIZE);
            List<GF2_8x8_Cache> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                batch.add(frontier.removeFirst());
            }

            boolean parallel = multithread && batch.size() >= PARALLEL_THRESHOLD;
            Collection<GF2_8x8_Cache> additions = parallel ? new ConcurrentLinkedQueue<GF2_8x8_Cache>() : new ArrayDeque<>();
            Collection<GF2_8x8_Cache> consumerAdditions = consumer == null ? null : new ConcurrentLinkedQueue<>();

            Stream<GF2_8x8_Cache> stream = parallel ? batch.parallelStream() : batch.stream();
            stream.forEach(state -> applyGenerators(state, consumer, additions, consumerAdditions));

            frontier.addAll(additions);

            if (consumer != null && !consumerAdditions.isEmpty()) {
                consumer.accept(new ArrayList<>(consumerAdditions));
            }
        }
        explored = true;
    }

    private void applyGenerators(GF2_8x8_Cache state,
                                 Consumer<? super Collection<GF2_8x8_Cache>> consumer,
                                 Collection<GF2_8x8_Cache> additions,
                                 Collection<GF2_8x8_Cache> consumerAdditions) {
        long stateValue = state.value;
        long nextZero = GFGenerator.GF2_8x8_Cache.multiplyEncoded(stateValue, encodedGeneratorZero);
        long nextOne  = GFGenerator.GF2_8x8_Cache.multiplyEncoded(stateValue, encodedGeneratorOne);
        enqueueEncoded(state, false, nextZero, consumer, additions, consumerAdditions);
        enqueueEncoded(state, true,  nextOne,  consumer, additions, consumerAdditions);
    }

    private void enqueueEncoded(GF2_8x8_Cache parent,
                                 boolean bit,
                                 long childValue,
                                 Consumer<? super Collection<GF2_8x8_Cache>> consumer,
                                 Collection<GF2_8x8_Cache> buffer,
                                 Collection<GF2_8x8_Cache> consumerBuffer) {
        if (visited.add(childValue)) {
            GF2_8x8_Cache child = parent.copyAndUpdateEncoded(bit, childValue);
            if (buffer != null) {
                buffer.add(child);
            } else {
                frontier.addLast(child);
            }
            if (consumerBuffer != null) {
                consumerBuffer.add(child);
            }
        }
    }

    public void setMultithread(boolean multithread) {
        this.multithread = multithread;
    }

    @Override
    public MemorySettings mem() {
        return MemorySettings.DEFAULT;
    }

    @Override
    public int order() {
        return visited.size();
    }

    @Override
    public int elements() {
        return GF2_8x8_Cache.size();
    }

    public GF2_8x8_Cache identity() {
        return identity;
    }

    public Set<Long> matrices() {
        return Collections.unmodifiableSet(visited);
    }
}


