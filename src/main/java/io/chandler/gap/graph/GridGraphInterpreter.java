package io.chandler.gap.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeSet;

public class GridGraphInterpreter {

	public static class Point {
		public double x;
		public double y;
		public Point(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
	public static void main(String[] args) throws FileNotFoundException {
		boolean MIRROR_X = false;
		int ROTATE = 0;

		// Assume a perfect grid alignment

		try (Scanner in = new Scanner(new File("/tmp/g2.txt"))) {

			// Cache all x and y values
			TreeSet<Double> xValues = new TreeSet<>();
			TreeSet<Double> yValues = new TreeSet<>();

			ArrayList<Point> points = new ArrayList<>();
			
			int lineNumber = 0;
			while (in.hasNextLine()) {
				lineNumber++;
				String line = in.nextLine();
				if (line.isEmpty()) {
					continue;
				}
				String[] parts = line.split(" ");
				int num = Integer.parseInt(parts[0]);
				if (num != lineNumber) {
					throw new IllegalArgumentException("Line " + lineNumber + " has number " + num + " but should have number " + lineNumber);
				}
				double x = Double.parseDouble(parts[1]);
				double y = Double.parseDouble(parts[2]);
				xValues.add(x);
				yValues.add(y);
				points.add(new Point(x, y));
			}

			// TODO this could work for fudged X and Y by calculating the low median spans
			// but for now we'll just use the perfect grid alignment

			ArrayList<Double> xValuesList = new ArrayList<>(xValues);
			ArrayList<Double> yValuesList = new ArrayList<>(yValues);

			// Now calculate the average span between adjacent points
			ArrayList<Double> xSpans = new ArrayList<>();
			ArrayList<Double> ySpans = new ArrayList<>();
			for (int j = 0; j < xValuesList.size() - 1; j++) {
				xSpans.add(xValuesList.get(j + 1) - xValuesList.get(j));
			}
			for (int j = 0; j < yValuesList.size() - 1; j++) {
				ySpans.add(yValuesList.get(j + 1) - yValuesList.get(j));
			}
			double xAverageSpan = xSpans.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			double yAverageSpan = ySpans.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			// And calculate deviation
			double xDeviation = xSpans.stream().mapToDouble(Double::doubleValue).map(span -> Math.abs(span - xAverageSpan)).average().orElse(0);
			double yDeviation = ySpans.stream().mapToDouble(Double::doubleValue).map(span -> Math.abs(span - yAverageSpan)).average().orElse(0);


			// Print everything
			System.out.println("X Spans: " + xSpans);
			System.out.println("Y Spans: " + ySpans);
			System.out.println("X Average Span: " + xAverageSpan);
			System.out.println("Y Average Span: " + yAverageSpan);
			System.out.println("X Deviation: " + xDeviation);
			System.out.println("Y Deviation: " + yDeviation);

			if (xDeviation < 0.01 || yDeviation < 0.01) {
				System.out.println("Perfect grid alignment");
			} else {
				System.out.println("Fudged grid alignment TODO");
				return;
			}

			// Now we've verified perfect alignment so we can find the minumums, and count
			double xMinimum = xValuesList.get(0);
			double yMinimum = yValuesList.get(0);
			int xCount = xValuesList.size();
			int yCount = yValuesList.size();

			System.out.println("X Minimum: " + xMinimum);
			System.out.println("Y Minimum: " + yMinimum);
			System.out.println("X Count: " + xCount);
			System.out.println("Y Count: " + yCount);

			int[][] grid = new int[xCount][yCount];
			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				int x = (int) ((point.x - xMinimum) / xAverageSpan);
				int y = (int) ((point.y - yMinimum) / yAverageSpan);
				grid[x][y] = i+1;
			}

			// Now transform the grid according to rotation and mirror

			// Setup transformation variables
			boolean transpose = false, mirrorX = false, mirrorY = false;
			switch (ROTATE) {
				case 0:
					// No transformation
					break;
				case 90:
					transpose = true;
					break;
				case 180:
					mirrorX = true;
					mirrorY = true;
					break;
				case 270:
					transpose = true;
					break;
				default:
					throw new IllegalArgumentException("Invalid rotate angle: " + ROTATE);
			}
			if (MIRROR_X) {
				mirrorX = !mirrorX;
			}

			// Apply transpose if needed (swap x,y axes)
			if (transpose) {
				int[][] transposedGrid = new int[yCount][xCount];
				for (int y = 0; y < yCount; y++) {
					for (int x = 0; x < xCount; x++) {
						transposedGrid[y][x] = grid[x][y];
					}
				}
				// Swap xCount and yCount after transposing
				int temp = xCount;
				xCount = yCount;
				yCount = temp;
				grid = transposedGrid;
			}

			// Mirror along X axis if needed (reverse X index)
			if (mirrorX) {
				int[][] mirroredGrid = new int[xCount][yCount];
				for (int x = 0; x < xCount; x++) {
					for (int y = 0; y < yCount; y++) {
						mirroredGrid[x][y] = grid[xCount - x - 1][y];
					}
				}
				grid = mirroredGrid;
			}

			// Mirror along Y axis if needed (reverse Y index)
			if (mirrorY) {
				int[][] mirroredGrid = new int[xCount][yCount];
				for (int x = 0; x < xCount; x++) {
					for (int y = 0; y < yCount; y++) {
						mirroredGrid[x][y] = grid[x][yCount - y - 1];
					}
				}
				grid = mirroredGrid;
			}


			// Now print the grid
			for (int y = 0; y < yCount; y++) {
				System.out.print("          '(");
				for (int x = 0; x < xCount; x++) {
					String cell = grid[x][y] == 0 ? "x" : grid[x][y] + "";
					System.out.print(cell + (x == xCount - 1 ? "),' +" : ","));
				}
				System.out.println();
			}
		}
	}
}
