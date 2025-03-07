package io.chandler.gap.graph;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

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
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;

public class GraphVisualizer extends Application {

    private static final int WIDTH = 1024;
    private static final int HEIGHT = 768;
    private static final double NODE_RADIUS = 20;

    private String filePath;
    private List<String> graphLines;
    private int currentGraphIndex = 0;
    // Add a map to store edge frequencies; keys are in the form "min-max".
    private Map<String, Integer> edgeFrequencyMap;
    private Map<Integer, Integer> vertexFrequencyMap;

    // Add new instance variables for layout configuration:
    private CheckBox usePlanarCheckBox;
    private TextField seedTextField;

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
        graphLines = readGraphLinesFromFile(filePath);

        if (graphLines.isEmpty()) {
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
        Label pageLabel = new Label("Graph 1 / " + graphLines.size());
		
        // Create layout configuration controls.
        usePlanarCheckBox = new CheckBox("Use Planar Layout");
        usePlanarCheckBox.setSelected(true);
		usePlanarCheckBox.setOnAction((e) -> {
                updateGraph(graphPane, pageLabel);});
        seedTextField = new TextField("42");
		
		// Add inc/dec arrows
		
		seedTextField.setOnKeyReleased(value -> {
				updateGraph(graphPane, pageLabel);
		});
        // You could also limit TextField input to numbers if needed.
        HBox layoutControls = new HBox(10, usePlanarCheckBox, new Label("Spring Seed:"), seedTextField);
        layoutControls.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setTop(layoutControls);

        HBox paginator = new HBox(10, prevButton, pageLabel, nextButton);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        
        // Add the "Remove Duplicates" button
        Button removeDupButton = new Button("Remove Duplicates");
        removeDupButton.setOnAction(e -> {
            removeDuplicates();
            currentGraphIndex = 0;
            updateGraph(graphPane, pageLabel);
        });
        paginator.getChildren().add(removeDupButton);

        root.setBottom(paginator);

        // Set button actions to update the displayed graph.
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                currentGraphIndex--;
                updateGraph(graphPane, pageLabel);
            }
        });

        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphLines.size() - 1) {
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
     * Reads the input file and stores each non-empty line.
     *
     * @param filePath Path to the file containing graph data.
     * @return List of input lines.
     */
    private List<String> readGraphLinesFromFile(String filePath) {
        List<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filePath))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filePath);
        }
        return lines;
    }

    /**
     * Builds a graph from a single line of input.
     *
     * @param line The input line containing graph data.
     * @return The constructed graph.
     */
    private Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        edgeFrequencyMap = new HashMap<>();
        vertexFrequencyMap = new HashMap<>();
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                // Add all vertices first and update vertex frequency count.
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                    vertexFrequencyMap.put(vertex, vertexFrequencyMap.getOrDefault(vertex, 0) + 1);
                }
                // Add edges to form a complete cycle and update edge frequency count.
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    int min = Math.min(a, b);
                    int max = Math.max(a, b);
                    String key = min + "-" + max;
                    edgeFrequencyMap.put(key, edgeFrequencyMap.getOrDefault(key, 0) + 1);
                    graph.addEdge(a, b);
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

        // Compute positions for vertices using networkx via Python.
        Map<Integer, double[]> positions = getNetworkXCoordinates(graph);
        

        // Draw edges and store Line objects in a map for interactivity.
        Map<DefaultEdge, Line> edgeLineMap = new HashMap<>();
        for (DefaultEdge edge : graph.edgeSet()) {
            int source = graph.getEdgeSource(edge);
            int target = graph.getEdgeTarget(edge);
            double[] sourcePos = positions.get(source);
            double[] targetPos = positions.get(target);

            Line line = new Line(sourcePos[0], sourcePos[1], targetPos[0], targetPos[1]);
            // Determine frequency for the edge using a canonical key.
            int min = Math.min(source, target);
            int max = Math.max(source, target);
            String key = min + "-" + max;
            Integer freq = edgeFrequencyMap.get(key);
            if (freq != null && freq > 1) {
                line.setStroke(Color.RED);
                line.setStrokeWidth(2.0);
            } else {
                line.setStroke(Color.BLACK);
                line.setStrokeWidth(1.0);
            }

            edgeLineMap.put(edge, line);
            pane.getChildren().add(line);
        }

        // Draw nodes with interactive dragging.
        Map<Integer, Circle> vertexCircleMap = new HashMap<>();
        Map<Integer, Text> vertexLabelMap = new HashMap<>();
        for (Integer vertex : graph.vertexSet()) {
            double[] pos = positions.get(vertex);
            Circle circle = new Circle(pos[0], pos[1], NODE_RADIUS);
            // Color the vertex based on its frequency.
            int freq = vertexFrequencyMap.getOrDefault(vertex, 1);
			Color color = getVertexColor(freq);
			// Set opacity
			color = Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
            circle.setFill(color);
            Text text = new Text(pos[0] - NODE_RADIUS/2, pos[1] + NODE_RADIUS/2, vertex.toString());
            vertexCircleMap.put(vertex, circle);
            vertexLabelMap.put(vertex, text);

            final int v = vertex; // capture vertex id for use in lambdas
            // Store offset between the mouse position and circle center.
            circle.setOnMousePressed(e -> {
                circle.setUserData(new double[] { circle.getCenterX() - e.getSceneX(), 
                                                     circle.getCenterY() - e.getSceneY() });
            });

            circle.setOnMouseDragged(e -> {
                double[] offset = (double[])circle.getUserData();
                double newX = e.getSceneX() + offset[0];
                double newY = e.getSceneY() + offset[1];
                circle.setCenterX(newX);
                circle.setCenterY(newY);
                text.setX(newX - NODE_RADIUS/2);
                text.setY(newY + NODE_RADIUS/2);

                // Update connected edges.
                for (DefaultEdge edge : graph.edgeSet()) {
                    int source = graph.getEdgeSource(edge);
                    int target = graph.getEdgeTarget(edge);
                    if (source == v) {
                        Line l = edgeLineMap.get(edge);
                        l.setStartX(newX);
                        l.setStartY(newY);
                    }
                    if (target == v) {
                        Line l = edgeLineMap.get(edge);
                        l.setEndX(newX);
                        l.setEndY(newY);
                    }
                }
            });

            pane.getChildren().addAll(text, circle);
        }
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
        Graph<Integer, DefaultEdge> currentGraph = buildGraphFromLine(graphLines.get(currentGraphIndex));
        drawPlanarGraph(currentGraph, graphPane);
        pageLabel.setText("Graph " + (currentGraphIndex + 1) + " / " + graphLines.size());
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
            // Decide on algorithm using UI controls.
            String algorithm = usePlanarCheckBox.isSelected() ? "planar" : "spring";
            String seedArg = seedTextField.getText();
            if (seedArg.isEmpty()) {
                seedArg = "42";
            }

            // Build the command, adjust "python3" if necessary.
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("networkx_layout.py");
            command.add(edgeData.toString());
            command.add(algorithm);
            command.add(seedArg);

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

    /**
     * Returns a color for a vertex based on its frequency.
     * Frequencies: 1 -> Light Gray, 2 -> Yellow, 3 -> Orange, and 4 or more -> Red.
     *
     * @param frequency The number of times the vertex appears.
     * @return The chosen Color.
     */
    private Color getVertexColor(int frequency) {
        if (frequency >= 4) {
            return Color.DARKGREEN;
        } else if (frequency == 3) {
            return Color.DARKORANGE;
        } else if (frequency == 2) {
            return Color.DARKRED;
        } else {
            return Color.BLACK;
        }
    }

    /**
     * Iterates through the stored graph lines and removes duplicates.
     * Two graphs are considered duplicates if they are isomorphic.
     */
    private void removeDuplicates() {
        List<String> uniqueLines = new ArrayList<>();
        for (String line : graphLines) {
            Graph<Integer, DefaultEdge> currentGraph = buildGraphFromLine(line);
            boolean duplicate = false;
            for (String uniqLine : uniqueLines) {
                Graph<Integer, DefaultEdge> uniqGraph = buildGraphFromLine(uniqLine);
                VF2GraphIsomorphismInspector<Integer, DefaultEdge> inspector =
                        new VF2GraphIsomorphismInspector<>(currentGraph, uniqGraph);
                if (inspector.isomorphismExists()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                uniqueLines.add(line);
            }
        }
        graphLines = uniqueLines;
    }
}