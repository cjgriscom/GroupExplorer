package io.chandler.gap.morajai;

import io.chandler.gap.morajai.MoraJaiBox.Color;
import java.util.*;

public class MJSolver {
    private static final Map<String, Color> COLOR_MAP = new HashMap<>();
    
    static {
        COLOR_MAP.put("GY", Color.C_GY);
        COLOR_MAP.put("RD", Color.C_RD);
        COLOR_MAP.put("GN", Color.C_GN);
        COLOR_MAP.put("BK", Color.C_BK);
        COLOR_MAP.put("WH", Color.C_WH);
        COLOR_MAP.put("PI", Color.C_PI);
        COLOR_MAP.put("PU", Color.C_PU);
        COLOR_MAP.put("YE", Color.C_YE);
        COLOR_MAP.put("BU", Color.C_BU);
        COLOR_MAP.put("OR", Color.C_OR);
    }

    /**
     * Solves the MoraJai puzzle using BFS search
     * @param puzzleState Array of color strings representing the 3x3 grid
     * @param targetColor The target color for all corners
     * @return List of tile indices to press (0-8), or null if no solution found
     */
    public static List<Integer> solve(String[] puzzleState, String targetColor) {
        return solve(puzzleState, targetColor, 70); // Default max depth of 20
    }

    /**
     * Solves the MoraJai puzzle using BFS search with depth limit
     * @param puzzleState Array of color strings representing the 3x3 grid
     * @param targetColor The target color for all corners
     * @param maxDepth Maximum search depth
     * @return List of tile indices to press (0-8), or null if no solution found
     */
    public static List<Integer> solve(String[] puzzleState, String targetColor, int maxDepth) {
        if (puzzleState.length != 9) {
            throw new IllegalArgumentException("Puzzle state must have exactly 9 tiles");
        }

        // Convert string colors to Color enum
        Color[] tileColors = new Color[9];
        for (int i = 0; i < 9; i++) {
            tileColors[i] = COLOR_MAP.get(puzzleState[i]);
            if (tileColors[i] == null) {
                throw new IllegalArgumentException("Invalid color: " + puzzleState[i]);
            }
        }

        Color target = COLOR_MAP.get(targetColor);
        if (target == null) {
            throw new IllegalArgumentException("Invalid target color: " + targetColor);
        }

        // Set up target colors for all 4 corners (same color)
        Color[] targetColors = {target, target, target, target};

        // Initialize the puzzle
        MoraJaiBox box = new MoraJaiBox();
        box.init(targetColors, tileColors);

        // Check if already solved
        if (box.areInnerMatchingOuter()) {
            return new ArrayList<>(); // Empty solution - already solved
        }

        // BFS data structures
        Queue<SearchNode> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();
        
        // Add initial state
        int initialState = box.getState();
        queue.offer(new SearchNode(initialState, new ArrayList<>()));
        visited.add(initialState);

        while (!queue.isEmpty()) {
            SearchNode current = queue.poll();
            
            // Skip if we've reached max depth
            if (current.moves.size() >= maxDepth) {
                continue;
            }
            
            // Try pressing each tile
            for (int tile = 0; tile < 9; tile++) {
                // Create new box for this move
                MoraJaiBox newBox = new MoraJaiBox();
                newBox.initFromState(targetColors, current.state);
                
                // Press the tile
                newBox.pressTile(tile);
                int newState = newBox.getState();
                
                // Skip if we've seen this state before
                if (visited.contains(newState)) {
                    continue;
                }
                
                // Check if solved
                if (newBox.areInnerMatchingOuter()) {
                    List<Integer> solution = new ArrayList<>(current.moves);
                    solution.add(tile);
                    return solution;
                }
                
                // Add to queue for further exploration
                visited.add(newState);
                List<Integer> newMoves = new ArrayList<>(current.moves);
                newMoves.add(tile);
                queue.offer(new SearchNode(newState, newMoves));
            }
        }
        
        return null; // No solution found
    }

    /**
     * Converts a solution to a human-readable string
     * @param solution List of tile indices to press
     * @return String representation of the solution
     */
    public static String solutionToString(List<Integer> solution) {
        if (solution == null) {
            return "No solution found";
        }
        if (solution.isEmpty()) {
            return "Already solved";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Solution (").append(solution.size()).append(" moves): ");
        for (int i = 0; i < solution.size(); i++) {
            if (i > 0 && i % 5 == 0) sb.append(" ");
            sb.append(solution.get(i) + 1);
        }
        return sb.toString();
    }

    /**
     * Prints the puzzle state in a 3x3 grid format
     * @param puzzleState Array of color strings
     */
    public static void printPuzzle(String[] puzzleState) {
        System.out.println("Puzzle state:");
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.print(puzzleState[i * 3 + j] + " ");
            }
            System.out.println();
        }
    }

    /**
     * Helper class for BFS search
     */
    private static class SearchNode {
        final int state;
        final List<Integer> moves;
        
        SearchNode(int state, List<Integer> moves) {
            this.state = state;
            this.moves = moves;
        }
    }

    /**
     * Test method
     */
    public static void main(String[] args) {
        String[] puzzleState = {"OR", "PU", "BU", "WH", "WH", "RD", "BK", "RD", "YE"};
        String targetColor = "BK";
        
        long startTime = System.currentTimeMillis();
        List<Integer> solution = solve(puzzleState, targetColor);
        long endTime = System.currentTimeMillis();
        
        System.out.println(solutionToString(solution));
        System.out.println("Search completed in " + (endTime - startTime) + " ms");
        
        // Test with a simple case
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Testing with a simple case:");
        String[] simplePuzzle = {"BK", "GY", "BK", "GY", "GY", "GY", "BK", "GY", "BK"};
        printPuzzle(simplePuzzle);
        System.out.println("Target color: BK");
        
        startTime = System.currentTimeMillis();
        solution = solve(simplePuzzle, "BK");
        endTime = System.currentTimeMillis();
        
        System.out.println(solutionToString(solution));
        System.out.println("Search completed in " + (endTime - startTime) + " ms");
    }
}