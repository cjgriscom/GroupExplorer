package io.chandler.gap.graph;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.PlanarityTestingAlgorithm;
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
import javafx.scene.control.ComboBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.chandler.gap.GroupExplorer;

// Add new imports for running Python and parsing JSON.
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;
import org.jgrapht.alg.drawing.IndexedFRLayoutAlgorithm2D;
import org.jgrapht.alg.drawing.model.Box2D;
import org.jgrapht.alg.drawing.model.LayoutModel2D;
import org.jgrapht.alg.drawing.model.MapLayoutModel2D;
import org.jgrapht.alg.drawing.model.Point2D;

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
    private TextField seedTextField, itersTextField, thetaTextField, normTextField;
    private ComboBox<String> layoutChoiceBox;
	private Label numSharedLinesLabel;

    public static void main(String[] args) {
        if (args.length > 0) {
            launch(args);
        } else {
            launch(new String[] { "/home/cjgriscom/Programming/GroupExplorer/PlanarStudy/m12/quadruple 3-cycles-quadruple 3-cycles-filtered.txt" });
        }
    }

    @Override
    public void start(Stage primaryStage) {
		System.out.println(bFoldsA(new int[] {17,18,19,20}, new int[] {19,18,17}));
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
        seedTextField = new TextField("0");
		itersTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_ITERATIONS));
		normTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_NORMALIZATION_FACTOR));
		thetaTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_THETA_FACTOR));
        numSharedLinesLabel = new Label("Shared Lines: 0");
                
		for (TextField textField : new TextField[] {seedTextField, itersTextField, normTextField, thetaTextField}) {
			// Set width
			textField.setPrefWidth(50);
			textField.setOnKeyReleased(value -> {
				updateGraph(graphPane, pageLabel);
			});
		}

        Button randomizeButton = new Button("Randomize");
        randomizeButton.setOnAction(e -> {
            seedTextField.setText(String.valueOf(new Random().nextInt(10000)));
            updateGraph(graphPane, pageLabel);
        });

        // Add Load button to select a new TXT file.
        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                graphLines = readGraphLinesFromFile(selectedFile.getAbsolutePath());
                currentGraphIndex = 0;
                updateGraph(graphPane, pageLabel);
            }
        });


        layoutChoiceBox = new ComboBox<>();
        layoutChoiceBox.getItems().addAll("Python Spring", "Python Planar", "Java Spring");
        layoutChoiceBox.setValue("Java Spring");  // default choice
        layoutChoiceBox.setOnAction(e -> updateGraph(graphPane, pageLabel));
        HBox layoutControls = new HBox(10, new Label("Layout:"), layoutChoiceBox, new Label("Seed:"), seedTextField, new Label("Iters:"), itersTextField, new Label("Theta Factor:"), thetaTextField, new Label("Norm Factor:"), normTextField, randomizeButton, loadButton);
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
        // Add the "Remove Folded" button
        Button removeFoldedButton = new Button("Remove Folded");
        removeFoldedButton.setOnAction(e -> {
            // Filter the graphLines list: keep only those that do not have folded polygons.
            List<String> filteredLines = new ArrayList<>();
            for (String line : graphLines) {
                if (!hasFoldedPolygons(line)) {
                    filteredLines.add(line);
                }
            }
            graphLines = filteredLines;
            currentGraphIndex = 0;
            updateGraph(graphPane, pageLabel);
        });
        paginator.getChildren().addAll(removeDupButton, removeFoldedButton, numSharedLinesLabel);

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
    private void drawPlanarGraph(Graph<Integer, DefaultEdge> graph, Pane pane, String sourceLine, Map<Integer, double[]> positions) {
        // --- Draw translucent shaded polygons based on source data ---
        // Parse the source line into a 3D array of polygons.
        int[][][] polys = GroupExplorer.parseOperationsArr(sourceLine);
        // Define an array of translucent fill colors for groups.
        Color[] groupColors = {
            Color.RED.deriveColor(0, 1, 1, 0.2),
            Color.BLUE.deriveColor(0, 1, 1, 0.2),
            Color.GREEN.deriveColor(0, 1, 1, 0.2),
            Color.ORANGE.deriveColor(0, 1, 1, 0.2)
        };

        // Create a list to hold references to the shaded polygons.
        final List<ShadedPolygonWrapper> shadedPolygons = new ArrayList<>();

        // Iterate through each group in the source data.
        for (int g = 0; g < polys.length; g++) {
            Color fillColor = groupColors[g % groupColors.length];
            // Each set may consist of multiple polygons.
            for (int[] polygon : polys[g]) {
                javafx.scene.shape.Polygon shadedPoly = new javafx.scene.shape.Polygon();
                // For each vertex in the polygon, add the computed coordinates.
                for (int vertex : polygon) {
                    double[] pos = positions.get(vertex);
                    if (pos != null) {
                        shadedPoly.getPoints().addAll(pos[0], pos[1]);
                    }
                }
                shadedPoly.setFill(fillColor);
                shadedPoly.setStroke(null);
                // Add the polygon to the pane first so it appears beneath nodes/edges.
                pane.getChildren().add(shadedPoly);

                // Save a wrapper that associates this polygon with its vertex IDs.
                shadedPolygons.add(new ShadedPolygonWrapper(shadedPoly, polygon));
            }
        }

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
                double[] offset = (double[]) circle.getUserData();
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

                // Update shaded polygons based on current node positions.
                updateShadedPolygons(vertexCircleMap, shadedPolygons);
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
        String currentLine = graphLines.get(currentGraphIndex);
        Graph<Integer, DefaultEdge> currentGraph = buildGraphFromLine(currentLine);
        Map<Integer, double[]> positions = getCoordinates(currentGraph);

		if (positions != null) {
			graphPane.getChildren().clear();
			pageLabel.setText("Graph " + (currentGraphIndex + 1) + " / " + graphLines.size());
			drawPlanarGraph(currentGraph, graphPane, currentLine, positions);
			numSharedLinesLabel.setText("Shared Lines: " + countSharedLines());
		}
    }

	private int countSharedLines() {
		int count = 0;
		for (Map.Entry<String, Integer> entry : edgeFrequencyMap.entrySet()) {
			if (entry.getValue() > 1) {
				count += 1;
			}
		}
		return count;
	}

    /**
     * New method that selects the layout method based on the dropdown.
     *
     * @param graph The graph to layout.
     * @return A mapping from vertex to (x, y) coordinates scaled to the window size, or null if an error occurs.
     */
    private Map<Integer, double[]> getCoordinates(Graph<Integer, DefaultEdge> graph) {
        String method = layoutChoiceBox.getValue();
        if ("Java Spring".equals(method)) {
            return getJavaSpringCoordinates(graph);
        } else {
            String algorithm = method.equals("Python Planar") ? "planar" : "spring";
            return getPythonCoordinates(graph, algorithm);
        }
    }

    /**
     * Refactor the original networkx method into this new helper for Python-based layouts.
     *
     * @param graph The graph to layout.
     * @param algorithm The layout algorithm to use.
     * @return A mapping from vertex to (x, y) coordinates scaled to the window size, or null if an error occurs.
     */
    private Map<Integer, double[]> getPythonCoordinates(Graph<Integer, DefaultEdge> graph, String algorithm) {
        Map<Integer, double[]> positions = new HashMap<>();
        StringBuilder edgeData = new StringBuilder();
        for (DefaultEdge edge : graph.edgeSet()) {
            int u = graph.getEdgeSource(edge);
            int v = graph.getEdgeTarget(edge);
            edgeData.append(u).append(",").append(v).append(";");
        }
        if (edgeData.length() > 0) {
            edgeData.setLength(edgeData.length() - 1);
        }
        try {
            String seedArg = seedTextField.getText();
            if (seedArg.isEmpty()) {
                seedArg = "42";
            }
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
            String jsonString = output.toString();
            JSONObject json = new JSONObject(jsonString);
            for (String key : json.keySet()) {
                JSONArray coords = json.getJSONArray(key);
                double x = coords.getDouble(0) * WIDTH;
                double y = coords.getDouble(1) * HEIGHT;
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

    /**
     * Updates the points of every shaded polygon wrapper based on the current positions of the corresponding nodes.
     *
     * @param vertexCircleMap Map of vertex IDs to their Circle objects.
     * @param shadedPolys List of wrapped shaded polygons.
     */
    private void updateShadedPolygons(Map<Integer, Circle> vertexCircleMap, List<ShadedPolygonWrapper> shadedPolys) {
        for (ShadedPolygonWrapper wrapper : shadedPolys) {
            javafx.scene.shape.Polygon poly = wrapper.polygon;
            poly.getPoints().clear();
            for (int vertex : wrapper.vertices) {
                Circle c = vertexCircleMap.get(vertex);
                if (c != null) {
                    poly.getPoints().addAll(c.getCenterX(), c.getCenterY());
                }
            }
        }
    }
    
    /**
     * Helper class that wraps a Polygon along with its source vertex IDs.
     */
    private static class ShadedPolygonWrapper {
        javafx.scene.shape.Polygon polygon;
        int[] vertices;
        
        ShadedPolygonWrapper(javafx.scene.shape.Polygon polygon, int[] vertices) {
            this.polygon = polygon;
            this.vertices = vertices;
        }
    }

    private boolean hasFoldedPolygons(String line) {
        int[][][] generator = GroupExplorer.parseOperationsArr(line);

		// For every combination in both orders
		for (int i = 0; i < generator.length; i++) {
			for (int j = 0; j < generator.length; j++) {
				if (j == i) continue;
				if (hasFoldedPolygon(generator[i], generator[j])) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasFoldedPolygon(int[][] listA, int[][] listB) {
		for (int[] a : listA) {
			for (int[] b : listB) {
				if (bFoldsA(a, b)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 *  For every possible pair of numbers in B, confirm if they are adjacent in A.
	 * Adjacent in A means they are next to each other in the array or at the start and end.
	 * If there are any pairs in B which are non-adjacent in A, return false.
	 * @param a
	 * @param b
	 * @return
	 */
	private boolean bFoldsA(int[] a, int[] b) {
        // If b has fewer than 2 vertices, nothing to check.
        if (b == null || b.length < 2) return true;
        // For every distinct pair in b, check that they appear adjacent in a (cyclically)
        for (int i = 0; i < b.length; i++) {
            for (int j = i + 1; j < b.length; j++) {
				if (i == j) continue;
                if (containsBoth(a, b[i], b[j]) && !areAdjacent(a, b[i], b[j])) {
                    return true;
                }
            }
        }
        return false;
	}

	/**
	 * Returns true if both x and y are in the array a.
	 */
	private boolean containsBoth(int[] a, int x, int y) {
		boolean xFound = false, yFound = false;
		for (int i = 0; i < a.length; i++) {
			if (a[i] == x) xFound = true;
			if (a[i] == y) yFound = true;
		}
		return xFound && yFound;
	}
	
	/**
	 * Helper method: returns true if x and y appear adjacent (in either order) in the circular array a.
	 */
	private boolean areAdjacent(int[] a, int x, int y) {
	    for (int i = 0; i < a.length; i++) {
	        int next = (i + 1) % a.length;
	        if ((a[i] == x && a[next] == y) || (a[i] == y && a[next] == x)) {
	            return true;
	        }
	    }
	    return false;
	}

	private Map<Integer, double[]> getJavaSpringCoordinates_Async(Graph<Integer, DefaultEdge> graph) {
		Map<Integer, double[]> positions = new HashMap<>();
		ExecutorService executor = Executors.newFixedThreadPool(1);
		Future<Map<Integer, double[]>> future = executor.submit(() -> getJavaSpringCoordinates(graph));
		try {
			positions = future.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
		}
		executor.shutdownNow();
		return positions;
	}

    /**
     * Uses the JGraphT IndexedFRLayoutAlgorithm2D (a variant of Fruchterman-Reingold) to compute positions.
     */
    private Map<Integer, double[]> getJavaSpringCoordinates(Graph<Integer, DefaultEdge> graph) {
        // Create a layout model; here we use the same WIDTH/HEIGHT as defined.
        LayoutModel2D<Integer> layoutModel = new MapLayoutModel2D<Integer>(new Box2D(WIDTH*2, HEIGHT*2));

		int iterations;
		double theta;
		double normalizationFactor;
		int seed;

		try {
			iterations = Integer.parseInt(itersTextField.getText());
			theta = Double.parseDouble(thetaTextField.getText());
			normalizationFactor = Double.parseDouble(normTextField.getText());
			seed = Integer.parseInt(seedTextField.getText());
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}

		if (iterations < 0) iterations = 1;
		if (normalizationFactor < 0.01) normalizationFactor = 0.01;
		if (theta < 0.01) theta = 0.01;
		if (iterations > 10000) iterations = 10000;
		if (normalizationFactor > 0.99) normalizationFactor = 0.99;
		if (theta > 0.99) theta = 0.99;

        // Create an instance of the JGraphT force-directed algorithm.
        IndexedFRLayoutAlgorithm2D<Integer, DefaultEdge> algorithm = new IndexedFRLayoutAlgorithm2D<>(
			iterations, theta, normalizationFactor, new Random(seed));
        algorithm.layout(graph, layoutModel);

        Map<Integer, double[]> positions = new HashMap<>();
        for (Integer vertex : graph.vertexSet()) {
            Point2D point = layoutModel.get(vertex);
            positions.put(vertex, new double[]{ point.getX(), point.getY() });
        }

		// Normalize positions to 0.1*WIDTH and 0.1*HEIGHT to 0.9*WIDTH and 0.9*HEIGHT
		// Get min max x and y values
		double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
		for (double[] position : positions.values()) {
			minX = Math.min(minX, position[0]);
			maxX = Math.max(maxX, position[0]);
			minY = Math.min(minY, position[1]);
			maxY = Math.max(maxY, position[1]);
		}
		// Normalize to 0.1*WIDTH and 0.1*HEIGHT to 0.9*WIDTH and 0.9*HEIGHT
		for (double[] position : positions.values()) {
			position[0] = (position[0] - minX) / (maxX - minX) * 0.8 * WIDTH + 0.1 * WIDTH;
			position[1] = (position[1] - minY) / (maxY - minY) * 0.8 * HEIGHT + 0.1 * HEIGHT;
		}
        return positions;
    }
}
