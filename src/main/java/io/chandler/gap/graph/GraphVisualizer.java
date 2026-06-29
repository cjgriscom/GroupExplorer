package io.chandler.gap.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.drawing.IndexedFRLayoutAlgorithm2D;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.PbinFile;
import io.chandler.gap.graph.genus.MultiGenus;
import io.chandler.gap.graph.layoutalgos.AxisConstrainedLayout;
import io.chandler.gap.graph.layoutalgos.AxisConstrainedLayoutMulti;
import io.chandler.gap.graph.layoutalgos.ConcentricConstrainedLayout;
import io.chandler.gap.graph.layoutalgos.GridLayout;
import io.chandler.gap.graph.layoutalgos.Java3D;
import io.chandler.gap.graph.layoutalgos.SATLayout;
import io.chandler.gap.graph.layoutalgos.JavaNetworkx;
import io.chandler.gap.graph.layoutalgos.JavaSpring;
import io.chandler.gap.graph.layoutalgos.LayoutAlgo;
import io.chandler.gap.graph.layoutalgos.LayoutAlgoArg;
import io.chandler.gap.graph.layoutalgos.LayoutCongestion;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.binding.Bindings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;

public class GraphVisualizer extends Application {

    //private static final int WIDTH = 1024;
    //private static final int HEIGHT = 768;
    private static final double NODE_RADIUS = 20;

    private String filePath;
    private List<String> graphLines = Collections.emptyList();
    private PbinFile pbinFile;
    private int currentGraphIndex = 0;
    // Add a map to store edge frequencies; keys are in the form "min-max".
    private Map<String, Integer> edgeFrequencyMap;
    private Map<Integer, Integer> vertexFrequencyMap;

    private Pane graphPane;

    private TextField seedTextField, itersTextField, thetaTextField, normTextField, triesTextField, initialItersTextField, repulsionFactorTextField, wTextField, hTextField, solutionTextField;
    // editable paginator index field (k in k / n)
    private TextField pageIndexTextField;
    private Label seedLabel, itersLabel, thetaLabel, normLabel, triesLabel, initialItersLabel, repulsionFactorLabel, wLabel, hLabel, solutionLabel;
    private Button solutionPrevButton, solutionNextButton;
    private ComboBox<String> layoutChoiceBox;
	private Label numSharedLinesLabel;
    private Button genusButton;
    private Button autButton;
    private Label fitLabel;
    // New checkbox to toggle the display of circles
    private CheckBox showCirclesCheckBox;
    private CheckBox showFittedNodesCheckBox;
    private CheckBox allSolutionsCheckBox;
    private CheckBox snapToGridCheckBox;
    private CheckBox lockRotationCheckBox;
    private CheckBox showSquareGridCheckBox;
    private CheckBox showHexGridCheckBox; // triangular lattice of points
    private CheckBox showHoneycombGridCheckBox; // hexagonal honeycomb outlines
    // Trackball rotation state (accumulated 3x3 rotation matrix) and last sphere vector
    private double[][] trackballRotation = new double[][] { {1,0,0}, {0,1,0}, {0,0,1} };
    private Double lastMouseX = null;
    private Double lastMouseY = null;
    
    // Scale parameter for zooming
    private double scale = 1.0;

    // Variables for caching the base coordinates using a composite key.
    private String cachedGraphKey = null;
    private Map<Integer, double[]> cachedBasePositions = new HashMap<>();
    private Map<Integer, double[]> manualPositions = null;
    private String manualPositionsCacheKey = null;

    private CheckBox showDirectionCheckBox;

    private List<DirectedArrow> directedArrows = new ArrayList<>();
    private Map<Integer, Circle> currentVertexCircleMap = new HashMap<>();
    private Map<Integer, Text> currentVertexLabelMap = new HashMap<>();
    private Map<DefaultEdge, Line> currentEdgeLineMap = new HashMap<>();
    private Graph<Integer, DefaultEdge> currentGraph;
    private List<ShadedPolygonWrapper> currentShadedPolygons = new ArrayList<>();
    private Set<Integer> selectedVertices = new HashSet<>();
    private Rectangle marqueeRectangle;
    private double marqueeStartX;
    private double marqueeStartY;
    private boolean marqueeActive = false;

    private CheckBox suppressRenderCheckBox;
    private boolean suppressRender = false;
    private boolean renderQueued = false;

    private Map<String, LayoutAlgo> layoutAlgoMap = new LinkedHashMap<>();
    {
        layoutAlgoMap.put("Java Spring", new JavaSpring());
        layoutAlgoMap.put("Java 3D", new Java3D());
        layoutAlgoMap.put("Java Networkx", new JavaNetworkx());
        layoutAlgoMap.put("Axis Constrained", new AxisConstrainedLayout());
        layoutAlgoMap.put("Axis Constrained Multi", new AxisConstrainedLayoutMulti());
        layoutAlgoMap.put("SAT Layout", new SATLayout());
        layoutAlgoMap.put("Planar Puzzle", new ConcentricConstrainedLayout());
        layoutAlgoMap.put("Grid Solver", new GridLayout());
    }
    private final String defaultLayout = "Java Networkx";

    // Observable list to track required arguments for the current algorithm
    private ObservableList<LayoutAlgoArg> requiredArgs = FXCollections.observableArrayList();

    private void bindVisibility(LayoutAlgoArg arg, Node... node) {
        for (Node n : node) {
            n.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> requiredArgs.contains(arg), requiredArgs));
            n.managedProperty().bind(n.visibleProperty());
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            launch(args);
        } else {
            String previewFile = "PlanarStudy/u4_2_2/l2-2-cycles-2-cycles-2-cycles_R2-filtered.txt";
            if (new File(previewFile).exists()) {
                launch(new String[] { previewFile });
            } else {
                launch(new String[]{});
            }            
        }
    }

    @Override
    public void stop() {
        closeGraphSource();
    }

    @Override
    public void start(Stage primaryStage) {
        if (getParameters().getRaw().size() > 0) {

            filePath = getParameters().getRaw().get(0);
            readGraphLinesFromFile(filePath);
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

        // Create paginator controls.
        Button prevButton = new Button("Previous");
        Button nextButton = new Button("Next");
        // Editable index field followed by total count label: "k / n"
        pageIndexTextField = new TextField(String.valueOf(currentGraphIndex + 1));
        pageIndexTextField.setPrefWidth(60);
        Label pageLabel = new Label(" / " + graphLineCount());

        // Create layout configuration controls.
        seedTextField = new TextField("0");
        itersTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_ITERATIONS));
        triesTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_ITERATIONS));
        initialItersTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_ITERATIONS));
        normTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_NORMALIZATION_FACTOR));
        thetaTextField = new TextField(String.valueOf(IndexedFRLayoutAlgorithm2D.DEFAULT_THETA_FACTOR));
        repulsionFactorTextField = new TextField(String.valueOf(0.));
        wTextField = new TextField("20");
        hTextField = new TextField("20");
        solutionTextField = new TextField("0");
        
        seedLabel = new Label("Seed:");
        itersLabel = new Label("Iters:");
        triesLabel = new Label("Tries:");
        initialItersLabel = new Label("Initial Iters:");
        thetaLabel = new Label("Theta Factor:");
        normLabel = new Label("Norm Factor:");
        repulsionFactorLabel = new Label("Repulsion Factor:");
        wLabel = new Label("Grid Width:");
        hLabel = new Label("Grid Height:");
        solutionLabel = new Label("Solution:");

        showFittedNodesCheckBox = new CheckBox("Show Fitted Nodes");
        showFittedNodesCheckBox.setSelected(false);

        numSharedLinesLabel = new Label("Shared Lines: 0");
        genusButton = new Button("Genus: ?");
        autButton = new Button("Aut: ?");
        fitLabel = new Label("");

        // Create a trackball control for full 3D rotation.
        graphPane.setOnMousePressed(e -> {
            if (lockRotationCheckBox != null && lockRotationCheckBox.isSelected()) {
                lastMouseX = null;
                lastMouseY = null;
                return;
            }
            if (!e.isSecondaryButtonDown()) {
                lastMouseX = null;
                lastMouseY = null;
                return;
            } else {
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                e.consume();
            }
        });
        graphPane.setOnMouseDragged(e -> {
            if (lockRotationCheckBox != null && lockRotationCheckBox.isSelected()) {
                return;
            }
            if (lastMouseX == null || lastMouseY == null) {
                return;
            }
            double deltaX = e.getSceneX() - lastMouseX;
            double deltaY = e.getSceneY() - lastMouseY;
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
            deltaX = -deltaX;

            // Apply yaw (around Y) for horizontal drag, and pitch (around X) for vertical drag
            double sensitivity = 0.01; // radians per pixel
            double[][] Ry = rotationFromAxisAngle(0, 1, 0, deltaX * sensitivity);
            double[][] Rx = rotationFromAxisAngle(1, 0, 0, deltaY * sensitivity);
            // New rotation applied in world axes order: first yaw then pitch
            trackballRotation = multiply3(Rx, multiply3(Ry, trackballRotation));

            updateGraph(graphPane, pageLabel);
        });

        graphPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isPrimaryButtonDown() && e.isShiftDown()) {
                marqueeActive = true;
                javafx.geometry.Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                marqueeStartX = local.getX();
                marqueeStartY = local.getY();
                if (marqueeRectangle == null) {
                    marqueeRectangle = new Rectangle();
                    marqueeRectangle.setFill(Color.DODGERBLUE.deriveColor(0, 1, 1, 0.15));
                    marqueeRectangle.setStroke(Color.DODGERBLUE);
                    marqueeRectangle.setStrokeWidth(1.5);
                    marqueeRectangle.setMouseTransparent(true);
                }
                marqueeRectangle.setX(marqueeStartX);
                marqueeRectangle.setY(marqueeStartY);
                marqueeRectangle.setWidth(0);
                marqueeRectangle.setHeight(0);
                if (!graphPane.getChildren().contains(marqueeRectangle)) {
                    graphPane.getChildren().add(marqueeRectangle);
                }
                marqueeRectangle.toFront();
                e.consume();
                return;
            }
            if (e.isPrimaryButtonDown() && !e.isShiftDown()) {
                Node target = e.getTarget() instanceof Node ? (Node) e.getTarget() : null;
                if (target instanceof Circle) {
                    Circle c = (Circle) target;
                    if (isInteractiveVertexCircle(c)) {
                        Integer vertex = findVertexForCircle(c);
                        if (vertex != null && selectedVertices.contains(vertex)) {
                            return;
                        }
                    }
                }
                selectedVertices.clear();
                refreshVertexHighlights();
            }
        });
        graphPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!marqueeActive || marqueeRectangle == null) {
                return;
            }
            javafx.geometry.Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = Math.min(marqueeStartX, local.getX());
            double y = Math.min(marqueeStartY, local.getY());
            marqueeRectangle.setX(x);
            marqueeRectangle.setY(y);
            marqueeRectangle.setWidth(Math.abs(local.getX() - marqueeStartX));
            marqueeRectangle.setHeight(Math.abs(local.getY() - marqueeStartY));
            e.consume();
        });
        graphPane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (marqueeActive && e.getButton() == MouseButton.PRIMARY) {
                finishMarqueeSelection();
                e.consume();
            }
        });
                
        // Create solution navigation buttons
        solutionPrevButton = new Button("<");
        solutionNextButton = new Button(">");
        solutionPrevButton.setOnAction(e -> {
            int current = Integer.parseInt(solutionTextField.getText());
            if (current > 0) {
                solutionTextField.setText(String.valueOf(current - 1));
                updateGraph(graphPane, pageLabel);
            }
        });
        solutionNextButton.setOnAction(e -> {
            int current = Integer.parseInt(solutionTextField.getText());
            solutionTextField.setText(String.valueOf(current + 1));
            updateGraph(graphPane, pageLabel);
        });
        solutionPrevButton.setPrefWidth(30);
        solutionNextButton.setPrefWidth(30);

        // Configure the text fields (set width and key listeners)
        for (TextField textField : new TextField[] {seedTextField, itersTextField, normTextField, thetaTextField, triesTextField, initialItersTextField, repulsionFactorTextField, wTextField, hTextField, solutionTextField}) {
            textField.setPrefWidth(50);
            textField.setOnKeyReleased(value -> {
                updateGraph(graphPane, pageLabel);
            });
        }

        showFittedNodesCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateGraph(graphPane, pageLabel);
        });

        Button randomizeButton = new Button("Randomize");
        randomizeButton.setOnAction(e -> {
            seedTextField.setText(String.valueOf(new Random().nextInt(10000)));
            updateGraph(graphPane, pageLabel);
        });

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Generator Files", "*.txt", "*.pbin"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("PBIN Files", "*.pbin")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                readGraphLinesFromFile(selectedFile.getAbsolutePath());
                currentGraphIndex = 0;
                // Update total label and index field
                pageLabel.setText(" / " + graphLineCount());
                pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            }
        });

        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportGraphLines(primaryStage));

        Button editCoordinatesButton = new Button("Edit Coordinates");
        editCoordinatesButton.setOnAction(e -> showEditCoordinatesDialog(primaryStage, graphPane, pageLabel));

        Button copy3DCoordinatesButton = new Button("3D Coordinates");
        copy3DCoordinatesButton.setOnAction(e -> show3DCoordinatesDialog(primaryStage));

        Button controlsButton = new Button("Controls");
        controlsButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Controls");
            alert.setHeaderText(null);
            alert.setContentText("Left Click + Drag: Adjust Graph Points\nShift + Left Click + Drag: Select Multiple Points\nRight Click + Drag: Rotate 3D view (disabled when Lock Rotation is on)");
            alert.showAndWait();
        });

        // Create layoutChoiceBox and other related checkboxes.
        layoutChoiceBox = new ComboBox<>();
        layoutChoiceBox.getItems().addAll(layoutAlgoMap.keySet());
        layoutChoiceBox.setValue(defaultLayout);  // default choice
        layoutChoiceBox.setOnAction(e -> {
            updateArgsVisibility();
            updateGraph(graphPane, pageLabel);
        });

        showCirclesCheckBox = new CheckBox("Show Circles");
        showCirclesCheckBox.setSelected(true);

        snapToGridCheckBox = new CheckBox("Snap to Grid");
        snapToGridCheckBox.setSelected(false);

        lockRotationCheckBox = new CheckBox("Lock Rotation");
        lockRotationCheckBox.setSelected(false);
        lockRotationCheckBox.setOnAction(e -> {
            String currentLine = getGraphLine(currentGraphIndex);
            if (lockRotationCheckBox.isSelected()) {
                if (circlesMatchGraph(buildGraphFromLine(currentLine))) {
                    syncLockedPositions(currentLine);
                }
            } else if (manualPositions != null && manualPositionsCacheKey != null
                    && manualPositionsCacheKey.startsWith("lock:")) {
                if (circlesMatchGraph(buildGraphFromLine(currentLine))) {
                    manualPositions = capturePositionsFromCircles();
                }
                manualPositionsCacheKey = buildLayoutCacheKey(currentLine);
            }
        });

        showSquareGridCheckBox = new CheckBox("Show Square Grid");
        showSquareGridCheckBox.setSelected(false);
        showSquareGridCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));

        // Triangular lattice of points.
        showHexGridCheckBox = new CheckBox("Show Triangular Grid");
        showHexGridCheckBox.setSelected(false);
        showHexGridCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));

        // Hexagonal honeycomb outlines.
        showHoneycombGridCheckBox = new CheckBox("Show Hexagonal Grid");
        showHoneycombGridCheckBox.setSelected(false);
        showHoneycombGridCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));

        showDirectionCheckBox = new CheckBox("Show Direction");
        showDirectionCheckBox.setSelected(false); // Default to not showing direction
        showDirectionCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));

        // All solutions checkbox (for algorithms that support it)
        allSolutionsCheckBox = new CheckBox("All Solutions");
        allSolutionsCheckBox.setSelected(false);
        allSolutionsCheckBox.setOnAction(e -> updateGraph(graphPane, pageLabel));

        // Suppress render checkbox
        suppressRenderCheckBox = new CheckBox("Suppress Render");
        suppressRenderCheckBox.setSelected(false);
        suppressRenderCheckBox.setOnAction(e -> {
            suppressRender = suppressRenderCheckBox.isSelected();
            if (!suppressRender && renderQueued) {
                renderQueued = false;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            }
        });

        // Create a top-bar HBox for the remaining controls.
        HBox topControls = new HBox(10, randomizeButton, loadButton, exportButton, editCoordinatesButton, copy3DCoordinatesButton, controlsButton);
        topControls.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setTop(topControls);

        // Create HBox for solution navigation
        HBox solutionBox = new HBox(5, solutionPrevButton, solutionTextField, solutionNextButton);
        solutionBox.setStyle("-fx-alignment: center-left;");

        // Create the sidebar VBox for layout configuration controls.
        VBox sidebar = new VBox(10,
            new Label("Layout:"), layoutChoiceBox,
            seedLabel, seedTextField,
            triesLabel, triesTextField,
            initialItersLabel, initialItersTextField,
            itersLabel, itersTextField,
            thetaLabel, thetaTextField,
            normLabel, normTextField,
            repulsionFactorLabel, repulsionFactorTextField,
            wLabel, wTextField,
            hLabel, hTextField,
            solutionLabel, solutionBox,
            showCirclesCheckBox,
            snapToGridCheckBox,
            lockRotationCheckBox,
            showSquareGridCheckBox,
            showHexGridCheckBox,
            showHoneycombGridCheckBox,
            showDirectionCheckBox,
            showFittedNodesCheckBox,
            allSolutionsCheckBox,
            suppressRenderCheckBox
        );
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-padding: 10;");
        root.setRight(sidebar);

        // Create the paginator controls.
        HBox paginator = new HBox(10, prevButton, pageIndexTextField, pageLabel, nextButton);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        
        // Add the "Remove Duplicates" button
        Button removeDupButton = new Button("Remove Duplicates");
        removeDupButton.setOnAction(e -> {
            removeDuplicates();
            currentGraphIndex = 0;
            if (graphLineCount() > 0) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
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
            for (int i = 0; i < graphLineCount(); i++) {
                String line = getGraphLine(i);
                if (!hasFoldedPolygons(line)) {
                    filteredLines.add(line);
                }
            }
            setGraphLines(filteredLines);
            currentGraphIndex = 0;
            if (graphLineCount() > 0) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            } else {
                // Clear the graphPane
                graphPane.getChildren().clear();
            }
        });

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
                    for (int i = 0; i < graphLineCount(); i++) {
                        String line = getGraphLine(i);
                        // Calling buildGraphFromLine will update the edgeFrequencyMap.
                        buildGraphFromLine(line);
                        int shared = countSharedLines();
                        if (shared == target) {
                            filteredLines.add(line);
                        }
                    }
                    setGraphLines(filteredLines);
                    currentGraphIndex = 0;
                    if (graphLineCount() > 0) {
                        updateGraph(graphPane, pageLabel);
                        updateGraphInfo(getGraphLine(currentGraphIndex));
                    } else {
                        // Clear the graphPane
                        graphPane.getChildren().clear();
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid number input for filtering shared lines.");
                }
            });
        });

        Button filterByFitButton = new Button("Filter by Fit");
        filterByFitButton.setOnAction(e -> showFilterByFitDialog(primaryStage, pageLabel));

        Button filterByCongestionButton = new Button("Congestion...");
        filterByCongestionButton.setOnAction(e -> showFilterByCongestionDialog(primaryStage, pageLabel));

        Button showGeneratorButton = new Button("Show Generator");
        showGeneratorButton.setOnAction(e -> showGeneratorDialog(primaryStage));

        paginator.getChildren().addAll(removeDupButton, removeFoldedButton, filterShareButton,
                filterByFitButton, filterByCongestionButton, showGeneratorButton,
                numSharedLinesLabel, genusButton, autButton, fitLabel);

        root.setBottom(paginator);

        // Set button actions to update the displayed graph.
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                currentGraphIndex--;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            }
        });

        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphLineCount() - 1) {
                currentGraphIndex++;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            }
        });

        // Allow typing an index (1-based) into the text field to jump directly
        pageIndexTextField.setOnKeyReleased(e -> {
            try {
                int idx = Integer.parseInt(pageIndexTextField.getText().trim());
                if (idx < 1) idx = 1;
                if (idx > graphLineCount()) idx = graphLineCount();
                currentGraphIndex = idx - 1;
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            } catch (NumberFormatException ex) {
                // Reset to current on invalid input
                //pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
            }
        });
        // Render the first graph.
        // Once layout is settled

        ChangeListener<Bounds> initListener = new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> obs, Bounds oldBounds, Bounds newBounds) {
                if (newBounds.getWidth() > 100 && newBounds.getHeight() > 100) {
                    updateArgsVisibility();
                    updateGraph(graphPane, pageLabel);
                    updateGraphInfo(getGraphLine(currentGraphIndex));
                    graphPane.layoutBoundsProperty().removeListener(this);
                }
            }
        };

        graphPane.layoutBoundsProperty().addListener(initListener);

        // Bind visibility to whether the requiredArgs contains the corresponding LayoutAlgoArg
        bindVisibility(LayoutAlgoArg.SEED, seedLabel, seedTextField);
        bindVisibility(LayoutAlgoArg.ITERS, itersLabel, itersTextField);
        bindVisibility(LayoutAlgoArg.THETA, thetaLabel, thetaTextField);
        bindVisibility(LayoutAlgoArg.NORM, normLabel, normTextField);
        bindVisibility(LayoutAlgoArg.TRIES, triesLabel, triesTextField);
        bindVisibility(LayoutAlgoArg.INITIAL_ITERS, initialItersLabel, initialItersTextField);
        bindVisibility(LayoutAlgoArg.REPULSION_FACTOR, repulsionFactorLabel, repulsionFactorTextField);
        bindVisibility(LayoutAlgoArg.SHOW_FITTED_NODES, showFittedNodesCheckBox);
        bindVisibility(LayoutAlgoArg.W, wLabel, wTextField);
        bindVisibility(LayoutAlgoArg.H, hLabel, hTextField);
        bindVisibility(LayoutAlgoArg.SOLUTION, solutionLabel, solutionBox);
        bindVisibility(LayoutAlgoArg.ALL_SOLUTIONS, allSolutionsCheckBox);

        Scene scene = new Scene(root, 1024, 768);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE && selectedVertices.size() >= 2 && currentGraph != null) {
                runForceDirectedOnSelection();
                e.consume();
            }
        });

        primaryStage.setTitle("Planar Graph Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateGraphInfo(String currentLine) {
        int[][][] generator = GroupExplorer.parseOperationsArr(currentLine);
        int maxVertex = 0;
        for (int[][] cycle : generator) {
            for (int[] polygon : cycle) {
                if (polygon.length > 0) {
                    maxVertex = Math.max(maxVertex, polygon[polygon.length - 1]);
                }
            }
        }
        
        numSharedLinesLabel.setText("Shared Lines: " + countSharedLines());
        String genus = "?";
        
        // Compute genus range 0-1 if it's not a huge graph 
        if (maxVertex < 128) genus = MultiGenus.computeGenusFromGenerators(
            Arrays.<int[][][]>asList(generator),
            new MultiGenus.ParameterizedMultiGenusOption(MultiGenus.MultiGenusOption.LIMIT_TO_GENUS_N, 1)).get(0) + "";
        
        genusButton.setText("Genus: " + (genus.equals("-1") ? ">= 2" : genus));

        // --- Automorphism group size readout ---
        // Auto-compute |Aut(G)| if the graph is reasonably small; otherwise wait for button press.
        if (maxVertex > 0 && maxVertex < 150) {
            try {
                BigInteger autOrder = GraphSymm.automorphismGroupOrder(generator, false);
                autButton.setText("Aut: " + autOrder);
            } catch (RuntimeException ex) {
                autButton.setText("Aut: err");
            }
        } else {
            autButton.setText("Aut: ?");
        }

        // Allow the user to (re)compute |Aut(G)| on demand, even for large graphs.
        autButton.setOnAction(e -> {
            try {
                BigInteger autOrder = GraphSymm.automorphismGroupOrder(generator, false);
                autButton.setText("Aut: " + autOrder);
            } catch (RuntimeException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Automorphism computation failed");
                alert.setHeaderText("Failed to compute |Aut(G)|");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        // Allow the user to recompute the genus with no limit
        genusButton.setOnAction((e) -> {
            // Recompute with no genus limit
            int genusR = MultiGenus.computeGenusFromGenerators(
                Arrays.<int[][][]>asList(generator)).get(0);
            genusButton.setText("Genus: " + genusR);
        });
    }

    private void closeGraphSource() {
        if (pbinFile != null) {
            try {
                pbinFile.close();
            } catch (IOException ignored) {
            }
            pbinFile = null;
        }
    }

    private void setGraphLines(List<String> lines) {
        closeGraphSource();
        graphLines = lines;
    }

    private void setPbinFile(PbinFile file) {
        closeGraphSource();
        graphLines = Collections.emptyList();
        pbinFile = file;
    }

    private int graphLineCount() {
        if (pbinFile != null) return pbinFile.size();
        return graphLines.size();
    }

    private boolean graphLinesEmpty() {
        return graphLineCount() == 0;
    }

    private String getGraphLine(int index) {
        try {
            if (pbinFile != null) return pbinFile.get(index);
            return graphLines.get(index);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read generator " + index, e);
        }
    }

    /**
     * Reads the input file. Text files are loaded into memory; PBIN files are
     * opened for random access (one block in memory at a time).
     */
    private void readGraphLinesFromFile(String filePath) {
        System.out.println("Reading file: " + filePath);
        closeGraphSource();
        graphLines = Collections.emptyList();
        if (filePath.endsWith(".pbin")) {
            try {
                setPbinFile(PbinFile.open(filePath));
            } catch (Exception e) {
                System.err.println("Failed to read PBIN file: " + e.getMessage());
            }
            return;
        }
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
        graphLines = lines;
    }

    private void exportGraphLines(Stage owner) {
        if (graphLinesEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Export");
            alert.setHeaderText(null);
            alert.setContentText("No graphs to export.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        if (filePath != null) {
            File source = new File(filePath);
            if (source.getParentFile() != null) {
                fileChooser.setInitialDirectory(source.getParentFile());
            }
            String baseName = source.getName();
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                baseName = baseName.substring(0, dot);
            }
            fileChooser.setInitialFileName(baseName + "-exported.txt");
        } else {
            fileChooser.setInitialFileName("graphs-exported.txt");
        }

        File selectedFile = fileChooser.showSaveDialog(owner);
        if (selectedFile == null) {
            return;
        }

        String outputPath = selectedFile.getAbsolutePath();
        if (!outputPath.toLowerCase().endsWith(".txt")) {
            outputPath += ".txt";
        }

        try (PrintWriter writer = new PrintWriter(outputPath)) {
            for (int i = 0; i < graphLineCount(); i++) {
                writer.println(getGraphLine(i));
            }
        } catch (FileNotFoundException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText(null);
            alert.setContentText("Could not write to file: " + outputPath);
            alert.showAndWait();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export");
        alert.setHeaderText(null);
        alert.setContentText("Exported " + graphLineCount() + " graph(s) to:\n" + outputPath);
        alert.showAndWait();
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
                    if (polygon.length > 2 || i == 0) { // Avoid miscounting edges in 2-cycles.
                        edgeFrequencyMap.put(key, edgeFrequencyMap.getOrDefault(key, 0) + 1);
                    }
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
            Color.ORANGE.deriveColor(0, 1, 1, 0.2),
            Color.PURPLE.deriveColor(0, 1, 1, 0.2)
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

                    // Calculate the perpendicular vector with a thin margin
                    double px = -ny * pane.getWidth() / 200;
                    double py = nx * pane.getWidth() / 200;

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

                // Save a wrapper that associates this polygon with its vertex IDs.
                shadedPolygons.add(new ShadedPolygonWrapper(shadedPoly, polygon));

                // Draw directed arrows for polygon edges if enabled.
                if (showDirectionCheckBox.isSelected()) {
                    for (int i = 0; i < polygon.length; i++) {
                        int a = polygon[i];
                        int b = polygon[(i + 1) % polygon.length];
                        double[] sourcePos = positions.get(a);
                        double[] targetPos = positions.get(b);
                        DirectedArrow arrow = createDirectedArrow(pane, sourcePos, targetPos, a, b);
                        directedArrows.add(arrow);
                    }
                }
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
            vertexCircleMap.put(vertex, circle);
            Text text = new Text(pos[0] - NODE_RADIUS/2, pos[1] + NODE_RADIUS/2, String.valueOf(vertex));
            vertexLabelMap.put(vertex, text);
            applyVertexAppearance(vertex, circle);

            final int v = vertex;
            circle.setOnMousePressed(e -> {
                if (e.isSecondaryButtonDown() || e.isShiftDown()) {
                    return;
                }
                Set<Integer> dragVertices = new HashSet<>();
                if (selectedVertices.contains(v) && selectedVertices.size() > 1) {
                    dragVertices.addAll(selectedVertices);
                } else {
                    dragVertices.add(v);
                }

                Map<Integer, double[]> startPositions = new HashMap<>();
                for (int dragVertex : dragVertices) {
                    Circle dragCircle = vertexCircleMap.get(dragVertex);
                    if (dragCircle != null) {
                        startPositions.put(dragVertex, new double[] {
                            dragCircle.getCenterX(), dragCircle.getCenterY()
                        });
                    }
                }

                circle.setUserData(new VertexDragContext(
                    circle.getCenterX() - e.getSceneX(),
                    circle.getCenterY() - e.getSceneY(),
                    e.getSceneX(),
                    e.getSceneY(),
                    startPositions,
                    dragVertices
                ));
            });

            circle.setOnMouseDragged(e -> {
                if (e.isShiftDown()) {
                    return;
                }
                VertexDragContext ctx = (VertexDragContext) circle.getUserData();
                if (ctx == null) {
                    return;
                }

                if (ctx.dragVertices.size() > 1) {
                    double deltaX = e.getSceneX() - ctx.startSceneX;
                    double deltaY = e.getSceneY() - ctx.startSceneY;
                    for (int dragVertex : ctx.dragVertices) {
                        double[] start = ctx.startPositions.get(dragVertex);
                        if (start != null) {
                            setVertexPosition(dragVertex, start[0] + deltaX, start[1] + deltaY);
                        }
                    }
                } else {
                    int dragVertex = ctx.dragVertices.iterator().next();
                    setVertexPosition(
                        dragVertex,
                        e.getSceneX() + ctx.offsetX,
                        e.getSceneY() + ctx.offsetY
                    );
                }
                updateVertexVisualsAfterMove();
            });

            circle.setOnMouseReleased(e -> {
                if (e.isShiftDown()) {
                    return;
                }
                VertexDragContext ctx = (VertexDragContext) circle.getUserData();
                if (ctx == null) {
                    return;
                }
                snapVerticesToGrid(ctx.dragVertices);
                circle.setUserData(null);
            });

            pane.getChildren().addAll(text, circle);
        }
        currentVertexCircleMap = vertexCircleMap;
        currentVertexLabelMap = vertexLabelMap;
        currentEdgeLineMap = edgeLineMap;
        currentGraph = graph;
        currentShadedPolygons = shadedPolygons;
        refreshVertexHighlights();
    }

    /**
     * Updates the graph pane with the current graph and updates the page label.
     *
     * @param graphPane The Pane used for drawing.
     * @param pageLabel The Label showing the current page.
     */
    private void updateGraph(Pane graphPane, Label pageLabel) {
        if (suppressRender) {
            renderQueued = true;
            return;
        }
        String currentLine = getGraphLine(currentGraphIndex);
        Graph<Integer, DefaultEdge> currentGraph = buildGraphFromLine(currentLine);
        Map<Integer, double[]> positions;
        String newCacheKey = buildLayoutCacheKey(currentLine);
        String positionLockKey = buildPositionLockKey(currentLine);
        boolean positionLockActive = lockRotationCheckBox != null && lockRotationCheckBox.isSelected();

        if (positionLockActive) {
            if (manualPositions != null && !positionLockKey.equals(manualPositionsCacheKey)) {
                manualPositions = null;
                manualPositionsCacheKey = null;
            }
            if (circlesMatchGraph(currentGraph)) {
                syncLockedPositions(currentLine);
            }
        }

        boolean useLockedPositions = positionLockActive
            && manualPositions != null
            && positionLockKey.equals(manualPositionsCacheKey);

        if (useLockedPositions) {
            positions = copyPositions(manualPositions);
        } else if (manualPositions != null && newCacheKey.equals(manualPositionsCacheKey)) {
            positions = copyPositions(manualPositions);
        } else {
            if (!positionLockActive && !newCacheKey.equals(manualPositionsCacheKey)) {
                manualPositions = null;
                manualPositionsCacheKey = null;
            }
            if (cachedGraphKey == null || !cachedGraphKey.equals(newCacheKey)) {

                String method = layoutChoiceBox.getValue();
                LayoutAlgo algo = layoutAlgoMap.get(method);
                algo.performLayout(Math.min(graphPane.getWidth(), graphPane.getHeight()), currentLine, currentGraph, getArgs(algo));
                Double fit = algo.getFitOut();

                cachedBasePositions = algo.getResult();
                cachedGraphKey = newCacheKey;
                if (fit != null) {
                    fitLabel.setText("Fit: " + String.format("%.5f", fit.doubleValue()));
                } else {
                    fitLabel.setText("");
                }
            }
            // Make a fresh copy of the cached base coordinates.
            positions = copyPositions(cachedBasePositions);

            // If positions are 3D, apply the trackball rotation matrix then re-center.
            if (!positions.isEmpty()) {
                int dim = positions.values().iterator().next().length;
                if (dim == 3) {
                    for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
                        double[] pos = entry.getValue();
                        double x = pos[0], y = pos[1], z = pos[2];
                        double rx = trackballRotation[0][0]*x + trackballRotation[0][1]*y + trackballRotation[0][2]*z;
                        double ry = trackballRotation[1][0]*x + trackballRotation[1][1]*y + trackballRotation[1][2]*z;
                        // double rz = trackballRotation[2][0]*x + trackballRotation[2][1]*y + trackballRotation[2][2]*z; // not used for 2D projection
                        pos[0] = rx;
                        pos[1] = ry;
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
        }
        
        if (positions != null) {
            selectedVertices.retainAll(positions.keySet());
            marqueeActive = false;
            graphPane.getChildren().clear();
            // Draw background grid (if enabled) behind all other elements.
            drawBackgroundGrid(graphPane);
            // Reset directed arrows so they can be re-created
            directedArrows = new ArrayList<>();
            // Update paginator display (keep text field and total in sync)
            String graphIdxString = String.valueOf(currentGraphIndex + 1);
            String curString = pageIndexTextField.getText();
            if (!curString.equals(graphIdxString)) {
                pageIndexTextField.setText(graphIdxString);
            }
            // Printthe generator
            //System.out.println("Generator: " + currentLine);
            pageLabel.setText(" / " + graphLineCount());
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
        for (int i = 0; i < graphLineCount(); i++) {
            String line = getGraphLine(i);
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
        setGraphLines(uniqueLines);
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
            
            if (wrapper.vertices.length == 2) {
                // Handle length-2 polygons (lines) - recreate the quadrilateral
                int a = wrapper.vertices[0];
                int b = wrapper.vertices[1];
                Circle sourceCircle = vertexCircleMap.get(a);
                Circle targetCircle = vertexCircleMap.get(b);
                
                if (sourceCircle != null && targetCircle != null) {
                    double[] sourcePos = {sourceCircle.getCenterX(), sourceCircle.getCenterY()};
                    double[] targetPos = {targetCircle.getCenterX(), targetCircle.getCenterY()};
                    
                    // Calculate the direction vector of the line
                    double dx = targetPos[0] - sourcePos[0];
                    double dy = targetPos[1] - sourcePos[1];
                    double length = Math.sqrt(dx * dx + dy * dy);
                    
                    // Normalize the direction vector
                    double nx = dx / length;
                    double ny = dy / length;
                    
                    // Calculate the perpendicular vector with a thin margin
                    double px = -ny * graphPane.getWidth() / 200;
                    double py = nx * graphPane.getWidth() / 200;
                    
                    // Define the four corners of the quadrilateral
                    double[] p1 = {sourcePos[0] + px, sourcePos[1] + py};
                    double[] p2 = {sourcePos[0] - px, sourcePos[1] - py};
                    double[] p3 = {targetPos[0] - px, targetPos[1] - py};
                    double[] p4 = {targetPos[0] + px, targetPos[1] + py};
                    
                    poly.getPoints().addAll(
                        p1[0], p1[1],
                        p2[0], p2[1],
                        p3[0], p3[1],
                        p4[0], p4[1]
                    );
                }
            } else {
                // Normal polygon - just add vertex positions
                for (int vertex : wrapper.vertices) {
                    Circle c = vertexCircleMap.get(vertex);
                    if (c != null) {
                        poly.getPoints().addAll(c.getCenterX(), c.getCenterY());
                    }
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

    public Double getArg(LayoutAlgoArg arg) {
        switch (arg) {
            case ITERS:
                return (double)Integer.parseInt(itersTextField.getText());
            case THETA:
                return Double.parseDouble(thetaTextField.getText());
            case NORM:
                return Double.parseDouble(normTextField.getText());
            case SEED:
                return (double)Integer.parseInt(seedTextField.getText());
            case SHOW_FITTED_NODES:
                return (showFittedNodesCheckBox.isSelected() ? 1. : 0.);
            case TRIES:
                return (double)Integer.parseInt(triesTextField.getText());
            case INITIAL_ITERS:
                return (double)Integer.parseInt(initialItersTextField.getText());
            case REPULSION_FACTOR:
                return Double.parseDouble(repulsionFactorTextField.getText());
            case W:
                return (double)Integer.parseInt(wTextField.getText());
            case H:
                return (double)Integer.parseInt(hTextField.getText());
            case SOLUTION:
                return (double)Integer.parseInt(solutionTextField.getText());
            case ALL_SOLUTIONS:
                return (allSolutionsCheckBox.isSelected() ? 1. : 0.);
        }
        return null;
    }

    private EnumMap<LayoutAlgoArg, Double> getArgs(LayoutAlgo algo) {
        EnumMap<LayoutAlgoArg, Double> args = new EnumMap<>(LayoutAlgoArg.class);
        for (LayoutAlgoArg arg : algo.getArgs()) {
            args.put(arg, getArg(arg));
        }
        return args;
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

    private double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1.0 - c;
        double[][] R = new double[3][3];
        R[0][0] = t*ax*ax + c;     R[0][1] = t*ax*ay - s*az; R[0][2] = t*ax*az + s*ay;
        R[1][0] = t*ay*ax + s*az;  R[1][1] = t*ay*ay + c;    R[1][2] = t*ay*az - s*ax;
        R[2][0] = t*az*ax - s*ay;  R[2][1] = t*az*ay + s*ax; R[2][2] = t*az*az + c;
        return R;
    }

    private double[][] multiply3(double[][] A, double[][] B) {
        double[][] C = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                C[i][j] = A[i][0]*B[0][j] + A[i][1]*B[1][j] + A[i][2]*B[2][j];
            }
        }
        return C;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
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

    /**
     * Updates the visibility of the argument controls (seed, iters, theta, norm, show fitted nodes)
     * based on the currently selected layout algorithm's getArgs() values.
     */
    private void updateArgsVisibility() {
        String method = layoutChoiceBox.getValue();
        LayoutAlgo algo = layoutAlgoMap.get(method);
        if (algo == null) return;

        // Update the observable list with the required arguments
        requiredArgs.setAll(algo.getArgs());
    }

    private static class VertexDragContext {
        final double offsetX;
        final double offsetY;
        final double startSceneX;
        final double startSceneY;
        final Map<Integer, double[]> startPositions;
        final Set<Integer> dragVertices;

        VertexDragContext(double offsetX, double offsetY, double startSceneX, double startSceneY,
                          Map<Integer, double[]> startPositions, Set<Integer> dragVertices) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.startSceneX = startSceneX;
            this.startSceneY = startSceneY;
            this.startPositions = startPositions;
            this.dragVertices = dragVertices;
        }
    }

    private boolean isInteractiveVertexCircle(Circle circle) {
        return currentVertexCircleMap.containsValue(circle);
    }

    private Integer findVertexForCircle(Circle circle) {
        for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
            if (entry.getValue() == circle) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void finishMarqueeSelection() {
        if (marqueeRectangle != null) {
            Bounds bounds = marqueeRectangle.getBoundsInParent();
            selectedVertices.clear();
            for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
                Circle circle = entry.getValue();
                if (bounds.contains(circle.getCenterX(), circle.getCenterY())) {
                    selectedVertices.add(entry.getKey());
                }
            }
            graphPane.getChildren().remove(marqueeRectangle);
        }
        marqueeActive = false;
        refreshVertexHighlights();
    }

    private void applyVertexAppearance(int vertex, Circle circle) {
        if (selectedVertices.contains(vertex)) {
            circle.setFill(Color.DODGERBLUE.deriveColor(0, 1, 1, 0.6));
            circle.setStroke(Color.DODGERBLUE);
            circle.setStrokeWidth(2.5);
        } else {
            int freq = vertexFrequencyMap.getOrDefault(vertex, 1);
            Color color = getVertexColor(freq);
            color = Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
            circle.setFill(color);
            circle.setStroke(null);
            circle.setStrokeWidth(0);
        }
    }

    private void refreshVertexHighlights() {
        for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
            applyVertexAppearance(entry.getKey(), entry.getValue());
        }
    }

    private void runForceDirectedOnSelection() {
        // Collect edges that have at least one endpoint in the selection
        networkx.Graph subgraph = new networkx.Graph();
        Set<Integer> involvedVertices = new HashSet<>(selectedVertices);
        for (DefaultEdge edge : currentGraph.edgeSet()) {
            int source = currentGraph.getEdgeSource(edge);
            int target = currentGraph.getEdgeTarget(edge);
            if (selectedVertices.contains(source) || selectedVertices.contains(target)) {
                subgraph.addEdge(source, target);
                involvedVertices.add(source);
                involvedVertices.add(target);
            }
        }

        // Build initial positions from current circle positions
        Map<Integer, double[]> initialPos = new HashMap<>();
        for (int v : involvedVertices) {
            Circle c = currentVertexCircleMap.get(v);
            if (c != null) {
                initialPos.put(v, new double[]{ c.getCenterX(), c.getCenterY() });
            }
        }

        // Fixed nodes = all involved vertices that are NOT selected
        Set<Integer> fixedNodes = new HashSet<>(involvedVertices);
        fixedNodes.removeAll(selectedVertices);

        Map<Integer, double[]> result = networkx.SpringLayout.springLayoutPartial(
            subgraph, initialPos, fixedNodes, 100, 2);

        // Apply new positions only to the selected vertices
        for (int v : selectedVertices) {
            double[] newPos = result.get(v);
            if (newPos != null) {
                setVertexPosition(v, newPos[0], newPos[1]);
            }
        }
        updateVertexVisualsAfterMove();
    }

    private void setVertexPosition(int vertex, double x, double y) {
        Circle circle = currentVertexCircleMap.get(vertex);
        Text text = currentVertexLabelMap.get(vertex);
        if (circle == null || currentGraph == null) {
            return;
        }
        circle.setCenterX(x);
        circle.setCenterY(y);
        if (text != null) {
            text.setX(x - NODE_RADIUS / 2);
            text.setY(y + NODE_RADIUS / 2);
        }
        for (DefaultEdge edge : currentGraph.edgeSet()) {
            int source = currentGraph.getEdgeSource(edge);
            int target = currentGraph.getEdgeTarget(edge);
            Line line = currentEdgeLineMap.get(edge);
            if (line == null) {
                continue;
            }
            if (source == vertex) {
                line.setStartX(x);
                line.setStartY(y);
            }
            if (target == vertex) {
                line.setEndX(x);
                line.setEndY(y);
            }
        }
    }

    private void updateVertexVisualsAfterMove() {
        updateShadedPolygons(currentVertexCircleMap, currentShadedPolygons);
        updateDirectedArrows();
    }

    private void snapVerticesToGrid(Set<Integer> vertices) {
        if (snapToGridCheckBox == null || !snapToGridCheckBox.isSelected() || graphPane == null) {
            return;
        }
        for (int vertex : vertices) {
            Circle circle = currentVertexCircleMap.get(vertex);
            if (circle == null) {
                continue;
            }
            double[] snapped = snapToNearestGridPoint(circle.getCenterX(), circle.getCenterY(), graphPane);
            setVertexPosition(vertex, snapped[0], snapped[1]);
        }
        updateVertexVisualsAfterMove();
    }

    private double computeGridSpacing(Pane pane) {
        double width = pane.getWidth();
        double height = pane.getHeight();
        if (width <= 0 || height <= 0) {
            return 40.0;
        }
        double targetPoints = 400.0;
        double area = width * height;
        double baseSpacing = Math.sqrt(area / targetPoints);
        return clamp(baseSpacing, 15.0, 80.0);
    }

    private double[] snapToNearestGridPoint(double x, double y, Pane pane) {
        List<double[]> gridPoints = collectSnapGridPoints(pane);
        if (gridPoints.isEmpty()) {
            return new double[] { x, y };
        }
        double bestDistSq = Double.MAX_VALUE;
        double bestX = x;
        double bestY = y;
        for (double[] point : gridPoints) {
            double dx = point[0] - x;
            double dy = point[1] - y;
            double distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestX = point[0];
                bestY = point[1];
            }
        }
        return new double[] { bestX, bestY };
    }

    private List<double[]> collectSnapGridPoints(Pane pane) {
        List<double[]> points = new ArrayList<>();
        if ((showSquareGridCheckBox == null || !showSquareGridCheckBox.isSelected()) &&
            (showHexGridCheckBox == null || !showHexGridCheckBox.isSelected()) &&
            (showHoneycombGridCheckBox == null || !showHoneycombGridCheckBox.isSelected())) {
            return points;
        }

        double width = pane.getWidth();
        double height = pane.getHeight();
        if (width <= 0 || height <= 0) {
            return points;
        }

        double spacing = computeGridSpacing(pane);
        if (showSquareGridCheckBox != null && showSquareGridCheckBox.isSelected()) {
            collectSquareGridPoints(points, width, height, spacing);
        }
        if (showHexGridCheckBox != null && showHexGridCheckBox.isSelected()) {
            collectTriangularGridPoints(points, width, height, spacing);
        }
        if (showHoneycombGridCheckBox != null && showHoneycombGridCheckBox.isSelected()) {
            collectHoneycombGridPoints(points, width, height, spacing);
        }
        return points;
    }

    private void collectSquareGridPoints(List<double[]> points, double width, double height, double spacing) {
        int cols = (int) Math.ceil(width / spacing);
        int rows = (int) Math.ceil(height / spacing);
        for (int i = 0; i <= cols; i++) {
            double x = i * spacing;
            for (int j = 0; j <= rows; j++) {
                points.add(new double[] { x, j * spacing });
            }
        }
    }

    private void collectTriangularGridPoints(List<double[]> points, double width, double height, double spacing) {
        double rowHeight = spacing * Math.sqrt(3) / 2.0;
        int rows = (int) Math.ceil(height / rowHeight) + 1;
        int cols = (int) Math.ceil(width / spacing) + 1;
        for (int row = 0; row <= rows; row++) {
            double y = row * rowHeight;
            double xOffset = (row % 2 == 0) ? 0.0 : spacing / 2.0;
            for (int col = 0; col <= cols; col++) {
                double x = xOffset + col * spacing;
                if (x > width + spacing) {
                    break;
                }
                points.add(new double[] { x, y });
            }
        }
    }

    private void collectHoneycombGridPoints(List<double[]> points, double width, double height, double spacing) {
        double r = spacing / 2.0;
        double sqrt3 = Math.sqrt(3.0);
        int qMax = (int) Math.ceil(width / (r * sqrt3)) + 2;
        int rMax = (int) Math.ceil(height / (r * 1.5)) + 2;

        for (int rIdx = -rMax; rIdx <= rMax; rIdx++) {
            for (int qIdx = -qMax; qIdx <= qMax; qIdx++) {
                double cx = r * sqrt3 * (qIdx + rIdx / 2.0) + width / 2.0;
                double cy = r * 1.5 * rIdx + height / 2.0;
                double margin = r;
                if (cx + margin < 0 || cx - margin > width ||
                    cy + margin < 0 || cy - margin > height) {
                    continue;
                }
                points.add(new double[] { cx, cy });
            }
        }
    }

    /**
     * Draws a faint background grid (square and/or hexagonal) behind the graph.
     * The grid density is chosen so that there are at least roughly 300 grid points
     * across the current pane area.
     */
    private void drawBackgroundGrid(Pane pane) {
        if ((showSquareGridCheckBox == null || !showSquareGridCheckBox.isSelected()) &&
            (showHexGridCheckBox == null || !showHexGridCheckBox.isSelected()) &&
            (showHoneycombGridCheckBox == null || !showHoneycombGridCheckBox.isSelected())) {
            return;
        }

        double width = pane.getWidth();
        double height = pane.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        double spacing = computeGridSpacing(pane);

        if (showSquareGridCheckBox != null && showSquareGridCheckBox.isSelected()) {
            drawSquareGrid(pane, width, height, spacing);
        }
        if (showHexGridCheckBox != null && showHexGridCheckBox.isSelected()) {
            drawTriangularGrid(pane, width, height, spacing);
        }
        if (showHoneycombGridCheckBox != null && showHoneycombGridCheckBox.isSelected()) {
            drawHoneycombGrid(pane, width, height, spacing);
        }
    }

    private void drawSquareGrid(Pane pane, double width, double height, double spacing) {
        Color pointColor = Color.GRAY.deriveColor(0, 1, 1, 0.6);
        int cols = (int) Math.ceil(width / spacing);
        int rows = (int) Math.ceil(height / spacing);
        double radius = 1.5;
        for (int i = 0; i <= cols; i++) {
            double x = i * spacing;
            for (int j = 0; j <= rows; j++) {
                double y = j * spacing;
                Circle c = new Circle(x, y, radius, pointColor);
                c.setStroke(null);
                pane.getChildren().add(c);
            }
        }
    }

    // Triangular lattice of points (hexagonal packing) – "triangular grid" mode.
    private void drawTriangularGrid(Pane pane, double width, double height, double spacing) {
        Color pointColor = Color.GRAY.deriveColor(0, 1, 1, 0.6);
        // For a hexagonal (triangular) lattice, vertical spacing between rows.
        double rowHeight = spacing * Math.sqrt(3) / 2.0;
        int rows = (int) Math.ceil(height / rowHeight) + 1;
        int cols = (int) Math.ceil(width / spacing) + 1;
        double radius = 1.7;
        for (int row = 0; row <= rows; row++) {
            double y = row * rowHeight;
            double xOffset = (row % 2 == 0) ? 0.0 : spacing / 2.0;
            for (int col = 0; col <= cols; col++) {
                double x = xOffset + col * spacing;
                if (x > width + spacing) {
                    break;
                }
                Circle c = new Circle(x, y, radius, pointColor);
                c.setStroke(null);
                pane.getChildren().add(c);
            }
        }
    }

    // Hexagonal honeycomb outlines – "hexagonal grid" mode.
    // Uses a standard pointy-top axial hex layout so neighboring cells share full edges.
    private void drawHoneycombGrid(Pane pane, double width, double height, double spacing) {
        Color lineColor = Color.GRAY.deriveColor(0, 1, 1, 0.5);

        // Use spacing to derive hex corner radius.
        double r = spacing / 2.0; // center-to-corner

        // Axial coordinate to pixel for pointy-top hexes (see redblobgames):
        // x = r * sqrt(3) * (q + r/2)
        // y = r * 3/2 * rIdx
        double sqrt3 = Math.sqrt(3.0);

        // Choose coordinate ranges large enough to cover the pane.
        int qMax = (int) Math.ceil(width / (r * sqrt3)) + 2;
        int rMax = (int) Math.ceil(height / (r * 1.5)) + 2;

        for (int rIdx = -rMax; rIdx <= rMax; rIdx++) {
            for (int qIdx = -qMax; qIdx <= qMax; qIdx++) {
                double cx = r * sqrt3 * (qIdx + rIdx / 2.0);
                double cy = r * 1.5 * rIdx;

                // Center the lattice in the pane.
                cx += width / 2.0;
                cy += height / 2.0;

                // Cull hexes completely outside the view (with a small margin).
                double margin = r;
                if (cx + margin < 0 || cx - margin > width ||
                    cy + margin < 0 || cy - margin > height) {
                    continue;
                }

                javafx.scene.shape.Polygon hex = new javafx.scene.shape.Polygon();
                for (int k = 0; k < 6; k++) {
                    double angle = Math.toRadians(60 * k - 30); // pointy-top orientation
                    double vx = cx + r * Math.cos(angle);
                    double vy = cy + r * Math.sin(angle);
                    hex.getPoints().addAll(vx, vy);
                }
                hex.setFill(null);
                hex.setStroke(lineColor);
                hex.setStrokeWidth(0.5);
                pane.getChildren().add(hex);
            }
        }
    }

    private void showGeneratorDialog(Stage owner) {
        if (graphLinesEmpty()) return;

        String generator = getGraphLine(currentGraphIndex);

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Generator " + (currentGraphIndex + 1) + " / " + graphLineCount());

        TextArea textArea = new TextArea(generator);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(6);
        textArea.setPrefColumnCount(80);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(ev -> dialog.close());

        VBox root = new VBox(10, textArea, closeButton);
        root.setStyle("-fx-padding: 10;");
        dialog.setScene(new Scene(root, 640, 180));
        dialog.show();

        textArea.requestFocus();
        textArea.selectAll();
    }

    private void show3DCoordinatesDialog(Stage owner) {
        if (graphLinesEmpty()) return;
        if (cachedBasePositions == null || cachedBasePositions.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("3D Coordinates");
            alert.setHeaderText(null);
            alert.setContentText("No coordinates available yet. Wait for the graph to render first.");
            alert.showAndWait();
            return;
        }
        int dim = cachedBasePositions.values().iterator().next().length;
        if (dim != 3) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("3D Coordinates");
            alert.setHeaderText(null);
            alert.setContentText("The current layout does not produce 3D coordinates.");
            alert.showAndWait();
            return;
        }

        String coordinatesText = format3DCoordinatesText();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("3D Coordinates " + (currentGraphIndex + 1) + " / " + graphLineCount());

        Label hintLabel = new Label("Raw layout output (vertex x y z). Select all or use Copy.");
        TextArea textArea = new TextArea(coordinatesText);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(12);
        textArea.setPrefColumnCount(40);

        Button copyButton = new Button("Copy");
        copyButton.setOnAction(ev -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(textArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button closeButton = new Button("Close");
        closeButton.setOnAction(ev -> dialog.close());

        HBox buttonBar = new HBox(10, copyButton, closeButton);
        VBox root = new VBox(10, hintLabel, textArea, buttonBar);
        root.setStyle("-fx-padding: 10;");
        dialog.setScene(new Scene(root, 420, 360));
        dialog.show();

        textArea.requestFocus();
        textArea.selectAll();
    }

    private void showEditCoordinatesDialog(Stage owner, Pane graphPane, Label pageLabel) {
        if (graphLinesEmpty()) return;
        if (currentVertexCircleMap == null || currentVertexCircleMap.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Edit Coordinates");
            alert.setHeaderText(null);
            alert.setContentText("No coordinates available yet. Wait for the graph to render first.");
            alert.showAndWait();
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Coordinates " + (currentGraphIndex + 1) + " / " + graphLineCount());

        Label hintLabel = new Label("One vertex per line: vertex x y");
        TextArea textArea = new TextArea(formatCoordinatesText());
        textArea.setWrapText(false);
        textArea.setPrefRowCount(12);
        textArea.setPrefColumnCount(40);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");
        okButton.setOnAction(ev -> {
            try {
                String currentLine = getGraphLine(currentGraphIndex);
                Map<Integer, double[]> parsed = parseCoordinatesText(textArea.getText());
                validateCoordinates(parsed, currentLine);
                manualPositions = parsed;
                manualPositionsCacheKey = buildLayoutCacheKey(currentLine);
                updateGraph(graphPane, pageLabel);
                dialog.close();
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Coordinates");
                alert.setHeaderText(null);
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });
        cancelButton.setOnAction(ev -> dialog.close());

        HBox buttonBar = new HBox(10, okButton, cancelButton);
        VBox root = new VBox(10, hintLabel, textArea, buttonBar);
        root.setStyle("-fx-padding: 10;");
        dialog.setScene(new Scene(root, 420, 360));
        dialog.show();

        textArea.requestFocus();
        textArea.selectAll();
    }

    private String buildLayoutCacheKey(String currentLine) {
        return currentLine + "_" + seedTextField.getText() + "_" + itersTextField.getText()
                + "_" + thetaTextField.getText() + "_" + normTextField.getText()
                + "_" + layoutChoiceBox.getValue() + "_" + triesTextField.getText()
                + "_" + initialItersTextField.getText() + "_" + showFittedNodesCheckBox.isSelected()
                + "_" + repulsionFactorTextField.getText() + "_" + wTextField.getText()
                + "_" + hTextField.getText() + "_" + solutionTextField.getText()
                + "_" + allSolutionsCheckBox.isSelected();
    }

    private String buildPositionLockKey(String currentLine) {
        return "lock:" + currentLine;
    }

    private Map<Integer, double[]> capturePositionsFromCircles() {
        Map<Integer, double[]> positions = new HashMap<>();
        for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
            Circle circle = entry.getValue();
            positions.put(entry.getKey(), new double[] { circle.getCenterX(), circle.getCenterY() });
        }
        return positions;
    }

    private void syncLockedPositions(String currentLine) {
        manualPositions = capturePositionsFromCircles();
        manualPositionsCacheKey = buildPositionLockKey(currentLine);
    }

    private boolean circlesMatchGraph(Graph<Integer, DefaultEdge> graph) {
        return !currentVertexCircleMap.isEmpty()
            && currentVertexCircleMap.keySet().equals(graph.vertexSet());
    }

    private Map<Integer, double[]> copyPositions(Map<Integer, double[]> source) {
        Map<Integer, double[]> copy = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : source.entrySet()) {
            double[] coord = entry.getValue();
            double[] coordCopy = new double[coord.length];
            System.arraycopy(coord, 0, coordCopy, 0, coord.length);
            copy.put(entry.getKey(), coordCopy);
        }
        return copy;
    }

    private String formatCoordinatesText() {
        List<Integer> vertices = new ArrayList<>(currentVertexCircleMap.keySet());
        Collections.sort(vertices);
        StringBuilder sb = new StringBuilder();
        for (int vertex : vertices) {
            Circle circle = currentVertexCircleMap.get(vertex);
            sb.append(vertex)
              .append(' ')
              .append(String.format("%.2f", circle.getCenterX()))
              .append(' ')
              .append(String.format("%.2f", circle.getCenterY()))
              .append('\n');
        }
        return sb.toString();
    }

    private String format3DCoordinatesText() {
        List<Integer> vertices = new ArrayList<>(cachedBasePositions.keySet());
        Collections.sort(vertices);
        StringBuilder sb = new StringBuilder();
        for (int vertex : vertices) {
            double[] coord = cachedBasePositions.get(vertex);
            sb.append(vertex)
              .append(' ')
              .append(String.format("%.6f", coord[0]))
              .append(' ')
              .append(String.format("%.6f", coord[1]))
              .append(' ')
              .append(String.format("%.6f", coord[2]))
              .append('\n');
        }
        return sb.toString();
    }

    private Map<Integer, double[]> parseCoordinatesText(String text) {
        Map<Integer, double[]> result = new HashMap<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = line.replace(':', ' ');
            String[] parts = line.split("[,\\s]+");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Expected 'vertex x y' on each line: " + rawLine);
            }
            int vertex = Integer.parseInt(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            if (result.containsKey(vertex)) {
                throw new IllegalArgumentException("Duplicate vertex: " + vertex);
            }
            result.put(vertex, new double[] { x, y });
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No coordinates found.");
        }
        return result;
    }

    private void validateCoordinates(Map<Integer, double[]> coordinates, String line) {
        Graph<Integer, DefaultEdge> graph = buildGraphFromLine(line);
        for (Integer vertex : graph.vertexSet()) {
            if (!coordinates.containsKey(vertex)) {
                throw new IllegalArgumentException("Missing coordinates for vertex " + vertex);
            }
        }
        for (Integer vertex : coordinates.keySet()) {
            if (!graph.containsVertex(vertex)) {
                throw new IllegalArgumentException("Unknown vertex " + vertex);
            }
        }
    }

    private static Graph<Integer, DefaultEdge> buildGraphFromLineStatic(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                }
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    graph.addEdge(a, b);
                }
            }
        }
        return graph;
    }

    private static LayoutAlgo createFreshLayoutAlgo(String name) {
        switch (name) {
            case "Java Spring": return new JavaSpring();
            case "Java 3D": return new Java3D();
            case "Java Networkx": return new JavaNetworkx();
            case "Axis Constrained": return new AxisConstrainedLayout();
            case "Axis Constrained Multi": return new AxisConstrainedLayoutMulti();
            case "SAT Layout": return new SATLayout();
            case "Planar Puzzle": return new ConcentricConstrainedLayout();
            case "Grid Solver": return new GridLayout();
            default: return new JavaNetworkx();
        }
    }

    private double computeCongestionForLine(String line, String layoutName,
            EnumMap<LayoutAlgoArg, Double> args, double boxSize) {
        Graph<Integer, DefaultEdge> graph = buildGraphFromLineStatic(line);
        LayoutAlgo algo = createFreshLayoutAlgo(layoutName);
        algo.performLayout(boxSize, line, graph, args);
        return LayoutCongestion.compute(graph, algo.getResult());
    }

    private void showFilterByCongestionDialog(Stage owner, Label pageLabel) {
        if (graphLinesEmpty()) return;

        String layoutName = layoutChoiceBox.getValue();
        LayoutAlgo templateAlgo = layoutAlgoMap.get(layoutName);
        if (templateAlgo == null) return;
        EnumMap<LayoutAlgoArg, Double> args = getArgs(templateAlgo);
        double boxSize = Math.max(500.0, Math.min(graphPane.getWidth(), graphPane.getHeight()));

        List<String> linesToProcess = new ArrayList<>(graphLineCount());
        for (int i = 0; i < graphLineCount(); i++) {
            linesToProcess.add(getGraphLine(i));
        }
        int total = linesToProcess.size();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Congestion (" + layoutName + ", " + total + " generators)");

        ObservableList<String> resultItems = FXCollections.observableArrayList();
        ListView<String> listView = new ListView<>(resultItems);
        listView.setPrefHeight(400);
        listView.setPrefWidth(700);

        Label progressLabel = new Label("0 / " + total + " completed");
        Label statsLabel = new Label("Avg: - | Min: - | Max: -");
        Button stopButton = new Button("Stop");
        TextField maxCongestionField = new TextField("1.0");
        maxCongestionField.setPrefWidth(80);
        Button applyFilterButton = new Button("Keep Below");

        HBox controlBar = new HBox(10, progressLabel, statsLabel, stopButton,
                new Label("Max:"), maxCongestionField, applyFilterButton);
        controlBar.setStyle("-fx-padding: 5; -fx-alignment: center-left;");

        VBox dialogRoot = new VBox(5, controlBar, listView);
        dialogRoot.setStyle("-fx-padding: 10;");

        dialog.setScene(new Scene(dialogRoot, 720, 480));

        List<double[]> congestionResults = Collections.synchronizedList(new ArrayList<>());

        int nThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completed = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            final int idx = i;
            final String line = linesToProcess.get(i);
            futures.add(executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                double congestion = computeCongestionForLine(line, layoutName, args, boxSize);
                congestionResults.add(new double[]{congestion, idx});
                int done = completed.incrementAndGet();
                if (done % Math.max(1, total / 100) == 0 || done == total) {
                    Platform.runLater(() -> refreshCongestionList(resultItems, congestionResults,
                            linesToProcess, progressLabel, statsLabel, done, total));
                }
            }));
        }

        stopButton.setOnAction(ev -> {
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();
            stopButton.setDisable(true);
            stopButton.setText("Stopped");
            Platform.runLater(() -> refreshCongestionList(resultItems, congestionResults,
                    linesToProcess, progressLabel, statsLabel, completed.get(), total));
        });

        applyFilterButton.setOnAction(ev -> {
            double threshold;
            try {
                threshold = Double.parseDouble(maxCongestionField.getText().trim());
            } catch (NumberFormatException ex) {
                return;
            }
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();

            List<double[]> snapshot;
            synchronized (congestionResults) {
                snapshot = new ArrayList<>(congestionResults);
            }
            snapshot.sort((a, b) -> Double.compare(a[0], b[0]));

            List<String> filtered = new ArrayList<>();
            for (double[] entry : snapshot) {
                if (entry[0] <= threshold) {
                    filtered.add(linesToProcess.get((int) entry[1]));
                }
            }
            setGraphLines(filtered);
            currentGraphIndex = 0;
            if (!graphLinesEmpty()) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            } else {
                graphPane.getChildren().clear();
            }
            pageLabel.setText(" / " + graphLineCount());
            pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
            dialog.close();
        });

        dialog.setOnCloseRequest(ev -> {
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();
        });

        dialog.show();
    }

    private static void refreshCongestionList(ObservableList<String> items, List<double[]> congestionResults,
            List<String> lines, Label progressLabel, Label statsLabel, int done, int total) {
        List<double[]> snapshot;
        synchronized (congestionResults) {
            snapshot = new ArrayList<>(congestionResults);
        }
        snapshot.sort((a, b) -> Double.compare(a[0], b[0]));
        List<String> display = new ArrayList<>(snapshot.size());
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double[] entry : snapshot) {
            double c = entry[0];
            sum += c;
            min = Math.min(min, c);
            max = Math.max(max, c);
            display.add(String.format("%.4f  |  %s", c, lines.get((int) entry[1])));
        }
        items.setAll(display);
        progressLabel.setText(done + " / " + total + " completed");
        if (!snapshot.isEmpty()) {
            double avg = sum / snapshot.size();
            statsLabel.setText(String.format("Avg: %.4f | Min: %.4f | Max: %.4f", avg, min, max));
        }
    }

    private void showFilterByFitDialog(Stage owner, Label pageLabel) {
        if (graphLinesEmpty()) return;

        LayoutAlgo satAlgo = layoutAlgoMap.get("SAT Layout");
        if (satAlgo == null) return;
        EnumMap<LayoutAlgoArg, Double> args = getArgs(satAlgo);
        int iterations = args.get(LayoutAlgoArg.ITERS).intValue();
        long seed = args.get(LayoutAlgoArg.SEED).longValue();
        int tries = args.get(LayoutAlgoArg.TRIES).intValue();
        int initialIters = args.get(LayoutAlgoArg.INITIAL_ITERS).intValue();
        double repulsionFactor = args.get(LayoutAlgoArg.REPULSION_FACTOR);

        List<String> linesToProcess = new ArrayList<>(graphLineCount());
        for (int i = 0; i < graphLineCount(); i++) {
            linesToProcess.add(getGraphLine(i));
        }
        int total = linesToProcess.size();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Filter by Fit (" + total + " generators)");

        ObservableList<String> resultItems = FXCollections.observableArrayList();
        ListView<String> listView = new ListView<>(resultItems);
        listView.setPrefHeight(400);
        listView.setPrefWidth(700);

        Label progressLabel = new Label("0 / " + total + " completed");
        Button stopButton = new Button("Stop");
        TextField keepBelowField = new TextField("0.2");
        keepBelowField.setPrefWidth(80);
        Button applyFilterButton = new Button("Keep Below");

        HBox controlBar = new HBox(10, progressLabel, stopButton,
                new Label("Threshold:"), keepBelowField, applyFilterButton);
        controlBar.setStyle("-fx-padding: 5; -fx-alignment: center-left;");

        VBox dialogRoot = new VBox(5, controlBar, listView);
        dialogRoot.setStyle("-fx-padding: 10;");

        dialog.setScene(new Scene(dialogRoot, 720, 480));

        // Shared mutable state for results
        List<double[]> fitResults = Collections.synchronizedList(new ArrayList<>());
        // fitResults entries: [fit, originalIndex]

        int nThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        AtomicInteger completed = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            final int idx = i;
            final String line = linesToProcess.get(i);
            futures.add(executor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                double fit = SATLayout.computeFitOnly(line, iterations, seed, tries, initialIters, repulsionFactor);
                fitResults.add(new double[]{fit, idx});
                int done = completed.incrementAndGet();
                if (done % Math.max(1, total / 100) == 0 || done == total) {
                    Platform.runLater(() -> refreshFitList(resultItems, fitResults, linesToProcess, progressLabel, done, total));
                }
            }));
        }

        stopButton.setOnAction(ev -> {
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();
            stopButton.setDisable(true);
            stopButton.setText("Stopped");
            Platform.runLater(() -> refreshFitList(resultItems, fitResults, linesToProcess, progressLabel, completed.get(), total));
        });

        applyFilterButton.setOnAction(ev -> {
            double threshold;
            try {
                threshold = Double.parseDouble(keepBelowField.getText().trim());
            } catch (NumberFormatException ex) {
                return;
            }
            // Stop if still running
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();

            // Build filtered list preserving fit-sorted order
            List<double[]> snapshot;
            synchronized (fitResults) {
                snapshot = new ArrayList<>(fitResults);
            }
            snapshot.sort((a, b) -> Double.compare(a[0], b[0]));

            List<String> filtered = new ArrayList<>();
            for (double[] entry : snapshot) {
                if (entry[0] < threshold) {
                    filtered.add(linesToProcess.get((int) entry[1]));
                }
            }
            setGraphLines(filtered);
            currentGraphIndex = 0;
            if (!graphLinesEmpty()) {
                updateGraph(graphPane, pageLabel);
                updateGraphInfo(getGraphLine(currentGraphIndex));
            } else {
                graphPane.getChildren().clear();
            }
            pageLabel.setText(" / " + graphLineCount());
            pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
            dialog.close();
        });

        dialog.setOnCloseRequest(ev -> {
            for (Future<?> f : futures) f.cancel(true);
            executor.shutdownNow();
        });

        dialog.show();
    }

    private static void refreshFitList(ObservableList<String> items, List<double[]> fitResults,
            List<String> lines, Label progressLabel, int done, int total) {
        List<double[]> snapshot;
        synchronized (fitResults) {
            snapshot = new ArrayList<>(fitResults);
        }
        snapshot.sort((a, b) -> Double.compare(a[0], b[0]));
        List<String> display = new ArrayList<>(snapshot.size());
        for (double[] entry : snapshot) {
            display.add(String.format("%.6f  |  %s", entry[0], lines.get((int) entry[1])));
        }
        items.setAll(display);
        progressLabel.setText(done + " / " + total + " completed");
    }
}
