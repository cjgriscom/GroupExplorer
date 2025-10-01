package io.chandler.gap;

import static io.chandler.gap.GFGenerator.GF2_8x8_Cache;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.chandler.gap.GroupExplorer.MemorySettings;

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
    private final int field;
    private final GF2_8x8_Cache identity;

    private final Deque<GF2_8x8_Cache> frontier = new ArrayDeque<>();
    private final Map<Long, GF2_8x8_Cache> visited = new HashMap<>();

    private boolean explored;

	public static void main(String[] args) {
		GFGenerator generator = Generators.o8m2_2_gf28;
		GFGroupExplorer explorer = new GFGroupExplorer(generator);
		HashSet<GF2_8x8_Cache> order2Results = new HashSet<>();
		int[] total = new int[1];
		explorer.explore((results) -> {
			total[0]++;
			if (results.getOrder() == 2) {
				order2Results.add(results);
			}

			if (total[0] % 100000 == 0) {
				System.out.println(total[0]);
			}
		});
		System.out.println(explorer.order());

		// Now create GroupExplorer and construct matching permutation cycles from adjacent generator
		System.out.println(order2Results.size());

		GroupExplorer groupExplorer = new GroupExplorer(Generators.o8m2_2, MemorySettings.COMPACT);
		System.out.println("Initial state: " + GroupExplorer.stateToNotation(groupExplorer.copyCurrentState()));

		// Apply the bitstrings to the permutation group explorer to extract 2-cycles
		for (GF2_8x8_Cache result : order2Results) {
			int generatorBits = result.generatorBits();
			int length = result.bitstringLength();
			for (int i = 0; i < length; i++) {
				boolean isOne = ((generatorBits >> i) & 1) == 1;
				groupExplorer.applyOperation(isOne ? 1 : 0);
			}
			System.out.println(GroupExplorer.stateToNotation(groupExplorer.copyCurrentState()));
			groupExplorer.resetElements(false);
		}


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

        enqueue(identity, null);
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

    private void enqueue(GF2_8x8_Cache cache, Consumer<GF2_8x8_Cache> consumer) {
        if (visited.putIfAbsent(cache.value, cache) == null) {
			if (consumer != null) {
				consumer.accept(cache);
			}
            frontier.addLast(cache);
        }
    }

    public void explore(Consumer<GF2_8x8_Cache> consumer) {
        if (explored) {
            return;
        }
        while (!frontier.isEmpty()) {
            GF2_8x8_Cache current = frontier.removeFirst();
            applyGenerators(current, consumer);
        }
        explored = true;
    }

    private void applyGenerators(GF2_8x8_Cache state, Consumer<GF2_8x8_Cache> consumer) {
        byte[][] stateMatrix = state.toMatrix();
        enqueue(state.copyAndUpdate(false, GFGenerator.multiply(stateMatrix, generatorZero, field)), consumer);
        enqueue(state.copyAndUpdate(true, GFGenerator.multiply(stateMatrix, generatorOne, field)), consumer);
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
        return Collections.unmodifiableSet(visited.keySet());
    }

    public GF2_8x8_Cache getMatrix(long encodedMatrix) {
        return visited.get(encodedMatrix);
    }
}


