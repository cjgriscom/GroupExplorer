package io.chandler.gap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.KeyframeStateCache;
import io.chandler.gap.cache.State;

/**
 * Computes BFS depth distributions for every puzzle in GridExplorer's
 * {@code samplePuzzles.ts} and writes spreadsheet-friendly CSV output.
 *
 * <p>Uses the same exploration setup as {@link Generators#exploreGroup}.
 */
public class SamplePuzzleDepthDistribution {

    private static final Path DEFAULT_PUZZLES =
        Paths.get("/home/cjgriscom/Programming/GridExplorer/src/samplePuzzles.ts");
    private static final Path DEFAULT_OUTPUT_DIR =
        Paths.get("sample_puzzle_depth_distribution");

    static final class PuzzleDef {
        final String id;
        final String name;
        final String groupName;
        final int order;
        final int pieces;
        final int godsNumber;
        final String generator;

        PuzzleDef(String id, String name, String groupName, int order, int pieces,
                int godsNumber, String generator) {
            this.id = id;
            this.name = name;
            this.groupName = groupName;
            this.order = order;
            this.pieces = pieces;
            this.godsNumber = godsNumber;
            this.generator = generator;
        }
    }

    static final class DepthStats {
        final TreeMap<Integer, Integer> countsByDepth;
        final int iterations;
        final int groupOrder;

        DepthStats(TreeMap<Integer, Integer> countsByDepth, int iterations, int groupOrder) {
            this.countsByDepth = countsByDepth;
            this.iterations = iterations;
            this.groupOrder = groupOrder;
        }
    }

    public static void main(String[] args) throws IOException {
        Path puzzlesPath = DEFAULT_PUZZLES;
        Path outputDir = DEFAULT_OUTPUT_DIR;
        long maxOrder = Long.MAX_VALUE;
        String puzzleIdIn = null;
        int compressLongs = 2;
        boolean noKeyframes = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--puzzles".equals(arg)) {
                puzzlesPath = Paths.get(args[++i]);
            } else if ("--output-dir".equals(arg)) {
                outputDir = Paths.get(args[++i]);
            } else if ("--max-order".equals(arg)) {
                maxOrder = Long.parseLong(args[++i]);
            } else if ("--puzzle-id".equals(arg)) {
                puzzleIdIn = args[++i];
            } else if ("--compress-longs".equals(arg)) {
                compressLongs = Integer.parseInt(args[++i]);
            } else if ("--keyframe-interval".equals(arg)) {
                KeyframeStateCache.KEYFRAME_INTERVAL = Integer.parseInt(args[++i]);
            } else if ("--no-keyframes".equals(arg)) {
                noKeyframes = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        final String puzzleId = puzzleIdIn;
        KeyframeStateCache.KEYFRAMES_ENABLED = !noKeyframes;
        List<PuzzleDef> puzzles = parseSamplePuzzles(puzzlesPath);
        if (puzzleId != null && puzzles.stream().noneMatch(p -> p.id.equals(puzzleId))) {
            throw new IllegalArgumentException("Unknown puzzle id: " + puzzleId);
        }
        Files.createDirectories(outputDir);

        Map<String, DepthStats> results = new LinkedHashMap<>();
        List<PuzzleDef> explored = new ArrayList<>();
        List<String> orderMismatches = new ArrayList<>();
        List<String> godsNumberMismatches = new ArrayList<>();

        for (PuzzleDef puzzle : puzzles) {
            if (puzzleId != null && !puzzle.id.equals(puzzleId)) {
                continue;
            }
            System.out.println("=== " + puzzle.id + " (" + puzzle.name + ") ===");
            if (puzzle.order > maxOrder) {
                System.out.println("Skipping: order " + puzzle.order + " exceeds max-order " + maxOrder);
                continue;
            }

            DepthStats stats = exploreDepthDistribution(
                puzzle.generator,
                compressMemFor(puzzle.id, compressLongs));
            results.put(puzzle.id, stats);
            explored.add(puzzle);

            int maxDepth = stats.countsByDepth.lastKey();
            int totalStates = totalStates(stats);
            System.out.println("Group order: " + stats.groupOrder
                + " (expected " + puzzle.order + ")");
            System.out.println("Max depth: " + maxDepth
                + " (godsNumber " + puzzle.godsNumber + ")");
            System.out.println("Total states: " + totalStates);
            System.out.println();

            if (totalStates != puzzle.order) {
                orderMismatches.add(String.format(
                    "%s: parsed order=%d, total states=%d, computed order=%d",
                    puzzle.id, puzzle.order, totalStates, stats.groupOrder));
            } else if (stats.groupOrder != puzzle.order) {
                orderMismatches.add(String.format(
                    "%s: parsed order=%d, total states=%d, computed order=%d",
                    puzzle.id, puzzle.order, totalStates, stats.groupOrder));
            }

            if (maxDepth != puzzle.godsNumber) {
                godsNumberMismatches.add(String.format(
                    "%s: godsNumber=%d, max depth=%d (delta %+d)",
                    puzzle.id, puzzle.godsNumber, maxDepth, maxDepth - puzzle.godsNumber));
            }
        }

        writeWideCsv(outputDir.resolve("depth_distribution_wide.csv"), explored, results);
        writeLongCsv(outputDir.resolve("depth_distribution_long.csv"), explored, results);
        writeSummaryCsv(outputDir.resolve("depth_distribution_summary.csv"), explored, results);
        writeMismatchSummary(
            outputDir.resolve("verification_mismatches.txt"),
            orderMismatches,
            godsNumberMismatches);

        System.out.println("Wrote CSV spreadsheets to " + outputDir.toAbsolutePath());
        printVerificationSummary(orderMismatches, godsNumberMismatches);

        if (puzzleId != null && explored.isEmpty()) {
            throw new IllegalStateException("No puzzles explored for id: " + puzzleId);
        }

        if (!orderMismatches.isEmpty() || !godsNumberMismatches.isEmpty()) {
            throw new IllegalStateException("Verification failed: "
                + orderMismatches.size() + " order mismatch(es), "
                + godsNumberMismatches.size() + " godsNumber mismatch(es)");
        }
    }

    static int totalStates(DepthStats stats) {
        return stats.countsByDepth.values().stream().mapToInt(Integer::intValue).sum();
    }

    static void printVerificationSummary(List<String> orderMismatches, List<String> godsNumberMismatches) {
        System.out.println();
        System.out.println("=== Verification summary ===");

        System.out.println("Order (parsed order vs total states / computed order):");
        if (orderMismatches.isEmpty()) {
            System.out.println("  All explored puzzles match.");
        } else {
            for (String line : orderMismatches) {
                System.out.println("  MISMATCH " + line);
            }
        }

        System.out.println("God's number (godsNumber vs max BFS depth):");
        if (godsNumberMismatches.isEmpty()) {
            System.out.println("  All explored puzzles match.");
        } else {
            for (String line : godsNumberMismatches) {
                System.out.println("  MISMATCH " + line);
            }
        }
    }

    static void writeMismatchSummary(Path path, List<String> orderMismatches,
            List<String> godsNumberMismatches) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("Order mismatches (parsed order vs total states / computed order):");
            out.newLine();
            if (orderMismatches.isEmpty()) {
                out.write("none");
                out.newLine();
            } else {
                for (String line : orderMismatches) {
                    out.write(line);
                    out.newLine();
                }
            }

            out.newLine();
            out.write("God's number mismatches (godsNumber vs max BFS depth):");
            out.newLine();
            if (godsNumberMismatches.isEmpty()) {
                out.write("none");
                out.newLine();
            } else {
                for (String line : godsNumberMismatches) {
                    out.write(line);
                    out.newLine();
                }
            }
        }
    }

    static MemorySettings compressMemFor(String puzzleId, int compressLongs) {
        if ("weyl_e8".equals(puzzleId)) {
            return MemorySettings.compressWeylE8(compressLongs);
        }
        return MemorySettings.compress(compressLongs);
    }

    static DepthStats exploreDepthDistribution(String generatorNotation, MemorySettings compressMem) {
        HashSet<State> states = new HashSet<>();
        GroupExplorer gap = new GroupExplorer(
            generatorNotation,
            compressMem,
            states,
            new HashSet<>(),
            new HashSet<>(),
            true);

        TreeMap<Integer, Integer> countsByDepth = new TreeMap<>();
        countsByDepth.put(0, 1);

        int iterations = gap.exploreStates(true, (newStates, depth) -> {
            countsByDepth.merge(depth, newStates.size(), Integer::sum);
        });

        return new DepthStats(countsByDepth, iterations, gap.order());
    }

    static void writeWideCsv(Path path, List<PuzzleDef> puzzles, Map<String, DepthStats> results)
            throws IOException {
        int maxDepth = puzzles.stream()
            .mapToInt(p -> results.get(p.id).countsByDepth.lastKey())
            .max()
            .orElse(0);

        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("depth");
            for (PuzzleDef puzzle : puzzles) {
                out.write(',');
                out.write(csv(puzzle.id));
            }
            out.newLine();

            for (int depth = 0; depth <= maxDepth; depth++) {
                out.write(Integer.toString(depth));
                for (PuzzleDef puzzle : puzzles) {
                    out.write(',');
                    Integer count = results.get(puzzle.id).countsByDepth.get(depth);
                    out.write(count == null ? "0" : count.toString());
                }
                out.newLine();
            }
        }
    }

    static void writeLongCsv(Path path, List<PuzzleDef> puzzles, Map<String, DepthStats> results)
            throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("puzzle_id,name,group_name,depth,new_states,cumulative_states,fraction_of_group");
            out.newLine();

            for (PuzzleDef puzzle : puzzles) {
                DepthStats stats = results.get(puzzle.id);
                int cumulative = 0;
                for (Entry<Integer, Integer> entry : stats.countsByDepth.entrySet()) {
                    cumulative += entry.getValue();
                    out.write(csv(puzzle.id));
                    out.write(',');
                    out.write(csv(puzzle.name));
                    out.write(',');
                    out.write(csv(puzzle.groupName));
                    out.write(',');
                    out.write(entry.getKey().toString());
                    out.write(',');
                    out.write(entry.getValue().toString());
                    out.write(',');
                    out.write(Integer.toString(cumulative));
                    out.write(',');
                    out.write(String.format("%.10f", (double) cumulative / stats.groupOrder));
                    out.newLine();
                }
            }
        }
    }

    static void writeSummaryCsv(Path path, List<PuzzleDef> puzzles, Map<String, DepthStats> results)
            throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("puzzle_id,name,group_name,pieces,expected_order,computed_order,gods_number,max_depth,total_states,order_matches,gods_number_matches,iterations");
            out.newLine();

            for (PuzzleDef puzzle : puzzles) {
                DepthStats stats = results.get(puzzle.id);
                int maxDepth = stats.countsByDepth.lastKey();
                int totalStates = totalStates(stats);
                boolean orderMatches = totalStates == puzzle.order && stats.groupOrder == puzzle.order;
                boolean godsNumberMatches = maxDepth == puzzle.godsNumber;

                out.write(csv(puzzle.id));
                out.write(',');
                out.write(csv(puzzle.name));
                out.write(',');
                out.write(csv(puzzle.groupName));
                out.write(',');
                out.write(Integer.toString(puzzle.pieces));
                out.write(',');
                out.write(Integer.toString(puzzle.order));
                out.write(',');
                out.write(Integer.toString(stats.groupOrder));
                out.write(',');
                out.write(Integer.toString(puzzle.godsNumber));
                out.write(',');
                out.write(Integer.toString(maxDepth));
                out.write(',');
                out.write(Integer.toString(totalStates));
                out.write(',');
                out.write(Boolean.toString(orderMatches));
                out.write(',');
                out.write(Boolean.toString(godsNumberMatches));
                out.write(',');
                out.write(Integer.toString(stats.iterations));
                out.newLine();
            }
        }
    }

    static List<PuzzleDef> parseSamplePuzzles(Path path) throws IOException {
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String content = raw.replaceAll("//[^\n]*", "");

        Pattern blockPattern = Pattern.compile(
            "\\{\\s*id:\\s*'([^']+)'\\s*,\\s*name:\\s*'((?:\\\\'|[^'])*)'\\s*,\\s*groupName:\\s*'((?:\\\\'|[^'])*)'"
            + "\\s*,\\s*order:\\s*(\\d+)\\s*,\\s*pieces:\\s*(\\d+).*?godsNumber:\\s*(\\d+).*?"
            + "generator:\\s*((?:'[^']*'\\s*(?:\\+\\s*)?)+)",
            Pattern.DOTALL);

        List<PuzzleDef> puzzles = new ArrayList<>();
        Matcher matcher = blockPattern.matcher(content);
        while (matcher.find()) {
            puzzles.add(new PuzzleDef(
                matcher.group(1),
                unescapeTs(matcher.group(2)),
                unescapeTs(matcher.group(3)),
                Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)),
                Integer.parseInt(matcher.group(6)),
                parseGeneratorLiteral(matcher.group(7))));
        }

        if (puzzles.isEmpty()) {
            throw new IllegalStateException("No puzzles parsed from " + path);
        }
        return puzzles;
    }

    static String parseGeneratorLiteral(String literal) {
        StringBuilder sb = new StringBuilder();
        Matcher part = Pattern.compile("'([^']*)'").matcher(literal);
        while (part.find()) {
            sb.append(part.group(1));
        }
        return sb.toString();
    }

    static String unescapeTs(String value) {
        return value.replace("\\'", "'");
    }

    static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
