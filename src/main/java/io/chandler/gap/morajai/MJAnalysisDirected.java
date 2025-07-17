package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import io.chandler.gap.util.TimeEstimator;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.PrintStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringJoiner;

public class MJAnalysisDirected {
	private static final int UNREACHABLE = -1;
	
	private final Color[] targetColors = { C_GY, C_GY, C_GY, C_GY };

	public String stateToJson(int state) {
		MoraJaiBox box = new MoraJaiBox();
		box.initFromState(targetColors, state);
		StringJoiner joiner = new StringJoiner(", ");
		for (int i = 0; i < 9; i++) {
			joiner.add("\"" + box.getTileColor(i).name().replace("C_", "") + "\"");
		}
		return "[" + joiner.toString() + "]";
	}

	public void fullDepthAnalysis() {
		String filename = "";
		for (Color color : targetColors) {
			filename += "_" + color.name();
		}
		System.out.println("Full depth analysis for " + filename);
		try (PrintStream out = new PrintStream(new File("depths_" + filename + ".txt"))) {

			MoraJaiBox box = new MoraJaiBox();

			HashMap<Integer, ArrayList<Integer>> directedGraph = new HashMap<>();

			TimeEstimator te = new TimeEstimator(1000000000);
			long lastUpdate = System.currentTimeMillis();

			
			

			// Loop through all states and track their children
			for (int state = 0; state < 1000000000; state++) {
				box.initFromState(targetColors, state);

				for (int i = 0; i < 9; i++) {
					box.pressTile(i);
					int newState = box.getState();
					directedGraph.computeIfAbsent(newState, k -> new ArrayList<>()).add(state);

					box.reset();
				}
				if (System.currentTimeMillis() - lastUpdate > 5000) {
					te.checkProgressEstimate(state, directedGraph.size());
					lastUpdate = System.currentTimeMillis();
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		MJAnalysisDirected analysis = new MJAnalysisDirected();
		analysis.fullDepthAnalysis();
	}
}
