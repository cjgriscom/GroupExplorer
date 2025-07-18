package io.chandler.gap.morajai;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.chandler.gap.morajai.MoraJaiBox.Color;

public class MJColorPrune {

	private static final int blueOrd = C_BU.ordinal();
	private static final int redOrd = C_RD.ordinal();
	private static final int greenOrd = C_GN.ordinal();
	private static final int yellowOrd = C_YE.ordinal();
	private static final int purpleOrd = C_PU.ordinal();
	private static final int orangeOrd = C_OR.ordinal();
	private static final int pinkOrd = C_PI.ordinal();
	private static final int blackOrd = C_BK.ordinal();
	private static final int whiteOrd = C_WH.ordinal();
	private static final int grayOrd = C_GY.ordinal();

	public static int prune(ExecutorService executor, boolean noBlue, Color[] targetColors, Consumer<Integer> markDead) {

		int prunedDead = 0;
	
		// Process states in parallel chunks
		int numChunks = 1000;
		int chunkSize = 1000000000 / numChunks;

		AtomicInteger atomicProgressCount = new AtomicInteger(0);
		AtomicInteger atomicDeadStatesCount = new AtomicInteger(0);

		for (int worker = 0; worker < numChunks; worker++) {
			int chunkIndex = worker;
			executor.submit(() -> {

				int startState = chunkIndex * chunkSize;
				int endState = Math.min(startState + chunkSize, 1000000000);

				BitSet states = new BitSet();

				int localProgressCount = 0;

				byte[] cnt = new byte[10];

				for (int state = startState; state < endState; state++) {
					fillCounts(state, cnt);
					localProgressCount++;
					int targetColorCount = 0;

					if (noBlue && cnt[blueOrd] > 0) {
						states.set(state-startState);
					}
					boolean containsWhite = false;
					if ((targetColorCount = countTarget(targetColors, C_WH)) != 0) {
						containsWhite = true;
						// Can't materialize white
						if (cnt[whiteOrd] == 0) states.set(state-startState);
					}
					if ((targetColorCount = countTarget(targetColors, C_GY)) != 0 || containsWhite) {
						int greysAndWhites = cnt[grayOrd] + cnt[whiteOrd];
						if (greysAndWhites == 0) {
							states.set(state-startState);
						} else if (greysAndWhites < targetColorCount) {
							// Orange and blue can both generate more white/gry
							if (	cnt[orangeOrd] +
										cnt[blueOrd] +
										greysAndWhites
										< targetColorCount) {
								states.set(state-startState);
							}
						}
					}
					if (basicColorPrune(targetColors, C_GN, greenOrd, cnt)) states.set(state-startState);
					if (basicColorPrune(targetColors, C_YE, yellowOrd, cnt)) states.set(state-startState);
					if (basicColorPrune(targetColors, C_PU, purpleOrd, cnt)) states.set(state-startState);
					if (basicColorPrune(targetColors, C_PI, pinkOrd, cnt)) states.set(state-startState);

					// Orange
					if ((targetColorCount = countTarget(targetColors, C_OR)) != 0) {
						int oranges = cnt[orangeOrd];
						if (oranges == 0) {
							states.set(state-startState);
						} else if (oranges == 1) {
							if (targetColorCount != 1) states.set(state-startState); 
						} else if (oranges < targetColorCount) {
							// Sum blue and grey and white
							int bgw = cnt[grayOrd] + cnt[blueOrd] + cnt[whiteOrd];
							// Simplified but good enough
							if (oranges + bgw < targetColorCount) {
								states.set(state-startState);
							}
						}
					}

					int reds = cnt[redOrd];
					// Red is maybe the most complicated, keep it simple for now
					if ((targetColorCount = countTarget(targetColors, C_RD)) != 0) {
						// Ensure at least one red
						if (reds == 0) states.set(state-startState);
						else {
							// There's at least one red
							if (reds < targetColorCount) {
								// Not enough red, for simplicity just sum all morphing combos
								int orangeBluesBlack = cnt[orangeOrd] + cnt[blueOrd] + cnt[blackOrd];
								int whitesAndGreys = cnt[whiteOrd] + cnt[grayOrd];
								if (orangeBluesBlack + whitesAndGreys + reds < targetColorCount) {
									states.set(state-startState);
								}
							}
						}
					}

					// Black
					if ((targetColorCount = countTarget(targetColors, C_BK)) != 0) {
						if (reds == 0) {
							// No red so black is a basic color
							if (basicColorPrune(targetColors, C_BK, blackOrd, cnt)) {
								states.set(state-startState);
							}
						} else {
							// For simplicity just sum all morphing combos
							int orangeBluesBlack = cnt[orangeOrd] + cnt[blueOrd] + cnt[blackOrd];
							int whitesAndGreys = cnt[whiteOrd] + cnt[grayOrd];
							if (orangeBluesBlack + whitesAndGreys < targetColorCount) {
								states.set(state-startState);
							}
						}
					}

					// Blue is also complicated
					if ((targetColorCount = countTarget(targetColors, C_BU)) != 0) {
						// Sum blue, orange, white, grey
						int blue = cnt[blueOrd];
						int whiteGrey = cnt[whiteOrd] + cnt[grayOrd];
						int orange = cnt[orangeOrd];

						if (reds == 0) {
							if (blue + whiteGrey + orange < targetColorCount) {
								states.set(state-startState);
							}
						} else {
							int black = cnt[blackOrd];
							if (black + blue + whiteGrey + orange < targetColorCount) {
								states.set(state-startState);
							}

						}
					}
				}


				atomicProgressCount.addAndGet(localProgressCount);

				int countSetBits = 0;
				synchronized (markDead) {
					// Iterate over all set bits
					for (int i = states.nextSetBit(0); i >= 0; i = states.nextSetBit(i+1)) {
						markDead.accept(i + startState);
						countSetBits++;
					}
				}
				atomicDeadStatesCount.addAndGet(countSetBits);
			});
		}

		while (true) {
			int completed = atomicProgressCount.get();
			if (completed == 1000000000) break;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		prunedDead = atomicDeadStatesCount.get();
		return prunedDead;
	
	}


	private static int countTarget(Color[] targetColors, Color color) {
		int count = 0;
		if (targetColors[0] == color) count++;
		if (targetColors[1] == color) count++;
		if (targetColors[2] == color) count++;
		if (targetColors[3] == color) count++;
		return count;
	}

	private static void fillCounts(int state, byte[] cnt) {
		Arrays.fill(cnt, (byte)0);
		for (int i = 0; i < 9; i++) {
			cnt[state % 10]++;
			state /= 10;
		}
	}

	// Prune colors that don't have any weird interactions
	private static boolean basicColorPrune(Color[] targetColors, Color targetColor, int colorOrd, byte[] cnt) {
		int targetColorCount = 0;
		if ((targetColorCount = countTarget(targetColors, targetColor)) != 0) {
			int basics = cnt[colorOrd];
			if (basics == 0) {
				return true;
			} else if (basics < targetColorCount) {
				// targetColorCount must be 2+
				int oranges = cnt[orangeOrd];
				int blues = cnt[blueOrd];
				int whitesAndGreysMinus2 = Math.max(0, cnt[whiteOrd] + cnt[grayOrd] - 2);
				if (basics < 2
						|| oranges == 0
						|| (oranges +
							blues + whitesAndGreysMinus2 +
							basics)
							< targetColorCount) {
					return true;
				}
			}
		}
		return false;
	}

}
