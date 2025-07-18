package io.chandler.gap.morajai;

import static io.chandler.gap.morajai.MoraJaiBoxSimplified.Color.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.chandler.gap.morajai.MoraJaiBoxSimplified.Color;

public class MJAnalysisSimplified {


	private final boolean noBlue;
	private final ThreadLocal<MoraJaiBoxSimplified> threadLocalBox = ThreadLocal.withInitial(MoraJaiBoxSimplified::new);

	private int threads = 17;

	public MJAnalysisSimplified(boolean noBlue) {
		this.noBlue = noBlue;
	}

	public MJAnalysisSimplified setThreads(int threads) {
		this.threads = threads;
		return this;
	}

	public String stateToJson(int state) {
		MoraJaiBoxSimplified box = new MoraJaiBoxSimplified();
		box.initFromState(state);
		StringJoiner joiner = new StringJoiner(", ");
		for (int i = 0; i < 9; i++) {
			joiner.add("\"" + box.getTileColor(i).name().replace("C_", "") + "\"");
		}
		return "[" + joiner.toString() + "]";
	}

	private void set(byte[] depths, int state) {
		depths[state/8] |= (byte)(1 << (state % 8));
	}

	private boolean isSet(byte[] depths, int state) {
		return (depths[state/8] & (1 << (state % 8))) != 0;
	}

	public void fullDepthAnalysis(int idx, Consumer<MJAnalysisStats> statsUpdate) {

		String filename = noBlue ? "noBlue" : "";
		for (int colorI : MoraJaiBoxSimplified.targetColors) {
			Color color = Color.values()[colorI];
			if (noBlue && color == C_BU) return;
			filename += "_" + color.name();
		}
		MJAnalysisStats stats = new MJAnalysisStats(idx, filename);
		statsUpdate.accept(stats);
		
		try (ExecutorService executor = Executors.newFixedThreadPool(threads);  
		     PrintStream out = new PrintStream(new File("morajai_gpu_depths/depths_v2_" + filename + ".txt"))) {

			MoraJaiBoxSimplified box = new MoraJaiBoxSimplified();
			
			byte[] reached    = new byte[125000000];
			byte[] current    = new byte[125000000];
			byte[] next       = new byte[125000000];

			// Loop through and mark each zero state
			int depth = 0;
			int counter = generateDepth0(box, current);
			stats.depth = 0;
			stats.statesAtDepth = counter;
			statsUpdate.accept(stats);

			Object monitor = new Object();
			int counterAccum = 0;

			while (counter > 0) {

				counterAccum += counter;

				int numChunks = 200;
				int remainingStates = 1_000_000_000 - counterAccum;

				int chunkSize = 1000000000 / numChunks;

				int[] monitor_counter = new int[1];
				int[] monitor_progressCount = new int[1];


				stats.unreached = remainingStates;
				stats.depth = depth;
				stats.statesAtDepth = counter;
				

				// Send update for depth
				statsUpdate.accept(stats);

				out.println("Depth " + depth + " has " + counter + " states");
				counter = 0;
				depth++;
								
				
				for (int worker = 0; worker < numChunks; worker++) {
					int chunkIndex = worker;
					executor.submit(() -> {
						MoraJaiBoxSimplified threadBox = threadLocalBox.get();
						int startState = chunkIndex * chunkSize;
						int endState = Math.min(startState + chunkSize, 1000000000);

						int localCounter = 0, localProgressCount = 0;

						
						nextState: for (int state = startState; state < endState; state++) {
							localProgressCount++;

							// Ignore reached states
							if (isSet(reached, state) || isSet(current, state)) continue;

							threadBox.initFromState(state);
							
							for (int i = 0; i < 9; i++) {
								threadBox.pressTile(i);
								int newState = threadBox.getState();

								if (isSet(current, newState)) { // Found a path to the previous depth
									set(next, state);
									localCounter++;
									continue nextState;
								}
								threadBox.reset();
							}
						}

						synchronized (monitor) {
							monitor_progressCount[0] += localProgressCount;
							monitor_counter[0] += localCounter;
							monitor.notifyAll();
						}
					});
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

				if (counter < 100) {
					// Loop through depthsNext true entries and print the state to json
					for (int i = 0; i < 1000000000; i++) {
						if (isSet(next, i)) {
							out.println(stateToJson(i));
						}
					}
				}

				if (counter > 0) {
					
					for (int i = 0; i < reached.length; i++) {
						reached[i] |= current[i];
						current[i] = next[i];
						next[i] = 0;
					}
				}
			}

			stats.complete = true;
			statsUpdate.accept(stats);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int generateDepth0(MoraJaiBoxSimplified box, byte[] depths) {
		int counter = 0;
		Color[] initColors = new Color[9];
		initColors[0] = Color.values()[MoraJaiBoxSimplified.targetColors[0]];
		initColors[2] = Color.values()[MoraJaiBoxSimplified.targetColors[1]];
		initColors[6] = Color.values()[MoraJaiBoxSimplified.targetColors[3]];
		initColors[8] = Color.values()[MoraJaiBoxSimplified.targetColors[2]];

		for (int i = 0; i < 100000; i++) {
			int recomp = 0;
			int decomp = i;
			for (int j = 0; j < 9; j++) {
				if (j == 0 || j == 2 || j == 6 || j == 8) {}
				else {
					initColors[j] = MoraJaiBoxSimplified.COLOR_VALUES[decomp % 10];
					decomp /= 10;
				}
				// TODO check this
				recomp += initColors[j].ordinal() * Math.pow(10, j);
			}

			box.initFromState(recomp);
			if (box.areInnerMatchingOuter()) {
				set(depths, box.getState());
				counter++;
			}
		}
		return counter;
	}

	public static void main(String[] args) {
		MoraJaiBoxSimplified.targetColors[0] = C_PI.ordinal();
		MoraJaiBoxSimplified.targetColors[1] = C_PI.ordinal();
		MoraJaiBoxSimplified.targetColors[2] = C_PI.ordinal();
		MoraJaiBoxSimplified.targetColors[3] = C_PI.ordinal();

		MJAnalysisSimplified analysis = new MJAnalysisSimplified(false);
		analysis.fullDepthAnalysis(5555, (stats) -> {
			System.out.println(stats.filename + " " + stats.depth + " " + stats.statesAtDepth + " " + stats.unreached);
		});
	}


}
