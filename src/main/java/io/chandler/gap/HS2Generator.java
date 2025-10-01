package io.chandler.gap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.function.BiConsumer;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.LongStateCache;
import io.chandler.gap.cache.State;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

public class HS2Generator {
	static final File hs2States = new File("hs_176.gap.lz4");
	static final File outDir = new File("hs_176.gap.states");
	static final File categoryListings = outDir.toPath().resolve("dir.txt").toFile();
	public static void main(String[] args) throws Exception {
		//Files.createDirectories(outDir.toPath());
		//GenerateHS2(hs2States);
		//categorizeHS2States(hs2States, outDir);

        String[] types = new String[] {
            "80p 2-cycles",
            "82p 2-cycles",
            "57p 3-cycles"
        };

        for (String type : types) {
            List<int[][]> cycles = loadHS2CategoryStates(type);

            // Save these cycles to a file
            File cyclesFile = new File("PlanarStudyMulti/hs_176/"+type+".txt");
            try (PrintWriter writer = new PrintWriter(cyclesFile)) {
                for (int[][] cycle : cycles) {
                    writer.println(GroupExplorer.cyclesToNotation(cycle));
                }
            }
            
            System.out.println(cycles.size());
        }

	}

    public static List<int[][]> loadHS2CategoryStates(String category) throws Exception {
        HashMap<String, Integer> categoryToSize = new HashMap<>();
        Scanner inC = new Scanner(categoryListings);
        while (inC.hasNextLine()) {
            String line = inC.nextLine();
            if (line.contains(category)) {
                String[] parts = line.split(": ");
                String size = parts[0];
                String name = parts[1];
                categoryToSize.put(name, Integer.parseInt(size));
            }
        }
        inC.close();

        File src = new File(outDir, category + ".hs_176.bytes.lz4");
        int size = categoryToSize.get(category);
        System.out.println(size);
        List<int[][]> cycles = new ArrayList<>();

        try (InputStream in = new LZ4BlockInputStream(new FileInputStream(src))) {
            for (int i = 0; i < size; i++) {
                int[] state = new int[176];
                for (int j = 0; j < 176; j++) {
                    state[j] = in.read() & 0xff;
                }
                cycles.add(GroupExplorer.stateToCycles(state));
            }
        }

        return cycles;
    }

    public static void categorizeHS2States(File in, File outDir) throws Exception {
        HashMap<String, OutputStream> cycleDescriptionToFile = new HashMap<>();
        exploreHS2(in, (state, desc) -> {
            if (!cycleDescriptionToFile.containsKey(desc)) {
                File file = new File(outDir, desc + ".hs_176.bytes.lz4");
                try {
                    cycleDescriptionToFile.put(desc, new DataOutputStream(new LZ4BlockOutputStream(
                        new FileOutputStream(file), 32*1024*1024)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            OutputStream dos = cycleDescriptionToFile.get(desc);
            try {
                for (int i : state) dos.write(i);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        for (OutputStream dos : cycleDescriptionToFile.values()) {
            try {
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

	public static void exploreHS2(File file,
			BiConsumer<int[], String> peekCyclesAndDescriptions) throws Exception {

		HashMap<String, Integer> cycleDescriptions = new HashMap<>();
		int elements, order;
        
		int progress0 = 0;
		
		try (DataInputStream dis = new DataInputStream(new LZ4BlockInputStream(new FileInputStream(file)))) {
			elements = dis.readInt();
			order = dis.readInt();
			System.out.println("Elements: " + elements);
			System.out.println("Order: " + order);
			for (int i = 0; i < order - 1; i++) {
				int[] state = new int[elements];
				for (int j = 0; j < elements; j++) {
					state[j] = dis.readInt();
				}
				
                String cycleDescription = GroupExplorer.describeState(elements, state);
                if (peekCyclesAndDescriptions != null) peekCyclesAndDescriptions.accept(state, cycleDescription);
                cycleDescriptions.merge(cycleDescription, 1, Integer::sum);

				int progress = (int)((long)i*100 / order);
				if (progress != progress0 && progress % 5 == 0) {
					System.out.println("Loading, " + progress + "%");
					progress0 = progress;
				}
			}
		}

        
        System.out.println("Elements: " + elements);
        System.out.println("Total group permutations: " + order);

        // Print sorted cycle descriptions
        System.out.println("Cycle structure frequencies:");
        cycleDescriptions.entrySet().stream()
            .sorted((e1, e2) -> {
                int comp = Integer.compare(e2.getValue(), e1.getValue()); // Sort by frequency descending
                if (comp == 0) {
                    return e1.getKey().compareTo(e2.getKey()); // If frequencies are equal, sort alphabetically
                }
                return comp;
            })
            .forEach(entry -> System.out.println(entry.getValue() + ": " + entry.getKey()));

	}
	/**
	 * Use a reduced-size cache to find the unique states, while
	 *   storing each new full state to disk
	 * 
	 * Note the start state (1,2,3,4,...,24) is not included
 	 * 
	 * @param file
	 * @throws Exception
	 */
    public static void GenerateHS2(File file) throws Exception {
        Set<State> set = new LongStateCache(18, 176);
        int elements = 176;
        int order = 88704000/2;
        GroupExplorer group = new GroupExplorer(
            Generators.hs_176, MemorySettings.DEFAULT, set);

        int[] totalStates = new int[]{0};

        try (DataOutputStream dos = new DataOutputStream(new LZ4BlockOutputStream(
                new FileOutputStream(file), 32*1024*1024))) {
            
            dos.writeInt(elements);
            dos.writeInt(order);
                
            group.exploreStates(true, (states, depth) -> {
                totalStates[0] += states.size();
                try {
                    for (int[] state : states) {
                        for (int i : state) dos.writeInt(i);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}