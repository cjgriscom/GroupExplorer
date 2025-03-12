package networkx;

import java.util.*;

public class Graph {
    private Set<Integer> nodes;
    private List<Edge> edges;
    private Map<Integer, Set<Integer>> adj;

    public Graph() {
        nodes = new HashSet<>();
        edges = new ArrayList<>();
        adj = new HashMap<>();
    }

    public void addEdge(int u, int v) {
        nodes.add(u);
        nodes.add(v);
        edges.add(new Edge(u, v));
        // update adjacency list for both directions
        if (!adj.containsKey(u)) {
            adj.put(u, new HashSet<>());
        }
        if (!adj.containsKey(v)) {
            adj.put(v, new HashSet<>());
        }
        adj.get(u).add(v);
        adj.get(v).add(u);
    }

    public Set<Integer> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
    
    public Map<Integer, Set<Integer>> getAdjacency() {
        return adj;
    }

    public static class Edge {
        public int u;
        public int v;
        public Edge(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }
} 