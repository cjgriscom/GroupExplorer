package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import io.chandler.gap.util.TimeEstimator;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.PrintStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.StringJoiner;

public class MJAnalysis {
	private static final int UNREACHABLE = -1;
	
	private final Color[] targetColors;

	public MJAnalysis(Color[] targetColors) {
		this.targetColors = targetColors;
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

	public void fullDepthAnalysis(boolean considerRotationsAsOne) {
		String filename = "";
		for (Color color : targetColors) {
			filename += "_" + color.name();
		}
		System.out.println("Full depth analysis for " + filename);
		try (PrintStream out = new PrintStream(new File("depths_" + filename + ".txt"))) {

			MoraJaiBox box = new MoraJaiBox();
			
			int[] depths = new int[1000000000];
			Arrays.fill(depths, UNREACHABLE);


			// Loop through and mark each zero state

			int depth = 0;

			System.out.println("Generate depth " + depth);

			int counter = generateDepth0(box, depths);

			while (counter > 0) {
				System.out.println("Depth " + depth + " has " + counter + " states");
				counter = 0;
				depth++;
				System.out.println("Generate depth " + depth);
				TimeEstimator te = new TimeEstimator(1000000000);
				long lastUpdate = System.currentTimeMillis();
				nextState: for (int state = 0; state < 1000000000; state++) {
					if (depths[state] != UNREACHABLE) continue; // Already reached in minimum moves

					box.initFromState(targetColors, state);
					
					for (int i = 0; i < 9; i++) {
						box.pressTile(i);
						int newState = box.getState();
						if (depths[newState] == depth - 1) {
							depths[state] = depth;
							counter++;
							continue nextState;
						}
						box.reset();
					}
					if (System.currentTimeMillis() - lastUpdate > 5000) {
						te.checkProgressEstimate(state, counter);
						lastUpdate = System.currentTimeMillis();
					}
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int generateDepth0(MoraJaiBox box, int[] depths) {
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
				depths[box.getState()] = 0;
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


	public static void main(String[] args) {
		for (Color color : Color.values()) {
			if (color == C_GY) continue;
			MJAnalysis analysis = new MJAnalysis(new Color[] { color, color, color, color });
			analysis.fullDepthAnalysis(true);
		}
	}
}
