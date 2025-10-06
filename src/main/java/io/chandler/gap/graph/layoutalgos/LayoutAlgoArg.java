package io.chandler.gap.graph.layoutalgos;

/**
 * These are all double arguments that can be passed to a layout algorithm.
 * Some like SHOW_FITTED_NODES are boolean arguments, but are stored as doubles.
 */
public enum LayoutAlgoArg {
	TRIES,
	INITIAL_ITERS,
	ITERS,
	SEED,
	THETA,
	NORM,
	SHOW_FITTED_NODES,
	REPULSION_FACTOR,
	W,
	H,
	SOLUTION,
	ALL_SOLUTIONS,
}
