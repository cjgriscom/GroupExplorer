package io.chandler.gap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.chandler.gap.cache.ParityStateCache;
import io.chandler.gap.cache.State;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class GroupExplorer implements AbstractGroupProperties {

    private final Set<State> stateMap;
    private Set<State> stateMapIncomplete;
    private Set<State> stateMapTmp;

    public int[] elements;
    public List<int[][]> parsedOperations;
    public int nElements;
    public final MemorySettings mem;
    public boolean multithread;

    public static class Generator {
        byte[][][] generator;
        Integer cachedHashCode;
        public Generator(int[][][] generator) {
            this.generator = new byte[generator.length][][];
            for (int i = 0; i < generator.length; i++) {
                this.generator[i] = new byte[generator[i].length][];
                for (int j = 0; j < generator[i].length; j++) {
                    this.generator[i][j] = new byte[generator[i][j].length];
                    for (int k = 0; k < generator[i][j].length; k++) {
                        this.generator[i][j][k] = (byte) generator[i][j][k];
                    }
                }
            }
        }

        public Generator(int[][] cycles) {
            this(new int[][][]{cycles});
        }

        @Override
        public String toString() {
            return GroupExplorer.generatorsToString(generator());
        }

        public static Generator combine(Generator a, Generator b) {
            int[][][] result = new int[a.generator.length + b.generator.length][][];
            for (int i = 0; i < a.generator.length; i++) {
                result[i] = a.generator()[i];
            }
            for (int i = 0; i < b.generator.length; i++) {
                result[a.generator.length + i] = b.generator()[i];
            }
            return new Generator(result);
        }
        
        public int[][][] generator() {
            int[][][] result = new int[generator.length][][];
            for (int i = 0; i < generator.length; i++) {
                result[i] = new int[generator[i].length][];
                for (int j = 0; j < generator[i].length; j++) {
                    result[i][j] = new int[generator[i][j].length];
                    for (int k = 0; k < generator[i][j].length; k++) {
                        result[i][j][k] = generator[i][j][k] & 0xff;
                    }
                }
            }
            return result;
        }

        public int hashCode() {
            if (cachedHashCode == null) {
                cachedHashCode = 0;
                for (byte[][] cycle : generator) {
                    for (byte[] element : cycle) {
                        cachedHashCode = cachedHashCode * 31 + Arrays.hashCode(element);
                    }
                }
            }
            return cachedHashCode;
        }
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || !(obj instanceof Generator)) return false;
            Generator generator1 = (Generator) obj;
            if (generator.length != generator1.generator.length) return false;
            for (int i = 0; i < generator.length; i++) {
                if (generator[i].length != generator1.generator[i].length) return false;
                for (int j = 0; j < generator[i].length; j++) {
                    if (generator[i][j].length != generator1.generator[i][j].length) return false;
                    for (int k = 0; k < generator[i][j].length; k++) {
                        if (generator[i][j][k] != generator1.generator[i][j][k]) return false;
                    }
                }
            }
            return true;
        }
    }

    public static enum MemorySettings {
        FASTEST,
        DEFAULT,
        COMPACT,
    }
    public GroupExplorer(String cycleNotation, MemorySettings mem) {
        this(cycleNotation, mem, new ObjectOpenHashSet<State>());
    }
    public GroupExplorer(Generator g, int nElements, MemorySettings mem, Set<State> stateMap) {
        this(g, nElements, mem, stateMap,  new ObjectOpenHashSet<State>(), new ObjectOpenHashSet<State>(), true);
    }
    public GroupExplorer(String cycleNotation, MemorySettings mem, Set<State> stateMap) {
        this(cycleNotation, mem, stateMap,  new ObjectOpenHashSet<State>(), new ObjectOpenHashSet<State>(), true);
    }
    public GroupExplorer(String cycleNotation, MemorySettings mem, Set<State> stateMap, Set<State> stateMapIncomplete, Set<State> stateMapTmp, boolean multithread) {
        for (String s : cycleNotation.split("\\(|\\)|,|\\[|\\]")) {
            if (!s.trim().isEmpty()) nElements = Math.max(nElements, Integer.parseInt(s.trim()));
        }

        this.multithread = multithread;
        this.mem = mem;
        this.stateMap = stateMap;
        this.stateMapIncomplete = stateMapIncomplete;
        this.stateMapTmp = stateMapTmp;
        elements = initializeElements(nElements);
        parsedOperations = parseOperations(cycleNotation);
    }

    public GroupExplorer(Generator g, int nElements, MemorySettings mem, Set<State> stateMap, Set<State> stateMapIncomplete, Set<State> stateMapTmp, boolean multithread) {
        this.nElements = nElements;
        this.multithread = multithread;
        this.mem = mem;
        this.stateMap = stateMap;
        this.stateMapIncomplete = stateMapIncomplete;
        this.stateMapTmp = stateMapTmp;
        elements = initializeElements(nElements);
        parsedOperations = Arrays.asList(g.generator());
    }

    public void setMultithread(boolean multithread) {
        this.multithread = multithread;
    }

    public void resetElements(boolean addInitialState) {
        elements = initializeElements(nElements);
        stateMap.clear();
        if (addInitialState) {
            stateMap.add(State.of(elements.clone(), nElements, mem));
        }
    }

    public void applyOperation(int index) {
        int[][] operation = parsedOperations.get(index);
        for (int[] cycle : operation) {
            for (int i = 0; i < cycle.length - 1; i++) {
                int current = cycle[i];
                int next = cycle[i + 1];
                int temp = elements[current - 1];
                elements[current - 1] = elements[next - 1];
                elements[next - 1] = temp;
            }
        }
        stateMap.add(State.of(elements.clone(), nElements, mem));
    }

    public int[] copyCurrentState() {
        return elements.clone();
    }

    public static void serializeState(DataOutputStream dos, int[] state) throws IOException {
        for (int i : state) {
            dos.writeInt(i);
        }
    }
    public void serialize(OutputStream out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(out)) {
            dos.writeInt(nElements);
            dos.writeInt(stateMap.size());

            for (State state : stateMap) {
                for (int i : state.state()) {
                    dos.writeInt(i);
                }
            }
        }
    }
        
    @Override
    public int order() {
        return stateMap.size();
    }

    @Override
    public int elements() {
        return nElements;
    }

    @Override
    public MemorySettings mem() {
        return mem;
    }
    
    public static String stateToNotation(int[] state) {
        int[][] cycles = stateToCycles(state);
        return cyclesToNotation(cycles);
    }

    public static int[][] stateToCycles(int[] state) {
        boolean[] visited = new boolean[state.length];
        List<List<Integer>> cyclesList = new ArrayList<>();

        for (int i = 0; i < state.length; i++) {
            if (!visited[i]) {
                int current = i;
                List<Integer> cycle = new ArrayList<>();
                
                do {
                    visited[current] = true;
                    cycle.add(current + 1);
                    current = state[current] - 1;
                } while (current != i);

                // Only add non-trivial cycles (length > 1)
                if (cycle.size() > 1) {
                    cyclesList.add(cycle);
                }
            }
        }

        return cyclesList.stream()
                .map(cycle -> cycle.stream().mapToInt(Integer::intValue).toArray())
                .toArray(int[][]::new);
    }

    public static String cyclesToNotation(int[][] cycles) {
        if (cycles.length == 0) {
            return "()";
        }

        StringBuilder notation = new StringBuilder();
        for (int[] cycle : cycles) {
            notation.append("(");
            for (int i = 0; i < cycle.length; i++) {
                notation.append(cycle[i]);
                if (i < cycle.length - 1) {
                    notation.append(",");
                }
            }
            notation.append(")");
        }

        return notation.toString();
    }

    private int[] initializeElements(int maxElement) {
        int[] result = new int[maxElement];
        for (int i = 0; i < maxElement; i++) {
            result[i] = i + 1;
        }
        return result;
    }


    int lastSize = 0;
    int iteration = 0;

    public void initIterativeExploration() {
        stateMapIncomplete.add(State.of(elements.clone(), nElements, mem));

        lastSize = 0;
        iteration = 0;
    }

    public int iterateExploration(boolean debug, int stateLimit, BiConsumer<List<int[]>, Integer> peekStateAndDepth) {
        int size = stateMap.size() + stateMapIncomplete.size();
        if (debug) System.out.println("Depth: " + iteration + " - " + (size - lastSize) + " - " + size);
        lastSize = size;
        iteration++;

        long sizeInit = size;

        Set<State> incompleteAdditions;
        List<int[]> peekList;
        boolean parallelStream;

        // Parallelize
        if (multithread && sizeInit > 10000) {
            incompleteAdditions = Collections.synchronizedSet(stateMapTmp);
            peekList = Collections.synchronizedList(new ArrayList<int[]>());
            parallelStream = true;
        } else {
            incompleteAdditions = stateMapTmp;
            peekList = new ArrayList<int[]>();
            parallelStream = false;
        }

        Iterator<State> iterator = stateMapIncomplete.iterator();


        // LMDB cache iterator is not multithreaded so let's create batches
        //     on main thread and process them in parallel
        while (iterator.hasNext()) {
            ArrayList<State> batch = new ArrayList<>();
            while (iterator.hasNext() && batch.size() < 10000) {
                batch.add(iterator.next());
            }

            Stream<State> stream;
            if (parallelStream) {
                stream = batch.parallelStream();
            } else {
                stream = batch.stream();
            }

            stream.forEach((state) -> {
                int[] currentState = state.state();

                for (int[][] operation : parsedOperations) {
                    int[] newState = applyOperation(currentState, operation);
                    State s = State.of(newState, nElements, mem);

                    if (!stateMapIncomplete.contains(s) && !stateMap.contains(s)) {
                        boolean addedFresh = incompleteAdditions.add(s);
                        if (addedFresh && peekStateAndDepth != null) peekList.add(newState);
                    }
                }
            });
        }

        if (peekList.size() > 0) {
            peekStateAndDepth.accept(peekList, iteration);
        }
        int preTransferSize = stateMap.size();
        int amntToTransfer = stateMapIncomplete.size();
        stateMap.addAll(stateMapIncomplete);
        if (stateMap.size() != preTransferSize + amntToTransfer) {
            throw new ParityStateCache.StateRejectedException("State map size mismatch: " + stateMap.size() + " != " + preTransferSize + " + " + amntToTransfer);
        }
        Set<State> tmp = stateMapIncomplete;
        stateMapIncomplete = stateMapTmp;
        stateMapTmp = tmp;
        stateMapTmp.clear();
        
        long sizeEnd = stateMap.size() + stateMapIncomplete.size();
        if (sizeInit == sizeEnd) {
            //System.out.println("Finished - " + stateMap.size() + " / " + stateMapIncomplete.size());
            return iteration;
        }
        if (stateLimit > 0 && sizeEnd > stateLimit) return -1;

        return -2;
    }

    public int exploreStates(boolean debug, BiConsumer<List<int[]>, Integer> peekStateAndDepth) {
       return exploreStates(debug, -1, peekStateAndDepth);
    }
    public int exploreStates(boolean debug, int stateLimit, BiConsumer<List<int[]>, Integer> peekStateAndDepth) {
        initIterativeExploration();
        
        while (true) {
            int ret = iterateExploration(debug, stateLimit, peekStateAndDepth);
            if (ret == -2) continue;
            return ret;
        }
    }
    
    public static int[][][] parseOperationsArr(String groupNotation) {
        List<int[][]> operations = parseOperations(groupNotation);
        return operations.toArray(new int[operations.size()][][]);
    }

    public static List<int[][]> parseOperations(String groupNotation) {
        List<int[][]> operations = new ArrayList<>();
        String[] parts = groupNotation.substring(1, groupNotation.length() - 1).split("\\),\\(");
        for (String part : parts) {
            String[] cycles = part.split("\\)\\(");
            int[][] operation = new int[cycles.length][];
            for (int i = 0; i < cycles.length; i++) {
                String[] elements = cycles[i].replaceAll("[()]", "").split(",");
                operation[i] = Arrays.stream(elements).mapToInt(Integer::parseInt).toArray();
            }
            operations.add(operation);
            //operations.add(reverseOperation(operation));
        }
        return operations;
    }

    public static int[][] reverseOperation(int[][] operation) {
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
    
    public static int[][] repeatOperation(int[][] operation, int n) {
        n++;
        int[][] result = new int[operation.length][];
        for (int i = 0; i < operation.length; i++) {
            int[] cycle = operation[i];
            int[] cycleRepeated = new int[cycle.length];
            for (int j = 0; j < cycle.length; j++) {
                cycleRepeated[j] = cycle[(j * n) % cycle.length];
            }
            result[i] = cycleRepeated;
        }
        return result;
    }

    private int[] applyOperation(int[] state, int[][] operation) {
        int[] newState = Arrays.copyOf(state, state.length);
        for (int[] cycle : operation) {
            if (cycle.length > 1) {
                int first = cycle[0];
                for (int i = 0; i < cycle.length - 1; i++) {
                    int current = cycle[i];
                    int next = cycle[i + 1];
                    newState[current - 1] = state[next - 1];
                }
                newState[cycle[cycle.length - 1] - 1] = state[first - 1];
            }
        }
        return newState;
    }

    public static String describeStateForCache(int nElements, int[] state) {
        int[][] cycles = stateToCycles(state);
        int[] nCycles = new int[nElements + 1];
        
        for (int[] cycle : cycles) {
            int length = cycle.length;
            if (length > 1) { // Ignore 1-cycles (fixed points)
                nCycles[length]++;
            }
        }
        
        StringBuilder description = new StringBuilder();
        for (int i = 2; i <= nElements; i++) {
            if (nCycles[i] > 0) {
                int mul = nCycles[i];
                description.append(mul).append(" ").append(i).append(" ");
            }
        }
        return description.toString();
    }

    public static String describeState(int nElements, int[] state) {
        int[][] cycles = stateToCycles(state);
        int[] nCycles = new int[nElements + 1];
        
        for (int[] cycle : cycles) {
            int length = cycle.length;
            if (length > 1) { // Ignore 1-cycles (fixed points)
                nCycles[length]++;
            }
        }
        
        StringBuilder description = new StringBuilder();
        boolean first = true;
        for (int i = 2; i <= nElements; i++) {
            if (nCycles[i] > 0) {
                if (!first) {
                    description.append(", ");
                }
                String multiplicity = getMultiplicityDescription(nCycles[i]);
                description.append(multiplicity).append(" ").append(i).append("-cycle");
                if (nCycles[i] > 1) {
                    description.append("s");
                }
                first = false;
            }
        }
        return description.toString();
    }

    private static String getMultiplicityDescription(int count) {
        switch (count) {
            case 1: return "single";
            case 2: return "dual";
            case 3: return "triple";
            case 4: return "quadruple";
            case 5: return "quintuple";
            default: return count + "p";
        }
    }

    public static boolean cyclesContainsAllElements(int nElements, int[][]... cyclesList) {
        int[] missingElements = new int[nElements];
        for (int[][] cycles : cyclesList) {
            for (int[] cycle : cycles) {
                for (int element : cycle) { missingElements[element - 1]++; }
            }
        }
        for (int missing : missingElements) {
            if (missing == 0) {
                return false;
            }
        }
        return true;
    }

    public static List<int[][][]> genIsomorphisms(int[][][] a) {
        List<int[][][]> checks = new ArrayList<>();
        Permu.applyGeneratorPermutationsAndRotations(a, (aPerm) -> {
            checks.add(renumberGenerators_fast(aPerm));
        });
        return checks;
    }

    public static void genIsomorphisms_Callback(int[][][] a, Consumer<int[][][]> callback) {
        genIsomorphisms_Callback(a, true, callback);
    }

    public static void genIsomorphisms_Callback(int[][][] a, boolean renumber, Consumer<int[][][]> callback) {
        Permu.applyGeneratorPermutationsAndRotations(a, (aPerm) -> {
            int[][][] check = renumber ? renumberGenerators_fast(aPerm) : aPerm;
            callback.accept(check);
        });
    }

    public static String renumberGeneratorNotation(String gapNotation) {
        Map<Integer, Integer> newIndices = new HashMap<>();
        int nextIndex = 1;
        StringBuilder result = new StringBuilder("[");
        String[] generators = gapNotation.substring(1, gapNotation.length() - 1).split(",(?=\\()");
    
        for (int i = 0; i < generators.length; i++) {
            String generator = generators[i].trim();
            StringBuilder newGenerator = new StringBuilder("");
            String[] cycles = generator.split("\\)\\(");
    
            for (int j = 0; j < cycles.length; j++) {
                newGenerator.append("(");
                String cycle = cycles[j].replaceAll("[()]", "");
                String[] elements = cycle.split(",");
                
                for (int k = 0; k < elements.length; k++) {
                    int oldIndex = Integer.parseInt(elements[k].trim());
                    if (!newIndices.containsKey(oldIndex)) {
                        newIndices.put(oldIndex, nextIndex++);
                    }
                    newGenerator.append(newIndices.get(oldIndex));
                    if (k < elements.length - 1) {
                        newGenerator.append(",");
                    }
                }
                newGenerator.append(")");
            }
    
            result.append(newGenerator);
            if (i < generators.length - 1) {
                result.append(",");
            }
        }
        result.append("]");
        return result.toString();
    }

    public static int[][][] renumberGenerators(int[][][] generators) {
        Map<Integer, Integer> newIndices = new HashMap<>();
        int nextIndex = 1;
        int[][][] result = new int[generators.length][][];

        for (int i = 0; i < generators.length; i++) {
            int[][] generator = generators[i];
            int[][] newGenerator = new int[generator.length][];

            for (int j = 0; j < generator.length; j++) {
                int[] cycle = generator[j];
                int[] newCycle = new int[cycle.length];

                for (int k = 0; k < cycle.length; k++) {
                    int oldIndex = cycle[k];
                    if (!newIndices.containsKey(oldIndex)) {
                        newIndices.put(oldIndex, nextIndex++);
                    }
                    newCycle[k] = newIndices.get(oldIndex);
                }

                newGenerator[j] = newCycle;
            }

            result[i] = newGenerator;
        }

        return result;
    }

    public static int[][][] renumberGenerators_fast(int[][][] generators) {
        int[] newIndices = new int[256]; // Assuming max element is < 256
        int nextIndex = 1;
        int[][][] result = new int[generators.length][][];
    
        for (int i = 0; i < generators.length; i++) {
            int[][] generator = generators[i];
            result[i] = new int[generator.length][];
    
            for (int j = 0; j < generator.length; j++) {
                int[] cycle = generator[j];
                int[] newCycle = new int[cycle.length];
    
                for (int k = 0; k < cycle.length; k++) {
                    int oldIndex = cycle[k];
                    if (newIndices[oldIndex] == 0) {
                        newIndices[oldIndex] = nextIndex++;
                    }
                    newCycle[k] = newIndices[oldIndex];
                }
    
                result[i][j] = newCycle;
            }
        }
    
        return result;
    }

    // Helper method to convert int[][][] to String (for debugging or display purposes)
    public static String generatorsToString(int[][][] generators) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < generators.length; i++) {
            if (generators[i].length == 0) continue; // Identity
            if (!first) {
                result.append(",");
            }
            first = false;
            for (int[] cycle : generators[i]) {
                result.append("(");
                for (int j = 0; j < cycle.length; j++) {
                    result.append(cycle[j]);
                    if (j < cycle.length - 1) {
                        result.append(",");
                    }
                }
                result.append(")");
            }
        }
        result.append("]");
        return result.toString();
    }

    public static int[] listOrderedElementsInCycles(int[][] aCycles) {
        int aElementCount = 0;
        for (int[] cycle : aCycles) { aElementCount+=cycle.length; }

        int[] aElements = new int[aElementCount];

        int aIndex = 0;
        for (int[] cycle : aCycles) {
            for (int element : cycle) {
                aElements[aIndex++] = element;
            }
        }

        Arrays.sort(aElements);
        return aElements;
    }

	public static boolean intersectionsMissing(int[][] aCycles, int[][] bCycles) {
		
        int[] aElements = listOrderedElementsInCycles(aCycles);
        int[] bElements = listOrderedElementsInCycles(bCycles);

        // For each cycle in a, make sure at least one element is in b
        for (int[] cycle : aCycles) {
            boolean found = false;
            for (int element : cycle) {
                if (Arrays.binarySearch(bElements, element) >= 0) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }

        // For each cycle in b, make sure at least one element is in a
        for (int[] cycle : bCycles) {
            boolean found = false;
            for (int element : cycle) {
                if (Arrays.binarySearch(aElements, element) >= 0) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        
        return false;
	}

}
