package io.chandler.gap;

import java.util.Arrays;

public class QuantumLoopover {

	static GapInterface gap;
	static {
		try {
			gap = new GapInterface();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
        System.out.println(gap.runGapCommands(genOrder(4,4), 3).get(2));
		//for (int i = 4; i <= 12; i+=2) {
		//	genOrder(i,i);
		//}
	}

	public static String genOrder(int m, int n) {
		
        String g1 = "", g2 = "", g3 = "", g4 = "";

        int[][] grid = new int[m][n];

        int x = 1;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                grid[i][j] = x;
                x++;
            }
        }

        boolean flip = false;
        for (int i = 0; i < m; i+=2) {
            {
            int[] row = new int[n];
            for (int j = 0; j < n; j++) {
                row[j] = grid[i][j];
            }
            if (flip) row = CycleInverter.invertArray(row);
            g1 += "("+Arrays.toString(row).replaceAll(" ", "").replaceAll("\\[", "").replaceAll("\\]", "")+")";
            }
            {
            int[] row = new int[n];
            for (int j = 0; j < n; j++) {
                row[j] = grid[i+1][j];
            }
            if (flip) row = CycleInverter.invertArray(row);
            g2 += "("+Arrays.toString(row).replaceAll(" ", "").replaceAll("\\[", "").replaceAll("\\]", "")+")";
            }
            flip = !flip;
        }
        flip = false;
        for (int i = 0; i < n; i+=2) {
            {
            int[] row = new int[m];
            for (int j = 0; j < m; j++) {
                row[j] = grid[j][i];
            }
            if (flip) row = CycleInverter.invertArray(row);
            g3 += "("+Arrays.toString(row).replaceAll(" ", "").replaceAll("\\[", "").replaceAll("\\]", "")+")";
            }
            {
            int[] row = new int[m];
            for (int j = 0; j < m; j++) {
                row[j] = grid[j][i+1];
            }
            if (flip) row = CycleInverter.invertArray(row);
            g4 += "("+Arrays.toString(row).replaceAll(" ", "").replaceAll("\\[", "").replaceAll("\\]", "")+")";
            }
            flip = !flip;
        }

        String gen = "[" + g1 + "," + g2 + "," + g3 + "," + g4 + "]";

		String size = gap.runGapSizeCommand(gen, 2).get(1);
		System.out.println(size);

        System.out.println(gen);
        return gen;

	}
}
