package io.chandler.gap;

import java.io.*;
import java.util.*;

import io.chandler.gap.util.TimeEstimator;

public class GapInterface {
    private static final String gapPath = "gap";
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public GapInterface() throws IOException {
        reset();
    }

    public static void main(String[] args) throws IOException {
        String filename = "matching_oct_generators_full.txt";
        String filenameOut = "matching_oct_generators_full_gap.txt";

        boolean singlePerCategory = true;

        Scanner in = new Scanner(new File(filename));
        int lines = 0;
        while (in.hasNextLine()) {
            lines++;
            in.nextLine();
        }
        in.close();
        in = new Scanner(new File(filename));

        TimeEstimator est = new TimeEstimator(lines);

        GapInterface gap = new GapInterface();

        int i = 0;

        TreeMap<Integer, TreeMap<String, Set<String>>> map = new TreeMap<>();

        long lastLoop = System.currentTimeMillis();

        boolean nextCategory = false;

        while (!Thread.interrupted() && in.hasNextLine()) {
            long time = System.currentTimeMillis();
            if (time - lastLoop > 5000) {
                est.checkProgressEstimate(i, map.size());
                lastLoop = time;

                while (System.in.available() > 0) {
                    int c = System.in.read();
                    if (c == 'n' || i == 'N') {
                        // Next category
                        nextCategory = true;
                    }
                    if (c == 'q' || i == 'Q') {
                        // Quit
                        break;
                    }
                }
            }
            i++;
            //if (i % 400 == 0) gap.reset();
            
            String input = in.nextLine();
            if (input.startsWith("(")) {
                if (nextCategory) {
                    continue;
                }
                String generator = "["+extractGenerator(input)+"]";
                if (generator != null) {
                    //System.out.println(generator);
                    List<String> out = gap.runGapCommands(generator, 3);
                    int order = Integer.parseInt(out.get(1).trim());
                    String groupKey = out.get(2).trim();
                    if (!map.containsKey(order)) map.put(order, new TreeMap<>());
                    TreeMap<String, Set<String>> orderMap = map.get(order);
                    if (!orderMap.containsKey(groupKey)) orderMap.put(groupKey, new TreeSet<>());
                    orderMap.get(groupKey).add(input);
                    if (singlePerCategory) nextCategory = true;
                }
            } else {
                nextCategory = false;
            }
        }


        PrintStream out = new PrintStream(new File(filenameOut));
        out.println("Summary:");
        for (Map.Entry<Integer, TreeMap<String, Set<String>>> o : map.entrySet()) {
            out.println(" * Order " + o.getKey());
            for (Map.Entry<String, Set<String>> e : o.getValue().entrySet()) {
                out.println("   * Group " + e.getKey());
                
            }
        }

        out.println("");
        for (Map.Entry<Integer, TreeMap<String, Set<String>>> o : map.entrySet()) {
            out.println(" ---- Order " + o.getKey() + " ---- ");
            for (Map.Entry<String, Set<String>> e : o.getValue().entrySet()) {
                out.println("  * Group " + e.getKey());
                for (String gen : e.getValue()) {
                    out.println(gen);
                }
            }
        }

        out.close();

        gap.close();
        in.close();
    }

    private static String extractGenerator(String input) {
        int start = input.indexOf('[');
        int end = input.lastIndexOf(']');
        if (start != -1 && end != -1 && start < end) {
            return input.substring(start + 1, end);
        }
        return null;
    }

    public void reset() throws IOException {
        if (process != null) close();
        List<String> commands = new ArrayList<>();
        commands.add(gapPath);
        commands.add("-q"); // Quiet mode, less output
        commands.add("--width");
        commands.add("1000000"); // Avoid line breaks

        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        process = pb.start();

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

    }

    public void close() throws IOException {
        writer.append((char)0xd);
        writer.flush();
        writer.close();
        try {
            process.waitFor();
        } catch (InterruptedException e) {e.printStackTrace();}
        process.destroy();
    }

    public List<String> runGapCommands(String generator, int readNLines) {
        try {
            // Send commands to GAP
            writer.write("g := Group(" + generator + ");");
            writer.newLine();
            writer.write("Print(Size(g), \"\\n\");");
            writer.newLine();
            writer.write("Print(StructureDescription(g), \"\\n\");");
            writer.newLine();
            writer.flush();

            // Read output from GAP
            List<String> lines = new ArrayList<>();
            String line;
            for (int i = 0; i < readNLines; i++) {
                line = reader.readLine();
                if (line.trim().isEmpty()) {
                    i--;
                    continue;
                }
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}