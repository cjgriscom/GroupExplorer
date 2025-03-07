package io.chandler.gap.graph;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.PlanarityTestingAlgorithm;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;

import io.chandler.gap.GroupExplorer;

// Add new imports for running Python and parsing JSON.
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;

public class GraphVisualizer extends Application {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double NODE_RADIUS = 20;

    private String filePath;
    private List<Graph<Integer, DefaultEdge>> graphPages;
    private int currentGraphIndex = 0;

    public static void main(String[] args) {
        if (args.length > 0) {
            launch(args);
        } else {
            launch(new String[] { "/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/m22/6p 3-cycles-6p 3-cycles-filtered.txt" });
        }
    }

    @Override
    public void start(Stage primaryStage) {
        filePath = getParameters().getRaw().get(0);
        graphPages = readGraphsFromFile(filePath);

        if (graphPages.isEmpty()) {
            System.out.println("No graphs loaded from the file.");
            return;
        }

        // Create the main layout:
        BorderPane root = new BorderPane();
        Pane graphPane = new Pane();
        root.setCenter(graphPane);

        // Create the paginator controls.
        Button prevButton = new Button("Previous");
        Button nextButton = new Button("Next");
        Label pageLabel = new Label("Graph 1 / " + graphPages.size());

        HBox paginator = new HBox(10, prevButton, pageLabel, nextButton);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setBottom(paginator);

        // Set button actions to update the displayed graph.
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                currentGraphIndex--;
                updateGraph(graphPane, pageLabel);
            }
        });

        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphPages.size() - 1) {
                currentGraphIndex++;
                updateGraph(graphPane, pageLabel);
            }
        });

        // Render the first graph.
        updateGraph(graphPane, pageLabel);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("Planar Graph Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Reads the entire file and builds a list of graphs (one per line).
     *
     * @param filePath Path to the file containing the graph data.
     * @return List of graphs.
     */
    private List<Graph<Integer, DefaultEdge>> readGraphsFromFile(String filePath) {
        List<Graph<Integer, DefaultEdge>> graphs = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filePath))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Graph<Integer, DefaultEdge> graph = buildGraphFromLine(line);
                if (graph != null) {
                    graphs.add(graph);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filePath);
        }
        return graphs;
    }

    /**
     * Builds a graph from a single line of input.
     *
     * @param line The input line containing graph data.
     * @return The constructed graph.
     */
    private Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices first.
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                // Add edges to form a complete cycle.
                for (int i = 0; i < polygon.length; i++) {
                    graph.addEdge(polygon[i], polygon[(i + 1) % polygon.length]);
                }
            }
        }
        return graph;
    }

    /**
     * Draws the graph in a planar embedding on the specified pane.
     *
     * @param graph The graph to draw.
     * @param pane  The pane to draw the graph on.
     */
    private void drawPlanarGraph(Graph<Integer, DefaultEdge> graph, Pane pane) {
        // Check planarity and get the embedding
        BoyerMyrvoldPlanarityInspector<Integer, DefaultEdge> inspector = new BoyerMyrvoldPlanarityInspector<>(graph);
		if (!inspector.isPlanar()) {
			System.out.println("Graph is not planar (!isPlanar).");
			return;
		}
        PlanarityTestingAlgorithm.Embedding<Integer, DefaultEdge> embedding = inspector.getEmbedding();

        if (embedding == null) {
            System.out.println("Graph is not planar.");
            return;
        }

        // Retrieve faces from the embedding using our helper method with getEdgesAround
        List<List<DefaultEdge>> faces = computeFaces(embedding);

        // Compute positions for vertices using networkx via Python.
        Map<Integer, double[]> positions = getNetworkXCoordinates(graph);
        if (positions == null) {
            System.out.println("Failed to get coordinates from networkx. Falling back to Tutte's algorithm.");
            positions = computePlanarLayout(embedding);
        }

        // Draw edges
        for (DefaultEdge edge : graph.edgeSet()) {
            int source = graph.getEdgeSource(edge);
            int target = graph.getEdgeTarget(edge);
            double[] sourcePos = positions.get(source);
            double[] targetPos = positions.get(target);

            Line line = new Line(sourcePos[0], sourcePos[1], targetPos[0], targetPos[1]);
            pane.getChildren().add(line);
        }

        // Draw nodes
        for (Integer vertex : graph.vertexSet()) {
            double[] pos = positions.get(vertex);
            Circle circle = new Circle(pos[0], pos[1], NODE_RADIUS);
            Text text = new Text(pos[0] - NODE_RADIUS / 2, pos[1] + NODE_RADIUS / 2, vertex.toString());

            pane.getChildren().addAll(circle, text);
        }
    }

    /**
     * Computes a planar layout for the graph using Tutte's algorithm.
     *
     * @param embedding The planar embedding of the graph.
     * @return A map of vertex positions.
     */
    private Map<Integer, double[]> computePlanarLayout(PlanarityTestingAlgorithm.Embedding<Integer, DefaultEdge> embedding) {
        Map<Integer, double[]> positions = new HashMap<>();
        Graph<Integer, DefaultEdge> graph = embedding.getGraph();
 
        // Retrieve faces from the embedding using our helper method with getEdgesAround
        List<List<DefaultEdge>> faces = computeFaces(embedding);
 
        // Choose the outer face as the face with the maximum number of distinct vertices
        List<Integer> outerFaceVertices = null;
        int maxFaceSize = 0;
        if (faces != null && !faces.isEmpty()) {
            for (List<DefaultEdge> face : faces) {
                List<Integer> verticesInFace = new ArrayList<>();
                for (DefaultEdge edge : face) {
                    Integer source = graph.getEdgeSource(edge);
                    Integer target = graph.getEdgeTarget(edge);
                    if (!verticesInFace.contains(source)) verticesInFace.add(source);
                    if (!verticesInFace.contains(target)) verticesInFace.add(target);
                }
                if (verticesInFace.size() > maxFaceSize) {
                    maxFaceSize = verticesInFace.size();
                    outerFaceVertices = verticesInFace;
                }
            }
        }
 
        // Fallback: if no outer face found, use circular layout for all vertices 
        if (outerFaceVertices == null) {
            int numVertices = graph.vertexSet().size();
            double angleIncrement = 2 * Math.PI / numVertices;
            double radius = Math.min(WIDTH, HEIGHT) / 3;
            double centerX = WIDTH / 2;
            double centerY = HEIGHT / 2;
            int i = 0;
            for (Integer vertex : graph.vertexSet()) {
                double angle = i * angleIncrement;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                positions.put(vertex, new double[]{x, y});
                i++;
            }
            return positions;
        }
 
        // Place outer face vertices on a convex polygon (circle)
        int outerCount = outerFaceVertices.size();
        double angleIncrement = 2 * Math.PI / outerCount;
        double outerRadius = Math.min(WIDTH, HEIGHT) / 3;
        double centerX = WIDTH / 2;
        double centerY = HEIGHT / 2;
        for (int i = 0; i < outerCount; i++) {
            double angle = i * angleIncrement;
            double x = centerX + outerRadius * Math.cos(angle);
            double y = centerY + outerRadius * Math.sin(angle);
            positions.put(outerFaceVertices.get(i), new double[]{x, y});
        }
 
        // Identify interior vertices (vertices not in the outer face)
        Set<Integer> outerSet = new HashSet<>(outerFaceVertices);
        List<Integer> interiorVertices = new ArrayList<>();
        for (Integer vertex : graph.vertexSet()) {
            if (!outerSet.contains(vertex)) {
                interiorVertices.add(vertex);
                // Initialize interior vertices at the center
                positions.put(vertex, new double[]{centerX, centerY});
            }
        }
 
        // Iterative relaxation (Tutte's algorithm) for interior vertices
        int iterations = 500;
        for (int iter = 0; iter < iterations; iter++) {
            for (Integer vertex : interiorVertices) {
                double sumX = 0;
                double sumY = 0;
                int count = 0;
                for (DefaultEdge edge : graph.edgesOf(vertex)) {
                    Integer neighbor = graph.getEdgeSource(edge).equals(vertex) ?
                            graph.getEdgeTarget(edge) : graph.getEdgeSource(edge);
                    double[] neighborPos = positions.get(neighbor);
                    sumX += neighborPos[0];
                    sumY += neighborPos[1];
                    count++;
                }
                if (count > 0) {
                    double newX = sumX / count;
                    double newY = sumY / count;
                    positions.put(vertex, new double[]{newX, newY});
                }
            }
        }
 
        return positions;
    }

    /**
     * Computes faces from the planar embedding using the cyclic order available via getEdgesAround.
     *
     * Each face is traced by following directed edges. A directed edge (u -> v) is marked as visited
     * so that each face is computed only once.
     *
     * @param embedding The planar embedding.
     * @return A list of faces. Each face is represented as a list of DefaultEdge.
     */
    private List<List<DefaultEdge>> computeFaces(PlanarityTestingAlgorithm.Embedding<Integer, DefaultEdge> embedding) {
        Graph<Integer, DefaultEdge> graph = embedding.getGraph();
        List<List<DefaultEdge>> faces = new ArrayList<>();
        Set<String> visitedDirectedEdges = new HashSet<>();

        // Iterate over all vertices
        for (Integer u : graph.vertexSet()) {
            List<DefaultEdge> edgesAroundU = embedding.getEdgesAround(u);
            if (edgesAroundU == null) continue;
            // For each edge (as a candidate starting directed edge)
            for (DefaultEdge e : edgesAroundU) {
                Integer v = graph.getEdgeSource(e).equals(u) ? graph.getEdgeTarget(e) : graph.getEdgeSource(e);
                String key = u + "->" + v;
                if (visitedDirectedEdges.contains(key))
                    continue;

                List<DefaultEdge> faceEdges = new ArrayList<>();

                Integer startU = u;
                Integer startV = v;
                Integer currentU = u;
                Integer currentV = v;
                DefaultEdge currentEdge = e;

                // Walk around the face until the starting directed edge is encountered again.
                do {
                    faceEdges.add(currentEdge);
                    visitedDirectedEdges.add(currentU + "->" + currentV);

                    // At vertex currentV, get the cyclic order of edges.
                    List<DefaultEdge> cyclicEdges = embedding.getEdgesAround(currentV);
                    if (cyclicEdges == null || cyclicEdges.isEmpty())
                        break;

                    // Find the position of the edge coming from currentU.
                    int pos = -1;
                    for (int i = 0; i < cyclicEdges.size(); i++) {
                        DefaultEdge edge = cyclicEdges.get(i);
                        if ((graph.getEdgeSource(edge).equals(currentV) && graph.getEdgeTarget(edge).equals(currentU)) ||
                            (graph.getEdgeTarget(edge).equals(currentV) && graph.getEdgeSource(edge).equals(currentU))) {
                            pos = i;
                            break;
                        }
                    }
                    if (pos == -1)
                        break; // should not happen, but exit if it does

                    // Pick the next edge in the cyclic order.
                    int nextPos = (pos + 1) % cyclicEdges.size();
                    DefaultEdge nextEdge = cyclicEdges.get(nextPos);

                    // Determine the next vertex from the current vertex.
                    Integer nextVertex = graph.getEdgeSource(nextEdge).equals(currentV)
                            ? graph.getEdgeTarget(nextEdge)
                            : graph.getEdgeSource(nextEdge);

                    // Move to the next directed edge.
                    currentU = currentV;
                    currentV = nextVertex;
                    currentEdge = nextEdge;
                } while (!(currentU.equals(startU) && currentV.equals(startV)));

                faces.add(faceEdges);
            }
        }
        return faces;
    }

    /**
     * Updates the graph pane with the current graph and updates the page label.
     *
     * @param graphPane The Pane used for drawing.
     * @param pageLabel The Label showing the current page.
     */
    private void updateGraph(Pane graphPane, Label pageLabel) {
        graphPane.getChildren().clear();
        Graph<Integer, DefaultEdge> currentGraph = graphPages.get(currentGraphIndex);
        drawPlanarGraph(currentGraph, graphPane);
        pageLabel.setText("Graph " + (currentGraphIndex + 1) + " / " + graphPages.size());
    }

    /**
     * Uses a Python shell to compute the coordinates of the graph using networkx's planar_layout.
     * The Python script (networkx_layout.py) must be in your working directory.
     * 
     * @param graph The graph to layout.
     * @return A mapping from vertex to (x, y) coordinates scaled to the window size, or null if an error occurs.
     */
    private Map<Integer, double[]> getNetworkXCoordinates(Graph<Integer, DefaultEdge> graph) {
        Map<Integer, double[]> positions = new HashMap<>();
        // Build edge data string in the format: "u,v;u,v;..."
        StringBuilder edgeData = new StringBuilder();
        for (DefaultEdge edge : graph.edgeSet()) {
            int u = graph.getEdgeSource(edge);
            int v = graph.getEdgeTarget(edge);
            edgeData.append(u).append(",").append(v).append(";");
        }
        if (edgeData.length() > 0) {
            // Remove trailing semicolon
            edgeData.setLength(edgeData.length() - 1);
        }

        try {
            // Build the command, adjust "python3" if necessary.
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("networkx_layout.py");
            command.add(edgeData.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            process.waitFor();
            
            // Parse the JSON output.
            String jsonString = output.toString();
            JSONObject json = new JSONObject(jsonString);
            for(String key : json.keySet()) {
                JSONArray coords = json.getJSONArray(key);
                double x = coords.getDouble(0);
                double y = coords.getDouble(1);
                // Assume the python output is normalized to [0,1]. Scale up to our pane dimensions:
                x = x * WIDTH;
                y = y * HEIGHT;
                positions.put(Integer.parseInt(key), new double[]{x, y});
            }
            return positions;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}