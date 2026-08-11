package io.chandler.gap.graph.filter;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.chandler.gap.graph.CongestionCuda;
import io.chandler.gap.graph.CongestionEvaluator;
import io.chandler.gap.graph.CongestionEvaluatorGPU;

/**
 * GPU-batched result filter using the same congestion pipeline as
 * {@link CongestionResultFilter}. Accumulates up to {@code batchSize} results
 * (or {@code batchWaitMs} since the first item) before submitting a batch to
 * {@link CongestionEvaluatorGPU}.
 * <p>
 * When the GPU input queue is near capacity ({@code remainingCapacity <= overflowBelowRemaining}),
 * new results are diverted to a parallel {@link CongestionResultFilter} CPU overflow pool
 * so study workers are less likely to block on {@link #add}.
 * <p>
 * Guard-band hits after a GPU batch are also deferred to that CPU pool (not run inline on
 * the single GPU thread), so ambiguous graphs do not stall the next CUDA batch.
 */
public final class CongestionResultFilterGPU extends AbstractResultFilter {
	private static final long POLL_CHECK_MS = 100L;

	private final CongestionEvaluatorGPU gpuEvaluator;
	private final CongestionResultFilter cpuOverflow;
	private final int batchSize;
	private final long batchWaitMs;
	/** Divert to CPU when GPU {@link #remainingCapacity()} is at most this. */
	private final int overflowBelowRemaining;
	private final AtomicInteger overflowedToCpu = new AtomicInteger();

	private CongestionResultFilterGPU(int maxQueueSize, CongestionEvaluatorGPU gpuEvaluator,
			CongestionResultFilter cpuOverflow, int batchSize, long batchWaitMs,
			int overflowBelowRemaining) {
		super(maxQueueSize, 1, "planar-study-gpu-filter-", Thread.MAX_PRIORITY);
		this.gpuEvaluator = gpuEvaluator;
		this.cpuOverflow = cpuOverflow;
		this.batchSize = batchSize;
		this.batchWaitMs = batchWaitMs;
		this.overflowBelowRemaining = overflowBelowRemaining;
	}

	public static Builder builder(int maxQueueSize) {
		return new Builder(maxQueueSize);
	}

	/** How many results were sent to the CPU overflow filter. */
	public int overflowedToCpuCount() {
		return overflowedToCpu.get();
	}

	@Override
	protected FilterDecision process(String result) {
		throw new UnsupportedOperationException("GPU filter uses batched runLoop");
	}

	@Override
	public void add(String result, boolean lastLoop, Runnable retainCandidate)
			throws InterruptedException {
		if (shouldOverflowToCpu()) {
			divertToCpu(result, lastLoop, retainCandidate);
			return;
		}
		if (tryAdd(result, lastLoop, retainCandidate)) {
			return;
		}
		// Race: queue filled after the capacity check.
		divertToCpu(result, lastLoop, retainCandidate);
	}

	private boolean shouldOverflowToCpu() {
		return remainingCapacity() <= overflowBelowRemaining;
	}

	private void divertToCpu(String result, boolean lastLoop, Runnable retainCandidate)
			throws InterruptedException {
		int n = overflowedToCpu.incrementAndGet();
		if (n == 1 || n % 1000 == 0) {
			System.out.println("  GPU filter near capacity -> CPU overflow (" + n
				+ " total, gpuRemaining=" + remainingCapacity()
				+ "/" + queueCapacity()
				+ ", threshold<=" + overflowBelowRemaining + ")");
		}
		cpuOverflow.add(result, lastLoop, retainCandidate);
	}

	@Override
	public void setOutput(PrintStream out) {
		super.setOutput(out);
		cpuOverflow.setOutput(out);
	}

	@Override
	public void resetStats(int acceptedSoFar) {
		super.resetStats(acceptedSoFar);
		cpuOverflow.resetStats(0);
	}

	@Override
	public int acceptedCount() {
		return super.acceptedCount() + cpuOverflow.acceptedCount();
	}

	@Override
	public int rejectedCount() {
		return super.rejectedCount() + cpuOverflow.rejectedCount();
	}

	@Override
	public int queuedCount() {
		return super.queuedCount() + cpuOverflow.queuedCount();
	}

	@Override
	public int failureCount() {
		return super.failureCount() + cpuOverflow.failureCount();
	}

	@Override
	public int threadCount() {
		return super.threadCount() + cpuOverflow.threadCount();
	}

	@Override
	public void setPauseOnFailure(boolean pauseOnFailure) {
		super.setPauseOnFailure(pauseOnFailure);
		cpuOverflow.setPauseOnFailure(pauseOnFailure);
	}

	@Override
	public void setFailurePauseHook(Runnable failurePauseHook) {
		super.setFailurePauseHook(failurePauseHook);
		cpuOverflow.setFailurePauseHook(failurePauseHook);
	}

	@Override
	public void drain() {
		super.drain();
		cpuOverflow.drain();
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
				try {
					if (pending.isEmpty()) {
						item = take();
					} else {
						long remaining = batchDeadlineMs - System.currentTimeMillis();
						long pollTimeoutMs = Math.min(POLL_CHECK_MS, Math.max(1L, remaining));
						item = poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
					}
				} catch (InterruptedException e) {
					if (isShutdown()) {
						break;
					}
					Thread.currentThread().interrupt();
					continue;
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
		} finally {
			// If we abort with items still pending (e.g. unexpected Error after take),
			// release queued accounting so drain()/producers cannot wedge forever.
			if (!pending.isEmpty()) {
				failPendingBatch(pending,
					new IllegalStateException("GPU filter leaving runLoop with " + pending.size() + " pending"));
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
		return isInputQueueEmpty() && pending.size() == CongestionResultFilterGPU.super.queuedCount();
	}

	private void flushBatch(List<QueuedResult> pending) {
		List<String> lines = new ArrayList<>(pending.size());
		for (QueuedResult item : pending) {
			lines.add(item.result);
		}

		CongestionEvaluatorGPU.BatchOutcome outcome;
		try {
			// Do not run guard-band CPU inline — that stalls this sole GPU thread.
			// Ambiguous graphs are handed to cpuOverflow instead.
			outcome = gpuEvaluator.evaluateBatch(lines, false);
			if (outcome.results.length != pending.size()) {
				throw new IllegalStateException(
						"GPU batch returned " + outcome.results.length + " results for " + pending.size() + " inputs");
			}
		} catch (Throwable t) {
			failPendingBatch(pending, t);
			return;
		}

		int deferred = outcome.guardBandFallbacks;
		if (deferred > 0) {
			System.out.println("  GPU batch n=" + pending.size()
					+ " deferred " + deferred + " guard-band hit(s) to CPU overflow"
					+ " (gpuRemaining=" + remainingCapacity() + ")");
		}

		for (int i = 0; i < pending.size(); i++) {
			QueuedResult item = pending.get(i);
			try {
				if (outcome.needsGuardBandFallback[i]) {
					// Full CPU re-eval on overflow pool; do not trust provisional GPU scores.
					cpuOverflow.add(item.result, item.lastLoop, item.retainCandidate);
				} else {
					CongestionEvaluator.Result eval = outcome.results[i];
					FilterDecision decision = eval.survived
							? FilterDecision.accept(String.format("cong=%.6f", eval.bestScore))
							: FilterDecision.rejectToCandidates();
					applyDecision(item, decision);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				notifyFailure("GPU defer guard-band to CPU interrupted i=" + i, e);
				try {
					applyDecision(item, FilterDecision.rejectToCandidates());
				} catch (Throwable t2) {
					e.addSuppressed(t2);
				}
			} catch (Throwable t) {
				notifyFailure("GPU applyDecision i=" + i + " / " + pending.size(), t);
				try {
					applyDecision(item, FilterDecision.rejectToCandidates());
				} catch (Throwable t2) {
					t.addSuppressed(t2);
				}
			} finally {
				// Item left the GPU queue (either decided here or re-queued on CPU).
				markProcessed();
			}
		}
		pending.clear();
	}

	/** Report failure, print sample inputs for reproduction, reject-to-candidates, clear pending. */
	private void failPendingBatch(List<QueuedResult> pending, Throwable t) {
		notifyFailure("GPU flushBatch n=" + pending.size(), t);
		int samples = Math.min(5, pending.size());
		for (int i = 0; i < samples; i++) {
			System.err.println("  failed-input[" + i + "]: " + pending.get(i).result);
		}
		if (pending.size() > samples) {
			System.err.println("  ... (" + (pending.size() - samples) + " more)");
		}
		System.err.flush();
		for (QueuedResult item : pending) {
			try {
				applyDecision(item, FilterDecision.rejectToCandidates());
			} catch (Throwable t2) {
				t.addSuppressed(t2);
			} finally {
				markProcessed();
			}
		}
		pending.clear();
	}

	@Override
	public void close() {
		super.close();
		cpuOverflow.close();
		CongestionCuda.shutdown();
	}

	/**
	 * Builder mirroring {@link CongestionResultFilter.Builder} plus GPU batch settings
	 * and CPU overflow controls.
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
		private int cpuOverflowThreads = Runtime.getRuntime().availableProcessors();
		private int cpuOverflowQueueSize = -1; // <=0 => same as maxQueueSize
		/** When GPU remaining capacity is <= this, divert to CPU. <=0 => default 2*batchSize. */
		private int overflowBelowRemaining = -1;

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

		/** CPU overflow worker threads (default: available processors). */
		public Builder cpuOverflowThreads(int cpuOverflowThreads) {
			if (cpuOverflowThreads < 1) {
				throw new IllegalArgumentException("cpuOverflowThreads must be >= 1");
			}
			this.cpuOverflowThreads = cpuOverflowThreads;
			return this;
		}

		/** CPU overflow queue capacity (default: same as GPU maxQueueSize).
		 *  When full, {@code add()} blocks study workers on the CPU path. */
		public Builder cpuOverflowQueueSize(int cpuOverflowQueueSize) {
			if (cpuOverflowQueueSize < 1) {
				throw new IllegalArgumentException("cpuOverflowQueueSize must be >= 1");
			}
			this.cpuOverflowQueueSize = cpuOverflowQueueSize;
			return this;
		}

		/**
		 * Divert to CPU when GPU {@code remainingCapacity() <= threshold}.
		 * Default: {@code 2 * batchSize}.
		 */
		public Builder overflowBelowRemaining(int overflowBelowRemaining) {
			if (overflowBelowRemaining < 0) {
				throw new IllegalArgumentException("overflowBelowRemaining must be >= 0");
			}
			this.overflowBelowRemaining = overflowBelowRemaining;
			return this;
		}

		public CongestionResultFilterGPU build() {
			if (!CongestionCuda.isAvailable()) {
				throw new IllegalStateException("CUDA backend unavailable: " + CongestionCuda.deviceName());
			}
			int overflowThreshold = overflowBelowRemaining >= 0
				? overflowBelowRemaining
				: Math.max(1, 2 * batchSize);
			int cpuQueue = cpuOverflowQueueSize > 0 ? cpuOverflowQueueSize : maxQueueSize;

			CongestionResultFilter.Builder cpuBuilder = CongestionResultFilter.builder(cpuQueue)
				.seeds(seeds)
				.checkpoints(checkpoints)
				.nRotations(nRotations)
				.threads(cpuOverflowThreads)
				.workerThreadPrefix("planar-study-cpu-overflow-")
				.workerPriority(Thread.MAX_PRIORITY - 1); // above study workers; below GPU batcher
			if (thresholds != null) {
				double[] th = new double[thresholds.length];
				for (int i = 0; i < thresholds.length; i++) {
					th[i] = thresholds[i];
				}
				cpuBuilder.thresholds(th);
			} else {
				cpuBuilder.thresholds((double[]) null);
			}
			CongestionResultFilter cpuOverflow = cpuBuilder.build();

			CongestionEvaluatorGPU gpuEvaluator =
					new CongestionEvaluatorGPU(seeds, checkpoints, thresholds, nRotations, guardBand);
			System.out.println("GPU congestion filter: overflow to CPU when remainingCapacity <= "
				+ overflowThreshold + " (CPU threads " + cpuOverflowThreads
				+ ", CPU queue " + cpuQueue
				+ ", priorities GPU=" + Thread.MAX_PRIORITY
				+ " CPU=" + (Thread.MAX_PRIORITY - 1) + ")");
			return new CongestionResultFilterGPU(
				maxQueueSize, gpuEvaluator, cpuOverflow, batchSize, batchWaitMs, overflowThreshold);
		}
	}

	@Override
	public String shortFilterName() {
		return "congestion-gpu";
	}
}
