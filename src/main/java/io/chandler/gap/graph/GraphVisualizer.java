package io.chandler.gap.graph;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.stage.FileChooser;
import javafx.scene.control.TextInputDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.alg.drawing.Box3D;
import io.chandler.gap.alg.drawing.LayoutModel3D;
import io.chandler.gap.alg.drawing.MapLayoutModel3D;
import io.chandler.gap.alg.drawing.IndexedFRLayoutAlgorithm3D;
import io.chandler.gap.alg.drawing.Point3D;
import io.chandler.gap.graph.genus.MultiGenus;

public class GraphVisualizer extends Application {

    //private static final int WIDTH = 1024;
    //private static final int HEIGHT = 768;
    private static final double NODE_RADIUS = 20;

    private String filePath;
    private List<String> graphLines;
    private int currentGraphIndex = 0;
    // Add a map to store edge frequencies; keys are in the form "min-max".
    private Map<String, Integer> edgeFrequencyMap;
    private Map<Integer, Integer> vertexFrequencyMap;

    private Pane graphPane;

    // Add new instance variables for layout configuration:
    private TextField seedTextField, itersTextField, thetaTextField, normTextField;
    private ComboBox<String> layoutChoiceBox;
	private Label numSharedLinesLabel;
    private Button genusButton;
    private Label fitLabel;
    // New checkbox to toggle the display of circles
    private CheckBox showCirclesCheckBox;
    // Remove the slider and add a trackball control for full 3D rotation.
    private Pane trackballPane;
    // Rotation angles (in radians) around X and Y axes.
    private double rotationX = 0;
    private double rotationY = 0;
    // For tracking mouse movement in the trackball.
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    
    // Scale parameter for zooming
    private double scale = 1.0;

    // Variables for caching the base coordinates using a composite key.
    private String cachedGraphKey = null;
    private Map<Integer, double[]> cachedBasePositions = new HashMap<>();

    // Add a new checkbox for showing direction
    private CheckBox showDirectionCheckBox;

    // Add new instance variables for interactive arrow updates:
    private List<DirectedArrow> directedArrows = new ArrayList<>();
    private Map<Integer, Circle> currentVertexCircleMap = new HashMap<>();

    public static void main(String[] args) {
        if (args.length > 0) {
            launch(args);
        } else {
            launch(new String[] { "/home/cjgriscom/Programming/GroupExplorer/PlanarStudyMulti/eliac/dual 4-cycles-triple 2-cycles-filtered.txt" });
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
        graphPane = new Pane();
        root.setCenter(graphPane);

        // Bind the graphPane size to the scene size
        graphPane.prefWidthProperty().bind(root.widthProperty());
        graphPane.prefHeightProperty().bind(root.heightProperty().subtract(100)); // Adjust for other UI elements

        // Add scroll event to adjust zoom scale using the scroll wheel
        graphPane.setOnScroll(e -> {
            double delta = e.getDeltaY();
            if (delta > 0) {
                scale += 0.05;
            } else if (delta < 0) {
                scale -= 0.05;
            }
            if (scale < 0.5) scale = 0.5;
            if (scale > 2.0) scale = 2.0;
            graphPane.setScaleX(scale);
            graphPane.setScaleY(scale);
        });

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
        genusButton = new Button("Genus: ?");
        fitLabel = new Label("");
        // Create a trackball control for full 3D rotation.
        trackballPane = new Pane();
        trackballPane.setPrefSize(50, 50);
        trackballPane.setStyle("-fx-background-color: lightgray; -fx-border-color: black;");
        // When the user presses the mouse, record the starting position.
        trackballPane.setOnMousePressed(e -> {
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
        });
        // When dragging, compute the deltas and update rotation angles.
        trackballPane.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - lastMouseX;
            double deltaY = e.getSceneY() - lastMouseY;
            // Adjust these factors to control sensitivity.
            rotationY += deltaX * 0.01;
            rotationX += deltaY * 0.01;
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
            updateGraph(graphPane, pageLabel);
        });
                
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
                updateGraphInfo(graphLines.get(currentGraphIndex));
            }
        });


        layoutChoiceBox = new ComboBox<>();
        layoutChoiceBox.getItems().addAll("Python Spring", "Python Planar", "Java Spring", "Java 3D", "Java Networkx", "Axis Constrained", "Axis Constrained Multi", "Planar Puzzle");
        layoutChoiceBox.setValue("Java Spring");  // default choice
        layoutChoiceBox.setOnAction(e -> updateGraph(graphPane, pageLabel));
        // Create the "Show Circles" checkbox. When toggled, updateGraph() is called.
        showCirclesCheckBox = new CheckBox("Show Circles");
        showCirclesCheckBox.setSelected(true);
        // Add the new checkbox for showing direction
        showDirectionCheckBox = new CheckBox("Show Direction");
        showDirectionCheckBox.setSelected(false); // Default to not showing direction
        showDirectionCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));
        HBox layoutControls = new HBox(10, new Label("Layout:"), layoutChoiceBox, new Label("Seed:"), seedTextField, new Label("Iters:"), itersTextField, new Label("Theta Factor:"), thetaTextField, new Label("Norm Factor:"), normTextField, showCirclesCheckBox, showDirectionCheckBox, randomizeButton, loadButton, trackballPane);
        layoutControls.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setTop(layoutControls);

        HBox paginator = new HBox(10, prevButton, pageLabel, nextButton);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        
        // Add the "Remove Duplicates" button
        Button removeDupButton = new Button("Remove Duplicates");
        removeDupButton.setOnAction(e -> {
            removeDuplicates();
            currentGraphIndex = 0;
            if (graphLines.size() > 0) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(graphLines.get(currentGraphIndex));
            } else {
                // Clear the graphPane
                graphPane.getChildren().clear();
            }
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
            if (graphLines.size() > 0) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(graphLines.get(currentGraphIndex));
            } else {
                // Clear the graphPane
                graphPane.getChildren().clear();
            }
        });

        // Add the "Filter Share Lines..." button.
        Button filterShareButton = new Button("Filter Share Lines...");
        filterShareButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("");
			dialog.resizableProperty().set(true);
			dialog.setWidth(400);
			dialog.setHeight(200);
            dialog.setTitle("Filter by Shared Lines");
            dialog.setHeaderText("Enter the number of shared lines required:");
            dialog.setContentText("Number:");
            dialog.showAndWait().ifPresent(input -> {
                try {
                    int target = Integer.parseInt(input);
                    // Filter the graphLines list based on the shared line count.
                    List<String> filteredLines = new ArrayList<>();
                    for (String line : graphLines) {
                        // Calling buildGraphFromLine will update the edgeFrequencyMap.
                        buildGraphFromLine(line);
                        int shared = countSharedLines();
                        if (shared == target) {
                            filteredLines.add(line);
                        }
                    }
                    graphLines = filteredLines;
                    currentGraphIndex = 0;
                    if (graphLines.size() > 0) {
                        updateGraph(graphPane, pageLabel);
                        updateGraphInfo(graphLines.get(currentGraphIndex));
                    } else {
                        // Clear the graphPane
                        graphPane.getChildren().clear();
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid number input for filtering shared lines.");
                }
            });
        });

        paginator.getChildren().addAll(removeDupButton, removeFoldedButton, filterShareButton, numSharedLinesLabel, genusButton, fitLabel);

        root.setBottom(paginator);

        // Set button actions to update the displayed graph.
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                currentGraphIndex--;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(graphLines.get(currentGraphIndex));
            }
        });

        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphLines.size() - 1) {
                currentGraphIndex++;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(graphLines.get(currentGraphIndex));
            }
        });
        // Render the first graph.
        // Once layout is settled

        ChangeListener<Bounds> initListener = new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> obs, Bounds oldBounds, Bounds newBounds) {
                if (newBounds.getWidth() > 100 && newBounds.getHeight() > 100) {
                    updateGraph(graphPane, pageLabel);
                    updateGraphInfo(graphLines.get(currentGraphIndex));
                    graphPane.layoutBoundsProperty().removeListener(this);
                }
            }
        };

        graphPane.layoutBoundsProperty().addListener(initListener);

        Scene scene = new Scene(root, 1024, 768);
        primaryStage.setTitle("Planar Graph Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateGraphInfo(String currentLine) {
        int[][][] generator = GroupExplorer.parseOperationsArr(currentLine);
        int maxVertex = 0;
        for (int[][] cycle : generator) {
            for (int[] polygon : cycle) {
                maxVertex = Math.max(maxVertex, polygon[polygon.length - 1]);
            }
        }
        
        numSharedLinesLabel.setText("Shared Lines: " + countSharedLines());
        String genus = "?";
        
        // Compute genus range 0-1 if it's not a huge graph 
        if (maxVertex < 128) genus = MultiGenus.computeGenusFromGenerators(
            Arrays.<int[][][]>asList(generator),
            MultiGenus.MultiGenusOption.LIMIT_TO_GENUS_1).get(0) + "";
        
        genusButton.setText("Genus: " + (genus.equals("-1") ? ">= 2" : genus));

        // Allow the user to recompute the genus with no limit
        genusButton.setOnAction((e) -> {
            // Recompute with no genus limit
            int genusR = MultiGenus.computeGenusFromGenerators(
                Arrays.<int[][][]>asList(generator)).get(0);
            genusButton.setText("Genus: " + genusR);
        });
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
                if (polygon.length == 2) {
                    // If the polygon is a line (2 points), create a quadrilateral
                    int a = polygon[0];
                    int b = polygon[1];
                    double[] sourcePos = positions.get(a);
                    double[] targetPos = positions.get(b);

                    // Calculate the direction vector of the line
                    double dx = targetPos[0] - sourcePos[0];
                    double dy = targetPos[1] - sourcePos[1];
                    double length = Math.sqrt(dx * dx + dy * dy);

                    // Normalize the direction vector
                    double nx = dx / length;
                    double ny = dy / length;

                    // Calculate the perpendicular vector with a 4px margin
                    double px = -ny * 4;
                    double py = nx * 4;

                    // Define the four corners of the quadrilateral
                    double[] p1 = {sourcePos[0] + px, sourcePos[1] + py};
                    double[] p2 = {sourcePos[0] - px, sourcePos[1] - py};
                    double[] p3 = {targetPos[0] - px, targetPos[1] - py};
                    double[] p4 = {targetPos[0] + px, targetPos[1] + py};

                    shadedPoly.getPoints().addAll(
                        p1[0], p1[1],
                        p2[0], p2[1],
                        p3[0], p3[1],
                        p4[0], p4[1]
                    );
                } else {
                    // Otherwise, draw the polygon as usual
                    for (int vertex : polygon) {
                        double[] pos = positions.get(vertex);
                        if (pos != null) {
                            shadedPoly.getPoints().addAll(pos[0], pos[1]);
                        }
                    }
                }
                shadedPoly.setFill(fillColor);
                shadedPoly.setStroke(null);
                // Add the polygon to the pane first so it appears beneath nodes/edges.
                pane.getChildren().add(shadedPoly);
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
        for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
            int vertex = entry.getKey();
            double[] pos = entry.getValue();
            Circle circle = new Circle(pos[0], pos[1], NODE_RADIUS);
            circle.visibleProperty().bind(showCirclesCheckBox.selectedProperty());
            // Color the vertex based on its frequency.
            int freq = vertexFrequencyMap.getOrDefault(vertex, 1);
			Color color = getVertexColor(freq);
			// Set opacity
			color = Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
            circle.setFill(color);
            Text text = new Text(pos[0] - NODE_RADIUS/2, pos[1] + NODE_RADIUS/2, vertex+"");
            vertexCircleMap.put(vertex, circle);
            vertexLabelMap.put(vertex, text);

            final int v = vertex; // capture vertex id
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

                // Update shaded polygons.
                updateShadedPolygons(vertexCircleMap, shadedPolygons);
                // Now update directed arrows.
                updateDirectedArrows();
            });

            pane.getChildren().addAll(text, circle);
        }
        // Cache the current vertex circle mapping for arrow updates.
        currentVertexCircleMap = vertexCircleMap;
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
        Map<Integer, double[]> positions;
        // Build a composite key based on the current line and all layout parameters.
        String newCacheKey = currentLine + "_" + seedTextField.getText() + "_" + itersTextField.getText() 
                             + "_" + thetaTextField.getText() + "_" + normTextField.getText()
                             + "_" + layoutChoiceBox.getValue();
        if (cachedGraphKey == null || !cachedGraphKey.equals(newCacheKey)) {
            double[] fitOut = new double[]{-1};
            cachedBasePositions = getCoordinates(currentLine, currentGraph, fitOut);
            cachedGraphKey = newCacheKey;
            if (fitOut[0] != -1) {
                fitLabel.setText("Fit: " + String.format("%.5f", fitOut[0]));
            } else {
                fitLabel.setText("");
            }
        }
        // Make a fresh copy of the cached base coordinates.
        positions = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : cachedBasePositions.entrySet()) {
            double[] coord = entry.getValue();
            double[] copy = new double[coord.length];
            System.arraycopy(coord, 0, copy, 0, coord.length);
            positions.put(entry.getKey(), copy);
        }

        // If positions are 3D, apply the trackball rotation (using rotationX and rotationY) then re-center.
        if (!positions.isEmpty()) {
            int dim = positions.values().iterator().next().length;
            if (dim == 3) {
                double angleX = rotationX; // rotation about horizontal axis
                double angleY = rotationY; // rotation about vertical axis
                for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
                    double[] pos = entry.getValue();
                    double x = pos[0], y = pos[1], z = pos[2];
                    // First, rotate around the x-axis:
                    double y1 = Math.cos(angleX) * y - Math.sin(angleX) * z;
                    double z1 = Math.sin(angleX) * y + Math.cos(angleX) * z;
                    // Next, rotate around the y-axis:
                    double rotatedX = Math.cos(angleY) * x + Math.sin(angleY) * z1;
                    double rotatedY = y1;
                    pos[0] = rotatedX;
                    pos[1] = rotatedY;
                }

                // Compute the average x and y to center the graph.
                double sumX = 0, sumY = 0;
                int count = positions.size();
                for (double[] pos : positions.values()){
                    sumX += pos[0];
                    sumY += pos[1];
                }
                double avgX = sumX / count;
                double avgY = sumY / count;

                // Calculate the translation offset to center the graph in the pane.
                double offsetX = graphPane.getWidth() / 2.0 - avgX;
                double offsetY = graphPane.getHeight() / 2.0 - avgY;
                for (double[] pos : positions.values()){
                    pos[0] += offsetX;
                    pos[1] += offsetY;
                }
            }
        }
        
        if (positions != null) {
            graphPane.getChildren().clear();
            // Reset directed arrows so they can be re-created
            directedArrows = new ArrayList<>();
            pageLabel.setText("Graph " + (currentGraphIndex + 1) + " / " + graphLines.size());
            drawPlanarGraph(currentGraph, graphPane, currentLine, positions);
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
    private Map<Integer, double[]> getCoordinates(String line, Graph<Integer, DefaultEdge> graph, double[] fitOut) {
        String method = layoutChoiceBox.getValue();
        if ("Java Spring".equals(method)) {
            return getJavaSpringCoordinates(graph);
        } else if ("Java 3D".equals(method)) {
            return getJava3DCoordinates(graph);
        } else if ("Java Networkx".equals(method)) {
            return getJavaNetworkxCoordinates(graph);
        } else if ("Axis Constrained".equals(method)) {
            return getJavaAxisConstrainedCoordinates(line, false, fitOut);
        } else if ("Axis Constrained Multi".equals(method)) {
            return getJavaAxisConstrainedCoordinates(line, true, fitOut);
        } else if ("Planar Puzzle".equals(method)) {
            return getJavaPlanarPuzzleCoordinates(line, fitOut);
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
                double x = coords.getDouble(0) * graphPane.getWidth();
                double y = coords.getDouble(1) * graphPane.getWidth();
                double z = coords.getDouble(2) * graphPane.getWidth();
                positions.put(Integer.parseInt(key), new double[]{x, y, z});
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

    /**
     * Uses the JGraphT IndexedFRLayoutAlgorithm2D (a variant of Fruchterman-Reingold) to compute positions.
     */
    private Map<Integer, double[]> getJavaSpringCoordinates(Graph<Integer, DefaultEdge> graph) {
        // Create a layout model; here we use the same WIDTH/HEIGHT as defined.
        LayoutModel2D<Integer> layoutModel = new MapLayoutModel2D<Integer>(
            new Box2D(graphPane.getWidth()*2, graphPane.getHeight()*2));

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
        double width = graphPane.getWidth();
        double height = graphPane.getHeight();
		// Normalize to 0.1*WIDTH and 0.1*HEIGHT to 0.9*WIDTH and 0.9*HEIGHT
		for (double[] position : positions.values()) {
			position[0] = (position[0] - minX) / (maxX - minX) * 0.8 * width + 0.1 * width;
			position[1] = (position[1] - minY) / (maxY - minY) * 0.8 * height + 0.1 * height;
		}
        return positions;
    }

    /**
     * Uses the IndexedFRLayoutAlgorithm3D to compute 3D positions.
     */
    private Map<Integer, double[]> getJava3DCoordinates(Graph<Integer, DefaultEdge> graph) {
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
        
        // Create a 3D layout model with a cube volume.
        double height = graphPane.getHeight();
        Box3D box3d = new Box3D(0,0,0, height*5, height*5, height*5);
        LayoutModel3D<Integer> layoutModel = new MapLayoutModel3D<>(box3d);
        
        // Create an instance of the new 3D algorithm.
        IndexedFRLayoutAlgorithm3D<Integer, DefaultEdge> algorithm3D =
            new IndexedFRLayoutAlgorithm3D<>(iterations, theta, normalizationFactor, new Random(seed));
        algorithm3D.layout(graph, layoutModel);
        
        // Extract positions from the layout model.
        Map<Integer, double[]> positions = new HashMap<>();
        for (Integer vertex : graph.vertexSet()) {
            Point3D point = layoutModel.get(vertex);
            positions.put(vertex, new double[]{ point.getX(), point.getY(), point.getZ() });
        }
        
        // Normalize positions for 2D rendering.
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        for (double[] pos : positions.values()) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
            minZ = Math.min(minZ, pos[2]);
            maxZ = Math.max(maxZ, pos[2]);
        }
        double minDim = Math.min(graphPane.getWidth(), graphPane.getHeight());
        for (double[] pos : positions.values()) {
            if (maxX - minX > 0)
                pos[0] = (pos[0] - minX) / (maxX - minX) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[0] = 0.5 * minDim;
            if (maxY - minY > 0)
                pos[1] = (pos[1] - minY) / (maxY - minY) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[1] = 0.5 * minDim;
            if (maxZ - minZ > 0)
                pos[2] = (pos[2] - minZ) / (maxZ - minZ) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[2] = 0.5 * minDim;
        }
        return positions;
    }

    private Map<Integer, double[]> getJavaNetworkxCoordinates(Graph<Integer, DefaultEdge> graph) {
        StringBuilder edgeData = new StringBuilder();
        for (DefaultEdge edge : graph.edgeSet()) {
            int u = graph.getEdgeSource(edge);
            int v = graph.getEdgeTarget(edge);
            edgeData.append(u).append(",").append(v).append(";");
        }
        if (edgeData.length() > 0) {
            edgeData.setLength(edgeData.length() - 1);
        }
        String seedArg = seedTextField.getText();
        if (seedArg.isEmpty()) {
            seedArg = "42";
        }
        long seedVal = Long.parseLong(seedArg);
        // Call the static computeLayout method from the NetworkxLayout class in the networkx package
        Map<Integer, double[]> positions = networkx.NetworkxLayout.computeLayout(edgeData.toString(), Integer.parseInt(itersTextField.getText()), 3, seedVal);

        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        for (double[] pos : positions.values()) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
            minZ = Math.min(minZ, pos[2]);
            maxZ = Math.max(maxZ, pos[2]);
        }
        double minDim = Math.min(graphPane.getWidth(), graphPane.getHeight());
        for (double[] pos : positions.values()) {
            if (maxX - minX > 0)
                pos[0] = (pos[0] - minX) / (maxX - minX) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[0] = 0.5 * minDim;
            if (maxY - minY > 0)
                pos[1] = (pos[1] - minY) / (maxY - minY) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[1] = 0.5 * minDim;
            if (maxZ - minZ > 0)
                pos[2] = (pos[2] - minZ) / (maxZ - minZ) * 0.8 * minDim + 0.1 * minDim;
            else
                pos[2] = 0.5 * minDim;
        }
        return positions;
    }

    private Map<Integer, double[]> getJavaAxisConstrainedCoordinates(String line, boolean multi, double[] fit) {
        long seedVal = Long.parseLong(seedTextField.getText());
        Map<Integer, double[]> positions;
        if (multi) {
            positions = networkx.AxisConstrainedLayoutMulti.computeLayout(line, Integer.parseInt(itersTextField.getText()), seedVal, this.graphPane.getHeight(), fit);
        } else {
            positions = networkx.AxisConstrainedLayout.computeLayout(line, Integer.parseInt(itersTextField.getText()), seedVal, this.graphPane.getHeight(), fit);
        }
        return positions;
    }

    private Map<Integer, double[]> getJavaPlanarPuzzleCoordinates(String line, double[] fit) {
        long seedVal = Long.parseLong(seedTextField.getText());
        Map<Integer, double[]> positions = networkx.ConcentricConstrainedLayout.computeLayout(line, Integer.parseInt(itersTextField.getText()), seedVal, this.graphPane.getHeight(), fit);
        return positions;
    }

    private DirectedArrow createDirectedArrow(Pane pane, double[] sourcePos, double[] targetPos, int source, int target) {
        double arrowLength = 10;
        double arrowWidth = 5;
        double midX = (sourcePos[0] + targetPos[0]) / 2;
        double midY = (sourcePos[1] + targetPos[1]) / 2;
        double dx = targetPos[0] - sourcePos[0];
        double dy = targetPos[1] - sourcePos[1];
        double angle = Math.atan2(dy, dx);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double x1 = midX - arrowLength * cos + arrowWidth * sin;
        double y1 = midY - arrowLength * sin - arrowWidth * cos;
        double x2 = midX - arrowLength * cos - arrowWidth * sin;
        double y2 = midY - arrowLength * sin + arrowWidth * cos;
        Line a1 = new Line(midX, midY, x1, y1);
        Line a2 = new Line(midX, midY, x2, y2);
        pane.getChildren().addAll(a1, a2);
        return new DirectedArrow(source, target, a1, a2);
    }

    private void updateDirectedArrows() {
        if (currentVertexCircleMap == null) return;
        for (DirectedArrow arrow : directedArrows) {
            Circle sourceCircle = currentVertexCircleMap.get(arrow.source);
            Circle targetCircle = currentVertexCircleMap.get(arrow.target);
            if (sourceCircle != null && targetCircle != null) {
                double[] srcPos = new double[] { sourceCircle.getCenterX(), sourceCircle.getCenterY() };
                double[] tgtPos = new double[] { targetCircle.getCenterX(), targetCircle.getCenterY() };
                arrow.update(srcPos, tgtPos);
            }
        }
    }

    private static class DirectedArrow {
        int source;
        int target;
        Line arrow1;
        Line arrow2;

        DirectedArrow(int source, int target, Line arrow1, Line arrow2) {
            this.source = source;
            this.target = target;
            this.arrow1 = arrow1;
            this.arrow2 = arrow2;
        }

        void update(double[] srcPos, double[] tgtPos) {
            double arrowLength = 10;
            double arrowWidth = 5;
            double midX = (srcPos[0] + tgtPos[0]) / 2;
            double midY = (srcPos[1] + tgtPos[1]) / 2;
            double dx = tgtPos[0] - srcPos[0];
            double dy = tgtPos[1] - srcPos[1];
            double angle = Math.atan2(dy, dx);
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            double x1 = midX - arrowLength * cos + arrowWidth * sin;
            double y1 = midY - arrowLength * sin - arrowWidth * cos;
            double x2 = midX - arrowLength * cos - arrowWidth * sin;
            double y2 = midY - arrowLength * sin + arrowWidth * cos;
            arrow1.setStartX(midX);
            arrow1.setStartY(midY);
            arrow1.setEndX(x1);
            arrow1.setEndY(y1);
            arrow2.setStartX(midX);
            arrow2.setStartY(midY);
            arrow2.setEndX(x2);
            arrow2.setEndY(y2);
        }
    }
}
