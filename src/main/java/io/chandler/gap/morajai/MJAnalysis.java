package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
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

			Queue<Integer> curStates = new ArrayDeque<>();
			HashSet<Integer> nextStates = new HashSet<>();

			int depth = 0;

			System.out.println("Generate depth " + depth);

			generateDepth0(box, depths, curStates);

			System.out.println("Depth " + depth + " has " + curStates.size() + " states");
			out.println("Depth " + depth + " has " + curStates.size() + " states");
			if (curStates.size() < 100) {
				for (int state : curStates) {
					out.println(stateToJson(state));
				}
			}

			depth++;

			// Loop through solved states and mark their children
			while (!curStates.isEmpty()) {
				System.out.println("Generate depth " + depth);
				while (!curStates.isEmpty()) {
					int state = curStates.poll();
					box.initFromState(targetColors, state);
					for (int i = 0; i < 9; i++) {
						box.pressTile(i);
						int newState = box.getState();
						if (depths[newState] == UNREACHABLE) {
							depths[newState] = depth;
							nextStates.add(newState);
						}
						box.reset();
					}
				}
				System.out.println("Depth " + depth + " has " + nextStates.size() + " states");
				out.println("Depth " + depth + " has " + nextStates.size() + " states");
				if (nextStates.size() < 100) {
					for (int state : nextStates) {
						out.println(stateToJson(state));
					}
				}
				curStates.addAll(nextStates);
				nextStates.clear();
				depth++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void generateDepth0_old(MoraJaiBox box, int[] depths, Queue<Integer> curStates) {
		for (int i = 0; i < 1000000000; i++) {
			box.initFromState(targetColors, i);
			if (box.areInnerMatchingOuter()) {
				depths[i] = 0;
				curStates.add(i);
			}
		}
	}

	private void generateDepth0(MoraJaiBox box, int[] depths, Queue<Integer> curStates) {
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
				curStates.add(box.getState());
			}
		}
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
