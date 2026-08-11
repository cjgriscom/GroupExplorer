package io.chandler.gap.graph.filter;

import java.util.Arrays;

import io.chandler.gap.graph.CongestionBatch;
import io.chandler.gap.graph.CongestionEvaluator;

/**
 * Result filter that scores each generator with the same congestion pipeline as
 * {@link CongestionBatch} / {@link CongestionEvaluator}. Survivors are accepted
 * with a {@code cong=...} comment; pruned results are rejected to candidates
 * (discarded on the last loop by {@link AbstractResultFilter}).
 * <p>
 * Uses a configurable worker thread pool (see {@link Builder#threads(int)}) so
 * congestion scoring can run in parallel across queued results.
 */
public class CongestionResultFilter extends AbstractResultFilter {
	private final CongestionEvaluator evaluator;

	private CongestionResultFilter(int maxQueueSize, int threads, CongestionEvaluator evaluator) {
		this(maxQueueSize, threads, evaluator, "planar-study-result-filter-");
	}

	CongestionResultFilter(int maxQueueSize, int threads, CongestionEvaluator evaluator,
			String workerThreadPrefix) {
		super(maxQueueSize, threads, workerThreadPrefix);
		this.evaluator = evaluator;
	}

	public static Builder builder(int maxQueueSize) {
		return new Builder(maxQueueSize);
	}

	@Override
	protected FilterDecision process(String result) {
		CongestionEvaluator.Result eval = evaluator.evaluate(result);
		if (eval.survived) {
			return FilterDecision.accept(String.format("cong=%.6f", eval.bestScore));
		}
		return FilterDecision.rejectToCandidates();
	}

	/**
	 * Builder mirroring the congestion-related {@link CongestionBatch} CLI args
	 * ({@code --seeds}, {@code --checkpoints}, {@code --thresholds}, {@code --n-rotations},
	 * {@code --threads}).
	 */
	public static final class Builder {
		private final int maxQueueSize;
		private long[] seeds = new long[]{0L};
		private int[] checkpoints = new int[]{500};
		private Double[] thresholds = null;
		private int nRotations = 1;
		private int threads = Runtime.getRuntime().availableProcessors();
		private String workerThreadPrefix = "planar-study-result-filter-";

		private Builder(int maxQueueSize) {
			this.maxQueueSize = maxQueueSize;
		}

		public Builder seeds(long... seeds) {
			if (seeds == null || seeds.length < 1) {
				throw new IllegalArgumentException("seeds must contain at least one value");
			}
			this.seeds = seeds.clone();
			return this;
		}

		public Builder checkpoints(int... checkpoints) {
			if (checkpoints == null || checkpoints.length < 1) {
				throw new IllegalArgumentException("checkpoints must contain at least one value");
			}
			int[] cps = checkpoints.clone();
			Arrays.sort(cps);
			for (int i = 1; i < cps.length; i++) {
				if (cps[i] <= cps[i - 1]) {
					throw new IllegalArgumentException("checkpoints must be strictly increasing");
				}
			}
			this.checkpoints = cps;
			return this;
		}

		public Builder thresholds(double... thresholds) {
			if (thresholds == null) {
				this.thresholds = null;
				return this;
			}
			Double[] boxed = new Double[thresholds.length];
			for (int i = 0; i < thresholds.length; i++) {
				boxed[i] = thresholds[i];
			}
			this.thresholds = boxed;
			return this;
		}

		public Builder nRotations(int nRotations) {
			if (nRotations < 1) {
				throw new IllegalArgumentException("nRotations must be >= 1");
			}
			this.nRotations = nRotations;
			return this;
		}

		/** Worker threads for parallel congestion scoring (default: available processors). */
		public Builder threads(int threads) {
			if (threads < 1) {
				throw new IllegalArgumentException("threads must be >= 1");
			}
			this.threads = threads;
			return this;
		}

		Builder workerThreadPrefix(String workerThreadPrefix) {
			if (workerThreadPrefix == null || workerThreadPrefix.isEmpty()) {
				throw new IllegalArgumentException("workerThreadPrefix must be non-empty");
			}
			this.workerThreadPrefix = workerThreadPrefix;
			return this;
		}

		public CongestionResultFilter build() {
			CongestionEvaluator evaluator =
					new CongestionEvaluator(seeds, checkpoints, thresholds, nRotations);
			return new CongestionResultFilter(maxQueueSize, threads, evaluator, workerThreadPrefix);
		}
	}

	@Override
	public String shortFilterName() {
		return "congestion";
	}
}
