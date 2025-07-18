package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.function.Consumer;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.screen.Screen.RefreshType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.apache.commons.cli.*;

public class MJAnalysis {

	private final boolean noBlue;
	private final Color[] targetColors;
	private final ThreadLocal<MoraJaiBox> threadLocalBox = ThreadLocal.withInitial(MoraJaiBox::new);

	private int threads = 17;

	private final Path storageDir;

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

		public boolean isUnreached(int state) {
			return depths[state] == UNREACHED;
		}

		public void markDead(int state) {
			depths[state] = DEAD;
		}
		
	}

	public MJAnalysis(Path storageDir, Color[] targetColors, boolean noBlue) {
		this.targetColors = targetColors;
		this.noBlue = noBlue;
		this.storageDir = storageDir;
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

	public void fullDepthAnalysis(int idx, Consumer<MJAnalysisStats> statsUpdate) {

		String filename = noBlue ? "_noBlue" : "";
		for (Color color : targetColors) {
			if (noBlue && color == C_BU) return;
			filename += "_" + color.name();
		}
		MJAnalysisStats stats = new MJAnalysisStats(idx, filename);
		statsUpdate.accept(stats);
		
		try (ExecutorService executor = Executors.newFixedThreadPool(threads);  
			 PrintStream out = new PrintStream(new File(storageDir.resolve("depths_v2_" + idx + filename + ".txt").toString()))) {
			out.println("Starting analysis for " + idx + " " + filename + " with CPU - pruner: " + true);

			MoraJaiBox box = new MoraJaiBox();
			
			DepthTracker depths = new DepthTracker();

			// Loop through and mark each zero state
			int depth = 0;
			int counter = generateDepth0(box, depths);
			stats.begun = true;
			stats.depth = 0;
			stats.statesAtDepth = counter;
			stats.pruning = true;
			statsUpdate.accept(stats);


			int prunedDead = MJColorPrune.prune(executor, noBlue, targetColors, (state) -> {
				depths.markDead(state);
			});
			stats.initalPruned = prunedDead;
			stats.dead = prunedDead;
			stats.pruning = false;
			statsUpdate.accept(stats);
			
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
							if (!depths.isUnreached(state)) continue;
							
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
							if (counter < 180) {
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
			out.println("Complete");
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
		Options options = new Options();

		Option skipToOption = new Option("s", "skipTo", true, "Skip to index");
		skipToOption.setRequired(false);
		options.addOption(skipToOption);

		Option numGpuThreadsOption = new Option("g", "numGpuThreads", true, "Number of GPU threads");
		numGpuThreadsOption.setRequired(false);
		options.addOption(numGpuThreadsOption);

		Option gpuPrunerThreadsOption = new Option("G", "gpuPrunerThreads", true, "Enable GPU pruner with G CPUthreads");
		gpuPrunerThreadsOption.setRequired(false);
		options.addOption(gpuPrunerThreadsOption);

		Option numCpuThreadsOption = new Option("c", "numCpuThreads", true, "Number of CPU threads");
		numCpuThreadsOption.setRequired(false);
		options.addOption(numCpuThreadsOption);

		Option numCpuInnerThreadsOption = new Option("i", "numCpuInnerThreads", true, "Number of CPU inner threads");
		numCpuInnerThreadsOption.setRequired(false);
		options.addOption(numCpuInnerThreadsOption);

		Option storageDirOption = new Option("d", "storageDir", true, "Storage directory");
		storageDirOption.setRequired(false);
		options.addOption(storageDirOption);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("MJAnalysis", options);

			System.exit(1);
			return;
		}

		int skipTo = Integer.parseInt(cmd.getOptionValue("skipTo", "0"));
		int numGPUThreads = Integer.parseInt(cmd.getOptionValue("numGpuThreads", "3"));
		int gpuPrunerThreads = Integer.parseInt(cmd.getOptionValue("gpuPrunerThreads", "0"));
		int numCPUThreads = Integer.parseInt(cmd.getOptionValue("numCpuThreads", "2"));
		int numInnerThreads = Integer.parseInt(cmd.getOptionValue("numCpuInnerThreads", "7"));
		boolean noBlue = Boolean.parseBoolean(cmd.getOptionValue("noBlue", "false"));
		Path storageDir = Paths.get(cmd.getOptionValue("storageDir", "morajai_depths"));

		if (noBlue && gpuPrunerThreads == 0 && numGPUThreads > 0) {
			System.err.println("GPU pruner is required for noBlue");
			System.exit(1);
		}

		// Error if storage directory doesn't exist
		if (!Files.exists(storageDir)) {
			System.err.println("Storage directory does not exist: " + storageDir);
			System.exit(1);
		}

		ExecutorService pruneExecutor = (gpuPrunerThreads > 0) ? Executors.newWorkStealingPool(gpuPrunerThreads) : null;
		
		int numThreads = numCPUThreads + numGPUThreads;

		int total = 10000;

		List<Integer> pool = buildSeededPool(-1234);

		for (int i = 0; i < skipTo; i++) {
			pool.remove(0);
		}
		
		BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(pool);
		
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

						Color[] targetColors = new Color[] { Color.values()[idx/1000%10], Color.values()[idx/100%10], Color.values()[idx/10%10], Color.values()[idx%10] };

						if (threadId < numGPUThreads) {
							MJAnalysisGPU analysis = new MJAnalysisGPU(storageDir, noBlue);
							if (pruneExecutor != null) analysis.enablePrune(pruneExecutor);
							analysis.fullDepthAnalysis(targetColors, idx, (s) -> {
								synchronized(stats) {
									stats[threadId] = s;
								}
							});
						} else {
							MJAnalysis analysis = new MJAnalysis(storageDir, targetColors, noBlue);
							analysis.setThreads(numInnerThreads);
							analysis.fullDepthAnalysis(idx, (s) -> {
								synchronized(stats) {
									stats[threadId] = s;
								}
							});
						}

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
			
			long lastCompleteRefresh = System.currentTimeMillis();

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
						String runStatus = s.pruning ? "Pruning" : "Running";
						line = String.format(headerFormat, ids[i] == -1 ? "-" : ids[i], s.idx, s.filename, runStatus, s.depth, s.statesAtDepth, s.unreached, s.dead);
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

				if (System.currentTimeMillis() - lastCompleteRefresh > 30_000) {
					screen.refresh(RefreshType.COMPLETE);
					lastCompleteRefresh = System.currentTimeMillis();
				} else {
					screen.refresh();
				}
				if (allComplete) break;
				Thread.sleep(300);
			}
		} finally {
			screen.stopScreen();
			screen.close();
		}

		if (pruneExecutor != null) pruneExecutor.shutdown();
		for (Thread thread : threads) {
			thread.join();
		}

		return;
	}
}
