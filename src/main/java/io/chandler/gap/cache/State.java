package io.chandler.gap.cache;

import java.math.BigInteger;
import java.util.Arrays;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.KeyframeStateCache.PrefixHash;

public abstract class State {
	public static State of(int[] state, int maxElement, MemorySettings mem) {
		if (mem.compressBits > 0) {
			throw new IllegalStateException(
				"COMPRESS mode uses KeyframeStateCache.tryAdd; do not call State.of directly");
		} else if (mem == MemorySettings.COMPACT) {
			return new StateFactorial(state, maxElement);
		} else if (mem == MemorySettings.FASTEST) {
			return new StateFast(state, maxElement);
		} else if (maxElement <= 31) {
			return new StateCompact(state, maxElement);
		} else if (maxElement <= 255) {
			return new StateByte(state, maxElement);
		} else {
			return new StateFast(state, maxElement);
		}
	}
	
	public abstract int[] state();

	/**
	 * Lightweight handle for COMPRESS mode. Frontier nodes retain a compact permutation
	 * ({@code byte[]} when {@code nElements <= 255}, otherwise {@code int[]});
	 * visited nodes drop it and reconstruct from {@link KeyframeStateCache} on demand.
	 */
	public static class StateCompressed extends State {
		public final int stateId;
		public final PrefixHash hash;
		private int[] permInt;
		private byte[] permBytes;
		private final KeyframeStateCache cache;

		public StateCompressed(int stateId, PrefixHash hash, int[] perm, KeyframeStateCache cache) {
			this.stateId = stateId;
			this.hash = hash;
			this.cache = cache;
			if (cache.nElements() <= 255) {
				this.permBytes = permToBytes(perm);
				this.permInt = null;
			} else {
				this.permInt = perm;
				this.permBytes = null;
			}
		}

		public void stripPerm() {
			this.permInt = null;
			this.permBytes = null;
		}

		public int getStateId() {
			return stateId;
		}

		/** Returns the frontier permutation if present, otherwise reconstructs. */
		public int[] frontierPerm() {
			if (permInt != null) {
				return permInt;
			}
			if (permBytes != null) {
				return permFromBytes(permBytes);
			}
			return cache.reconstruct(stateId);
		}

		public KeyframeStateCache cache() {
			return cache;
		}

		@Override
		public int[] state() {
			return frontierPerm();
		}

		private static byte[] permToBytes(int[] perm) {
			byte[] bytes = new byte[perm.length];
			for (int i = 0; i < perm.length; i++) {
				bytes[i] = (byte) perm[i];
			}
			return bytes;
		}

		private static int[] permFromBytes(byte[] perm) {
			int[] result = new int[perm.length];
			for (int i = 0; i < perm.length; i++) {
				result[i] = perm[i] & 0xff;
			}
			return result;
		}

		@Override
		public int hashCode() {
			return hash.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj instanceof StateCompressed) {
				return hash.equals(((StateCompressed) obj).hash);
			}
			return false;
		}
	}


	static class StateFactorial extends State {
        private final byte[] factorialRepresentation;
        private final short length;
        private StateFactorial(byte[] state, short length) {
            this.length = length;
            this.factorialRepresentation = state;
        }
        private StateFactorial(int[] state, int maxElement) {
            this.length = (short)state.length;
            this.factorialRepresentation = encodeFactorial(state).toByteArray();
        }

		public static int[] stateFromBytes(byte[] bytes, int maxElement) {
			return decodeFactorial(new BigInteger(bytes), maxElement);
		}

		public byte[] bytes() {
			return factorialRepresentation;
		}

		private BigInteger encodeFactorial(int[] state) {
			BigInteger result = BigInteger.ZERO;
			boolean[] used = new boolean[state.length + 1];
			
			for (int i = 0; i < state.length; i++) {
				int smaller = 0;
				for (int j = 1; j < state[i]; j++) {
					if (!used[j]) {
						smaller++;
					}
				}
				result = result.add(BigInteger.valueOf(smaller).multiply(factorial(state.length - 1 - i)));
				used[state[i]] = true;
			}
			return result;
		}
		private static final ThreadLocal<BigInteger[]> FACTORIAL_CACHE = ThreadLocal.withInitial(() -> new BigInteger[1000]);

		private static BigInteger factorial(int n) {
			if (n < 0) {
				throw new IllegalArgumentException("Factorial is not defined for negative numbers");
			}
			if (n < 1000) {
				BigInteger[] cache = FACTORIAL_CACHE.get();
				if (cache[n] == null) {
					cache[n] = calculateFactorial(n);
				}
				return cache[n];
			}
			return calculateFactorial(n);
		}
	
		private static BigInteger calculateFactorial(int n) {
			BigInteger result = BigInteger.ONE;
			for (int i = 2; i <= n; i++) {
				result = result.multiply(BigInteger.valueOf(i));
			}
			return result;
		}
	
		@Override
		public int[] state() {
			return decodeFactorial(new BigInteger(factorialRepresentation), length);
		}
	
		private static int[] decodeFactorial(BigInteger encoded, int length) {
			int[] result = new int[length];
			boolean[] used = new boolean[length + 1];
			
			for (int i = 0; i < length; i++) {
				BigInteger f = factorial(length - 1 - i);
				int count = encoded.divide(f).intValue();
				encoded = encoded.mod(f);
				
				for (int j = 1; j <= length; j++) {
					if (!used[j]) {
						if (count == 0) {
							result[i] = j;
							used[j] = true;
							break;
						}
						count--;
					}
				}
			}
			return result;
		}


        @Override
		public int hashCode() {
			byte[] data = factorialRepresentation;
			int length = data.length;
			int seed = 0x9747b28c;  // Arbitrary seed

			final int c1 = 0xcc9e2d51;
			final int c2 = 0x1b873593;

			int h1 = seed;
			int roundedEnd = (length & 0xfffffffc);  // round down to 4 byte block

			for (int i = 0; i < roundedEnd; i += 4) {
				int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
				k1 *= c1;
				k1 = (k1 << 15) | (k1 >>> 17);
				k1 *= c2;

				h1 ^= k1;
				h1 = (h1 << 13) | (h1 >>> 19);
				h1 = h1 * 5 + 0xe6546b64;
			}

			// tail
			int k1 = 0;
			switch (length & 0x03) {
				case 3:
					k1 = (data[roundedEnd + 2] & 0xff) << 16;
					// fallthrough
				case 2:
					k1 |= (data[roundedEnd + 1] & 0xff) << 8;
					// fallthrough
				case 1:
					k1 |= (data[roundedEnd] & 0xff);
					k1 *= c1;
					k1 = (k1 << 15) | (k1 >>> 17);
					k1 *= c2;
					h1 ^= k1;
			}

			// finalization
			h1 ^= length;
			h1 ^= h1 >>> 16;
			h1 *= 0x85ebca6b;
			h1 ^= h1 >>> 13;
			h1 *= 0xc2b2ae35;
			h1 ^= h1 >>> 16;

			return h1;
		}

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || !(obj instanceof StateFactorial)) return false;
            StateFactorial other = (StateFactorial) obj;
            return Arrays.equals(factorialRepresentation, other.factorialRepresentation);
        }
    }

	static class StateByte extends State {
		byte[] stateB;

		private StateByte(byte[] state, int maxElement) {
			this.stateB = state;
		}
		private StateByte(int[] state, int maxElement) {
			this.stateB = new byte[state.length];
			for (int i = 0; i < state.length; i++) {
				this.stateB[i] = (byte) state[i];
			}
		}
		@Override
		public int[] state() {
			int[] state = new int[stateB.length];
			for (int i = 0; i < stateB.length; i++) {
				state[i] = stateB[i] & 0xff;
			}
			return state;
		}
		public int hashCode() {
			return Arrays.hashCode(stateB);
		}
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || !(obj instanceof State)) return false;
			StateByte state1 = (StateByte) obj;
			return Arrays.equals(stateB, state1.stateB);
		}
	}


	static class StateFast extends State {
		int[] state;

		private StateFast(int[] state, int maxElement) {
			this.state = state;
		}
		@Override
		public int[] state() {
			return state;
		}
		public int hashCode() {
			return Arrays.hashCode(state);
		}
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || !(obj instanceof State)) return false;
			StateFast state1 = (StateFast) obj;
			return Arrays.equals(state, state1.state);
		}
	}


	static class StateCompact extends State {
        private static final int BITS_PER_ELEMENT = 5;
        private static final int ELEMENTS_PER_INT = 32 / BITS_PER_ELEMENT;
        private static final int ELEMENT_MASK = (1 << BITS_PER_ELEMENT) - 1;

        private int[] compactState;
		private final int length;

        private StateCompact(int[] state, int maxElement) {
            if (maxElement > 31) {
                throw new IllegalArgumentException("Max element is greater than 31");
            }
			this.length = state.length;
            this.compactState = new int[(state.length + ELEMENTS_PER_INT - 1) / ELEMENTS_PER_INT];
            for (int i = 0; i < state.length; i++) {
                setElement(i, state[i]);
            }
        }

        @Override
        public int[] state() {
            int[] result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = getElement(i);
            }
            return result;
        }

        private void setElement(int index, int value) {
            int arrayIndex = index / ELEMENTS_PER_INT;
            int bitOffset = (index % ELEMENTS_PER_INT) * BITS_PER_ELEMENT;
            compactState[arrayIndex] &= ~(ELEMENT_MASK << bitOffset);
            compactState[arrayIndex] |= (value & ELEMENT_MASK) << bitOffset;
        }

        private int getElement(int index) {
            int arrayIndex = index / ELEMENTS_PER_INT;
            int bitOffset = (index % ELEMENTS_PER_INT) * BITS_PER_ELEMENT;
            return (compactState[arrayIndex] >> bitOffset) & ELEMENT_MASK;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(compactState);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || !(obj instanceof StateCompact)) return false;
            StateCompact other = (StateCompact) obj;
            return Arrays.equals(compactState, other.compactState);
        }

		public static void main(String[] args) {
			testStateFactorial();
		}
	
		private static void testStateFactorial() {
			int[][] testCases = {
				{1, 2, 3},
				{3, 2, 1},
				{2, 1, 3},
				{1, 2, 3, 4, 5},
				{5, 4, 3, 2, 1},
				{2, 4, 1, 5, 3}
			};
	
			for (int[] testCase : testCases) {
				System.out.println("Testing: " + Arrays.toString(testCase));
				StateFactorial state = new StateFactorial(testCase, testCase.length);
				BigInteger encoded = new BigInteger(state.factorialRepresentation);
				int[] decoded = state.state();
				System.out.println("Encoded: " + encoded);
				System.out.println("Decoded: " + Arrays.toString(decoded));
				System.out.println("Match: " + Arrays.equals(testCase, decoded));
				System.out.println();
			}
		}
    }

}
