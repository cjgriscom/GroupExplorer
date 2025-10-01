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

public class MCL2Generator {
	static final File mcl2States = new File("mcl_2.gap.lz4");
	static final File outDir = new File("mcl_2.gap.states");
	public static void main(String[] args) throws Exception {
		//Files.createDirectories(outDir.toPath());
		//GenerateMCL2(mcl2States);
		//categorizeMCL2States(mcl2States, outDir);

        String[] types = new String[] {
            "120p 2-cycles",
            "132p 2-cycles",
            "90p 3-cycles",
            "87p 3-cycles"
        };

        for (String type : types) {
            List<int[][]> cycles = loadMCL2CategoryStates(type);

            // Save these cycles to a file
            File cyclesFile = new File("PlanarStudyMulti/mcl_2/"+type+".txt");
            try (PrintWriter writer = new PrintWriter(cyclesFile)) {
                for (int[][] cycle : cycles) {
                    writer.println(GroupExplorer.cyclesToNotation(cycle));
                }
            }
            
            System.out.println(cycles.size());
        }

	}

    public static List<int[][]> loadMCL2CategoryStates(String category) throws Exception {

        File src = new File(outDir, category + ".mcl_2.bytes.lz4");
        List<int[][]> cycles = new ArrayList<>();

        try (InputStream in = new LZ4BlockInputStream(new FileInputStream(src))) {

            done: while (true) {
                int[] state = new int[275];
                for (int j = 0; j < 275; j++) {
                    int b = in.read();
                    if (b < 0) break done;
                    state[j] = (b & 0xff) | ((in.read() & 0xff) << 8);
                }
                cycles.add(GroupExplorer.stateToCycles(state));
            }
        }

        return cycles;
    }

    public static void categorizeMCL2States(File in, File outDir) throws Exception {
        HashMap<String, OutputStream> cycleDescriptionToFile = new HashMap<>();
        exploreMCL2(in, (state, desc) -> {
            if (!cycleDescriptionToFile.containsKey(desc)) {
                File file = new File(outDir, desc + ".mcl_2.bytes.lz4");
                try {
                    cycleDescriptionToFile.put(desc, new DataOutputStream(new LZ4BlockOutputStream(
                        new FileOutputStream(file), 32*1024*1024)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            OutputStream dos = cycleDescriptionToFile.get(desc);
            try {
                for (int i : state) {dos.write(i & 0xff); dos.write((i >> 8) & 0xff);}
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

	public static void exploreMCL2(File file,
			BiConsumer<int[], String> peekCyclesAndDescriptions) throws Exception {

		HashMap<String, Integer> cycleDescriptions = new HashMap<>();
		int elements, order;
        
		int progress0 = 0;
		
        int counted = 0;
		try (DataInputStream dis = new DataInputStream(new LZ4BlockInputStream(new FileInputStream(file)))) {
			elements = dis.readInt();
			order = dis.readInt();
			System.out.println("Elements: " + elements);
			System.out.println("Order: " + order);
			for (int i = 0; i < order - 1; i++) {
                try {
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
                    counted++;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
		}

        
        System.out.println("Elements: " + elements);
        System.out.println("Total included permutations: " + counted);

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
    public static void GenerateMCL2(File file) throws Exception {
        Set<State> set = new LongStateCache(7, 275);
        int elements = 275;
        int order = 898128000*2;
        GroupExplorer group = new GroupExplorer(
            Generators.mcl_2, MemorySettings.DEFAULT, set);

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