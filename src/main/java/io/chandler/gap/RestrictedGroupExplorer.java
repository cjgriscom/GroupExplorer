package io.chandler.gap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * This class explores an unconventional group generator over the permutations
 * of 8 points.
 * 
 * One possible unrestricted move is this:
 * 
 * A: (1,4,3)(2,6,8)
 * Which cycles 1,4,3 in a 3 cycle simultaneously with 2,6,8
 * 
 * Another possible but restricted move is 
 * B: (1,5,3)(4,8,7) with -1,0,1 restriction
 * 
 * As well as 
 * C: (2,4,7)(3,5,6) with -1,0,1 restriction
 *
 * The -1,0,1 restriction works like this: 
 * Every time the move B is applied it increments a counter defaulting to 0. 
 * So applying B increments the counter to 1. 
 * From there B' (b inverted) can be applied, setting the counter back to 0.
 * Likewise, B' can be applied again, setting the counter to -1. 
 * The counter can't wrap around, so B can only be applied when the counter is -1 or 0. 
 * C works the same way.
 */

public class RestrictedGroupExplorer {
	public static final boolean UNLOCK_FULL_GROUP = false;
    private int[] currentState;
    private int moveB_counter = 0;  // Counter for move B (-1, 0, 1)
    private int moveC_counter = 0;  // Counter for move C (-1, 0, 1)
    
    // Define the moves
    private static final int[][] MOVE_A = {
        {1, 4, 8},
        {2, 6, 3}
    };
    
    private static final int[][] MOVE_B = {
        {1, 3, 5}, // or {1, 5, 3}
        {4, 8, 7}
    };
    
    private static final int[][] MOVE_C = {
        {2, 7, 4}, // or {2, 4, 7}
        {3, 5, 6}
    };
    
    public RestrictedGroupExplorer() {
        // Initialize state with identity permutation [1,2,3,4,5,6,7,8]
        currentState = new int[8];
        for (int i = 0; i < 8; i++) {
            currentState[i] = i + 1;
        }
    }
    
    public boolean canApplyMove(char move) {
		if (UNLOCK_FULL_GROUP) return true; // Test full group without restrictions
        switch (move) {
            case 'A':
                return true;  // A is unrestricted
            case 'a':
                return true;  // A inverse is unrestricted
            case 'B':
                return moveB_counter < 1;  // Can apply if counter is -1 or 0
            case 'b':  // B inverse
                return moveB_counter > -1;  // Can apply if counter is 0 or 1
            case 'C':
                return moveC_counter < 1;
            case 'c':  // C inverse
                return moveC_counter > -1;
            default:
                throw new IllegalArgumentException("Invalid move: " + move);
        }
    }
    
    public void applyMove(char move) {
        if (!canApplyMove(move)) {
            throw new IllegalStateException("Cannot apply move " + move + " in current state");
        }
        
        switch (move) {
            case 'A':
                applyPermutation(MOVE_A);
                break;
            case 'a':
                applyPermutation(reverseOperation(MOVE_A));
                break;
            case 'B':
                applyPermutation(MOVE_B);
                moveB_counter++;
                break;
            case 'b':
                applyPermutation(reverseOperation(MOVE_B));
                moveB_counter--;
                break;
            case 'C':
                applyPermutation(MOVE_C);
                moveC_counter++;
                break;
            case 'c':
                applyPermutation(reverseOperation(MOVE_C));
                moveC_counter--;
                break;
        }
    }
    
    private void applyPermutation(int[][] cycles) {
        int[] newState = currentState.clone();
        for (int[] cycle : cycles) {
            for (int i = 0; i < cycle.length - 1; i++) {
                int current = cycle[i];
                int next = cycle[i + 1];
                newState[current - 1] = currentState[next - 1];
            }
            newState[cycle[cycle.length - 1] - 1] = currentState[cycle[0] - 1];
        }
        currentState = newState;
    }
    
    private int[][] reverseOperation(int[][] operation) {
        int[][] reversedOps = new int[operation.length][];
        for (int o = 0; o < operation.length; o++) {
            int[] cycle = operation[o];
            int[] reversed = new int[cycle.length];
            for (int i = 0; i < cycle.length; i++) {
                reversed[i] = cycle[cycle.length - i - 1];
            }
            reversedOps[o] = reversed;
        }
        return reversedOps;
    }
    
    public int[] getCurrentState() {
        return currentState.clone();
    }
    
    public String getStateAsString() {
        return Arrays.toString(currentState);
    }
    
    public int getMoveB_counter() {
        return moveB_counter;
    }
    
    public int getMoveC_counter() {
        return moveC_counter;
    }
    
    public RestrictedState getCurrentRestrictedState() {
        return new RestrictedState(currentState, moveB_counter, moveC_counter);
    }

    public static void main(String[] args) {
        Set<RestrictedState> exploredStates = new HashSet<>();
        Queue<RestrictedState> unexploredStates = new LinkedList<>();
        Map<RestrictedState, Map<Character, RestrictedState>> transitions = new HashMap<>();
        
        // Start with identity permutation
        RestrictedGroupExplorer explorer = new RestrictedGroupExplorer();
        RestrictedState initialState = explorer.getCurrentRestrictedState();
        unexploredStates.add(initialState);
        
        // Available moves (uppercase for forward, lowercase for inverse)
        char[] possibleMoves = {'A', 'a', 'B', 'b', 'C', 'c'};
        
        while (!unexploredStates.isEmpty()) {
            RestrictedState currentState = unexploredStates.poll();
            
            if (exploredStates.contains(currentState)) {
                continue;
            }
            
            // Apply the state to a new explorer instance
            RestrictedGroupExplorer tempExplorer = new RestrictedGroupExplorer();
            tempExplorer.setState(currentState);
            
            Map<Character, RestrictedState> stateTransitions = new HashMap<>();
            
            for (char move : possibleMoves) {
                if (tempExplorer.canApplyMove(move)) {
                    // Create a copy to apply the move
                    RestrictedGroupExplorer newExplorer = new RestrictedGroupExplorer();
                    newExplorer.setState(currentState);
                    
                    try {
                        newExplorer.applyMove(move);
                        RestrictedState newState = newExplorer.getCurrentRestrictedState();
                        
                        stateTransitions.put(move, newState);
                        
                        if (!exploredStates.contains(newState)) {
                            unexploredStates.add(newState);
                        }
                    } catch (IllegalStateException e) {
                        // Move is not applicable; skip
                        System.out.println("Cannot apply move " + move + " from state " + currentState);
                    }
                }
            }
            
            transitions.put(currentState, stateTransitions);
            exploredStates.add(currentState);
            
            // Print progress every 1000 states
            if (exploredStates.size() % 1000 == 0) {
                System.out.printf("Explored: %d, Unexplored: %d%n", 
                    exploredStates.size(), unexploredStates.size());
            }
        }
        
        // Print final results
        System.out.println("\nExploration complete!");
        System.out.println("Total states in group: " + exploredStates.size());
        
        // Print some example transitions
        System.out.println("\nExample transitions from initial state:");
        Map<Character, RestrictedState> initialTransitions = transitions.get(initialState);
        for (Map.Entry<Character, RestrictedState> entry : initialTransitions.entrySet()) {
            System.out.printf("Move %c -> %s%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Adds a setter to initialize the explorer's state based on a RestrictedState.
     */
    public void setState(RestrictedState state) {
        this.currentState = state.getPermutation().clone();
        this.moveB_counter = state.getBCounter();
        this.moveC_counter = state.getCCounter();
    }
}

class RestrictedState {
    private final int[] permutation;
    private final int bCounter;
    private final int cCounter;

    public RestrictedState(int[] permutation, int bCounter, int cCounter) {
        this.permutation = permutation.clone();
        this.bCounter = bCounter;
        this.cCounter = cCounter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestrictedState that = (RestrictedState) o;
        return bCounter == that.bCounter &&
               cCounter == that.cCounter &&
               Arrays.equals(permutation, that.permutation);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(permutation);
        result = 31 * result + bCounter;
        result = 31 * result + cCounter;
        return result;
    }
    public int[] getPermutation() {
        return permutation.clone();
    }
    
    public int getBCounter() {
        return bCounter;
    }
    
    public int getCCounter() {
        return cCounter;
    }
    @Override
    public String toString() {
        return "State{perm=" + Arrays.toString(permutation) + 
               ", B=" + bCounter + 
               ", C=" + cCounter + "}";
    }
}