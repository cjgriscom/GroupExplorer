package io.chandler.gap.morajai;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

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

				IntOpenHashSet states = new IntOpenHashSet();

				int localProgressCount = 0;

				byte[] cnt = new byte[10];

				for (int state = startState; state < endState; state++) {
					fillCounts(state, cnt);
					localProgressCount++;
					if (state % 1000 == 0) {
						atomicProgressCount.addAndGet(localProgressCount); // Update progress estimate every 1000 states}
						localProgressCount = 0;
					}
					int targetColorCount = 0;

					if (noBlue && cnt[blueOrd] > 0) {
						states.add(state);
					}
					boolean containsWhite = false;
					if ((targetColorCount = countTarget(targetColors, C_WH)) != 0) {
						containsWhite = true;
						// Can't materialize white
						if (cnt[whiteOrd] == 0) states.add(state);
					}
					if ((targetColorCount = countTarget(targetColors, C_GY)) != 0 || containsWhite) {
						int greysAndWhites = cnt[grayOrd] + cnt[whiteOrd];
						if (greysAndWhites == 0) {
							states.add(state);
						} else if (greysAndWhites < targetColorCount) {
							// Orange and blue can both generate more white/gry
							if (	cnt[orangeOrd] +
										cnt[blueOrd] +
										greysAndWhites
										< targetColorCount) {
								states.add(state);
							}
						}
					}
					basicColorPrune(targetColors, C_GN, greenOrd, state, cnt, states);
					basicColorPrune(targetColors, C_YE, yellowOrd, state, cnt, states);
					basicColorPrune(targetColors, C_PU, purpleOrd, state, cnt, states);
					basicColorPrune(targetColors, C_PI, pinkOrd, state, cnt, states);

					// Orange
					if ((targetColorCount = countTarget(targetColors, C_OR)) != 0) {
						int oranges = cnt[orangeOrd];
						if (oranges == 0) {
							states.add(state);
						} else if (oranges == 1) {
							if (targetColorCount != 1) states.add(state); 
						} else if (oranges < targetColorCount) {
							// Sum blue and grey and white
							int bgw = cnt[grayOrd] + cnt[blueOrd] + cnt[whiteOrd];
							// Simplified but good enough
							if (oranges + bgw < targetColorCount) {
								states.add(state);
							}
						}
					}

					int reds = cnt[redOrd];
					// Red is maybe the most complicated, keep it simple for now
					if ((targetColorCount = countTarget(targetColors, C_RD)) != 0) {
						// Ensure at least one red
						if (reds == 0) states.add(state);
						else {
							// There's at least one red
							if (reds < targetColorCount) {
								// Not enough red, for simplicity just sum all morphing combos
								int orangeBluesBlack = cnt[orangeOrd] + cnt[blueOrd] + cnt[blackOrd];
								int whitesAndGreys = cnt[whiteOrd] + cnt[grayOrd];
								if (orangeBluesBlack + whitesAndGreys + reds < targetColorCount) {
									states.add(state);
								}
							}
						}
					}

					// Black
					if ((targetColorCount = countTarget(targetColors, C_BK)) != 0) {
						if (reds == 0) {
							// No red so black is a basic color
							basicColorPrune(targetColors, C_BK, blackOrd, state, cnt, states);
						} else {
							// For simplicity just sum all morphing combos
							int orangeBluesBlack = cnt[orangeOrd] + cnt[blueOrd] + cnt[blackOrd];
							int whitesAndGreys = cnt[whiteOrd] + cnt[grayOrd];
							if (orangeBluesBlack + whitesAndGreys < targetColorCount) {
								states.add(state);
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
								states.add(state);
							}
						} else {
							int black = cnt[blackOrd];
							if (black + blue + whiteGrey + orange < targetColorCount) {
								states.add(state);
							}

						}
					}
				}


				atomicProgressCount.addAndGet(localProgressCount);

				atomicDeadStatesCount.addAndGet(states.size());
				synchronized (markDead) {
					for (int state : states) {
						markDead.accept(state);
					}
				}
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
	private static void basicColorPrune(Color[] targetColors, Color targetColor, int colorOrd, int state, byte[] cnt, IntOpenHashSet states) {
		int targetColorCount = 0;
		if ((targetColorCount = countTarget(targetColors, targetColor)) != 0) {
			int basics = cnt[colorOrd];
			if (basics == 0) {
				states.add(state);
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
					states.add(state);
				}
			}
		}
	}

}
