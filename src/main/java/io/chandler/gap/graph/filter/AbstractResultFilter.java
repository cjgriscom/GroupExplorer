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
	private final Thread[] workers;
	private final AtomicInteger accepted = new AtomicInteger();
	private final AtomicInteger rejected = new AtomicInteger();
	private final AtomicInteger queued = new AtomicInteger();
	private final Object drainLock = new Object();
	private final Object handleLock = new Object();
	private volatile PrintStream out;
	private volatile boolean shutdown;

	protected AbstractResultFilter(int maxQueueSize) {
		this(maxQueueSize, 1);
	}

	protected AbstractResultFilter(int maxQueueSize, int threads) {
		if (maxQueueSize < 1) {
			throw new IllegalArgumentException("maxQueueSize must be >= 1");
		}
		if (threads < 1) {
			throw new IllegalArgumentException("threads must be >= 1");
		}
		this.queue = new ArrayBlockingQueue<>(maxQueueSize);
		this.workers = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			Thread worker = new Thread(this::runLoop, "planar-study-result-filter-" + i);
			worker.setDaemon(true);
			workers[i] = worker;
			worker.start();
		}
	}

	/** Number of filter worker threads. */
	public final int threadCount() {
		return workers.length;
	}

	/** Subclasses decide accept / reject-to-candidates / discard for each result. */
	protected abstract FilterDecision process(String result);

	public final void setOutput(PrintStream out) {
		this.out = out;
	}

	public final void resetStats(int acceptedSoFar) {
		accepted.set(acceptedSoFar);
		rejected.set(0);
	}

	public final int acceptedCount() { return accepted.get(); }
	public final int rejectedCount() { return rejected.get(); }
	public final int queuedCount() { return queued.get(); }

	/**
	 * Enqueue a result for async filtering. Blocks if the queue is at capacity.
	 *
	 * @param result           generator result string
	 * @param lastLoop         when true, rejected results are always discarded
	 * @param retainCandidate  run if the result should remain a next-round candidate
	 *                         (accept, or reject-to-candidates when not lastLoop);
	 *                         may be null
	 */
	public final void add(String result, boolean lastLoop, Runnable retainCandidate)
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
	public final void drain() {
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
				handle(item);
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
				handle(leftover);
			} finally {
				markProcessed();
			}
		}
	}

	protected final void handle(QueuedResult item) {
		FilterDecision decision = process(item.result);
		applyDecision(item, decision);
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
	}
}
