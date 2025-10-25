package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import io.chandler.gap.GroupExplorer;

/**
 * GridRipper processes a file of generator strings in parallel,
 * running GridSolver on each line to find grid embeddings.
 */
public class GridRipper {
	private final int parallelism;
	private final String filename;
	private final int width;
	private final int height;
	private final long timeoutMS;
	
	private final AtomicInteger processedCount = new AtomicInteger(0);
	private final Deque<String> linesWithSolutions = new ConcurrentLinkedDeque<String>();
	private final AtomicInteger timeoutCount = new AtomicInteger(0);
	private final AtomicInteger errorCount = new AtomicInteger(0);
	
	/**
	 * Creates a new GridRipper with the specified parameters.
	 * 
	 * @param parallelism Number of parallel threads (e.g., 16)
	 * @param filename Path to the file containing generator strings (one per line)
	 * @param width Grid width
	 * @param height Grid height
	 * @param timeoutMS Timeout in milliseconds for each grid solve operation
	 */
	public GridRipper(int parallelism, String filename, int width, int height, long timeoutMS) {
		this.parallelism = parallelism;
		this.filename = filename;
		this.width = width;
		this.height = height;
		this.timeoutMS = timeoutMS;
	}
	
	/**
	 * Processes all lines in the file in parallel.
	 */
	public void run() {
		ExecutorService executor = Executors.newFixedThreadPool(parallelism);
		
		System.out.println("GridRipper starting...");
		System.out.println("  File: " + filename);
		System.out.println("  Grid: " + width + "x" + height);
		System.out.println("  Parallelism: " + parallelism);
		System.out.println("  Timeout: " + timeoutMS + "ms");
		System.out.println();
		
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String line;
			int lineNumber = 0;
			
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				final String generatorString = line.trim();
				final int currentLineNumber = lineNumber;
				
				// Skip empty lines
				if (generatorString.isEmpty()) {
					continue;
				}
				
				// Submit task to executor
				executor.submit(() -> processLine(currentLineNumber, generatorString));
			}
			
			// Shutdown executor and wait for completion
			executor.shutdown();
			System.out.println("\nAll tasks submitted. Waiting for completion...");
			
			try {
				// Wait for all tasks to complete (with a very long timeout)
				if (!executor.awaitTermination(365, TimeUnit.DAYS)) {
					System.err.println("Executor did not terminate in the specified time.");
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				System.err.println("Executor interrupted");
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			
			// Print summary
			System.out.println("\n=== GridRipper Summary ===");
			System.out.println("  Lines processed: " + processedCount.get());
			System.out.println("  Lines with solutions: " + linesWithSolutions.size());
			System.out.println("  Grid: " + width + "x" + height);
			System.out.println("  Errors: " + errorCount.get());
			System.out.println("  Timeouts: " + timeoutCount.get());
			System.out.println("  Lines with solutions:");
			System.out.println("    " + String.join(", ", linesWithSolutions));
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Processes a single line from the file.
	 */
	private void processLine(int lineNumber, String generatorString) {
		try {
			// Parse the generator operations
			int[][][] genOps = GroupExplorer.parseOperationsArr(generatorString);
			
			// Build the graph from the generators
			Graph<Integer, DefaultEdge> graph = PlanarStudyRepeated.buildGraphFromCombinedGen(genOps, false);
			
			// Create a GridSolver with custom timeout
			GridSolver solver = new GridSolver(graph, timeoutMS);
			
			// Get the fixed pair from the first operation of the first cycle
			int[] fixedPair = genOps[0][0];
			
			// Solve with timeout by running in a separate thread with timeout
			long startTime = System.currentTimeMillis();
			List<Map<Integer, int[]>> solutions;
			try {
				solutions = solver.solveAll(width, height, fixedPair, false);
			} catch (TimeoutException e) {
				System.out.println("Line " + lineNumber + ": Timeout (" + (System.currentTimeMillis() - startTime) + "ms)");
				timeoutCount.incrementAndGet();
				return;
			}

			long elapsed = System.currentTimeMillis() - startTime;
			
			// Update counters
			processedCount.incrementAndGet();
			
			if (!solutions.isEmpty()) {
				linesWithSolutions.add(lineNumber + "");
				
				synchronized (System.out) {
					System.out.println("Line " + lineNumber + ": Found " + solutions.size() + 
						" solution(s) in " + elapsed + "ms");
					System.out.println("  Generators: " + generatorString);
					
					// Print first solution
					if (!solutions.isEmpty()) {
						System.out.println("  First solution:");
						String gridStr = GridSolver.printGrid(solutions.get(0));
						for (String gridLine : gridStr.split("\n")) {
							System.out.println("    " + gridLine);
						}
					}
					System.out.println();
				}
			} else {
				synchronized (System.out) {
					System.out.println("Line " + lineNumber + ": No solutions found (" + elapsed + "ms)");
				}
			}
			
		} catch (Exception e) {
			errorCount.incrementAndGet();
			synchronized (System.err) {
				System.err.println("Error processing line " + lineNumber + ": " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Example usage
	 */
	public static void main(String[] args) {
		// Default parameters
		int parallelism = 16;
		String filename = "PlanarStudyMulti/we7/l2-2-cycles-2-cycles-2-cycles_R1-filtered.txt";
		int width = 12;
		int height = 12;
		long timeoutMS = 10000; // 30 seconds
		
		// Parse command line arguments if provided
		if (args.length >= 1) filename = args[0];
		if (args.length >= 2) width = Integer.parseInt(args[1]);
		if (args.length >= 3) height = Integer.parseInt(args[2]);
		if (args.length >= 4) timeoutMS = Long.parseLong(args[3]);
		if (args.length >= 5) parallelism = Integer.parseInt(args[4]);
		
		GridRipper ripper = new GridRipper(parallelism, filename, width, height, timeoutMS);
		ripper.run();
	}
}

