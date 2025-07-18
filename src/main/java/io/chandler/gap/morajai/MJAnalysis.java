package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.PrintStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

public class MJAnalysis {

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

	private final boolean noBlue;
	private final Color[] targetColors;
	private final ThreadLocal<MoraJaiBox> threadLocalBox = ThreadLocal.withInitial(MoraJaiBox::new);

	private int threads = 17;


	private static class DepthTracker {
		private final byte[] depths;

		private static final byte UNREACHED = -1;
		private static final byte DEAD = -2;

		DepthTracker() {
			depths = new byte[1000000000];
			Arrays.fill(depths, UNREACHED);
		}
		
		public int getDepth(int state) {
			return depths[state] & 0xff;
		}

		public void setDepth(int state, int depth) {
			if (depth > 250) throw new RuntimeException("Depth exceeds 250");
			depths[state] = (byte)depth;
		}

		public boolean isDead(int state) {
			return depths[state] == DEAD;
		}

		public boolean isUnreached(int state) {
			return depths[state] == UNREACHED;
		}

		public void markDead(int state) {
			depths[state] = DEAD;
		}
		
	}

	public MJAnalysis(Color[] targetColors, boolean noBlue) {
		this.targetColors = targetColors;
		this.noBlue = noBlue;
	}

	public MJAnalysis setThreads(int threads) {
		this.threads = threads;
		return this;
	}

	public String stateToJson(int state) {
		MoraJaiBox box = new MoraJaiBox();
		box.initFromState(targetColors, state);
		StringJoiner joiner = new StringJoiner(", ");
		for (int i = 0; i < 9; i++) {
			joiner.add("\"" + box.getTileColor(i).name().replace("C_", "") + "\"");
		}
		return "[" + joiner.toString() + "]";
	}

	private int countTarget(Color[] targetColors, Color color) {
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
	private void basicColorPrune(Color targetColor, int colorOrd, int state, byte[] cnt, IntOpenHashSet states) {
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

	public void fullDepthAnalysis(int idx, Consumer<MJAnalysisStats> statsUpdate) {

		String filename = noBlue ? "noBlue" : "";
		for (Color color : targetColors) {
			if (noBlue && color == C_BU) return;
			filename += "_" + color.name();
		}
		MJAnalysisStats stats = new MJAnalysisStats(idx, filename);
		statsUpdate.accept(stats);
		
		try (ExecutorService executor = Executors.newFixedThreadPool(threads);  
		     PrintStream out = new PrintStream(new File("morajai_depths/depths_v2_" + filename + ".txt"))) {

			MoraJaiBox box = new MoraJaiBox();
			
			DepthTracker depths = new DepthTracker();

			// Loop through and mark each zero state
			int depth = 0;
			int counter = generateDepth0(box, depths);
			stats.begun = true;
			stats.depth = 0;
			stats.statesAtDepth = counter;
			statsUpdate.accept(stats);

			final boolean CHECK_FALSE_POSITIVES = false;

			int prunedDead = 0;
			{
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
							basicColorPrune(C_GN, greenOrd, state, cnt, states);
							basicColorPrune(C_YE, yellowOrd, state, cnt, states);
							basicColorPrune(C_PU, purpleOrd, state, cnt, states);
							basicColorPrune(C_PI, pinkOrd, state, cnt, states);

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
									basicColorPrune(C_BK, blackOrd, state, cnt, states);
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
						synchronized (depths) {
							for (int state : states) {
								depths.markDead(state);
							}
						}
						return null;
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
				stats.initalPruned = prunedDead;
			}


			Object monitor = new Object();
			int counterAccum = 0;

			while (counter > 0) {
				counterAccum += counter;

				int numChunks;
				int remainingStates = 1_000_000_000 - prunedDead - counterAccum;
				if (remainingStates > 600_000_000) {
					numChunks = 200;
				} else if (remainingStates > 400_000_000) {
					numChunks = 128;
				} else if (remainingStates > 200_000_000) {
					numChunks = 100;
				} else if (remainingStates > 100_000_000) {
					numChunks = 64;
				} else {
					numChunks = 32;
				}

				int chunkSize = 1000000000 / numChunks;

				int[] monitor_counter = new int[1];
				int[] monitor_progressCount = new int[1];


				stats.unreached = remainingStates;
				stats.depth = depth;
				stats.dead = prunedDead;
				stats.statesAtDepth = counter;
				

				// Send update for depth
				statsUpdate.accept(stats);

				out.println("Depth " + depth + " has " + counter + " states");
				counter = 0;
				depth++;
				
				// Capture depth in final variable for lambda
				final int currentDepth = depth;
				
				List<Future<ArrayList<Integer>>> futures = new ArrayList<>();
				IntOpenHashSet deadStates = new IntOpenHashSet();
				
				for (int worker = 0; worker < numChunks; worker++) {
					int chunkIndex = worker;
					Future<ArrayList<Integer>> future = executor.submit(() -> {
						MoraJaiBox threadBox = threadLocalBox.get();
						int startState = chunkIndex * chunkSize;
						int endState = Math.min(startState + chunkSize, 1000000000);

						ArrayList<Integer> states = new ArrayList<>();
						ArrayList<Integer> localDeadStates = new ArrayList<>();

						int localCounter = 0, localProgressCount = 0;

						
						nextState: for (int state = startState; state < endState; state++) {
							localProgressCount++;

							// Already reached in minimum moves, or dead end
							if (!depths.isUnreached(state) && (!CHECK_FALSE_POSITIVES || !depths.isDead(state))) continue;
							
							/*if (state % 1000 == 0) {
								synchronized (monitor) {
									monitor_progressCount[0] += localProgressCount;
									monitor_counter[0] += localCounter;
								}
								localProgressCount = 0;
								localCounter = 0;
							}*/

							threadBox.initFromState(targetColors, state);
							
							boolean pathsRemain = false;
							for (int i = 0; i < 9; i++) {
								threadBox.pressTile(i);
								int newState = threadBox.getState();
								
								if (depths.isUnreached(newState) && newState != state) {
									pathsRemain = true;
								}

								if (depths.getDepth(newState) == currentDepth - 1) { // Found a path to the previous depth
									states.add(state);
									localCounter++;
									continue nextState;
								}
								threadBox.reset();
							}

							if (!pathsRemain) {
								// Dead
								localDeadStates.add(state);
							}
						}

						if (localDeadStates.size() > 0) synchronized (deadStates) {
							deadStates.addAll(localDeadStates);
						}
						synchronized (monitor) {
							monitor_progressCount[0] += localProgressCount;
							monitor_counter[0] += localCounter;
							monitor.notifyAll();
						}
						return states;
					});
					futures.add(future);
				}

				int mainCounter = 0, mainProgressCount = 0;
				while (true) {
					try {
						synchronized (monitor) {
							monitor.wait(2000);
							mainProgressCount = monitor_progressCount[0];
							mainCounter = monitor_counter[0];
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (mainProgressCount == 1000000000) break;
				}

				counter = mainCounter;

				synchronized (deadStates) {
					for (int state : deadStates) {
						prunedDead ++;
						depths.markDead(state);
					}
				}

				// Write results to depths array		
				try {
					boolean firstResult = true;
					for (Future<ArrayList<Integer>> future : futures) {
						ArrayList<Integer> states = future.get();
						for (int state : states) {
							depths.setDepth(state, currentDepth);
							if (counter < 100) {
								out.println(stateToJson(state));
							} else if (firstResult) {
								out.println(stateToJson(state));
								firstResult = false;
							}
						}
					}
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					break;
				}
				
			}

			stats.complete = true;
			statsUpdate.accept(stats);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int generateDepth0(MoraJaiBox box, DepthTracker depths) {
		int counter = 0;
		Color[] initColors = new Color[9];
		initColors[0] = targetColors[0];
		initColors[2] = targetColors[1];
		initColors[6] = targetColors[3];
		initColors[8] = targetColors[2];

		for (int i = 0; i < 100000; i++) {
			int decomp = i;
			for (int j = 0; j < 9; j++) {
				if (j == 0 || j == 2 || j == 6 || j == 8) continue;
				initColors[j] = MoraJaiBox.COLOR_VALUES[decomp % 10];
				decomp /= 10;
			}

			box.init(targetColors, initColors);
			if (box.areInnerMatchingOuter()) {
				depths.setDepth(box.getState(), 0);
				counter++;
			}
		}
		return counter;
	}


	public MoraJaiBox getBox(int state) {
		MoraJaiBox box = new MoraJaiBox();
		box.initFromState(targetColors, state);
		return box;
	}

	public boolean isSolved(int state) {
		return getBox(state).areInnerMatchingOuter();
	}

	private static List<Integer> buildPurpleAndYellowPool(int startpoint) {
		List<Integer> pool = new ArrayList<>();
		int idx = -1;
		for (Color a : Color.values()) {
			for (Color b : Color.values()) {
				for (Color c : Color.values()) {
					for (Color d : Color.values()) {
						idx++;
						if (a != C_YE && b != C_YE && c != C_YE && d != C_YE) continue;
						if (a != C_PU && b != C_PU && c != C_PU && d != C_PU) continue;

						if (idx < startpoint) {
							continue;
						}

						pool.add(idx);
					}
				}
			}
		}
		return pool;
	}

	private static List<Integer> buildSeededPool(int seed) {
		List<Integer> pool = new ArrayList<>();
		for (int i = 0; i < 10000; i++) {
			pool.add(i);
		}
		Collections.shuffle(pool, new Random(seed));
		return pool;
	}


	public static void main(String[] args) throws IOException, InterruptedException {
		//List<Integer> pool = buildPurpleAndYellowPool(4637);
		List<Integer> pool = buildSeededPool(-1234);
		boolean noBlue = false;

		int total = 10000;
		int skipTo = 41;

		for (int i = 0; i < skipTo; i++) {
			pool.remove(0);
		}
		
		BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(pool);
	
		int numThreads = 3;
		int numInnerThreads = 6;
		MJAnalysisStats[] stats = new MJAnalysisStats[numThreads];
		int[] ids = new int[numThreads];
		Arrays.fill(ids, -1);
		Thread[] threads = new Thread[numThreads];

		for (int i = 0; i < numThreads; i++) {
			int threadId = i;
			stats[threadId] = new MJAnalysisStats(threadId, "-");
			threads[threadId] = new Thread(() -> {
				while (true) {
					try {
						
						Integer idx = queue.poll();
						if (idx == null) break;
						ids[threadId] = total - queue.size() - 1;

						MJAnalysis analysis = new MJAnalysis(new Color[] { Color.values()[idx/1000%10], Color.values()[idx/100%10], Color.values()[idx/10%10], Color.values()[idx%10] }, noBlue);
						analysis.setThreads(numInnerThreads);
						analysis.fullDepthAnalysis(idx, (s) -> {
							synchronized(stats) {
								stats[threadId] = s;
							}
						});

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			threads[threadId].start();
		}

		Terminal terminal = new DefaultTerminalFactory().createTerminal();
		Screen screen = new TerminalScreen(terminal);
		screen.startScreen();
		screen.setCursorPosition(null);

		try {
			final String headerFormat = "%-5s %-6s %-30s %-12s %-8s %-10s %-12s %-12s";
    		final String header = String.format(headerFormat, "#", "ID", "Filename", "Status", "Depth", "Size", "Unreached", "Dead");
			
			TerminalSize terminalSize = screen.getTerminalSize();
			

			while (true) {
				KeyStroke keyStroke = screen.pollInput();
				if (keyStroke != null && keyStroke.getKeyType() == KeyType.Character) {
					if (keyStroke.getCharacter() == 'q') {
						System.exit(1);
						break;
					}
					if (keyStroke.getCharacter() == 'c') {
						queue.clear();
					}
				}

				TextGraphics tg = screen.newTextGraphics();
				TerminalSize newSize = screen.doResizeIfNecessary();
				if (newSize != null) {
					terminalSize = newSize;
					screen.clear();
				}

				tg.setBackgroundColor(TextColor.ANSI.BLUE);
				tg.putString(0, 0, String.format("%-" + terminalSize.getColumns() + "s", header));
				tg.setBackgroundColor(TextColor.ANSI.BLACK);

				boolean allComplete = true;
				for (int i = 0; i < numThreads; i++) {
					if (threads[i].isAlive()) {
						allComplete = false;
					}
					MJAnalysisStats s = stats[i];
					String line;
					if (!s.begun) {
						line = String.format(headerFormat, ids[i] == -1 ? "-" : ids[i], s.idx, s.filename, "Waiting", "-", "-", "-", "-");
					} else if (s.complete) {
						line = String.format(headerFormat, ids[i] == -1 ? "-" : ids[i], s.idx, s.filename, "Complete", s.depth, s.statesAtDepth, s.unreached, s.dead);
					} else {
						line = String.format(headerFormat, ids[i] == -1 ? "-" : ids[i], s.idx, s.filename, "Running", s.depth, s.statesAtDepth, s.unreached, s.dead);
					}
					tg.putString(0, i + 1, String.format("%-" + terminalSize.getColumns() + "s", line));
				}
				
				String footer = "Press 'q' to quit, 'c' to clear queue. ";
				if (!queue.isEmpty()) {
					footer += queue.size() + " items remaining.";
				} else {
					footer += "Queue empty, waiting for jobs to finish...";
				}
				tg.putString(0, terminalSize.getRows() - 1, String.format("%-" + terminalSize.getColumns() + "s", footer));

				screen.refresh();
				if (allComplete) break;
				Thread.sleep(300);
			}
		} finally {
			screen.stopScreen();
			screen.close();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		return;
	}
}
