package io.chandler.gap.graph.filter;

import java.io.PrintStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async bounded queue that filters result strings before writing them to the
 * output file. Subclasses implement {@link #process(String)} to accept
 * (optionally with a comment), reject-to-candidates, or discard.
 * <p>
 * {@link #add(String, boolean, Runnable)} blocks when the queue is full.
 * On {@code lastLoop}, reject-to-candidates is forced to discard.
 * <p>
 * When constructed with {@code threads > 1}, multiple workers compete on the
 * same queue so {@link #process(String)} can run in parallel.
 * <p>
 * Worker crashes are reported loudly, optionally pause the study via
 * {@link #setFailurePauseHook(Runnable)}, and the worker auto-restarts unless
 * {@link #close()} has been called.
 */
public abstract class AbstractResultFilter {
	protected static final class QueuedResult {
		public final String result;
		public final boolean lastLoop;
		public final Runnable retainCandidate;

		QueuedResult(String result, boolean lastLoop, Runnable retainCandidate) {
			this.result = result;
			this.lastLoop = lastLoop;
			this.retainCandidate = retainCandidate;
		}
	}

	private final ArrayBlockingQueue<QueuedResult> queue;
	private final int maxQueueSize;
	private final Thread[] workers;
	private final AtomicInteger accepted = new AtomicInteger();
	private final AtomicInteger rejected = new AtomicInteger();
	private final AtomicInteger queued = new AtomicInteger();
	private final AtomicInteger failures = new AtomicInteger();
	private final AtomicInteger restarts = new AtomicInteger();
	private final Object drainLock = new Object();
	private final Object handleLock = new Object();
	private volatile PrintStream out;
	private volatile boolean shutdown;
	private volatile boolean pauseOnFailure = true;
	private volatile Runnable failurePauseHook;

	protected AbstractResultFilter(int maxQueueSize) {
		this(maxQueueSize, 1);
	}

	protected AbstractResultFilter(int maxQueueSize, int threads) {
		this(maxQueueSize, threads, "planar-study-result-filter-");
	}

	protected AbstractResultFilter(int maxQueueSize, int threads, String workerThreadPrefix) {
		this(maxQueueSize, threads, workerThreadPrefix, Thread.NORM_PRIORITY);
	}

	protected AbstractResultFilter(int maxQueueSize, int threads, String workerThreadPrefix,
			int workerPriority) {
		if (maxQueueSize < 1) {
			throw new IllegalArgumentException("maxQueueSize must be >= 1");
		}
		if (threads < 1) {
			throw new IllegalArgumentException("threads must be >= 1");
		}
		if (workerThreadPrefix == null || workerThreadPrefix.isEmpty()) {
			throw new IllegalArgumentException("workerThreadPrefix must be non-empty");
		}
		int priority = clampPriority(workerPriority);
		this.maxQueueSize = maxQueueSize;
		this.queue = new ArrayBlockingQueue<>(maxQueueSize);
		this.workers = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			final int index = i;
			Thread worker = new Thread(() -> runSupervised(index), workerThreadPrefix + i);
			worker.setDaemon(true);
			worker.setPriority(priority);
			workers[i] = worker;
			worker.start();
		}
	}

	private static int clampPriority(int workerPriority) {
		if (workerPriority < Thread.MIN_PRIORITY) {
			return Thread.MIN_PRIORITY;
		}
		if (workerPriority > Thread.MAX_PRIORITY) {
			return Thread.MAX_PRIORITY;
		}
		return workerPriority;
	}

	/** Number of filter worker threads. */
	public int threadCount() {
		return workers.length;
	}

	/** Configured input queue capacity. */
	public final int queueCapacity() {
		return maxQueueSize;
	}

	/** Free slots in the input queue (0 = full). */
	public final int remainingCapacity() {
		return queue.remainingCapacity();
	}

	/** Items waiting in the input queue (excludes in-flight / batch-buffered accounting quirks beyond {@link #queuedCount()}). */
	public final int inputQueueSize() {
		return queue.size();
	}

	/** Cumulative process/batch failures (does not include clean shutdown). */
	public int failureCount() {
		return failures.get();
	}

	/** How many times workers re-entered {@link #runLoop()} after a crash. */
	public int restartCount() {
		return restarts.get();
	}

	/**
	 * When true (default), {@link #notifyFailure(String, Throwable)} invokes
	 * {@link #setFailurePauseHook(Runnable)} so the study can pause for inspection.
	 */
	public void setPauseOnFailure(boolean pauseOnFailure) {
		this.pauseOnFailure = pauseOnFailure;
	}

	public boolean isPauseOnFailure() {
		return pauseOnFailure;
	}

	/** Optional hook (e.g. set PlanarStudy paused) when a failure is reported and pause-on-failure is on. */
	public void setFailurePauseHook(Runnable failurePauseHook) {
		this.failurePauseHook = failurePauseHook;
	}

	/** Subclasses decide accept / reject-to-candidates / discard for each result. */
	protected abstract FilterDecision process(String result);

	public void setOutput(PrintStream out) {
		this.out = out;
	}

	public void resetStats(int acceptedSoFar) {
		accepted.set(acceptedSoFar);
		rejected.set(0);
	}

	public int acceptedCount() { return accepted.get(); }
	public int rejectedCount() { return rejected.get(); }
	public int queuedCount() { return queued.get(); }

	/**
	 * Enqueue a result for async filtering. Blocks if the queue is at capacity.
	 *
	 * @param result           generator result string
	 * @param lastLoop         when true, rejected results are always discarded
	 * @param retainCandidate  run if the result should remain a next-round candidate
	 *                         (accept, or reject-to-candidates when not lastLoop);
	 *                         may be null
	 */
	public void add(String result, boolean lastLoop, Runnable retainCandidate)
			throws InterruptedException {
		queued.incrementAndGet();
		try {
			queue.put(new QueuedResult(result, lastLoop, retainCandidate));
		} catch (InterruptedException e) {
			queued.decrementAndGet();
			notifyDrainWaiters();
			throw e;
		}
	}

	/**
	 * Non-blocking enqueue. Returns false if the queue is full (caller still owns the work).
	 */
	protected final boolean tryAdd(String result, boolean lastLoop, Runnable retainCandidate) {
		queued.incrementAndGet();
		if (queue.offer(new QueuedResult(result, lastLoop, retainCandidate))) {
			return true;
		}
		queued.decrementAndGet();
		notifyDrainWaiters();
		return false;
	}

	/** {@link #add} wrapping {@link InterruptedException} as unchecked. */
	public final void addUnchecked(String result, boolean lastLoop, Runnable retainCandidate) {
		try {
			add(result, lastLoop, retainCandidate);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while queueing result", e);
		}
	}

	/** Block until all queued results have been processed. */
	public void drain() {
		synchronized (drainLock) {
			while (queued.get() > 0) {
				try {
					drainLock.wait(100L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	public void close() {
		shutdown = true;
		for (Thread worker : workers) {
			worker.interrupt();
		}
		for (Thread worker : workers) {
			try {
				worker.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	protected final boolean isShutdown() {
		return shutdown;
	}

	protected final QueuedResult poll(long timeout, TimeUnit unit) throws InterruptedException {
		return queue.poll(timeout, unit);
	}

	protected final QueuedResult take() throws InterruptedException {
		return queue.take();
	}

	protected final QueuedResult pollNonBlocking() {
		return queue.poll();
	}

	/** True when no results are waiting in the input queue (may still be in a batch buffer). */
	protected final boolean isInputQueueEmpty() {
		return queue.isEmpty();
	}

	protected final void markProcessed() {
		queued.decrementAndGet();
		notifyDrainWaiters();
	}

	/**
	 * Loud failure report; increments {@link #failureCount()}. When
	 * {@link #isPauseOnFailure()} is set, runs the pause hook.
	 */
	protected final void notifyFailure(String context, Throwable t) {
		int n = failures.incrementAndGet();
		String thread = Thread.currentThread().getName();
		System.err.println();
		System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		System.err.println("RESULT FILTER FAILURE #" + n + " on " + thread);
		System.err.println("  context: " + context);
		System.err.println("  filter:  " + getClass().getSimpleName() + " / " + shortFilterName());
		System.err.println("  queued:  " + queued.get() + "  accepted: " + accepted.get()
			+ "  rejected: " + rejected.get()
			+ "  restarts: " + restarts.get());
		if (t != null) {
			System.err.println("  error:   " + t.getClass().getName() + ": " + t.getMessage());
			t.printStackTrace(System.err);
		}
		if (pauseOnFailure) {
			System.err.println("  pause_on_failure: ON — pausing study workers (type 'resume' to continue)");
			Runnable hook = failurePauseHook;
			if (hook != null) {
				try {
					hook.run();
				} catch (Throwable hookError) {
					System.err.println("  pause hook failed: " + hookError);
					hookError.printStackTrace(System.err);
				}
			}
		} else {
			System.err.println("  pause_on_failure: OFF — continuing / auto-restarting");
		}
		System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		System.err.println();
		System.err.flush();
	}

	private void runSupervised(int index) {
		while (!shutdown) {
			try {
				runLoop();
				// Normal return: shutdown completed inside runLoop.
				return;
			} catch (Throwable t) {
				if (shutdown) {
					return;
				}
				notifyFailure("runLoop crashed (worker " + index + ")", t);
			}
			if (shutdown) {
				return;
			}
			int r = restarts.incrementAndGet();
			System.err.println("Auto-restarting planar-study-result-filter-" + index
				+ " (restart #" + r + ") in 1s...");
			System.err.flush();
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				if (shutdown) {
					return;
				}
				Thread.currentThread().interrupt();
			}
		}
	}

	protected void runLoop() {
		while (!shutdown) {
			QueuedResult item;
			try {
				item = queue.take();
			} catch (InterruptedException e) {
				if (shutdown) break;
				continue;
			}
			try {
				safeHandle(item);
			} finally {
				markProcessed();
			}
		}
		drainLeftoverQueue();
	}

	protected final void drainLeftoverQueue() {
		QueuedResult leftover;
		while ((leftover = queue.poll()) != null) {
			try {
				safeHandle(leftover);
			} finally {
				markProcessed();
			}
		}
	}

	/** Process one item; on failure reject-to-candidates and report. */
	protected final void safeHandle(QueuedResult item) {
		try {
			FilterDecision decision = process(item.result);
			applyDecision(item, decision);
		} catch (Throwable t) {
			notifyFailure("process: " + truncate(item.result, 120), t);
			try {
				applyDecision(item, FilterDecision.rejectToCandidates());
			} catch (Throwable t2) {
				t.addSuppressed(t2);
			}
		}
	}

	protected final void handle(QueuedResult item) {
		safeHandle(item);
	}

	protected final void applyDecision(QueuedResult item, FilterDecision decision) {
		if (item.lastLoop && decision.isReject()) {
			decision = FilterDecision.rejectDiscard();
		}
		synchronized (handleLock) {
			switch (decision.kind) {
				case ACCEPT: {
					PrintStream sink = out;
					if (sink != null) {
						if (decision.comment != null && !decision.comment.isEmpty()) {
							sink.println(item.result + " # " + decision.comment);
						} else {
							sink.println(item.result);
						}
					}
					accepted.incrementAndGet();
					if (item.retainCandidate != null) {
						item.retainCandidate.run();
					}
					break;
				}
				case REJECT_TO_CANDIDATES:
					rejected.incrementAndGet();
					if (item.retainCandidate != null) {
						item.retainCandidate.run();
					}
					break;
				case REJECT_DISCARD:
					rejected.incrementAndGet();
					break;
				default:
					throw new IllegalStateException("Unknown decision: " + decision.kind);
			}
		}
	}

	private static String truncate(String s, int max) {
		if (s == null) return "null";
		if (s.length() <= max) return s;
		return s.substring(0, max) + "...";
	}

	private void notifyDrainWaiters() {
		synchronized (drainLock) {
			drainLock.notifyAll();
		}
	}

	/**
	 * Decision returned by {@link AbstractResultFilter#process(String)}.
	 */
	public static final class FilterDecision {
		enum Kind { ACCEPT, REJECT_TO_CANDIDATES, REJECT_DISCARD }

		final Kind kind;
		/** Optional comment appended as {@code " # " + comment} when accepting. */
		final String comment;

		private FilterDecision(Kind kind, String comment) {
			this.kind = kind;
			this.comment = comment;
		}

		static FilterDecision accept() {
			return accept(null);
		}

		static FilterDecision accept(String comment) {
			return new FilterDecision(Kind.ACCEPT, comment);
		}

		static FilterDecision rejectToCandidates() {
			return new FilterDecision(Kind.REJECT_TO_CANDIDATES, null);
		}

		static FilterDecision rejectDiscard() {
			return new FilterDecision(Kind.REJECT_DISCARD, null);
		}

		boolean isReject() {
			return kind == Kind.REJECT_TO_CANDIDATES || kind == Kind.REJECT_DISCARD;
		}
	}

	/** Pass-through filter: never rejects and never adds a comment. */
	public static final class NoOpResultFilter extends AbstractResultFilter {
		public NoOpResultFilter(int maxQueueSize) {
			super(maxQueueSize);
		}

		@Override
		protected FilterDecision process(String result) {
			return FilterDecision.accept();
		}

		@Override
		public String shortFilterName() {
			return "filtered"; // pass thru to isomorphism filter, legacy filename "filtered"
		}
	}

	public abstract String shortFilterName();
}
