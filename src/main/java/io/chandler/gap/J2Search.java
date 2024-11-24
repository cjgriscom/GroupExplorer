package io.chandler.gap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import io.chandler.gap.GroupExplorer.Generator;
import io.chandler.gap.GroupExplorer.MemorySettings;

public class J2Search {

	// You can renumber the whole icochop by repeating the 
	//     operations and assigning numbers to one face at a time

	public static void main(String[] args) throws IOException {
		
		checkIcoChop2();
	}

	public static int[][] reorder5_5(int[][] src) {
		ArrayList<int[]> out = new ArrayList<>();
		for (int[] x : src) {
			if (x.length == 5) out.add(0, x);
			else out.add(x);
		}
		
		return out.toArray(new int[out.size()][]);
	}
	public static void checkIcoChop2() throws IOException {

		Generator gen = J2Search.getRenumberedGenerator_5circles(Generators.j2);

		int srces_chk = 5;
		String cat_check = "10p 10-cycles";

		boolean takeSubgroups = false;

		System.out.println(GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators(gen.generator())));

		ArrayList<int[][]> test = new ArrayList<>();

		
		PrintStream out = new PrintStream(new FileOutputStream("40p2.txt"));
		GroupExplorer ge = new GroupExplorer(gen, 100, MemorySettings.FASTEST, new HashSet<>());
		Generators.exploreGroup(ge, (state, disc) -> {
			if (disc.equals(cat_check)) test.add(GroupExplorer.stateToCycles(state));
		});
		ArrayList<Generator> found = new ArrayList<>();

		HashMap<String, Integer> outputCounts = new HashMap<>();

		nextGenerator:
		for (int iii = 0; iii < test.size(); iii++) {
			int[][] cycles = test.get(iii);
			Generator vGen = new Generator(new int[][][] {
				cycles,
				gen.generator()[0],
			});
			GroupExplorer verify = new GroupExplorer(vGen, 100, MemorySettings.FASTEST, new HashSet<>());
			int iter = verify.exploreStates(false, 1000, null);
			if (iter == -1 ^ takeSubgroups) {
				//System.out.println("found");
				found.add(vGen);

				out.println(GroupExplorer.generatorsToString(vGen.generator()));

				HashMap<String, Integer> groups = new HashMap<>();
				for (int[] cycle : cycles) {
					HashMap<Integer, Integer> srcesS = new HashMap<>();
					for (int i : cycle) {
						srcesS.put((i-1)/5, srcesS.getOrDefault((i-1)/5, 0) + 1);
					}
					ArrayList<String> srces = new ArrayList<>();
					for (Entry<Integer, Integer> entry : srcesS.entrySet()) {
						//srces.add(entry.getValue() + "_" + entry.getKey());
						srces.add(entry.getValue() + "");
					}
					Collections.sort(srces);
					groups.put(srces.toString(), groups.getOrDefault(srces.toString(), 0) + 1);
					//System.out.println(srces.toString());
				}
				ArrayList<Integer> values_sort = new ArrayList<>(groups.values());
				Collections.sort(values_sort);
				
				String valuesString = "";
				for (int i : values_sort) {
					valuesString += (i + " ");
				}
				//System.out.print(iii + " / " + test.size() + ": ");
				//System.out.println(valuesString);
				if (valuesString.contains("5 5")) System.out.println(GroupExplorer.generatorsToString(vGen.generator()));
				outputCounts.put(valuesString, outputCounts.getOrDefault(valuesString, 0) + 1);

				//if (values_sort.contains(5)) System.out.println(GroupExplorer.generatorsToString(vGen.generator()));

			}
			//if (found.size() > 20) break;
		}
		//System.out.println(found);

		System.out.println(outputCounts);
		/*
		Generator g = found.get(0);
		for (int i = 1; i < 20; i++) {
			g = Generator.combine(g, found.get(i));
		}
		GroupExplorer verify = new GroupExplorer(g, 100, MemorySettings.FASTEST, new HashSet<>());
		int iter = verify.exploreStates(true, null);
		*/
		out.close();


	}

	public static void checkIcoChop() throws IOException {
		Generator gen = getRenumberedGenerator_Icochop();

		ArrayList<int[][]> p40_2Cycles = new ArrayList<>();
		
		PrintStream out = new PrintStream(new FileOutputStream("40p2.txt"));
		GroupExplorer ge = new GroupExplorer(gen, 100, MemorySettings.FASTEST, new HashSet<>());
		Generators.exploreGroup(ge, (state, disc) -> {
			out.println(disc);
			out.println(GroupExplorer.cyclesToNotation(GroupExplorer.stateToCycles(state)));
			if (disc.equals("40p 2-cycles")) {
				p40_2Cycles.add(GroupExplorer.stateToCycles(state));
			}
		});
		out.close();

		System.exit(0);


		TreeMap<String, Integer> occurances1Accum = new TreeMap<>();
		for (int[][] cycles : p40_2Cycles) {
			// Look for cycles that contain indices 1-5

			int occurances1_5_31_35 = 0;;
			int occurances6_10_36_40 = 0;
			for (int[] cycle : cycles) {
				int o1_5_cycle = 0;
				int i31_35_cycle = 0;
				int i6_10_cycle = 0;
				int i36_40_cycle = 0;
				for (int i : cycle) {
					if (i >= 1 && i <= 5) o1_5_cycle++;
					if (i >= 31 && i <= 35) i31_35_cycle++;
					if (i >= 6 && i <= 10) i6_10_cycle++;
					if (i >= 36 && i <= 40) i36_40_cycle++;
				}
				if (o1_5_cycle == 1 && i31_35_cycle == 1) occurances1_5_31_35++;
				if (i6_10_cycle == 1 && i36_40_cycle == 1) occurances6_10_36_40++;
			}

			String comb1 = occurances1_5_31_35 + "_" + occurances6_10_36_40;

			// Now figure out how to see if they're symmetrical

			occurances1Accum.put(comb1, occurances1Accum.getOrDefault(comb1, 0) + 1);
		}

		System.out.println(occurances1Accum);
		/*
		 * {3=70, 4=175, 5=70}
		 * {3=70, 4=175, 5=70}
		 */
	}

	public static Generator getRenumberedGenerator_5circles(String generator) {

		{
			ArrayList<int[][]> test = new ArrayList<>();

			GroupExplorer ge = new GroupExplorer(generator, MemorySettings.FASTEST, new HashSet<>());
			Generators.exploreGroup(ge, (state, disc) -> {
				if (disc.equals("20p 5-cycles")) {
					test.add(GroupExplorer.stateToCycles(state));
				}
			});
			Generator vGen = new Generator(new int[][][] {
				test.get(0),
				GroupExplorer.parseOperationsArr(generator)[0],
				GroupExplorer.parseOperationsArr(generator)[1],
			});
			GroupExplorer verify = new GroupExplorer(vGen, 100, MemorySettings.FASTEST, new HashSet<>());
			int iter = verify.exploreStates(false, 10000, null);
			if (iter == -1) {
				//System.out.println("found");
				return new Generator(GroupExplorer.renumberGenerators(vGen.generator()));
			}
			
		}
		return null;
	}

	public static Generator getRenumberedGenerator_Icochop() {

		GroupExplorer ge = new GroupExplorer(Generators.j2, MemorySettings.FASTEST);

		ArrayList<int[]> states0 = new ArrayList<>();

		ArrayList<int[]> statesSplitMove = new ArrayList<>();

		ge.setMultithread(false);

		Generators.exploreGroup(ge, (state, disc) -> {
			if (disc.equals("dual 5-cycles, 6p 15-cycles")) states0.add(state);
			if (disc.equals("40p 2-cycles")) statesSplitMove.add(state);
		});

		// State to cycles 0

		int[][] cycles = GroupExplorer.stateToCycles(states0.get(0));
		cycles = reorder5_5(cycles);

		int[][] cyclesRen = null;
		int[][] found = null;
		int[][] piggyback = null;

		for (int[] state : states0) {
			if (state == states0.get(0)) continue;
			int[][][] gen = new int[][][] {
				cycles, reorder5_5(GroupExplorer.stateToCycles(state)),
				GroupExplorer.stateToCycles(statesSplitMove.get(0))
			};
			gen = GroupExplorer.renumberGenerators(gen);
			cyclesRen = gen[0];
			found = gen[1];
			piggyback = gen[2];

			gen = new int[][][] {gen[0], gen[1]};

			TreeSet<Integer> cycle0Numbers = new TreeSet<>();
			TreeSet<Integer> cycle1Numbers = new TreeSet<>();
			for (int i : gen[1][0]) cycle0Numbers.add(i);
			for (int i : gen[1][1]) cycle1Numbers.add(i);
			if (cycle0Numbers.last() - cycle0Numbers.first() > 25-11
					 || cycle0Numbers.first() <= 10) continue;
			if (cycle1Numbers.last() - cycle1Numbers.first() > 25-11
					|| cycle1Numbers.first() <= 10) continue;

			int order ;

			//if (found == 0) {
				System.out.println(GroupExplorer.generatorsToString(gen));
				Generator gg = new Generator(gen);
				System.out.println(order = getOrder(gg));
			//}
			//break;

			if (order == 300) {
				break;
			}
		}

		System.out.println("Ico Chop with gearing");

		int[][] gen0Ren = cyclesRen, gen1Ren = found;


		int[][][] combinedOriginal = new int[][][]{gen0Ren, gen1Ren};

		System.out.println("Combined original");
		System.out.println(GroupExplorer.generatorsToString(GroupExplorer.renumberGenerators(combinedOriginal)));
		System.out.println();
		// Manually renumber
		int[][][] helper = new int[][][]{new int[][] {
			 gen0Ren[0], gen1Ren[0], // Front faces
			 gen0Ren[1], gen1Ren[1], // Back faces
		}};

		/*Remapref = [
			(1,2,3,4,5)(6,7,8,9,10)
			(31,32,33,34,35)(36,37,38,39,40)] */
		int[][] remap = new int[][] {
			{1,2,3,4,5},{6,7,8,9,10},
			{31,32,33,34,35},{36,37,38,39,40}
		};

		int[][][] fulloriginal = Generator.combine(
									Generator.combine(
											new Generator(helper),
											new Generator(combinedOriginal)
									), new Generator(piggyback)).generator();
		int[][][] fullRenumbered = GroupExplorer.renumberGenerators(fulloriginal);

		int[][] remapRef = fullRenumbered[0];

		System.out.println("Remap ref");
		System.out.println(GroupExplorer.generatorsToString(new int[][][]{fullRenumbered[0]}));

		TreeMap<Integer, Integer> map = new TreeMap<Integer, Integer>();
		for (int i = 0; i < remap.length; i++) {
			for (int j = 0; j < remap[i].length; j++) {
				Integer old = map.put(remapRef[i][j], remap[i][j]);
				if (old != null && old != remap[i][j]) 
					throw new IllegalArgumentException("Collision: " + remapRef[i][j] + " -> " + remap[i][j] + " overwrites old value " + old);
			}
		}



		int[][][] fullRenumberedRenumbered = new int[fullRenumbered.length][][];
		

		GroupExplorer numberer = new GroupExplorer(GroupExplorer.generatorsToString(new int[][][]{fullRenumbered[1], fullRenumbered[2]}), MemorySettings.FASTEST);


		System.out.println("Numberer");
		System.out.println(GroupExplorer.cyclesToNotation(numberer.parsedOperations.get(1)));
		numberer.resetElements(false);

		TreeSet<Integer> remainingIndexGroups = new TreeSet<>();
		for (int i = 1; i <= 20; i++) remainingIndexGroups.add(i);
		remainingIndexGroups.remove(5/5);
		remainingIndexGroups.remove(10/5);
		remainingIndexGroups.remove(35/5);
		remainingIndexGroups.remove(40/5);

		numberer.exploreStates(true, (states, depth) -> {
			for (int[] state : states) {
				if (!map.containsKey(state[0])) {
					int indexGroup = remainingIndexGroups.pollFirst();
					for (int ele = 0; ele < 5; ele++) {
						map.put(state[ele], (indexGroup-1)*5 + ele + 1);
					}
				}
			}
		});
		
		
		TreeSet<Integer> unusedRemaps = new TreeSet<>();
		for (int i = 1; i <= 100; i++) unusedRemaps.add(i);
		unusedRemaps.removeAll(map.keySet());

		System.out.println(unusedRemaps.size());

		for (int i = 0; i < fullRenumbered.length; i++) {
			fullRenumberedRenumbered[i] = new int[fullRenumbered[i].length][];
			for (int j = 0; j < fullRenumbered[i].length; j++) {
				fullRenumberedRenumbered[i][j] = new int[fullRenumbered[i][j].length];
				for (int k = 0; k < fullRenumbered[i][j].length; k++) {
					int old = fullRenumbered[i][j][k];
					Integer n = map.get(old);
					//System.out.println(fullRenumbered[i][j][k] + " - " + n);
					fullRenumberedRenumbered[i][j][k] = n;
				}
			}
		}



		int[][][] fullRenumberedRenumbered2 = new int[fullRenumberedRenumbered.length-1][][];
		for (int i = 1; i < fullRenumberedRenumbered.length; i++) {
			fullRenumberedRenumbered2[i-1] = fullRenumberedRenumbered[i];
		}

		System.out.println();
		System.out.println(GroupExplorer.generatorsToString(fullRenumberedRenumbered2));
		
		return new Generator(fullRenumberedRenumbered2);

	}

	private static int getOrder(Generator gen) {
		
		GroupExplorer verify = new GroupExplorer(gen, 100, MemorySettings.FASTEST, new HashSet<>());
		int iter = verify.exploreStates(false, 604801, null);
		if (iter < 0) return iter;
		return verify.order();
	}

	public static void old(String[] args) {
		GroupExplorer ge = new GroupExplorer(Generators.j2, MemorySettings.FASTEST);

		ArrayList<int[]> states0 = new ArrayList<>();
		ArrayList<int[]> states2 = new ArrayList<>();

		Generators.exploreGroup(ge, (state, disc) -> {
			if (disc.equals("20p 5-cycles")) states0.add(state);
			if (disc.equals("40p 2-cycles")) states2.add(state);
		});

		int[][] gen0 = GroupExplorer.stateToCycles(states0.get(0));
		int[][] piggyback = GroupExplorer.stateToCycles(states0.get(100));

		ArrayList<int[][][]> generatorsNewNumbering = new ArrayList<>();

		nextPair:
		for (int[] state1 : states0) {
			if (state1 == states0.get(0)) continue;
			if (state1 == states0.get(100)) continue;
			int[][][] genArr = new int[][][] {
				gen0,
				GroupExplorer.stateToCycles(state1),
				piggyback};
			int[][][] genRe = GroupExplorer.renumberGenerators(genArr);
			Generator combined = new Generator(new int[][][]{genRe[0], genRe[1]});

			int[][] gen1Re = genRe[1];

			for (int i = 0 ; i < 20; i++) {
				// Check that each cycle contains one element from each 1-5 range
				for (int[] cycle : gen1Re) {
					HashSet<Integer> ranges = new HashSet<>();

					for (int v : cycle) {
						int vrange = (v-1)/5+1; // outputs 1-20
						ranges.add(vrange);
					}

					if (ranges.size() != 5) continue nextPair;
				}
				
			}

			

			GroupExplorer verify = new GroupExplorer(combined, 100, MemorySettings.FASTEST, new HashSet<>());
			int iter = verify.exploreStates(false, 3000, null);
			if (iter < 0) continue;

			if (verify.order() == 300) generatorsNewNumbering.add(genRe);
		}

		System.out.println("Found " + generatorsNewNumbering.size());

		ge = new GroupExplorer(GroupExplorer.generatorsToString(generatorsNewNumbering.get(0)), MemorySettings.FASTEST);

		states0.clear();
		states2.clear();

		Generators.exploreGroup(ge, (state, disc) -> {
			if (disc.equals("20p 5-cycles")) states0.add(state);
			if (disc.equals("40p 2-cycles")) states2.add(state);
		});

		HashMap<String, Integer> misc = new HashMap<>();
		for (int[] s2 : states2) {
			int[][] c2 = GroupExplorer.stateToCycles(s2);
			HashMap<String, Integer> tmpStateCache = new HashMap<>();
			for (int[] cycle : c2) {
				String key = "";
				int i0 = cycle[0];
				int i1 = cycle[1];
				i0 = (i0 - 1) / 5 + 1; // Categorize by face
				i1 = (i1 - 1) / 5 + 1;
				// Sort
				if (i0 > i1) { int tmp = i0; i0 = i1; i1 = tmp; }
				if (i0 == i1) {
					key = "same";
				}
				key += i0 + "," + i1;
				tmpStateCache.put(key, tmpStateCache.getOrDefault(key, 0) + 1);
			}

			for (Entry<String, Integer> entry : tmpStateCache.entrySet()) {
				misc.put(entry.getKey(), entry.getValue());
			}
		}

		System.out.println(misc);
		
	}
}
