package io.chandler.gap.graph.filter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.chandler.gap.GapInterface;
import io.chandler.gap.GroupExplorer;

/**
 * Scans PlanarStudy {@code *2-cycles-2-cycles-2-cycles_R1*.txt} results with a
 * self-contained Remi triality check ({@code G = <r1,r2,r3>}). Writes/appends
 * passing results incrementally. Resume-safe: skips {@code file:} entries already
 * present in the summary.
 */
public final class TrialityPlanarStudyScan {
	private TrialityPlanarStudyScan() {}

	public static void main(String[] args) throws Exception {
		Path root = Paths.get(args.length > 0 ? args[0] : "PlanarStudy");
		Path outPath = Paths.get(args.length > 1 ? args[1] : "PlanarStudy/triality-R1-summary.txt");
		int threads = args.length > 2
				? Integer.parseInt(args[2])
				: Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

		List<Path> files = findMatchingFiles(root);
		files.sort(Comparator.comparingLong(p -> {
			try {
				return Files.size(p);
			} catch (IOException e) {
				return Long.MAX_VALUE;
			}
		}));

		Set<String> already = loadCompletedKeys(outPath);
		boolean fresh = !Files.exists(outPath) || Files.size(outPath) == 0;
		if (fresh) {
			try (BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8);
					PrintWriter out = new PrintWriter(w)) {
				out.println("# Triality (Remi) filter scan of PlanarStudy *2-cycles-2-cycles-2-cycles_R1*.txt");
				out.println("# Each candidate uses G=<r1,r2,r3>; reported by folder/filename.");
				out.println();
			}
		}

		System.out.println("Found " + files.size() + " matching files under " + root);
		System.out.println("Summary: " + outPath + " (already done: " + already.size() + ")");
		System.out.println("GAP workers: " + threads);

		ConcurrentLinkedQueue<GapInterface> pool = new ConcurrentLinkedQueue<>();
		for (int i = 0; i < threads; i++) {
			pool.add(new GapInterface());
		}

		AtomicInteger linesChecked = new AtomicInteger();
		int filesDone = 0;
		int filesWithHits = already.size();
		int totalPasses = 0;

		ExecutorService exec = Executors.newFixedThreadPool(threads);
		try {
			for (Path file : files) {
				String key = fileKey(file);
				filesDone++;
				if (already.contains(key)) {
					System.out.println("skip " + key + " [" + filesDone + "/" + files.size() + "]");
					continue;
				}
				long t0 = System.currentTimeMillis();
				List<String> filePasses = scanFile(file, pool, exec, linesChecked);
				long ms = System.currentTimeMillis() - t0;
				if (!filePasses.isEmpty()) {
					filesWithHits++;
					totalPasses += filePasses.size();
					appendFileResults(outPath, key, filePasses);
					System.out.println("HIT " + key + " (" + filePasses.size() + " pass, "
							+ ms + "ms)  [" + filesDone + "/" + files.size()
							+ " files, " + linesChecked.get() + " lines, "
							+ totalPasses + " new passes this run]");
				} else {
					System.out.println("done " + key + " (0 pass, " + ms + "ms)  ["
							+ filesDone + "/" + files.size() + " files, "
							+ linesChecked.get() + " lines]");
				}
			}
		} finally {
			exec.shutdownNow();
			GapInterface g;
			while ((g = pool.poll()) != null) {
				try {
					g.close();
				} catch (IOException ignored) {}
			}
		}

		System.out.println("Done. files_with_hits~=" + filesWithHits
				+ " new_passes=" + totalPasses + " -> " + outPath);
	}

	private static String fileKey(Path file) {
		return file.getParent().getFileName() + "/" + file.getFileName();
	}

	private static Set<String> loadCompletedKeys(Path outPath) throws IOException {
		Set<String> keys = new HashSet<>();
		if (!Files.isRegularFile(outPath)) {
			return keys;
		}
		try (BufferedReader r = Files.newBufferedReader(outPath, StandardCharsets.UTF_8)) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("file: ")) {
					keys.add(line.substring("file: ".length()).trim());
				}
			}
		}
		return keys;
	}

	private static void appendFileResults(Path outPath, String key, List<String> passes)
			throws IOException {
		try (BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				PrintWriter out = new PrintWriter(w)) {
			out.println("file: " + key);
			for (int i = 0; i < passes.size(); i++) {
				out.println("  result [" + i + "]: " + passes.get(i));
			}
			out.println();
		}
	}

	private static List<Path> findMatchingFiles(Path root) throws IOException {
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(root)) {
			return out;
		}
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
			for (Path dir : dirs) {
				try (DirectoryStream<Path> files = Files.newDirectoryStream(dir,
						"*2-cycles-2-cycles-2-cycles_R1*.txt")) {
					for (Path f : files) {
						if (Files.isRegularFile(f) && Files.size(f) > 0) {
							out.add(f);
						}
					}
				}
			}
		}
		return out;
	}

	private static List<String> scanFile(Path file, ConcurrentLinkedQueue<GapInterface> pool,
			ExecutorService exec, AtomicInteger linesChecked) throws Exception {
		List<String> candidates = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = TrialityResultFilter.stripInlineComment(line);
				if (line.isEmpty()) continue;
				if (!looksLikeThreeInvolutions(line)) continue;
				candidates.add(line);
			}
		}
		if (candidates.isEmpty()) {
			return List.of();
		}

		System.out.println("  scanning " + candidates.size() + " candidates in "
				+ fileKey(file) + " ...");

		List<Future<String>> futures = new ArrayList<>(candidates.size());
		AtomicInteger localDone = new AtomicInteger();
		int total = candidates.size();
		for (String cand : candidates) {
			futures.add(exec.submit(() -> {
				GapInterface gap = null;
				for (;;) {
					gap = pool.poll();
					if (gap != null) break;
					Thread.sleep(5L);
				}
				try {
					linesChecked.incrementAndGet();
					int n = localDone.incrementAndGet();
					if (total >= 500 && n % 500 == 0) {
						System.out.println("    " + fileKey(file) + " " + n + "/" + total);
					}
					String verdict = TrialityResultFilter.evaluateSelfContained(gap, cand);
					return verdict.startsWith("pass") ? cand : null;
				} finally {
					pool.add(gap);
				}
			}));
		}

		List<String> passes = new ArrayList<>();
		for (Future<String> fut : futures) {
			String pass = fut.get();
			if (pass != null) {
				passes.add(pass);
			}
		}
		return passes;
	}

	private static boolean looksLikeThreeInvolutions(String notation) {
		try {
			int[][][] gens = GroupExplorer.parseOperationsArr(notation);
			if (gens.length != 3) return false;
			for (int[][] g : gens) {
				if (g.length == 0) return false;
				for (int[] c : g) {
					if (c.length != 2) return false;
				}
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
