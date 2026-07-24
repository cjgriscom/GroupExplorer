package io.chandler.gap.graph.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.chandler.gap.graph.CongestionCuda;
import io.chandler.gap.graph.CongestionEvaluator;
import io.chandler.gap.graph.CongestionEvaluatorGPU;

/**
 * GPU-batched result filter using the same congestion pipeline as
 * {@link CongestionResultFilter}. Accumulates up to {@code batchSize} results
 * (or {@code batchWaitMs} since the first item) before submitting a batch to
 * {@link CongestionEvaluatorGPU}. Fails hard if CUDA is unavailable or batch
 * evaluation fails.
 */
public final class CongestionResultFilterGPU extends AbstractResultFilter {
	private static final long POLL_CHECK_MS = 100L;

	private final CongestionEvaluatorGPU gpuEvaluator;
	private final int batchSize;
	private final long batchWaitMs;

	private CongestionResultFilterGPU(int maxQueueSize, CongestionEvaluatorGPU gpuEvaluator,
			int batchSize, long batchWaitMs) {
		super(maxQueueSize, 1);
		this.gpuEvaluator = gpuEvaluator;
		this.batchSize = batchSize;
		this.batchWaitMs = batchWaitMs;
	}

	public static Builder builder(int maxQueueSize) {
		return new Builder(maxQueueSize);
	}

	@Override
	protected FilterDecision process(String result) {
		throw new UnsupportedOperationException("GPU filter uses batched runLoop");
	}

	@Override
	protected void runLoop() {
		List<QueuedResult> pending = new ArrayList<>(batchSize);
		long batchDeadlineMs = 0L;

		try {
			while (!isShutdown()) {
				if (shouldFlushBatch(pending, batchDeadlineMs)) {
					flushBatch(pending);
					batchDeadlineMs = 0L;
					continue;
				}

				QueuedResult item;
				if (pending.isEmpty()) {
					item = take();
				} else {
					long remaining = batchDeadlineMs - System.currentTimeMillis();
					long pollTimeoutMs = Math.min(POLL_CHECK_MS, Math.max(1L, remaining));
					item = poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
				}

				if (item != null) {
					if (pending.isEmpty()) {
						batchDeadlineMs = System.currentTimeMillis() + batchWaitMs;
					}
					pending.add(item);
				}
			}

			QueuedResult leftover;
			while ((leftover = pollNonBlocking()) != null) {
				if (pending.isEmpty()) {
					batchDeadlineMs = System.currentTimeMillis();
				}
				pending.add(leftover);
			}
			if (!pending.isEmpty()) {
				flushBatch(pending);
			}
		} catch (InterruptedException e) {
			if (!isShutdown()) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private boolean shouldFlushBatch(List<QueuedResult> pending, long batchDeadlineMs) {
		if (pending.isEmpty()) {
			return false;
		}
		if (pending.size() >= batchSize) {
			return true;
		}
		if (isShutdown()) {
			return true;
		}
		if (System.currentTimeMillis() >= batchDeadlineMs) {
			return true;
		}
		return isInputQueueEmpty() && pending.size() == queuedCount();
	}

	private void flushBatch(List<QueuedResult> pending) {
		List<String> lines = new ArrayList<>(pending.size());
		for (QueuedResult item : pending) {
			lines.add(item.result);
		}

		CongestionEvaluatorGPU.BatchOutcome outcome = gpuEvaluator.evaluateBatch(lines);
		CongestionEvaluator.Result[] results = outcome.results;
		if (results.length != pending.size()) {
			throw new IllegalStateException(
					"GPU batch returned " + results.length + " results for " + pending.size() + " inputs");
		}

		for (int i = 0; i < pending.size(); i++) {
			try {
				QueuedResult item = pending.get(i);
				CongestionEvaluator.Result eval = results[i];
				FilterDecision decision = eval.survived
						? FilterDecision.accept(String.format("cong=%.6f", eval.bestScore))
						: FilterDecision.rejectToCandidates();
				applyDecision(item, decision);
			} finally {
				markProcessed();
			}
		}
		pending.clear();
	}

	@Override
	public void close() {
		super.close();
		CongestionCuda.shutdown();
	}

	/**
	 * Builder mirroring {@link CongestionResultFilter.Builder} plus GPU batch settings.
	 */
	public static final class Builder {
		private final int maxQueueSize;
		private long[] seeds = new long[]{0L};
		private int[] checkpoints = new int[]{500};
		private Double[] thresholds = null;
		private int nRotations = 1;
		private int batchSize = 256;
		private long batchWaitMs = 30_000L;
		private double guardBand = 0.1;

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

		public Builder batchSize(int batchSize) {
			if (batchSize < 1) {
				throw new IllegalArgumentException("batchSize must be >= 1");
			}
			this.batchSize = batchSize;
			return this;
		}

		public Builder batchWaitMs(long batchWaitMs) {
			if (batchWaitMs < 0) {
				throw new IllegalArgumentException("batchWaitMs must be >= 0");
			}
			this.batchWaitMs = batchWaitMs;
			return this;
		}

		public Builder guardBand(double guardBand) {
			if (guardBand < 0.0) {
				throw new IllegalArgumentException("guardBand must be >= 0");
			}
			this.guardBand = guardBand;
			return this;
		}

		public CongestionResultFilterGPU build() {
			if (!CongestionCuda.isAvailable()) {
				throw new IllegalStateException("CUDA backend unavailable: " + CongestionCuda.deviceName());
			}
			CongestionEvaluatorGPU gpuEvaluator =
					new CongestionEvaluatorGPU(seeds, checkpoints, thresholds, nRotations, guardBand);
			return new CongestionResultFilterGPU(maxQueueSize, gpuEvaluator, batchSize, batchWaitMs);
		}
	}
}
