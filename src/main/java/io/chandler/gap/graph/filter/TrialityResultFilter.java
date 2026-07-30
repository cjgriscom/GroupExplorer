package io.chandler.gap.graph.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.chandler.gap.GapInterface;
import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;

/**
 * Result filter for Remi Delaby's criteria on thin, flag-transitive, residually
 * connected rank-3 coset geometries admitting trialities but no dualities.
 * <p>
 * For a triple of involutions {@code r1,r2,r3} generating a reference group
 * {@code G}, accept when (up to relabeling of the three generators):
 * <ul>
 *   <li>{@code Gi = <rj,rk>} satisfy {@code Gi ∩ Gj = <rk>} and
 *       {@code G1 ∩ G2 ∩ G3 = {1}}</li>
 *   <li>{@code G1 ∩ G2 G3 = {1, r2, r3, r3 r2}} (flag-transitivity)</li>
 *   <li>{@code <r1,r2,r3> = G}</li>
 *   <li>there exists {@code T ∈ Aut(G)} with {@code T(r1)=r2, T(r2)=r3, T(r3)=r1}</li>
 *   <li>there is no {@code T ∈ Aut(G)} with {@code T(r1)=r2, T(r2)=r1, T(r3)=r3}</li>
 * </ul>
 * Intermediate PlanarStudy results with fewer than three generators are kept as
 * candidates ({@link FilterDecision#rejectToCandidates()}) so Phase 1 pairs can
 * still proceed to Phase 2.
 * <p>
 * GAP sessions are not shared across threads; default worker count is 1.
 */
public final class TrialityResultFilter extends AbstractResultFilter {
	private final ThreadLocal<GapInterface> gapLocal;

	private TrialityResultFilter(int maxQueueSize, int threads, String referenceGroup) {
		super(maxQueueSize, threads);
		this.gapLocal = ThreadLocal.withInitial(() -> {
			try {
				GapInterface gap = new GapInterface();
				gap.loadReferenceGroup(referenceGroup);
				return gap;
			} catch (IOException e) {
				throw new RuntimeException("Failed to start GAP for TrialityResultFilter", e);
			}
		});
	}

	public static Builder builder(int maxQueueSize) {
		return new Builder(maxQueueSize);
	}

	@Override
	protected FilterDecision process(String result) {
		String notation = stripInlineComment(result);
		int[][][] gens;
		try {
			gens = GroupExplorer.parseOperationsArr(notation);
		} catch (RuntimeException e) {
			return FilterDecision.rejectToCandidates();
		}
		if (gens.length != 3) {
			// Phase 1 pairs / incomplete candidates: keep for later rounds.
			return FilterDecision.rejectToCandidates();
		}
		for (int[][] g : gens) {
			if (!isInvolutionCycles(g)) {
				return FilterDecision.rejectToCandidates();
			}
		}

		String gapGens = GroupExplorer.generatorsToString(gens);
		// Strip outer brackets for GAP: GroupExplorer emits "[r1,r2,r3]".
		String inner = gapGens.substring(1, gapGens.length() - 1);
		try {
			String verdict = gapLocal.get().runPrintCommand(buildCheckScript(inner));
			if (verdict.startsWith("pass")) {
				return FilterDecision.accept(verdict);
			}
			return FilterDecision.rejectToCandidates();
		} catch (IOException e) {
			throw new RuntimeException("GAP triality check failed for " + notation, e);
		}
	}

	/**
	 * Evaluate a single generator triple against the reference group.
	 * Returns the GAP verdict string ({@code pass:...} or {@code fail:...}).
	 */
	public String evaluate(String generatorNotation) {
		String notation = stripInlineComment(generatorNotation);
		int[][][] gens = GroupExplorer.parseOperationsArr(notation);
		if (gens.length != 3) {
			return "fail:need3";
		}
		String gapGens = GroupExplorer.generatorsToString(gens);
		String inner = gapGens.substring(1, gapGens.length() - 1);
		try {
			return gapLocal.get().runPrintCommand(buildCheckScript(inner));
		} catch (IOException e) {
			throw new RuntimeException("GAP triality check failed", e);
		}
	}

	/**
	 * GAP check that uses a preloaded reference group {@code G} (from
	 * {@link #loadReferenceGroup} / filter construction).
	 */
	private static String buildCheckScript(String gensCommaSeparated) {
		return buildCheckScript(gensCommaSeparated, false);
	}

	/**
	 * @param selfAsG when true, set {@code G := Group(gens)} inside the script
	 *                (no preloaded reference group required).
	 */
	static String buildCheckScript(String gensCommaSeparated, boolean selfAsG) {
		// Tries all 6 labelings of (r1,r2,r3). Prints pass:<perm> or fail:<reason>.
		// When selfAsG, declare G local to avoid GAP "Unbound global variable"
		// syntax warnings on stdout (those desync GapInterface.readNonEmptyLine).
		String locals = selfAsG
				? "  local gens, perms, p, r1, r2, r3, H, G, G1, G2, G3, S, g, x, t, d, expected;\n"
				: "  local gens, perms, p, r1, r2, r3, H, G1, G2, G3, S, g, x, t, d, expected;\n";
		String gInit = selfAsG
				? "  G := Group(gens);;\n"
				: "";
		return ""
			+ "tri_check := function()\n"
			+ locals
			+ "  gens := [" + gensCommaSeparated + "];\n"
			+ "  if Length(gens) <> 3 then return \"fail:need3\"; fi;\n"
			+ "  if ForAny(gens, x -> Order(x) <> 2) then return \"fail:invol\"; fi;\n"
			+ "  if gens[1]=gens[2] or gens[1]=gens[3] or gens[2]=gens[3] then return \"fail:distinct\"; fi;\n"
			+ gInit
			+ "  perms := [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]];\n"
			+ "  for p in perms do\n"
			+ "    r1 := gens[p[1]];; r2 := gens[p[2]];; r3 := gens[p[3]];;\n"
			+ "    H := Group(r1,r2,r3);;\n"
			+ "    if Size(H) <> Size(G) then continue; fi;\n"
			+ "    G1 := Group(r2,r3);; G2 := Group(r1,r3);; G3 := Group(r1,r2);;\n"
			+ "    if Intersection(G1,G2) <> Group(r3) then continue; fi;\n"
			+ "    if Intersection(G1,G3) <> Group(r2) then continue; fi;\n"
			+ "    if Intersection(G2,G3) <> Group(r1) then continue; fi;\n"
			+ "    if Size(Intersection(Intersection(G1,G2),G3)) <> 1 then continue; fi;\n"
			+ "    S := [];;\n"
			+ "    for g in AsList(G1) do\n"
			+ "      if ForAny(AsList(G2), x -> x^-1 * g in G3) then AddSet(S, g); fi;\n"
			+ "    od;\n"
			+ "    expected := Set([Identity(H), r2, r3, r3*r2]);;\n"
			+ "    if S <> expected then continue; fi;\n"
			+ "    t := GroupHomomorphismByImages(H, H, [r1,r2,r3], [r2,r3,r1]);;\n"
			+ "    if t = fail or not IsBijective(t) then continue; fi;\n"
			+ "    d := GroupHomomorphismByImages(H, H, [r1,r2,r3], [r2,r1,r3]);;\n"
			+ "    if d <> fail and IsBijective(d) then continue; fi;\n"
			+ "    return Concatenation(\"pass:perm\", String(p));\n"
			+ "  od;\n"
			+ "  return \"fail:no_labeling\";\n"
			+ "end;;\n"
			+ "Print(tri_check(), \"\\n\");\n";
	}

	/** Evaluate using {@code G = <r1,r2,r3>} (no separate reference group). */
	public static String evaluateSelfContained(GapInterface gap, String generatorNotation)
			throws IOException {
		String notation = stripInlineComment(generatorNotation);
		int[][][] gens = GroupExplorer.parseOperationsArr(notation);
		if (gens.length != 3) {
			return "fail:need3";
		}
		for (int[][] g : gens) {
			if (!isInvolutionCycles(g)) {
				return "fail:invol";
			}
		}
		String gapGens = GroupExplorer.generatorsToString(gens);
		String inner = gapGens.substring(1, gapGens.length() - 1);
		return gap.runPrintCommand(buildCheckScript(inner, true));
	}

	private static boolean isInvolutionCycles(int[][] cycles) {
		// Each cycle length must be 2 (product of disjoint transpositions).
		for (int[] c : cycles) {
			if (c.length != 2) {
				return false;
			}
		}
		return cycles.length > 0;
	}

	static String stripInlineComment(String line) {
		int hash = line.indexOf(" #");
		return hash >= 0 ? line.substring(0, hash).trim() : line.trim();
	}

	public static final class Builder {
		private final int maxQueueSize;
		private String referenceGroup;
		private int threads = 1;

		private Builder(int maxQueueSize) {
			this.maxQueueSize = maxQueueSize;
		}

		public Builder referenceGroup(String referenceGroup) {
			this.referenceGroup = referenceGroup;
			return this;
		}

		/** GAP worker threads (each gets its own GAP process). Default 1. */
		public Builder threads(int threads) {
			if (threads < 1) {
				throw new IllegalArgumentException("threads must be >= 1");
			}
			this.threads = threads;
			return this;
		}

		public TrialityResultFilter build() {
			if (referenceGroup == null || referenceGroup.isEmpty()) {
				throw new IllegalArgumentException("referenceGroup is required");
			}
			return new TrialityResultFilter(maxQueueSize, threads, referenceGroup);
		}
	}

	/**
	 * Quick verification entry point.
	 * <pre>
	 * TrialityResultFilter [l3_3|nonsplit|both] [results.txt ...]
	 * </pre>
	 */
	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "both";
		List<String> files = new ArrayList<>();
		for (int i = 1; i < args.length; i++) {
			files.add(args[i]);
		}

		if ("l3_3".equals(mode) || "both".equals(mode)) {
			runGroupCheck("l3_3", Generators.l3_3, files.isEmpty()
					? List.of("PlanarStudy/l3_3/quadruple 2-cycles-quadruple 2-cycles-quadruple 2-cycles_R2-filtered.txt")
					: files);
		}
		if ("nonsplit".equals(mode) || "both".equals(mode)) {
			List<String> nsFiles = files;
			if ("both".equals(mode) || files.isEmpty()) {
				nsFiles = List.of(
						"PlanarStudy/psl_3_2_nonsplit_ext/6p 2-cycles-6p 2-cycles-6p 2-cycles_R1-filtered.txt");
			}
			runGroupCheck("psl_3_2_nonsplit_ext", Generators.psl_3_2_nonsplit_ext, nsFiles);
		}
		if ("search".equals(mode)) {
			String which = args.length > 1 ? args[1] : "l3_3";
			String gen = "l3_3".equals(which) ? Generators.l3_3 : Generators.psl_3_2_nonsplit_ext;
			searchExistence(which, gen);
		}
	}

	private static void runGroupCheck(String name, String generator, List<String> resultFiles)
			throws Exception {
		System.out.println("=== " + name + " ===");
		TrialityResultFilter filter = TrialityResultFilter.builder(16)
				.referenceGroup(generator)
				.build();
		try {
			int accepted = 0;
			int total = 0;
			for (String file : resultFiles) {
				Path path = Paths.get(file);
				if (!Files.isRegularFile(path)) {
					System.out.println("  (missing) " + file);
					continue;
				}
				System.out.println("  Scanning " + file);
				try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty()) continue;
						total++;
						String verdict = filter.evaluate(line);
						if (verdict.startsWith("pass")) {
							accepted++;
							System.out.println("  PASS: " + stripInlineComment(line) + " # " + verdict);
						}
					}
				}
			}
			System.out.println("  File scan: " + accepted + " / " + total + " passed");
			searchExistence(name, generator);
		} finally {
			filter.close();
		}
	}

	/** Exhaustive (r1-fixed) search inside the largest involution class via GAP. */
	private static void searchExistence(String name, String generator) throws IOException {
		GapInterface gap = new GapInterface();
		try {
			gap.loadReferenceGroup(generator);
			String out = gap.runPrintCommand(""
					+ "tri_search := function()\n"
					+ "  local ccs, c, involClasses, best, reps, r1, r2, r3, nGeom, nTri, nPass, H, G1, G2, G3, S, g, x, t, d, expected, examples;\n"
					+ "  ccs := ConjugacyClasses(G);;\n"
					+ "  involClasses := [];;\n"
					+ "  for c in ccs do\n"
					+ "    if Order(Representative(c)) = 2 then Add(involClasses, c); fi;\n"
					+ "  od;\n"
					+ "  if Length(involClasses) = 0 then return \"no_involutions\"; fi;\n"
					+ "  best := involClasses[1];;\n"
					+ "  for c in involClasses do\n"
					+ "    if Size(c) > Size(best) then best := c; fi;\n"
					+ "  od;\n"
					+ "  reps := Elements(best);; r1 := Representative(best);;\n"
					+ "  nGeom := 0;; nTri := 0;; nPass := 0;; examples := [];;\n"
					+ "  for r2 in reps do\n"
					+ "    if r2 = r1 then continue; fi;\n"
					+ "    for r3 in reps do\n"
					+ "      if r3 = r1 or r3 = r2 then continue; fi;\n"
					+ "      H := Group(r1,r2,r3);;\n"
					+ "      if Size(H) <> Size(G) then continue; fi;\n"
					+ "      G1 := Group(r2,r3);; G2 := Group(r1,r3);; G3 := Group(r1,r2);;\n"
					+ "      if Intersection(G1,G2) <> Group(r3) then continue; fi;\n"
					+ "      if Intersection(G1,G3) <> Group(r2) then continue; fi;\n"
					+ "      if Intersection(G2,G3) <> Group(r1) then continue; fi;\n"
					+ "      if Size(Intersection(Intersection(G1,G2),G3)) <> 1 then continue; fi;\n"
					+ "      S := [];;\n"
					+ "      for g in AsList(G1) do\n"
					+ "        if ForAny(AsList(G2), x -> x^-1 * g in G3) then AddSet(S, g); fi;\n"
					+ "      od;\n"
					+ "      expected := Set([Identity(H), r2, r3, r3*r2]);;\n"
					+ "      if S <> expected then continue; fi;\n"
					+ "      nGeom := nGeom + 1;;\n"
					+ "      t := GroupHomomorphismByImages(H, H, [r1,r2,r3], [r2,r3,r1]);;\n"
					+ "      if t = fail or not IsBijective(t) then continue; fi;\n"
					+ "      nTri := nTri + 1;;\n"
					+ "      d := GroupHomomorphismByImages(H, H, [r1,r2,r3], [r2,r1,r3]);;\n"
					+ "      if d <> fail and IsBijective(d) then continue; fi;\n"
					+ "      nPass := nPass + 1;;\n"
					+ "      if Length(examples) < 2 then\n"
					+ "        Add(examples, [r1,r2,r3]);;\n"
					+ "      fi;\n"
					+ "    od;\n"
					+ "  od;\n"
					+ "  return Concatenation(\"classSize=\", String(Size(best)),\n"
					+ "    \" nGeom=\", String(nGeom),\n"
					+ "    \" nTri=\", String(nTri),\n"
					+ "    \" nPass=\", String(nPass),\n"
					+ "    \" examples=\", String(examples));\n"
					+ "end;;\n"
					+ "Print(tri_search(), \"\\n\");\n");
			System.out.println("  GAP search (" + name + "): " + out);
		} finally {
			gap.close();
		}
	}

	@Override
	public String shortFilterName() {
		return "triality";
	}
}
