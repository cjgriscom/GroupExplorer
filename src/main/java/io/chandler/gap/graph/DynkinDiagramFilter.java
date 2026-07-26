package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.graph.CoxeterRelatorChecker.MatchResult;

/**
 * Filters PlanarStudy-style generator lines by Coxeter/Dynkin relators.
 * <p>
 * Each input line must contain one combined generator string with the same number
 * of involutions as the spec. Generators may appear in any order; accepted lines
 * are written in diagram order (e.g. A, B, C, D for F4).
 */
public final class DynkinDiagramFilter {
	private final CoxeterDynkinSpec spec;
	private final String outputSuffix;

	public DynkinDiagramFilter(CoxeterDynkinSpec spec) {
		this(spec, "-dynkin");
	}

	public DynkinDiagramFilter(CoxeterDynkinSpec spec, String outputSuffix) {
		this.spec = spec;
		this.outputSuffix = outputSuffix;
	}

	public CoxeterDynkinSpec spec() {
		return spec;
	}

	public String outputSuffix() {
		return outputSuffix;
	}

	public static Path outputPath(Path input, String suffix) {
		String fileName = input.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		if (dot >= 0) {
			return input.resolveSibling(fileName.substring(0, dot) + suffix + fileName.substring(dot));
		}
		return input.resolveSibling(fileName + suffix);
	}

	public Path filterFile(Path input) throws IOException {
		Path output = outputPath(input, outputSuffix);
		filterFile(input, output);
		return output;
	}

	public FilterStats filterFile(Path input, Path output) throws IOException {
		FilterStats stats = new FilterStats();
		try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
				BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				stats.totalLines++;
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				MatchResult result = filterLine(trimmed);
				if (result.matches) {
					stats.accepted++;
					writer.write(reorderLine(trimmed, result));
					writer.newLine();
				} else {
					stats.rejected++;
				}
			}
		}
		return stats;
	}

	public MatchResult filterLine(String line) {
		int[][][] generators = GroupExplorer.parseOperationsArr(stripInlineComment(line));
		return CoxeterRelatorChecker.match(generators, spec);
	}

	public String reorderLine(String line, MatchResult result) {
		int[][][] generators = GroupExplorer.parseOperationsArr(stripInlineComment(line));
		int[][][] ordered = new int[spec.generatorCount()][][];
		for (int diagramPos = 0; diagramPos < spec.generatorCount(); diagramPos++) {
			ordered[diagramPos] = generators[result.diagramOrder[diagramPos]];
		}
		String reordered = GroupExplorer.generatorsToString(ordered);
		if (result.comment != null && !result.comment.isEmpty()) {
			return reordered + " # " + result.comment;
		}
		return reordered;
	}

	/** Strip PlanarStudy-style trailing comments ({@code " # ..."}). */
	public static String stripInlineComment(String line) {
		int hash = line.indexOf(" #");
		return hash >= 0 ? line.substring(0, hash).trim() : line.trim();
	}

	public static final class FilterStats {
		public int totalLines;
		public int accepted;
		public int rejected;

		@Override
		public String toString() {
			return "FilterStats{totalLines=" + totalLines
					+ ", accepted=" + accepted
					+ ", rejected=" + rejected + "}";
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("Usage: DynkinDiagramFilter <input.txt> [output-suffix]");
			System.err.println("  Default spec: F4 (A-B=3, B-C=4, C-D=3, other pairs = 2)");
			System.err.println("  Default output: <input-base>-dynkin.txt");
			System.exit(1);
		}

		Path input = Paths.get(args[0]);
		String suffix = args.length >= 2 ? args[1] : "-dynkin";
		CoxeterDynkinSpec spec = CoxeterDynkinSpec.f4();
		DynkinDiagramFilter filter = new DynkinDiagramFilter(spec, suffix);

		System.out.println("Input:  " + input);
		System.out.println("Spec:   F4 " + String.join("-", spec.labels()));
		System.out.println("Suffix: " + suffix);

		Path output = outputPath(input, suffix);
		FilterStats stats = filter.filterFile(input, output);

		System.out.println("Output: " + output);
		System.out.println(stats);
	}
}
