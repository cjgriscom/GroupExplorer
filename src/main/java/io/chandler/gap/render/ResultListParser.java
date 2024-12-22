package io.chandler.gap.render;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import io.chandler.gap.GroupExplorer;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.paint.Color;
import javafx.util.Pair;

public class ResultListParser {
	private static final Color blank = Color.rgb(30, 30, 30);

    // Positive Colors (Light Variants)
    private static final Color[] posColors = new Color[] {
        Color.rgb(255, 102, 102), // Light Red
        Color.rgb(255, 153, 102), // Light Orange
        Color.rgb(255, 255, 102), // Light Yellow
        Color.rgb(102, 255, 102), // Light Green
        Color.rgb(102, 178, 255), // Light Blue
        Color.rgb(204, 153, 255),  // Light Purple
    };

    // Negative Colors (Dark Variants)
    private static final Color[] negColors = new Color[] {
        Color.rgb(153, 0, 0),     // Dark Red
        Color.rgb(153, 51, 0),    // Dark Orange
        Color.rgb(204, 204, 0),   // Dark Yellow
        Color.rgb(0, 153, 0),     // Dark Green
        Color.rgb(0, 102, 204),   // Dark Blue
        Color.rgb(102, 51, 153)   // Dark Purple
    };
	public static List<Pair<Integer, Color>> getColorList(Solid solid, String selectedResult, SimpleStringProperty descriptionOut) {
		TreeMap<Integer, Pair<Integer, Color>> colorMap = new TreeMap<>();
		
		// Parse the selected result
		String substring = selectedResult.substring(selectedResult.indexOf("[") + 1, selectedResult.indexOf("]"));
		int[][][] generator = GroupExplorer.parseOperationsArr(substring);

		StringBuilder description = new StringBuilder();
		for (int color = 0; color < generator.length; color++) {
			description.append("\n--\n");
			int[][] cycles = generator[color];
			for (int[] cycle : cycles) {
				int face = solid.getPosOrNegFaceFromGenerator(cycle);
				boolean isNegative = face < 0;
				Color[] colorSrc = isNegative ? negColors : posColors;
				colorMap.put(Math.abs(face) - 1, new Pair<>(color, colorSrc[color]));
				description.append((Math.abs(face)) + (isNegative ? "L" : "R"));
				description.append(", ");
			}
		}

		ArrayList<Pair<Integer, Color>> colorList = new ArrayList<>();
		for (int i = 0; i < solid.getMeshViews().size(); i++) {
			colorList.add(colorMap.getOrDefault(i, new Pair<>(-1, blank)));
		}

		descriptionOut.set(description.toString());
		return colorList;
	}	
}
