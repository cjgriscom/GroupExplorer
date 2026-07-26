package io.chandler.gap.graph;

import java.util.Arrays;

/**
 * Coxeter/Dynkin diagram data for a linear or custom labeled generator layout.
 * <p>
 * Generators are indexed {@code 0 .. n-1} in diagram order (e.g. F4: A=0, B=1, C=2, D=3).
 * Unlisted pairs use {@link #defaultOffEdgeOrder()} (typically 2).
 */
public final class CoxeterDynkinSpec {
	private final int generatorCount;
	private final int defaultOffEdgeOrder;
	private final int[][] pairOrders;
	private final String[] labels;

	private CoxeterDynkinSpec(
			int generatorCount,
			int defaultOffEdgeOrder,
			int[][] pairOrders,
			String[] labels) {
		if (generatorCount < 2) {
		 throw new IllegalArgumentException("generatorCount must be >= 2");
		}
		if (defaultOffEdgeOrder < 2) {
			throw new IllegalArgumentException("defaultOffEdgeOrder must be >= 2");
		}
		this.generatorCount = generatorCount;
		this.defaultOffEdgeOrder = defaultOffEdgeOrder;
		this.pairOrders = pairOrders;
		this.labels = labels;
	}

	public int generatorCount() {
		return generatorCount;
	}

	public int defaultOffEdgeOrder() {
		return defaultOffEdgeOrder;
	}

	public String label(int index) {
		return labels[index];
	}

	public String[] labels() {
		return labels.clone();
	}

	/** Coxeter label m_ij for diagram positions i and j (symmetric). */
	public int requiredOrder(int i, int j) {
		if (i == j) {
			return 1;
		}
		if (i > j) {
			int tmp = i;
			i = j;
			j = tmp;
		}
		return pairOrders[i][j];
	}

	/**
	 * F4 Dynkin/Coxeter diagram:
	 * A --- B === C --- D with edge orders 3, 4, 3 and all other pairs order 2.
	 */
	public static CoxeterDynkinSpec f4() {
		return builder(4)
				.labels("A", "B", "C", "D")
				.edge(0, 1, 3)
				.edge(1, 2, 4)
				.edge(2, 3, 3)
				.build();
	}

	public static Builder builder(int generatorCount) {
		return new Builder(generatorCount);
	}

	public static final class Builder {
		private final int generatorCount;
		private int defaultOffEdgeOrder = 2;
		private final int[][] pairOrders;
		private String[] labels;

		private Builder(int generatorCount) {
			this.generatorCount = generatorCount;
			this.pairOrders = new int[generatorCount][generatorCount];
			for (int i = 0; i < generatorCount; i++) {
				Arrays.fill(pairOrders[i], defaultOffEdgeOrder);
				pairOrders[i][i] = 1;
			}
			this.labels = defaultLabels(generatorCount);
		}

		public Builder defaultOffEdgeOrder(int order) {
			if (order < 2) {
				throw new IllegalArgumentException("defaultOffEdgeOrder must be >= 2");
			}
			this.defaultOffEdgeOrder = order;
			for (int i = 0; i < generatorCount; i++) {
				for (int j = 0; j < generatorCount; j++) {
					if (i != j) {
						pairOrders[i][j] = order;
					}
				}
			}
			return this;
		}

		public Builder labels(String... labels) {
			if (labels.length != generatorCount) {
				throw new IllegalArgumentException(
						"Expected " + generatorCount + " labels, got " + labels.length);
			}
			this.labels = labels.clone();
			return this;
		}

		public Builder edge(int i, int j, int order) {
			if (i < 0 || i >= generatorCount || j < 0 || j >= generatorCount) {
				throw new IndexOutOfBoundsException("Edge indices out of range: " + i + ", " + j);
			}
			if (i == j) {
				throw new IllegalArgumentException("Edge endpoints must differ");
			}
			if (order < 2) {
				throw new IllegalArgumentException("Edge order must be >= 2");
			}
			pairOrders[i][j] = order;
			pairOrders[j][i] = order;
			return this;
		}

		public CoxeterDynkinSpec build() {
			int[][] ordersCopy = new int[generatorCount][];
			for (int i = 0; i < generatorCount; i++) {
				ordersCopy[i] = pairOrders[i].clone();
			}
			return new CoxeterDynkinSpec(generatorCount, defaultOffEdgeOrder, ordersCopy, labels.clone());
		}
	}

	private static String[] defaultLabels(int n) {
		String[] result = new String[n];
		for (int i = 0; i < n; i++) {
			result[i] = "s" + (i + 1);
		}
		return result;
	}
}
