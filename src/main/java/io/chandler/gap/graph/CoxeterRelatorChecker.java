package io.chandler.gap.graph;

import java.util.Arrays;

import io.chandler.gap.GroupExplorer;

/**
 * Checks whether a set of involutions satisfies the Coxeter relators encoded in a
 * {@link CoxeterDynkinSpec}, allowing generators to be unlabeled in the input.
 */
public final class CoxeterRelatorChecker {
	private CoxeterRelatorChecker() {}

	public static final class MatchResult {
		public final boolean matches;
		/** Diagram-order indices into the original generator array, or null if no match. */
		public final int[] diagramOrder;
		public final String comment;

		private MatchResult(boolean matches, int[] diagramOrder, String comment) {
			this.matches = matches;
			this.diagramOrder = diagramOrder;
			this.comment = comment;
		}

		static MatchResult reject() {
			return new MatchResult(false, null, null);
		}

		static MatchResult accept(int[] diagramOrder, String comment) {
			return new MatchResult(true, diagramOrder.clone(), comment);
		}
	}

	public static MatchResult match(int[][][] generators, CoxeterDynkinSpec spec) {
		if (generators.length != spec.generatorCount()) {
			return MatchResult.reject();
		}

		int nElements = maxElement(generators);
		int[][] pairOrders = computePairOrders(generators, nElements);

		int n = spec.generatorCount();
		int[] perm = new int[n];
		for (int i = 0; i < n; i++) {
			perm[i] = i;
		}

		do {
			if (permutationMatchesSpec(perm, pairOrders, spec)) {
				int[] diagramOrder = invertPermutation(perm);
				String comment = formatLabels(spec, diagramOrder);
				return MatchResult.accept(diagramOrder, comment);
			}
		} while (nextPermutation(perm));

		return MatchResult.reject();
	}

	public static boolean isInvolution(int[][] cycles, int nElements) {
		int[] identity = identityState(nElements);
		int[] once = GroupExplorer.applyOperation(identity, cycles);
		int[] twice = GroupExplorer.applyOperation(once, cycles);
		return Arrays.equals(identity, twice);
	}

	public static int compositionOrder(int[][] left, int[][] right, int nElements) {
		int[] perm = compose(left, right, nElements);
		return permutationOrder(perm, nElements);
	}

	private static boolean permutationMatchesSpec(int[] perm, int[][] pairOrders, CoxeterDynkinSpec spec) {
		int n = spec.generatorCount();
		for (int i = 0; i < n; i++) {
			if (!isInvolutionFromPairMatrix(perm[i], pairOrders)) {
				return false;
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (pairOrders[perm[i]][perm[j]] != spec.requiredOrder(i, j)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isInvolutionFromPairMatrix(int genIndex, int[][] pairOrders) {
		return pairOrders[genIndex][genIndex] == 1;
	}

	private static int[][] computePairOrders(int[][][] generators, int nElements) {
		int n = generators.length;
		int[][] orders = new int[n][n];
		for (int i = 0; i < n; i++) {
			orders[i][i] = isInvolution(generators[i], nElements) ? 1 : -1;
			for (int j = i + 1; j < n; j++) {
				int order = compositionOrder(generators[i], generators[j], nElements);
				orders[i][j] = order;
				orders[j][i] = order;
			}
		}
		return orders;
	}

	private static int[] compose(int[][] left, int[][] right, int nElements) {
		int[] identity = identityState(nElements);
		int[] afterRight = GroupExplorer.applyOperation(identity, right);
		return GroupExplorer.applyOperation(afterRight, left);
	}

	private static int permutationOrder(int[] state, int nElements) {
		int[] identity = identityState(nElements);
		if (Arrays.equals(state, identity)) {
			return 1;
		}

		int[] power = Arrays.copyOf(state, state.length);
		int maxOrder = Math.max(24, nElements * 2);
		for (int k = 2; k <= maxOrder; k++) {
			power = multiplyStates(power, state);
			if (Arrays.equals(power, identity)) {
				return k;
			}
		}
		return -1;
	}

	/** Compose left ∘ right on one-line permutation states (1-based values). */
	private static int[] multiplyStates(int[] left, int[] right) {
		int[] result = new int[left.length];
		for (int i = 0; i < left.length; i++) {
			result[i] = left[right[i] - 1];
		}
		return result;
	}

	private static int maxElement(int[][][] generators) {
		int max = 0;
		for (int[][] gen : generators) {
			for (int[] cycle : gen) {
				for (int v : cycle) {
					max = Math.max(max, v);
				}
			}
		}
		return max;
	}

	private static int[] identityState(int nElements) {
		int[] state = new int[nElements];
		for (int i = 0; i < nElements; i++) {
			state[i] = i + 1;
		}
		return state;
	}

	private static int[] invertPermutation(int[] perm) {
		int[] inverse = new int[perm.length];
		for (int diagramPos = 0; diagramPos < perm.length; diagramPos++) {
			inverse[perm[diagramPos]] = diagramPos;
		}
		return inverse;
	}

	private static String formatLabels(CoxeterDynkinSpec spec, int[] diagramOrder) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < diagramOrder.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(spec.label(diagramOrder[i]));
		}
		return sb.toString();
	}

	private static boolean nextPermutation(int[] perm) {
		int n = perm.length;
		int i = n - 2;
		while (i >= 0 && perm[i] >= perm[i + 1]) {
			i--;
		}
		if (i < 0) {
			return false;
		}
		int j = n - 1;
		while (perm[j] <= perm[i]) {
		 j--;
		}
		swap(perm, i, j);
		for (int left = i + 1, right = n - 1; left < right; left++, right--) {
			swap(perm, left, right);
		}
		return true;
	}

	private static void swap(int[] arr, int i, int j) {
		int tmp = arr[i];
		arr[i] = arr[j];
		arr[j] = tmp;
	}
}
