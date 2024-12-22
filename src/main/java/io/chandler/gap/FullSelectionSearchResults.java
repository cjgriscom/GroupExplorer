package io.chandler.gap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import io.chandler.gap.GroupExplorer.Generator;

public class FullSelectionSearchResults {

    // Define the number of threads (adjust N as needed)
    static final int N = 20;//Runtime.getRuntime().availableProcessors();

	public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            // Parse the existing results file
            String filePath = "/home/cjgriscom/Programming/GroupTxt/2024-12-11 2x6 FullSelectionSearch_PI_resize.txt";
            FileReader reader = new FileReader(filePath);
            FullSelectionSearchResults results = FullSelectionSearchResults.parse(reader);
            reader.close();

            boolean exit = false;

            while (!exit) {
                System.out.println("\nPlease select an option:");
                System.out.println("s - Assign missing structure descriptions");
                System.out.println("r - Recheck sizes for uncategorized generators");
                System.out.println("f - Filter results interactively");
                System.out.println("w - Write results to file");
                System.out.println("q - Quit");
                System.out.print("Your selection: ");
                String choice = scanner.nextLine().trim();

                switch (choice.toLowerCase()) {
                    case "s":
                        // Assign missing structure descriptions
                        ArrayList<Integer> orders = new ArrayList<>(results.results.keySet());
                        // Sort in reverse order
                        Collections.sort(orders, Collections.reverseOrder());

                        for (Integer order : orders) {
                            results.getStructureDescriptions(order);
                        }
                        results.sort();
                        System.out.println("Structure descriptions assigned.");
                        break;

                    case "r":
                        // Recheck sizes for uncategorized generators
                        results.recheckSize();
                        System.out.println("Rechecked sizes for uncategorized generators.");
                        break;

                    case "f":
                        // Filter results interactively
                        results.interactiveStructureFilter();
                        results.sort();
                        System.out.println("Results filtered interactively.");
                        break;

                    case "w":
                        // Write results back to the file
                        FileWriter writer = new FileWriter(filePath);
                        results.write(writer);
                        writer.close();
                        System.out.println("Results written to file.");
                        break;

                    case "q":
                        // Quit the program
                        exit = true;
                        System.out.println("Exiting program.");
                        break;

                    default:
                        System.out.println("Invalid option. Please try again.");
                        break;
                }
            }

            scanner.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	
    // Map from group size to list of generator entries
    private Map<Integer, List<GeneratorEntry>> results = new TreeMap<>();


	GapInterface gap;

	public FullSelectionSearchResults() throws IOException {
		gap = new GapInterface();
	}

    // Inner class to hold generator entries
    public static class GeneratorEntry {
        public String generator;
        public int elements;
        public String structureDescription;

        public GeneratorEntry(String generator, int elements, String structureDescription) {
            this.generator = generator;
            this.elements = elements;
            this.structureDescription = structureDescription;
        }
    }

    // Static parse method to read the generators.txt file
    public static FullSelectionSearchResults parse(Reader reader) throws IOException {
        FullSelectionSearchResults fullResults = new FullSelectionSearchResults();

        BufferedReader br = new BufferedReader(reader);
        String line;
        Integer currentGroupSize = null;
        int expectedCount = 0;
        int actualCount = 0;

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty()) continue;
	
			if (line.matches("-?\\d+: \\d+")) {
				// New group size section
				String[] parts = line.split(":");
				currentGroupSize = Integer.parseInt(parts[0].trim());
				expectedCount = Integer.parseInt(parts[1].trim());
				actualCount = 0;
				fullResults.results.putIfAbsent(currentGroupSize, new ArrayList<>());
			} else if (line.startsWith("[")) {
				// Generator entry line
				String generatorStr = null;
				int elements = 0;
				String structureDescription = null;

				// Split the line on " - " to separate generator, elements, and structure description
				String[] parts = line.split(" - ");
				if (parts.length >= 2) {
					generatorStr = parts[0].trim();
					String elementsPart = parts[1].trim();

					// Extract the number from "elements=XX"
					if (elementsPart.startsWith("elements=")) {
						String elementsStr = elementsPart.substring("elements=".length()).trim();
						elementsStr = elementsStr.replaceAll("\\D", ""); // Remove non-digit characters
						elements = Integer.parseInt(elementsStr);
					} else {
						// Handle cases where elements part is missing or malformed
						System.err.println("Malformed elements part: " + elementsPart);
					}

					if (parts.length == 3) {
						structureDescription = parts[2].trim();
					}
				} else {
					// Handle lines that don't match the expected format
					System.err.println("Skipping unrecognized line: " + line);
					continue;
				}

				// Clean up the generator string
				generatorStr = generatorStr.trim();

				fullResults.results.get(currentGroupSize)
						.add(new GeneratorEntry(generatorStr, elements, structureDescription));
				actualCount++;
			} else {
				// Skip or handle unexpected lines
				System.err.println("Skipping unrecognized line: " + line);
			}
		}
        br.close();

        return fullResults;
    }

    // recheckSize method to categorize generators in the -1 group
    public void recheckSize() throws IOException {
        List<GeneratorEntry> uncategorized = results.get(-1);
        if (uncategorized == null || uncategorized.isEmpty()) {
            System.out.println("No uncategorized generators to recheck.");
            return;
        }

        List<GeneratorEntry> toRemove = new ArrayList<>();
        Map<Integer, List<GeneratorEntry>> toAdd = new LinkedHashMap<>();

		int reclassified = 0;
        for (GeneratorEntry entry : uncategorized) {
            try {
                String generatorStr = entry.generator;
                // Ensure generatorStr is properly formatted
                generatorStr = generatorStr.trim();

                // Remove outer brackets if present
                if (generatorStr.startsWith("[") && generatorStr.endsWith("]")) {
                    generatorStr = generatorStr.substring(1, generatorStr.length() - 1).trim();
                }

                Generator generator = new Generator(GroupExplorer.parseOperationsArr(generatorStr));
                // Adjust the method call to match your GapInterface implementation
                List<String> gapOutput = gap.runGapSizeCommand(generator.toString(), 2);
                if (gapOutput.isEmpty()) {
                    System.err.println("Failed to get group size for generator: " + generatorStr);
                    continue;
                }
                String sizeStr = gapOutput.get(1).trim();
                int groupSize = Integer.parseInt(sizeStr);

                // Remove from uncategorized
                toRemove.add(entry);

                // Add to appropriate group
                results.putIfAbsent(groupSize, new ArrayList<>());
                results.get(groupSize).add(entry);

				reclassified++;
            } catch (Exception e) {
                System.err.println("Error processing generator: " + entry.generator);
                e.printStackTrace();
            }
        }
		System.out.println("Reclassified " + reclassified + " generators");

        // Remove processed entries from uncategorized
        results.get(-1).removeAll(toRemove);

        // If the uncategorized list is empty, remove it from the map
        if (results.get(-1).isEmpty()) {
            results.remove(-1);
        }
    }

    // write method to output the results in the same format
    public void write(Writer writer) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);

        for (Map.Entry<Integer, List<GeneratorEntry>> entry : results.entrySet()) {
            int groupSize = entry.getKey();
            List<GeneratorEntry> generators = entry.getValue();

            if (generators.size() == 0) continue;
            bw.write(groupSize + ": " + generators.size());
            bw.newLine();

            for (GeneratorEntry genEntry : generators) {
                StringBuilder line = new StringBuilder();
                line.append("   ").append(genEntry.generator);
                line.append(" - elements=").append(genEntry.elements);
                if (genEntry.structureDescription != null && !genEntry.structureDescription.isEmpty()) {
                    line.append(" - ").append(genEntry.structureDescription);
                }
                bw.write(line.toString());
                bw.newLine();
            }
        }

        bw.flush();
    }


    // Retrieve structure descriptions for a given group size
    public void getStructureDescriptions(int order) {
		getStructureDescriptions(order, false);
	}

    // Retrieve structure descriptions for a given group size
    public void getStructureDescriptions(int order, boolean forceUpdate) {
        List<GeneratorEntry> entries = results.get(order);
        if (entries == null || entries.isEmpty()) {
            System.out.println("No generators found for group size " + order);
            return;
        } else {
            System.out.println("Getting structure descriptions for order " + order);
        }

        int total = entries.size();
        final AtomicInteger updated = new AtomicInteger(0);

        // Create a fixed thread pool with N threads
        ExecutorService executor = Executors.newFixedThreadPool(N);

        // Synchronized list to keep track of GapInterface instances
        List<GapInterface> gapInstances = Collections.synchronizedList(new ArrayList<>());

        // Create a ThreadLocal to store GapInterface instances per thread
        ThreadLocal<GapInterface> gapLocal = ThreadLocal.withInitial(() -> {
            try {
                GapInterface gapInstance = new GapInterface();
                gapInstances.add(gapInstance); // Add to the synchronized list
                return gapInstance;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create GapInterface", e);
            }
        });

        List<Future<?>> futures = new ArrayList<>();

        for (GeneratorEntry entry : entries) {
            if (entry.structureDescription != null && !forceUpdate)
                continue;

            // Submit a task for each entry
            Future<?> future = executor.submit(() -> {
                try {
                    String generatorStr = entry.generator.trim();

                    // Remove outer brackets if present
                    if (generatorStr.startsWith("[") && generatorStr.endsWith("]")) {
                        generatorStr = generatorStr.substring(1, generatorStr.length() - 1).trim();
                    }

                    // Create a Generator object for the generator string
                    Generator generator = new Generator(GroupExplorer.parseOperationsArr(generatorStr));

                    // Get the GapInterface from ThreadLocal
                    GapInterface gapInstance = gapLocal.get();

                    // Run GAP commands and retrieve the structure description
                    List<String> gapOutput = gapInstance.runGapCommands(generator.toString(), 3);
                    if (gapOutput.size() < 3) {
                        System.err.println("Failed to get structure description for generator: " + generatorStr);
                        return;
                    }

                    String structureDescription = gapOutput.get(2).trim();
                    entry.structureDescription = structureDescription;

                    int currentUpdated = updated.incrementAndGet();
                    System.out.println("Updated structure description for generator " + currentUpdated + " of " + total);

                } catch (Exception e) {
                    System.err.println("Error processing generator: " + entry.generator);
                    e.printStackTrace();
                }
            });
            futures.add(future);
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Shutdown the executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        // Close all GapInterface instances
        for (GapInterface gapInstance : gapInstances) {
            try {
                gapInstance.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // New sort method to sort each list of GeneratorEntry objects
    public void sort() {
        for (List<GeneratorEntry> entries : results.values()) {
            entries.sort(Comparator
                .comparingInt((GeneratorEntry e) -> e.elements)
                .thenComparing(e -> e.structureDescription == null ? "" : e.structureDescription));
        }
    }

    public void interactiveStructureFilter() {
        // Step 1: Collect all unique 'elements - structureDescription' combinations
        List<String> combinations = new ArrayList<>();
        for (List<GeneratorEntry> entries : results.values()) {
            for (GeneratorEntry entry : entries) {
                String key = entry.elements + " - " + entry.structureDescription;
                if (!combinations.contains(key)) {
                    combinations.add(key);
                }
            }
        }

        // For tracking progress and deletions
        int total = combinations.size();
        Set<String> toDelete = new HashSet<>();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Starting interactive filtering.");
        for (int i = 0; i < total; i++) {
            String combination = combinations.get(i);
            System.out.printf("(%d/%d): %s - Keep? (Y/n/q/c): ", i + 1, total, combination);
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("n")) {
                // User chose to delete this combination
                toDelete.add(combination);
            } else if (input.equals("q")) {
                // Quit the loop without committing changes
                System.out.println("Exiting without committing changes.");
                return;
            } else if (input.equals("c")) {
                // Commit the changes made so far
                System.out.println("Committing changes made so far.");
                break;
            }
            // Default is to keep the combination
        }

        // Step 3: Remove entries that match the selected combinations
        for (List<GeneratorEntry> entries : results.values()) {
            entries.removeIf(entry -> {
                String key = entry.elements + " - " + entry.structureDescription;
                return toDelete.contains(key);
            });
        }

        System.out.println("Selected combinations have been deleted from the results.");
    }
} 