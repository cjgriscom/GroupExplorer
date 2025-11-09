package io.chandler.gap.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.chandler.gap.GapInterface;
import io.chandler.gap.GroupExplorer;

public class OrbitAnalysis {

    private static final Pattern GAP_GROUP_PATTERN = Pattern.compile("GAP:\\s*Group\\((.+)\\)\\s*");

    public static void main(String[] args) throws IOException {
        while (true) {
            d(args);
        }
    }

    public static void d(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        List<String> labels = new ArrayList<>();
        List<String> generators = new ArrayList<>();

        String currentSetLabel = null;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (line.startsWith("Set ")) {
                currentSetLabel = line.trim();
                if (line.contains("(singleton)")) {
                    labels.add(currentSetLabel);
                    generators.add(null);
                    currentSetLabel = null;
                }
            } else if (currentSetLabel != null) {
                Matcher m = GAP_GROUP_PATTERN.matcher(line.trim());
                if (m.find()) {
                    labels.add(currentSetLabel);
                    generators.add(m.group(1));
                    currentSetLabel = null;
                }
            }
        }
        //scanner.close();

        GapInterface gap = new GapInterface();
        for (int i = 0; i < labels.size(); i++) {
            if (generators.get(i) == null) {
                continue;
                //System.out.println(labels.get(i) + " -> (singleton)");
            } else {
                int[][][] nnn = GroupExplorer.renumberGenerators(GroupExplorer.parseOperationsArr(generators.get(i)));
                int n = Integer.MIN_VALUE;
                for (int[][] cycle : nnn) {
                    for (int[] element : cycle) {
                        for (int e : element) {
                            n = Math.max(n, e);
                        }
                    }
                }
                
                List<String> result = gap.runGapCommands(generators.get(i), 3);
                String size = result.get(1).trim();
                String structure = result.get(2).trim();
                System.out.println(labels.get(i).split(":")[0] + " -> " + structure + "("+n+" points)  (order " + size + ")");
            }
        }
        gap.close();
    }
}
