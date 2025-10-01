package io.chandler.gap.graph;

import java.util.HashMap;
import java.util.List;

import io.chandler.gap.GapInterface;

public class Gyrobifastigium {
	public static void main(String[] args) throws Exception {

		GapInterface gap = new GapInterface();

		// Config bits
		// First gyro has none
		// Second gyro: reverse red, reverse blue
		// End cap: closed?, inverted?

		HashMap<String, String> sizeCache = new HashMap<>();

		String[] gyroRed = {"(a,b,c)(d,f,e)", "(c,b,a)(d,e,f)"};
		String[] gyroBlue = {"(a,d,g)(f,c,h)", "(g,d,a)(h,c,f)"};

		String startA = "b";
		String startB = "e";

		String endA = "g";
		String endB = "h";

		int n = 8;
		int nConfigs = 2*n;

		for (int i = 0; i < Math.pow(2, nConfigs); i++) {
			boolean[] config = new boolean[nConfigs];
			for (int j = 0; j < nConfigs; j++) {
				config[j] = (i & (1 << j)) != 0;
			}

			String generatorA = "";
			String generatorB = "";

			boolean closed = config[2*n-1];
			boolean inverted = config[2*n-2];
			if (inverted && !closed) continue;

			String lastPrefix = "";
			String lastEndPrefix = n == 1 ? "" : "" + (n-1);

			for (int j = 0; j < n; j++) {

				
				int reverseRed = 0;
				int reverseBlue = 0;
				if (j > 0) {
					reverseRed = config[2*(j-1)] ? 1 : 0;
					reverseBlue = config[2*(j-1) + 1] ? 1 : 0;
				}
				String generatorPieceA = gyroRed[reverseRed];
				String generatorPieceB = gyroBlue[reverseBlue];

				String prefix = j == 0 ? "" : "" + j;

				String[] repl = new String[8];
				for (int k = 0; k < 8; k++) {
					repl[k] = prefix + (k + 1);
				}

				if (closed && j == 0) {
					if (inverted) {
					repl[startA.charAt(0) - 'a'] = lastEndPrefix + (endB.charAt(0) - 'a' + 1);
					repl[startB.charAt(0) - 'a'] = lastEndPrefix + (endA.charAt(0) - 'a' + 1);
					} else {
					repl[startA.charAt(0) - 'a'] = lastEndPrefix + (endA.charAt(0) - 'a' + 1);
					repl[startB.charAt(0) - 'a'] = lastEndPrefix + (endB.charAt(0) - 'a' + 1);
					}
				} else if (j > 0) {
					repl[startA.charAt(0) - 'a'] = lastPrefix + (endA.charAt(0) - 'a' + 1);
					repl[startB.charAt(0) - 'a'] = lastPrefix + (endB.charAt(0) - 'a' + 1);
				}

				for (int k = 0; k < 8; k++) {
					generatorPieceA = generatorPieceA.replaceAll(((char)(k + 'a')) + "", repl[k]);
					generatorPieceB = generatorPieceB.replaceAll(((char)(k + 'a')) + "", repl[k]);
				}

				generatorA += generatorPieceA;
				generatorB += generatorPieceB;

				lastPrefix = prefix;
			}

			String generator = "[" + generatorA + "," + generatorB + "]";
			for (int j = 0; j < 8; j++) {
				generator = generator.replaceAll(((char)(j + 'a')) + "", "" + (j + 1));
			}



			//System.out.println(generator);
			List<String> out = gap.runGapSizeCommand(generator, 2);
			String size = out.get(1);
			String structure = sizeCache.get(size) == null ? "" : sizeCache.get(size) + "*";

			if (structure.isEmpty() || size.length() <= 3) {
				structure = gap.runGapCommands(generator, 3).get(2);
				sizeCache.put(size, structure);
			}

			System.out.println(n + " " + Integer.toUnsignedString(i, 2) + " " + size + " " + structure);
			
		}
	}
}
