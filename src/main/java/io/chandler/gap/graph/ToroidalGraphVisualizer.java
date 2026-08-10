package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import io.chandler.gap.GroupExplorer;
import io.chandler.gap.PbinFile;
import io.chandler.gap.graph.layoutalgos.JavaNetworkx;
import io.chandler.gap.graph.layoutalgos.LayoutAlgoArg;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Networkx-only graph visualizer for aligning and flattening a graph onto a torus.
 *
 * Phase A (free 3D): trackball + shift-scroll to align the force-directed cloud with
 * a preview torus (axis = Y).
 * Phase B (torus, irreversible): every vertex is projected onto the torus surface and
 * stored as (u, v) where u is the major angle and v is the minor (tube) angle. The view
 * can be spun (yaw) and tilted (pitch); back faces are culled using the surface normal.
 * Vertices drag along the surface and optionally snap to an Nu x Nv lattice.
 * Phase C (flat view): the same (u, v) coordinates rendered as the unwrapped fundamental
 * square with wrap-around edges, which is where a flattening is easiest to finish.
 */
public class ToroidalGraphVisualizer extends Application {

    private static final double TWO_PI = Math.PI * 2.0;

    private static final double NODE_RADIUS = 8.0;
    /** Half-thickness of shaded 2-cycle strips; kept in proportion to node handles. */
    private static final double SHADED_EDGE_HALF_WIDTH = NODE_RADIUS * 0.55;
    private static final double MIN_MAJOR_RADIUS = 40.0;
    private static final double MIN_MINOR_RADIUS = 10.0;
    private static final int DEFAULT_MAJOR_COLUMNS = 28;
    private static final int DEFAULT_MINOR_COLUMNS = 8;
    private static final int DEFAULT_RELAX_ITERS = 400;

    /** Toroidal-looking generator used when no file is supplied. */
    private static final String DEFAULT_GENERATOR =
            "[(2,80)(4,53)(5,102)(6,100)(7,72)(9,27)(10,96)(12,28)(13,19)(14,94)(16,22)(17,108)(21,62)"
            + "(23,51)(24,98)(25,103)(26,79)(29,61)(30,48)(31,59)(32,37)(33,86)(34,69)(35,50)(36,56)"
            + "(38,84)(39,111)(40,105)(42,66)(44,83)(45,78)(46,95)(47,87)(49,97)(52,82)(55,63)(57,77)"
            + "(58,85)(60,75)(65,106)(67,109)(68,104)(70,92)(71,73)(74,110)(76,81)(90,91)(107,112),"
            + "(1,67)(2,22)(3,99)(4,15)(6,33)(7,16)(8,14)(9,21)(10,66)(12,88)(18,51)(19,81)(23,101)"
            + "(24,95)(25,80)(26,74)(27,35)(28,93)(29,57)(30,63)(32,49)(34,96)(39,78)(40,77)(41,75)"
            + "(42,112)(43,94)(44,83)(45,98)(46,111)(47,61)(48,79)(50,104)(52,65)(53,54)(55,110)"
            + "(58,70)(59,108)(60,64)(62,68)(69,107)(71,91)(72,103)(73,90)(82,106)(84,102)(87,105)"
            + "(89,109),"
            + "(2,85)(3,105)(4,71)(5,95)(6,52)(8,48)(9,75)(10,98)(11,45)(12,64)(13,91)(14,69)(16,19)"
            + "(17,80)(18,109)(20,112)(21,28)(22,102)(23,70)(24,49)(25,78)(26,86)(27,101)(29,65)"
            + "(30,59)(33,83)(34,41)(35,89)(36,88)(37,40)(38,81)(42,50)(43,110)(44,53)(46,90)(47,67)"
            + "(51,58)(54,57)(55,56)(61,97)(68,77)(72,74)(73,79)(76,99)(82,93)(92,108)(96,106)"
            + "(107,111)]";

    private String filePath;
    private List<String> graphLines = Collections.emptyList();
    private PbinFile pbinFile;
    private int currentGraphIndex = 0;

    private Map<String, Integer> edgeFrequencyMap;
    private Map<Integer, Integer> vertexFrequencyMap;

    private Pane graphPane;
    private Label pageLabel;
    private Label statusLabel;
    private TextField pageIndexTextField;
    private TextField seedTextField;
    private TextField itersTextField;
    private TextField majorColumnsTextField;
    private TextField minorColumnsTextField;
    private TextField majorRadiusTextField;
    private TextField minorRadiusTextField;
    private TextField relaxItersTextField;

    private CheckBox showCirclesCheckBox;
    private CheckBox showLabelsCheckBox;
    private CheckBox snapToGridCheckBox;
    private CheckBox showTorusGridCheckBox;
    private CheckBox disableOcclusionCheckBox;
    private CheckBox invertSideCheckBox;
    private CheckBox torusModeCheckBox;
    private CheckBox flatViewCheckBox;

    private Button snapAllButton;
    private Button relaxButton;

    private final JavaNetworkx layoutAlgo = new JavaNetworkx();

    /** Accumulated free-mode trackball (unused once torus mode is entered). */
    private double[][] trackballRotation = identity3();
    private Double lastMouseX = null;
    private Double lastMouseY = null;
    /** True while middle-button dragging a torus-into-itself (u,v) roll. */
    private boolean surfaceRollActive = false;

    private double scale = 1.0;

    private String cachedGraphKey = null;
    private Map<Integer, double[]> cachedBasePositions = new HashMap<>();

    // --- Torus state ---
    private boolean torusMode = false;
    private double majorRadius = 200.0;
    private double minorRadius = 60.0;
    /** Camera spin around the torus axis. */
    private double viewYaw = 0.0;
    /** Camera tilt; a nonzero default makes the hole visible. */
    private double viewPitch = 0.55;
    /** Continuous surface coords: [u, v] per vertex. */
    private Map<Integer, double[]> surfacePositions = new HashMap<>();
    /** View-rotated world coords: [x, y, z] per vertex, for the coordinate dialog. */
    private Map<Integer, double[]> worldPositions = new HashMap<>();

    private Map<Integer, Circle> currentVertexCircleMap = new HashMap<>();
    private Map<Integer, Text> currentVertexLabelMap = new HashMap<>();
    private Map<DefaultEdge, Line> currentEdgeLineMap = new HashMap<>();
    private List<ShadedPolygonWrapper> currentShadedPolygons = new ArrayList<>();
    private Graph<Integer, DefaultEdge> currentGraph;
    private Set<Integer> selectedVertices = new HashSet<>();

    private static final Color[] GROUP_COLORS = {
            Color.RED.deriveColor(0, 1, 1, 0.2),
            Color.BLUE.deriveColor(0, 1, 1, 0.2),
            Color.GREEN.deriveColor(0, 1, 1, 0.2),
            Color.ORANGE.deriveColor(0, 1, 1, 0.2),
            Color.PURPLE.deriveColor(0, 1, 1, 0.2)
    };

    private Rectangle marqueeRectangle;
    private double marqueeStartX;
    private double marqueeStartY;
    private boolean marqueeActive = false;

    /** Cached flat-view rectangle: x, y, width, height. */
    private double[] flatRect = new double[] { 0, 0, 1, 1 };

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        closeGraphSource();
    }

    @Override
    public void start(Stage primaryStage) {
        if (!getParameters().getRaw().isEmpty()) {
            filePath = getParameters().getRaw().get(0);
            readGraphLinesFromFile(filePath);
        } else {
            graphLines = Collections.singletonList(DEFAULT_GENERATOR);
        }

        BorderPane root = new BorderPane();
        graphPane = new Pane();
        root.setCenter(graphPane);
        graphPane.prefWidthProperty().bind(root.widthProperty());
        graphPane.prefHeightProperty().bind(root.heightProperty().subtract(100));

        graphPane.setOnScroll(e -> {
            double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX();
            if (delta == 0) {
                return;
            }
            if (e.isShiftDown()) {
                double factor = delta > 0 ? 1.05 : 1.0 / 1.05;
                scalePointCoordinates(factor);
                e.consume();
                return;
            }
            if (delta > 0) {
                scale += 0.05;
            } else if (delta < 0) {
                scale -= 0.05;
            }
            scale = clamp(scale, 0.5, 2.0);
            graphPane.setScaleX(scale);
            graphPane.setScaleY(scale);
        });

        installRotationHandlers();
        installMarqueeHandlers();

        pageIndexTextField = new TextField("1");
        pageIndexTextField.setPrefWidth(60);
        pageLabel = new Label(" / " + graphLineCount());
        statusLabel = new Label("");

        seedTextField = new TextField("0");
        itersTextField = new TextField("3000");
        majorColumnsTextField = new TextField(String.valueOf(DEFAULT_MAJOR_COLUMNS));
        minorColumnsTextField = new TextField(String.valueOf(DEFAULT_MINOR_COLUMNS));
        majorRadiusTextField = new TextField(String.valueOf((int) majorRadius));
        minorRadiusTextField = new TextField(String.valueOf((int) minorRadius));
        relaxItersTextField = new TextField(String.valueOf(DEFAULT_RELAX_ITERS));
        for (TextField tf : new TextField[] { seedTextField, itersTextField, majorColumnsTextField,
                minorColumnsTextField, majorRadiusTextField, minorRadiusTextField, relaxItersTextField }) {
            tf.setPrefWidth(70);
        }
        seedTextField.setOnKeyReleased(e -> {
            if (!torusMode) {
                updateGraph();
            }
        });
        itersTextField.setOnKeyReleased(e -> {
            if (!torusMode) {
                updateGraph();
            }
        });
        majorColumnsTextField.setOnKeyReleased(e -> updateGraph());
        minorColumnsTextField.setOnKeyReleased(e -> updateGraph());
        majorRadiusTextField.setOnKeyReleased(e -> applyRadiusFields());
        minorRadiusTextField.setOnKeyReleased(e -> applyRadiusFields());
        // Radii are estimated from the cloud until the torus is committed.
        majorRadiusTextField.setDisable(true);
        minorRadiusTextField.setDisable(true);

        showCirclesCheckBox = new CheckBox("Show Circles");
        showCirclesCheckBox.setSelected(true);
        showCirclesCheckBox.setOnAction(e -> updateGraph());

        showLabelsCheckBox = new CheckBox("Show Labels");
        showLabelsCheckBox.setSelected(false);
        showLabelsCheckBox.setOnAction(e -> updateGraph());

        snapToGridCheckBox = new CheckBox("Snap to Grid");
        snapToGridCheckBox.setSelected(false);

        showTorusGridCheckBox = new CheckBox("Show Torus Grid");
        showTorusGridCheckBox.setSelected(true);
        showTorusGridCheckBox.setOnAction(e -> updateGraph());

        disableOcclusionCheckBox = new CheckBox("Show Hidden Points");
        disableOcclusionCheckBox.setSelected(false);
        disableOcclusionCheckBox.setOnAction(e -> updateGraph());

        invertSideCheckBox = new CheckBox("Invert Side");
        invertSideCheckBox.setSelected(false);
        invertSideCheckBox.setOnAction(e -> updateGraph());

        torusModeCheckBox = new CheckBox("Torus Mode");
        torusModeCheckBox.setSelected(false);
        torusModeCheckBox.setOnAction(e -> {
            if (torusModeCheckBox.isSelected() && !torusMode) {
                enterTorusMode();
            } else if (!torusModeCheckBox.isSelected() && torusMode) {
                // Irreversible: force the checkbox back on.
                torusModeCheckBox.setSelected(true);
            }
        });

        flatViewCheckBox = new CheckBox("Flat (u,v) View");
        flatViewCheckBox.setSelected(false);
        flatViewCheckBox.setDisable(true);
        flatViewCheckBox.setOnAction(e -> updateGraph());

        snapAllButton = new Button("Snap All");
        snapAllButton.setDisable(true);
        snapAllButton.setOnAction(e -> {
            snapVerticesToTorusGrid(new HashSet<>(surfacePositions.keySet()));
            updateGraph();
        });

        relaxButton = new Button("Relax on Torus");
        relaxButton.setDisable(true);
        relaxButton.setOnAction(e -> {
            relaxOnTorus((int) parseDouble(relaxItersTextField.getText(), DEFAULT_RELAX_ITERS));
            updateGraph();
        });

        Button randomizeButton = new Button("Randomize");
        randomizeButton.setOnAction(e -> {
            if (torusMode) {
                return;
            }
            seedTextField.setText(String.valueOf(new Random().nextInt(10000)));
            updateGraph();
        });

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Generator Files", "*.txt", "*.pbin"),
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("PBIN Files", "*.pbin"));
            File selected = fileChooser.showOpenDialog(primaryStage);
            if (selected != null) {
                resetTorusState();
                readGraphLinesFromFile(selected.getAbsolutePath());
                filePath = selected.getAbsolutePath();
                currentGraphIndex = 0;
                pageLabel.setText(" / " + graphLineCount());
                pageIndexTextField.setText("1");
                updateGraph();
            }
        });

        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportGraphLines(primaryStage));

        Button editCoordsButton = new Button("Edit Coordinates");
        editCoordsButton.setOnAction(e -> showEditCoordinatesDialog(primaryStage));

        Button coordsButton = new Button("Torus Coordinates");
        coordsButton.setOnAction(e -> showTorusCoordinatesDialog(primaryStage));

        Button controlsButton = new Button("Controls");
        controlsButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Controls");
            alert.setHeaderText(null);
            alert.setContentText(
                    "Free 3D mode (align):\n"
                    + "  Right-drag: trackball rotate\n"
                    + "  Shift+scroll: scale the point cloud against the preview torus\n"
                    + "  Scroll: canvas zoom\n"
                    + "  Aim for the torus axis running vertically, then flip Torus Mode.\n\n"
                    + "Torus mode (irreversible):\n"
                    + "  Right-drag horizontal: spin the view (yaw)\n"
                    + "  Right-drag vertical: tilt the view (pitch)\n"
                    + "  Middle-drag: rotate the torus into itself (shift all u/v)\n"
                    + "  Shift+scroll: grow/shrink the torus\n"
                    + "  Left-drag node: slide along the surface (follows the mouse)\n"
                    + "  Show Hidden Points: draw points + torus wire on both sides (edges still cull)\n"
                    + "  Invert Side: show the opposite side of the surface\n"
                    + "  Snap to Grid: snap on release to the Nu x Nv lattice\n"
                    + "  Snap All / Relax on Torus: batch flattening helpers\n\n"
                    + "Flat (u,v) view:\n"
                    + "  Unwrapped fundamental square; edges wrap across the borders\n"
                    + "  Grid cells are drawn square regardless of Nu / Nv\n\n"
                    + "Changing Nu / Nv only changes grid density, never vertex positions.");
            alert.showAndWait();
        });

        HBox topControls = new HBox(10, randomizeButton, loadButton, exportButton,
                editCoordsButton, coordsButton, controlsButton);
        topControls.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setTop(topControls);

        VBox sidebar = new VBox(8,
                new Label("Layout: Java Networkx"),
                new Label("Seed:"), seedTextField,
                new Label("Iters:"), itersTextField,
                new Label("Nu (major):"), majorColumnsTextField,
                new Label("Nv (minor):"), minorColumnsTextField,
                new Label("Major R:"), majorRadiusTextField,
                new Label("Minor r:"), minorRadiusTextField,
                showCirclesCheckBox,
                showLabelsCheckBox,
                snapToGridCheckBox,
                showTorusGridCheckBox,
                disableOcclusionCheckBox,
                invertSideCheckBox,
                torusModeCheckBox,
                flatViewCheckBox,
                new Label("Relax iters:"), relaxItersTextField,
                relaxButton,
                snapAllButton);
        sidebar.setPrefWidth(210);
        sidebar.setStyle("-fx-padding: 10;");
        root.setRight(sidebar);

        Button prevButton = new Button("Previous");
        Button nextButton = new Button("Next");
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                resetTorusState();
                currentGraphIndex--;
                pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
                updateGraph();
            }
        });
        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphLineCount() - 1) {
                resetTorusState();
                currentGraphIndex++;
                pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
                updateGraph();
            }
        });
        pageIndexTextField.setOnAction(e -> {
            try {
                int idx = Integer.parseInt(pageIndexTextField.getText().trim()) - 1;
                if (idx >= 0 && idx < graphLineCount()) {
                    resetTorusState();
                    currentGraphIndex = idx;
                    updateGraph();
                }
            } catch (NumberFormatException ignored) {
            }
        });

        HBox paginator = new HBox(10, prevButton, pageIndexTextField, pageLabel, nextButton, statusLabel);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setBottom(paginator);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Toroidal Graph Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();

        if (!graphLinesEmpty()) {
            updateGraph();
        }
    }

    // -------------------------------------------------------------------------
    // Rotation / marquee
    // -------------------------------------------------------------------------

    private void installRotationHandlers() {
        graphPane.setOnMousePressed(e -> {
            lastMouseX = null;
            lastMouseY = null;
            surfaceRollActive = false;
            if (e.isMiddleButtonDown()) {
                if (!torusMode) {
                    return;
                }
                surfaceRollActive = true;
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                e.consume();
                return;
            }
            if (!e.isSecondaryButtonDown()) {
                return;
            }
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();
            e.consume();
        });
        graphPane.setOnMouseDragged(e -> {
            if (lastMouseX == null || lastMouseY == null) {
                return;
            }
            double deltaX = e.getSceneX() - lastMouseX;
            double deltaY = e.getSceneY() - lastMouseY;
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();

            if (surfaceRollActive) {
                // Rotate the torus into itself: shift every vertex's (u, v) together.
                // Horizontal → spin around the major axis; vertical → roll around the tube.
                if (!e.isMiddleButtonDown() || !torusMode) {
                    return;
                }
                double du = -deltaX / Math.max(1.0, majorRadius);
                double dv = -deltaY / Math.max(1.0, minorRadius);
                for (double[] s : surfacePositions.values()) {
                    s[0] = wrap2pi(s[0] + du);
                    s[1] = wrap2pi(s[1] + dv);
                }
                updateGraph();
                e.consume();
                return;
            }

            if (!e.isSecondaryButtonDown()) {
                return;
            }
            deltaX = -deltaX;
            double sensitivity = 0.01;

            if (torusMode) {
                if (flatViewCheckBox.isSelected()) {
                    return;
                }
                viewYaw += deltaX * sensitivity;
                viewPitch = clamp(viewPitch + deltaY * sensitivity, -Math.PI / 2, Math.PI / 2);
            } else {
                double[][] ry = rotationFromAxisAngle(0, 1, 0, deltaX * sensitivity);
                double[][] rx = rotationFromAxisAngle(1, 0, 0, deltaY * sensitivity);
                trackballRotation = multiply3(rx, multiply3(ry, trackballRotation));
            }
            updateGraph();
        });
        graphPane.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                surfaceRollActive = false;
                lastMouseX = null;
                lastMouseY = null;
            }
        });
    }

    private void installMarqueeHandlers() {
        graphPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isPrimaryButtonDown() && e.isShiftDown()) {
                marqueeActive = true;
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
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
            Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
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
    }

    // -------------------------------------------------------------------------
    // Torus mode entry
    // -------------------------------------------------------------------------

    private void resetTorusState() {
        torusMode = false;
        viewYaw = 0.0;
        viewPitch = 0.55;
        surfacePositions = new HashMap<>();
        worldPositions = new HashMap<>();
        trackballRotation = identity3();
        if (torusModeCheckBox != null) {
            torusModeCheckBox.setSelected(false);
            torusModeCheckBox.setDisable(false);
            flatViewCheckBox.setSelected(false);
            flatViewCheckBox.setDisable(true);
            snapAllButton.setDisable(true);
            relaxButton.setDisable(true);
            majorRadiusTextField.setDisable(true);
            minorRadiusTextField.setDisable(true);
        }
    }

    private void enterTorusMode() {
        if (graphLinesEmpty() || cachedBasePositions.isEmpty()) {
            torusModeCheckBox.setSelected(false);
            return;
        }

        Map<Integer, double[]> rotated = rotateAndCenter3D(cachedBasePositions);
        estimateTorusParams(rotated);

        surfacePositions = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : rotated.entrySet()) {
            double[] p = entry.getValue();
            double rho = Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            double u = Math.atan2(p[0], p[2]);
            double v = Math.atan2(p[1], rho - majorRadius);
            surfacePositions.put(entry.getKey(), new double[] { wrap2pi(u), wrap2pi(v) });
        }

        torusMode = true;
        viewYaw = 0.0;
        torusModeCheckBox.setDisable(true);
        flatViewCheckBox.setDisable(false);
        snapAllButton.setDisable(false);
        relaxButton.setDisable(false);
        majorRadiusTextField.setDisable(false);
        minorRadiusTextField.setDisable(false);
        syncRadiusFields();
        updateGraph();
    }

    /**
     * Fits an axis-aligned torus (axis = Y) to the centered cloud: the major radius is the
     * mean distance from the axis, the minor radius the mean distance from the core circle.
     */
    private void estimateTorusParams(Map<Integer, double[]> rotatedCentered) {
        if (rotatedCentered.isEmpty()) {
            return;
        }
        double sumRho = 0;
        for (double[] p : rotatedCentered.values()) {
            sumRho += Math.sqrt(p[0] * p[0] + p[2] * p[2]);
        }
        double r0 = sumRho / rotatedCentered.size();

        double sumTube = 0;
        for (double[] p : rotatedCentered.values()) {
            double rho = Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            double dr = rho - r0;
            sumTube += Math.sqrt(dr * dr + p[1] * p[1]);
        }
        majorRadius = Math.max(MIN_MAJOR_RADIUS, r0);
        minorRadius = Math.max(MIN_MINOR_RADIUS, sumTube / rotatedCentered.size());
        // Keep the tube from swallowing the hole.
        minorRadius = Math.min(minorRadius, majorRadius * 0.85);
    }

    private void syncRadiusFields() {
        majorRadiusTextField.setText(String.format("%.1f", majorRadius));
        minorRadiusTextField.setText(String.format("%.1f", minorRadius));
    }

    private void applyRadiusFields() {
        if (!torusMode) {
            return;
        }
        majorRadius = Math.max(MIN_MAJOR_RADIUS, parseDouble(majorRadiusTextField.getText(), majorRadius));
        minorRadius = Math.max(MIN_MINOR_RADIUS, parseDouble(minorRadiusTextField.getText(), minorRadius));
        updateGraph();
    }

    // -------------------------------------------------------------------------
    // Layout / update
    // -------------------------------------------------------------------------

    private void updateGraph() {
        if (graphLinesEmpty()) {
            graphPane.getChildren().clear();
            return;
        }
        String currentLine = getGraphLine(currentGraphIndex);
        currentGraph = buildGraphFromLine(currentLine);

        if (!torusMode) {
            String cacheKey = buildLayoutCacheKey(currentLine);
            if (cachedGraphKey == null || !cachedGraphKey.equals(cacheKey)) {
                EnumMap<LayoutAlgoArg, Double> args = new EnumMap<>(LayoutAlgoArg.class);
                args.put(LayoutAlgoArg.SEED, parseDouble(seedTextField.getText(), 0));
                args.put(LayoutAlgoArg.ITERS, parseDouble(itersTextField.getText(), 3000));
                double box = Math.min(graphPane.getWidth(), graphPane.getHeight());
                if (box < 10) {
                    box = 600;
                }
                layoutAlgo.performLayout(box, currentLine, currentGraph, args);
                cachedBasePositions = copyPositions(layoutAlgo.getResult());
                cachedGraphKey = cacheKey;
            }
        }

        boolean flat = torusMode && flatViewCheckBox.isSelected();
        Map<Integer, double[]> screenPositions = new HashMap<>();
        Map<Integer, Boolean> frontFacing = new HashMap<>();

        if (flat) {
            recomputeFlatRect();
            for (Map.Entry<Integer, double[]> entry : surfacePositions.entrySet()) {
                double[] s = entry.getValue();
                screenPositions.put(entry.getKey(), flatToScreen(s[0], s[1]));
                frontFacing.put(entry.getKey(), true);
            }
        } else if (torusMode) {
            worldPositions = new HashMap<>();
            double[][] view = viewRotation();
            for (Map.Entry<Integer, double[]> entry : surfacePositions.entrySet()) {
                double[] s = entry.getValue();
                double[] world = apply3(view, torusPoint(s[0], s[1]));
                worldPositions.put(entry.getKey(), world);
                // Face culling for edges/shaded polys (respects Invert Side only).
                frontFacing.put(entry.getKey(), isFacingCamera(apply3(view, torusNormal(s[0], s[1]))[2]));
                screenPositions.put(entry.getKey(), worldToScreen(world));
            }
        } else {
            Map<Integer, double[]> rotated = rotateAndCenter3D(cachedBasePositions);
            estimateTorusParams(rotated);
            syncRadiusFields();
            for (Map.Entry<Integer, double[]> entry : rotated.entrySet()) {
                double[] p = entry.getValue();
                frontFacing.put(entry.getKey(), isFacingCamera(p[2]));
                screenPositions.put(entry.getKey(), worldToScreen(p));
            }
        }

        selectedVertices.retainAll(screenPositions.keySet());
        marqueeActive = false;
        graphPane.getChildren().clear();
        graphPane.setClip(flat ? flatClip() : null);

        if (showTorusGridCheckBox.isSelected()) {
            if (flat) {
                drawFlatGrid();
            } else {
                drawTorusOverlay();
            }
        }

        pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
        pageLabel.setText(" / " + graphLineCount());

        drawGraph(currentGraph, currentLine, screenPositions, frontFacing, flat);
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        if (!torusMode) {
            statusLabel.setText("   Free 3D align");
            return;
        }
        int nu = getMajorColumns();
        int nv = getMinorColumns();
        Set<Long> occupied = new HashSet<>();
        int collisions = 0;
        for (double[] s : surfacePositions.values()) {
            long k = Math.floorMod(Math.round(s[0] / (TWO_PI / nu)), nu);
            long j = Math.floorMod(Math.round(s[1] / (TWO_PI / nv)), nv);
            if (!occupied.add(k * nv + j)) {
                collisions++;
            }
        }
        statusLabel.setText(String.format("   Lattice %dx%d = %d sites, %d vertices, %d collisions",
                nu, nv, nu * nv, surfacePositions.size(), collisions));
    }

    private double[][] viewRotation() {
        return multiply3(rotationFromAxisAngle(1, 0, 0, viewPitch),
                rotationFromAxisAngle(0, 1, 0, viewYaw));
    }

    private double[] torusPoint(double u, double v) {
        double rho = majorRadius + minorRadius * Math.cos(v);
        return new double[] { rho * Math.sin(u), minorRadius * Math.sin(v), rho * Math.cos(u) };
    }

    /** Outward unit normal of the torus surface at (u, v). */
    private double[] torusNormal(double u, double v) {
        return new double[] { Math.cos(v) * Math.sin(u), Math.sin(v), Math.cos(v) * Math.cos(u) };
    }

    /** ∂p/∂u of the torus parametrization (major-circle tangent). */
    private double[] torusPartialU(double u, double v) {
        double rho = majorRadius + minorRadius * Math.cos(v);
        return new double[] { rho * Math.cos(u), 0, -rho * Math.sin(u) };
    }

    /** ∂p/∂v of the torus parametrization (tube-circle tangent). */
    private double[] torusPartialV(double u, double v) {
        return new double[] {
                -minorRadius * Math.sin(v) * Math.sin(u),
                minorRadius * Math.cos(v),
                -minorRadius * Math.sin(v) * Math.cos(u)
        };
    }

    /**
     * Torus wireframe / lattice visibility. Show Hidden Points draws both sides of the torus.
     */
    private boolean isTorusOverlayVisible(double[][] view, double u, double v) {
        if (showHiddenPoints()) {
            return true;
        }
        return isFacingCamera(apply3(view, torusNormal(u, v))[2]);
    }

    private boolean showHiddenPoints() {
        return disableOcclusionCheckBox != null && disableOcclusionCheckBox.isSelected();
    }

    /**
     * Whether a surface sample is drawn for Invert Side (does not include Show Hidden Points —
     * that flag is for vertices and the torus overlay only).
     */
    private boolean isFacingCamera(double cameraFacingComponent) {
        boolean front = cameraFacingComponent >= 0;
        if (invertSideCheckBox != null && invertSideCheckBox.isSelected()) {
            front = !front;
        }
        return front;
    }

    /**
     * Maps a screen-space mouse delta to (du, dv) using the screen projection of the
     * torus tangents at (u, v). This keeps drag direction consistent on both the near
     * and far sides of the tube (where a naive Δy → Δv mapping inverts).
     */
    private double[] screenDeltaToSurfaceDelta(double[][] view, double u, double v,
            double deltaX, double deltaY) {
        double[] tu = apply3(view, torusPartialU(u, v));
        double[] tv = apply3(view, torusPartialV(u, v));
        // Orthographic screen uses (x, y) of the viewed point.
        double a = tu[0], b = tv[0];
        double c = tu[1], d = tv[1];
        double det = a * d - b * c;
        if (Math.abs(det) < 1e-8) {
            // Degenerate foreshortening: fall back to arc-length scaling.
            double rho = Math.max(1.0, majorRadius + minorRadius * Math.cos(v));
            return new double[] { deltaX / rho, deltaY / Math.max(1.0, minorRadius) };
        }
        return new double[] {
                (deltaX * d - deltaY * b) / det,
                (a * deltaY - c * deltaX) / det
        };
    }

    private double[] worldToScreen(double[] p) {
        double cx = graphPane.getWidth() / 2.0;
        double cy = graphPane.getHeight() / 2.0;
        return new double[] { cx + p[0], cy + p[1] };
    }

    private Map<Integer, double[]> rotateAndCenter3D(Map<Integer, double[]> base) {
        Map<Integer, double[]> rotated = new HashMap<>();
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (Map.Entry<Integer, double[]> entry : base.entrySet()) {
            double[] p = entry.getValue();
            double z = p.length > 2 ? p[2] : 0;
            double[] r = apply3(trackballRotation, new double[] { p[0], p[1], z });
            rotated.put(entry.getKey(), r);
            sumX += r[0];
            sumY += r[1];
            sumZ += r[2];
            count++;
        }
        if (count == 0) {
            return rotated;
        }
        double cx = sumX / count;
        double cy = sumY / count;
        double cz = sumZ / count;
        for (double[] p : rotated.values()) {
            p[0] -= cx;
            p[1] -= cy;
            p[2] -= cz;
        }
        return rotated;
    }

    // -------------------------------------------------------------------------
    // Flat (u,v) view geometry
    // -------------------------------------------------------------------------

    /** Sizes the unwrapped square so that Nu x Nv lattice cells come out square. */
    private void recomputeFlatRect() {
        double availW = Math.max(50, graphPane.getWidth() - 60);
        double availH = Math.max(50, graphPane.getHeight() - 60);
        int nu = getMajorColumns();
        int nv = getMinorColumns();
        double cell = Math.min(availW / nu, availH / nv);
        double w = cell * nu;
        double h = cell * nv;
        flatRect = new double[] {
                (graphPane.getWidth() - w) / 2.0,
                (graphPane.getHeight() - h) / 2.0,
                w, h
        };
    }

    private Rectangle flatClip() {
        double pad = NODE_RADIUS + 2;
        return new Rectangle(flatRect[0] - pad, flatRect[1] - pad,
                flatRect[2] + 2 * pad, flatRect[3] + 2 * pad);
    }

    private double[] flatToScreen(double u, double v) {
        return new double[] {
                flatRect[0] + (wrap2pi(u) / TWO_PI) * flatRect[2],
                flatRect[1] + (wrap2pi(v) / TWO_PI) * flatRect[3]
        };
    }

    private void drawFlatGrid() {
        int nu = getMajorColumns();
        int nv = getMinorColumns();
        Color wire = Color.GRAY.deriveColor(0, 1, 1, 0.4);
        for (int k = 0; k <= nu; k++) {
            double x = flatRect[0] + flatRect[2] * k / nu;
            Line l = new Line(x, flatRect[1], x, flatRect[1] + flatRect[3]);
            l.setStroke(k == 0 || k == nu ? Color.DIMGRAY : wire);
            l.setStrokeWidth(k == 0 || k == nu ? 1.5 : 0.6);
            l.setMouseTransparent(true);
            graphPane.getChildren().add(l);
        }
        for (int j = 0; j <= nv; j++) {
            double y = flatRect[1] + flatRect[3] * j / nv;
            Line l = new Line(flatRect[0], y, flatRect[0] + flatRect[2], y);
            l.setStroke(j == 0 || j == nv ? Color.DIMGRAY : wire);
            l.setStrokeWidth(j == 0 || j == nv ? 1.5 : 0.6);
            l.setMouseTransparent(true);
            graphPane.getChildren().add(l);
        }
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    private void drawTorusOverlay() {
        double[][] view = viewRotation();
        Color wire = Color.GRAY.deriveColor(0, 1, 1, 0.4);

        // Tube rings (constant u) and major circles (constant v), front-facing segments only.
        int ringCount = 16;
        for (int i = 0; i < ringCount; i++) {
            double u = TWO_PI * i / ringCount;
            addSurfaceCurve(view, wire, u, u, 0, TWO_PI, 24, true);
        }
        int loopCount = 8;
        for (int j = 0; j < loopCount; j++) {
            double v = TWO_PI * j / loopCount;
            addSurfaceCurve(view, wire, 0, TWO_PI, v, v, 48, false);
        }

        if (!torusMode) {
            return;
        }
        // Lattice dots on the visible side.
        int nu = getMajorColumns();
        int nv = getMinorColumns();
        for (int k = 0; k < nu; k++) {
            double u = TWO_PI * k / nu;
            for (int j = 0; j < nv; j++) {
                double v = TWO_PI * j / nv;
                if (!isTorusOverlayVisible(view, u, v)) {
                    continue;
                }
                double[] screen = worldToScreen(apply3(view, torusPoint(u, v)));
                Circle dot = new Circle(screen[0], screen[1], 1.8);
                dot.setFill(Color.DARKSLATEGRAY.deriveColor(0, 1, 1, 0.6));
                dot.setMouseTransparent(true);
                graphPane.getChildren().add(dot);
            }
        }
    }

    /**
     * Draws a curve on the torus by sampling between (u0,v0) and (u1,v1), emitting only
     * the segments whose endpoints are both visible under the current occlusion settings.
     */
    private void addSurfaceCurve(double[][] view, Color stroke,
            double u0, double u1, double v0, double v1, int samples, boolean sweepV) {
        double[] prevScreen = null;
        boolean prevFront = false;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double u = sweepV ? u0 : u0 + (u1 - u0) * t;
            double v = sweepV ? v0 + (v1 - v0) * t : v0;
            boolean front = isTorusOverlayVisible(view, u, v);
            double[] screen = worldToScreen(apply3(view, torusPoint(u, v)));
            if (i > 0 && front && prevFront) {
                Line seg = new Line(prevScreen[0], prevScreen[1], screen[0], screen[1]);
                seg.setStroke(stroke);
                seg.setStrokeWidth(0.7);
                seg.setMouseTransparent(true);
                graphPane.getChildren().add(seg);
            }
            prevScreen = screen;
            prevFront = front;
        }
    }

    private void drawGraph(Graph<Integer, DefaultEdge> graph,
            String sourceLine,
            Map<Integer, double[]> screenPositions,
            Map<Integer, Boolean> frontFacing,
            boolean flat) {

        List<ShadedPolygonWrapper> shadedPolygons = new ArrayList<>();
        Map<DefaultEdge, Line> edgeLineMap = new HashMap<>();

        if (flat) {
            drawFlatShapes(graph, sourceLine, screenPositions, shadedPolygons, edgeLineMap);
        } else {
            drawSpatialShapes(graph, sourceLine, screenPositions, frontFacing,
                    shadedPolygons, edgeLineMap);
        }

        Map<Integer, Circle> vertexCircleMap = new HashMap<>();
        Map<Integer, Text> vertexLabelMap = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : screenPositions.entrySet()) {
            int vertex = entry.getKey();
            // Points: Show Hidden Points draws both sides; otherwise face-cull like edges.
            if (!showHiddenPoints() && !frontFacing.getOrDefault(vertex, true)) {
                continue;
            }
            double[] pos = entry.getValue();
            Circle circle = new Circle(pos[0], pos[1], NODE_RADIUS);
            circle.setVisible(showCirclesCheckBox.isSelected());
            applyVertexAppearance(vertex, circle);
            // Dim points that are on the hidden face so they don't fight the front side.
            if (showHiddenPoints() && !frontFacing.getOrDefault(vertex, true)) {
                circle.setOpacity(0.35);
            }
            vertexCircleMap.put(vertex, circle);

            Text text = new Text(pos[0] - NODE_RADIUS / 2, pos[1] + NODE_RADIUS / 2, String.valueOf(vertex));
            text.setVisible(showLabelsCheckBox.isSelected());
            vertexLabelMap.put(vertex, text);

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
                Map<Integer, double[]> startSurface = new HashMap<>();
                Map<Integer, double[]> startScreen = new HashMap<>();
                Map<Integer, double[]> startRotated = new HashMap<>();
                double[] dragCentroid = null;
                if (!torusMode) {
                    dragCentroid = computeRotatedCentroid(cachedBasePositions);
                    Map<Integer, double[]> allRotated = rotateAndCenter3D(cachedBasePositions);
                    for (int dv : dragVertices) {
                        double[] rp = allRotated.get(dv);
                        if (rp != null) {
                            startRotated.put(dv, new double[] { rp[0], rp[1], rp[2] });
                        }
                    }
                }
                for (int dv : dragVertices) {
                    Circle dc = vertexCircleMap.get(dv);
                    if (dc != null) {
                        startScreen.put(dv, new double[] { dc.getCenterX(), dc.getCenterY() });
                    }
                    double[] s = surfacePositions.get(dv);
                    if (torusMode && s != null) {
                        startSurface.put(dv, new double[] { s[0], s[1] });
                    }
                }
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                circle.setUserData(new VertexDragContext(
                        local.getX(), local.getY(),
                        startScreen, startSurface, startRotated, dragCentroid, dragVertices));
            });

            circle.setOnMouseDragged(e -> {
                if (e.isShiftDown()) {
                    return;
                }
                VertexDragContext ctx = (VertexDragContext) circle.getUserData();
                if (ctx == null) {
                    return;
                }
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                if (torusMode && flatViewCheckBox.isSelected()) {
                    dragOnFlat(ctx, local);
                } else if (torusMode) {
                    dragOnTorus(ctx, local);
                } else {
                    dragFree3D(ctx, local);
                }
            });

            circle.setOnMouseReleased(e -> {
                if (e.isShiftDown()) {
                    return;
                }
                VertexDragContext ctx = (VertexDragContext) circle.getUserData();
                if (ctx == null) {
                    return;
                }
                if (torusMode) {
                    if (snapToGridCheckBox.isSelected()) {
                        snapVerticesToTorusGrid(ctx.dragVertices);
                    }
                    // Full redraw so culling, wrapped edges and copies stay consistent.
                    updateGraph();
                }
                circle.setUserData(null);
            });

            graphPane.getChildren().addAll(text, circle);
        }

        currentVertexCircleMap = vertexCircleMap;
        currentVertexLabelMap = vertexLabelMap;
        currentEdgeLineMap = edgeLineMap;
        currentShadedPolygons = shadedPolygons;
        refreshVertexHighlights();
    }

    private void drawSpatialShapes(Graph<Integer, DefaultEdge> graph, String sourceLine,
            Map<Integer, double[]> screenPositions, Map<Integer, Boolean> frontFacing,
            List<ShadedPolygonWrapper> shadedPolygons, Map<DefaultEdge, Line> edgeLineMap) {

        int[][][] polys = GroupExplorer.parseOperationsArr(sourceLine);
        for (int g = 0; g < polys.length; g++) {
            Color fillColor = GROUP_COLORS[g % GROUP_COLORS.length];
            for (int[] polygon : polys[g]) {
                if (!polygonFrontFacing(polygon, frontFacing)) {
                    continue;
                }
                javafx.scene.shape.Polygon shadedPoly = buildShadedPolygon(polygon, screenPositions);
                if (shadedPoly.getPoints().isEmpty()) {
                    continue;
                }
                shadedPoly.setFill(fillColor);
                shadedPoly.setStroke(null);
                shadedPoly.setMouseTransparent(true);
                graphPane.getChildren().add(shadedPoly);
                shadedPolygons.add(new ShadedPolygonWrapper(shadedPoly, polygon));
            }
        }

        for (DefaultEdge edge : graph.edgeSet()) {
            int source = graph.getEdgeSource(edge);
            int target = graph.getEdgeTarget(edge);
            if (!frontFacing.getOrDefault(source, true) || !frontFacing.getOrDefault(target, true)) {
                continue;
            }
            double[] sourcePos = screenPositions.get(source);
            double[] targetPos = screenPositions.get(target);
            if (sourcePos == null || targetPos == null) {
                continue;
            }
            Line line = styledEdgeLine(source, target,
                    sourcePos[0], sourcePos[1], targetPos[0], targetPos[1]);
            edgeLineMap.put(edge, line);
            graphPane.getChildren().add(line);
        }
    }

    /**
     * Flat view: shapes are unrolled across the periodic boundary and redrawn in the
     * neighbouring copies of the fundamental domain so wrap-around is visible.
     */
    private void drawFlatShapes(Graph<Integer, DefaultEdge> graph, String sourceLine,
            Map<Integer, double[]> screenPositions,
            List<ShadedPolygonWrapper> shadedPolygons, Map<DefaultEdge, Line> edgeLineMap) {

        int[][][] polys = GroupExplorer.parseOperationsArr(sourceLine);
        for (int g = 0; g < polys.length; g++) {
            Color fillColor = GROUP_COLORS[g % GROUP_COLORS.length];
            for (int[] polygon : polys[g]) {
                double[][] unrolled = unrollFlat(polygon, screenPositions);
                if (unrolled == null) {
                    continue;
                }
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        double ox = di * flatRect[2];
                        double oy = dj * flatRect[3];
                        if (!touchesFlatRect(unrolled, ox, oy)) {
                            continue;
                        }
                        javafx.scene.shape.Polygon shape = shadedShapeFromPoints(unrolled, ox, oy);
                        if (shape.getPoints().isEmpty()) {
                            continue;
                        }
                        shape.setFill(fillColor);
                        shape.setStroke(null);
                        shape.setMouseTransparent(true);
                        graphPane.getChildren().add(shape);
                        if (di == 0 && dj == 0) {
                            shadedPolygons.add(new ShadedPolygonWrapper(shape, polygon));
                        }
                    }
                }
            }
        }

        for (DefaultEdge edge : graph.edgeSet()) {
            int source = graph.getEdgeSource(edge);
            int target = graph.getEdgeTarget(edge);
            double[] a = screenPositions.get(source);
            double[] b = screenPositions.get(target);
            if (a == null || b == null) {
                continue;
            }
            double bx = a[0] + wrapSpan(b[0] - a[0], flatRect[2]);
            double by = a[1] + wrapSpan(b[1] - a[1], flatRect[3]);
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    double ox = di * flatRect[2];
                    double oy = dj * flatRect[3];
                    double[][] pts = { { a[0], a[1] }, { bx, by } };
                    if (!touchesFlatRect(pts, ox, oy)) {
                        continue;
                    }
                    Line line = styledEdgeLine(source, target,
                            a[0] + ox, a[1] + oy, bx + ox, by + oy);
                    graphPane.getChildren().add(line);
                    if (di == 0 && dj == 0) {
                        edgeLineMap.put(edge, line);
                    }
                }
            }
        }
    }

    /** Unrolls a polygon's flat positions so consecutive vertices take the short way around. */
    private double[][] unrollFlat(int[] polygon, Map<Integer, double[]> screenPositions) {
        double[][] pts = new double[polygon.length][2];
        double[] first = screenPositions.get(polygon[0]);
        if (first == null) {
            return null;
        }
        pts[0][0] = first[0];
        pts[0][1] = first[1];
        for (int i = 1; i < polygon.length; i++) {
            double[] p = screenPositions.get(polygon[i]);
            if (p == null) {
                return null;
            }
            pts[i][0] = pts[i - 1][0] + wrapSpan(p[0] - pts[i - 1][0], flatRect[2]);
            pts[i][1] = pts[i - 1][1] + wrapSpan(p[1] - pts[i - 1][1], flatRect[3]);
        }
        return pts;
    }

    private boolean touchesFlatRect(double[][] pts, double ox, double oy) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double[] p : pts) {
            minX = Math.min(minX, p[0] + ox);
            maxX = Math.max(maxX, p[0] + ox);
            minY = Math.min(minY, p[1] + oy);
            maxY = Math.max(maxY, p[1] + oy);
        }
        return maxX >= flatRect[0] && minX <= flatRect[0] + flatRect[2]
                && maxY >= flatRect[1] && minY <= flatRect[1] + flatRect[3];
    }

    private javafx.scene.shape.Polygon shadedShapeFromPoints(double[][] pts, double ox, double oy) {
        javafx.scene.shape.Polygon shape = new javafx.scene.shape.Polygon();
        if (pts.length == 2) {
            double dx = pts[1][0] - pts[0][0];
            double dy = pts[1][1] - pts[0][1];
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length < 1e-9) {
                return shape;
            }
            double px = -(dy / length) * SHADED_EDGE_HALF_WIDTH;
            double py = (dx / length) * SHADED_EDGE_HALF_WIDTH;
            shape.getPoints().addAll(
                    pts[0][0] + ox + px, pts[0][1] + oy + py,
                    pts[0][0] + ox - px, pts[0][1] + oy - py,
                    pts[1][0] + ox - px, pts[1][1] + oy - py,
                    pts[1][0] + ox + px, pts[1][1] + oy + py);
        } else {
            for (double[] p : pts) {
                shape.getPoints().addAll(p[0] + ox, p[1] + oy);
            }
        }
        return shape;
    }

    private Line styledEdgeLine(int source, int target, double x1, double y1, double x2, double y2) {
        Line line = new Line(x1, y1, x2, y2);
        String key = Math.min(source, target) + "-" + Math.max(source, target);
        Integer freq = edgeFrequencyMap.get(key);
        if (freq != null && freq > 1) {
            line.setStroke(Color.RED);
            line.setStrokeWidth(2.0);
        } else {
            line.setStroke(Color.BLACK);
            line.setStrokeWidth(1.0);
        }
        line.setMouseTransparent(true);
        return line;
    }

    private boolean polygonFrontFacing(int[] polygon, Map<Integer, Boolean> frontFacing) {
        for (int v : polygon) {
            if (!frontFacing.getOrDefault(v, true)) {
                return false;
            }
        }
        return true;
    }

    private javafx.scene.shape.Polygon buildShadedPolygon(int[] polygon,
            Map<Integer, double[]> screenPositions) {
        double[][] pts = new double[polygon.length][2];
        for (int i = 0; i < polygon.length; i++) {
            double[] p = screenPositions.get(polygon[i]);
            if (p == null) {
                return new javafx.scene.shape.Polygon();
            }
            pts[i][0] = p[0];
            pts[i][1] = p[1];
        }
        return shadedShapeFromPoints(pts, 0, 0);
    }

    private void updateShadedPolygons() {
        boolean flat = torusMode && flatViewCheckBox.isSelected();
        for (ShadedPolygonWrapper wrapper : currentShadedPolygons) {
            double[][] pts = new double[wrapper.vertices.length][2];
            boolean ok = true;
            for (int i = 0; i < wrapper.vertices.length; i++) {
                Circle c = currentVertexCircleMap.get(wrapper.vertices[i]);
                if (c == null) {
                    ok = false;
                    break;
                }
                if (i == 0 || !flat) {
                    pts[i][0] = c.getCenterX();
                    pts[i][1] = c.getCenterY();
                } else {
                    pts[i][0] = pts[i - 1][0] + wrapSpan(c.getCenterX() - pts[i - 1][0], flatRect[2]);
                    pts[i][1] = pts[i - 1][1] + wrapSpan(c.getCenterY() - pts[i - 1][1], flatRect[3]);
                }
            }
            if (!ok) {
                continue;
            }
            javafx.scene.shape.Polygon rebuilt = shadedShapeFromPoints(pts, 0, 0);
            wrapper.polygon.getPoints().setAll(rebuilt.getPoints());
        }
    }

    // -------------------------------------------------------------------------
    // Dragging
    // -------------------------------------------------------------------------

    private void dragOnTorus(VertexDragContext ctx, Point2D local) {
        double deltaX = local.getX() - ctx.startLocalX;
        double deltaY = local.getY() - ctx.startLocalY;
        double[][] view = viewRotation();
        for (int v : ctx.dragVertices) {
            double[] start = ctx.startSurface.get(v);
            if (start == null) {
                continue;
            }
            // Per-vertex Jacobian so the far/inner tube doesn't invert Δy → Δv.
            double[] duv = screenDeltaToSurfaceDelta(view, start[0], start[1], deltaX, deltaY);
            surfacePositions.put(v, new double[] {
                    wrap2pi(start[0] + duv[0]),
                    wrap2pi(start[1] + duv[1])
            });
        }
        for (int v : ctx.dragVertices) {
            double[] s = surfacePositions.get(v);
            if (s == null) {
                continue;
            }
            boolean faceFront = isFacingCamera(apply3(view, torusNormal(s[0], s[1]))[2]);
            boolean front = showHiddenPoints() || faceFront;
            double[] screen = worldToScreen(apply3(view, torusPoint(s[0], s[1])));
            applyDraggedVertexVisual(v, screen, front);
            Circle c = currentVertexCircleMap.get(v);
            if (c != null && showHiddenPoints()) {
                c.setOpacity(faceFront ? 1.0 : 0.35);
            }
        }
        updateEdgeEndpoints();
    }

    private void dragOnFlat(VertexDragContext ctx, Point2D local) {
        double deltaX = local.getX() - ctx.startLocalX;
        double deltaY = local.getY() - ctx.startLocalY;
        for (int v : ctx.dragVertices) {
            double[] start = ctx.startSurface.get(v);
            if (start == null) {
                continue;
            }
            double u = start[0] + (deltaX / flatRect[2]) * TWO_PI;
            double vv = start[1] + (deltaY / flatRect[3]) * TWO_PI;
            surfacePositions.put(v, new double[] { wrap2pi(u), wrap2pi(vv) });
            applyDraggedVertexVisual(v, flatToScreen(u, vv), true);
        }
        updateEdgeEndpoints();
    }

    private void applyDraggedVertexVisual(int vertex, double[] screen, boolean front) {
        Circle c = currentVertexCircleMap.get(vertex);
        Text t = currentVertexLabelMap.get(vertex);
        if (!front) {
            if (c != null) {
                c.setVisible(false);
            }
            if (t != null) {
                t.setVisible(false);
            }
            return;
        }
        if (c != null) {
            c.setCenterX(screen[0]);
            c.setCenterY(screen[1]);
            c.setVisible(showCirclesCheckBox.isSelected());
        }
        if (t != null) {
            t.setX(screen[0] - NODE_RADIUS / 2);
            t.setY(screen[1] + NODE_RADIUS / 2);
            t.setVisible(showLabelsCheckBox.isSelected());
        }
    }

    private void dragFree3D(VertexDragContext ctx, Point2D local) {
        double deltaX = local.getX() - ctx.startLocalX;
        double deltaY = local.getY() - ctx.startLocalY;
        double cx = graphPane.getWidth() / 2.0;
        double cy = graphPane.getHeight() / 2.0;
        double[] centroid = ctx.startCentroid != null ? ctx.startCentroid : new double[] { 0, 0, 0 };
        double[][] inv = transpose3(trackballRotation);

        for (int v : ctx.dragVertices) {
            double[] startScreen = ctx.startScreen.get(v);
            double[] rp = ctx.startRotated.get(v);
            if (startScreen == null || rp == null) {
                continue;
            }
            double nx = startScreen[0] + deltaX;
            double ny = startScreen[1] + deltaY;
            applyDraggedVertexVisual(v, new double[] { nx, ny }, true);

            // rotateAndCenter: R*base - centroid. Invert with frozen start centroid/z.
            double[] abs = { (nx - cx) + centroid[0], (ny - cy) + centroid[1], rp[2] + centroid[2] };
            cachedBasePositions.put(v, apply3(inv, abs));
        }
        updateEdgeEndpoints();
    }

    private double[] computeRotatedCentroid(Map<Integer, double[]> base) {
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (double[] p : base.values()) {
            double[] r = apply3(trackballRotation,
                    new double[] { p[0], p[1], p.length > 2 ? p[2] : 0 });
            sumX += r[0];
            sumY += r[1];
            sumZ += r[2];
            count++;
        }
        if (count == 0) {
            return new double[] { 0, 0, 0 };
        }
        return new double[] { sumX / count, sumY / count, sumZ / count };
    }

    private void updateEdgeEndpoints() {
        if (currentGraph == null) {
            return;
        }
        boolean flat = torusMode && flatViewCheckBox.isSelected();
        for (Map.Entry<DefaultEdge, Line> entry : currentEdgeLineMap.entrySet()) {
            DefaultEdge edge = entry.getKey();
            Line line = entry.getValue();
            Circle cs = currentVertexCircleMap.get(currentGraph.getEdgeSource(edge));
            Circle ct = currentVertexCircleMap.get(currentGraph.getEdgeTarget(edge));
            if (cs == null || ct == null) {
                continue;
            }
            double endX = ct.getCenterX();
            double endY = ct.getCenterY();
            if (flat) {
                endX = cs.getCenterX() + wrapSpan(endX - cs.getCenterX(), flatRect[2]);
                endY = cs.getCenterY() + wrapSpan(endY - cs.getCenterY(), flatRect[3]);
            }
            line.setStartX(cs.getCenterX());
            line.setStartY(cs.getCenterY());
            line.setEndX(endX);
            line.setEndY(endY);
        }
        updateShadedPolygons();
    }

    // -------------------------------------------------------------------------
    // Snapping / relaxation
    // -------------------------------------------------------------------------

    private void snapVerticesToTorusGrid(Set<Integer> vertices) {
        double stepU = TWO_PI / getMajorColumns();
        double stepV = TWO_PI / getMinorColumns();
        for (int v : vertices) {
            double[] s = surfacePositions.get(v);
            if (s == null) {
                continue;
            }
            surfacePositions.put(v, new double[] {
                    wrap2pi(Math.round(s[0] / stepU) * stepU),
                    wrap2pi(Math.round(s[1] / stepV) * stepV)
            });
        }
    }

    /**
     * Fruchterman-Reingold in the torus's flat metric: distances are measured in arc length
     * with periodic wrap on both axes, so the relaxation never fights the seam.
     */
    private void relaxOnTorus(int iterations) {
        int n = surfacePositions.size();
        if (n < 2 || currentGraph == null) {
            return;
        }
        List<Integer> ids = new ArrayList<>(surfacePositions.keySet());
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            index.put(ids.get(i), i);
        }

        double periodX = TWO_PI * majorRadius;
        double periodY = TWO_PI * minorRadius;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double[] s = surfacePositions.get(ids.get(i));
            xs[i] = s[0] * majorRadius;
            ys[i] = s[1] * minorRadius;
        }

        List<int[]> edges = new ArrayList<>();
        for (DefaultEdge e : currentGraph.edgeSet()) {
            Integer a = index.get(currentGraph.getEdgeSource(e));
            Integer b = index.get(currentGraph.getEdgeTarget(e));
            if (a != null && b != null) {
                edges.add(new int[] { a, b });
            }
        }

        double ideal = Math.sqrt((periodX * periodY) / n);
        double temperature = ideal;
        double cooling = Math.pow(0.02, 1.0 / Math.max(1, iterations));
        double[] dx = new double[n];
        double[] dy = new double[n];

        for (int iter = 0; iter < iterations; iter++) {
            java.util.Arrays.fill(dx, 0);
            java.util.Arrays.fill(dy, 0);

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ddx = wrapSpan(xs[j] - xs[i], periodX);
                    double ddy = wrapSpan(ys[j] - ys[i], periodY);
                    double dist = Math.sqrt(ddx * ddx + ddy * ddy);
                    if (dist > 2.5 * ideal) {
                        continue;
                    }
                    dist = Math.max(dist, 1e-6);
                    double force = (ideal * ideal) / dist;
                    double ux = ddx / dist;
                    double uy = ddy / dist;
                    dx[i] -= ux * force;
                    dy[i] -= uy * force;
                    dx[j] += ux * force;
                    dy[j] += uy * force;
                }
            }

            for (int[] e : edges) {
                double ddx = wrapSpan(xs[e[1]] - xs[e[0]], periodX);
                double ddy = wrapSpan(ys[e[1]] - ys[e[0]], periodY);
                double dist = Math.max(Math.sqrt(ddx * ddx + ddy * ddy), 1e-6);
                double force = (dist * dist) / ideal;
                double ux = ddx / dist;
                double uy = ddy / dist;
                dx[e[0]] += ux * force;
                dy[e[0]] += uy * force;
                dx[e[1]] -= ux * force;
                dy[e[1]] -= uy * force;
            }

            for (int i = 0; i < n; i++) {
                double mag = Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]);
                if (mag < 1e-9) {
                    continue;
                }
                double step = Math.min(mag, temperature);
                xs[i] = wrapPositive(xs[i] + dx[i] / mag * step, periodX);
                ys[i] = wrapPositive(ys[i] + dy[i] / mag * step, periodY);
            }
            temperature *= cooling;
        }

        for (int i = 0; i < n; i++) {
            surfacePositions.put(ids.get(i), new double[] {
                    wrap2pi(xs[i] / majorRadius), wrap2pi(ys[i] / minorRadius) });
        }
    }

    // -------------------------------------------------------------------------
    // Scale
    // -------------------------------------------------------------------------

    private void scalePointCoordinates(double factor) {
        if (torusMode) {
            majorRadius = Math.max(MIN_MAJOR_RADIUS, majorRadius * factor);
            minorRadius = Math.max(MIN_MINOR_RADIUS, minorRadius * factor);
            syncRadiusFields();
            updateGraph();
            return;
        }
        if (cachedBasePositions.isEmpty()) {
            return;
        }
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (double[] p : cachedBasePositions.values()) {
            sumX += p[0];
            sumY += p[1];
            sumZ += p.length > 2 ? p[2] : 0;
            count++;
        }
        double cx = sumX / count;
        double cy = sumY / count;
        double cz = sumZ / count;
        for (double[] p : cachedBasePositions.values()) {
            p[0] = cx + (p[0] - cx) * factor;
            p[1] = cy + (p[1] - cy) * factor;
            if (p.length > 2) {
                p[2] = cz + (p[2] - cz) * factor;
            }
        }
        updateGraph();
    }

    // -------------------------------------------------------------------------
    // Graph I/O
    // -------------------------------------------------------------------------

    private void closeGraphSource() {
        if (pbinFile != null) {
            try {
                pbinFile.close();
            } catch (IOException ignored) {
            }
            pbinFile = null;
        }
    }

    private int graphLineCount() {
        if (pbinFile != null) {
            return pbinFile.size();
        }
        return graphLines.size();
    }

    private boolean graphLinesEmpty() {
        return graphLineCount() == 0;
    }

    private String getGraphLine(int index) {
        try {
            if (pbinFile != null) {
                return pbinFile.get(index);
            }
            return graphLines.get(index);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read generator " + index, e);
        }
    }

    private void readGraphLinesFromFile(String path) {
        System.out.println("Reading file: " + path);
        closeGraphSource();
        graphLines = Collections.emptyList();
        cachedGraphKey = null;
        if (path.endsWith(".pbin")) {
            try {
                pbinFile = PbinFile.open(path);
            } catch (Exception e) {
                System.err.println("Failed to read PBIN file: " + e.getMessage());
            }
            return;
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    int commentPos = line.indexOf("#");
                    if (commentPos > 0) {
                        line = line.substring(0, commentPos).trim();
                    }
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + path + " " + e.getMessage());
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
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File selected = fileChooser.showSaveDialog(owner);
        if (selected == null) {
            return;
        }
        String outputPath = selected.getAbsolutePath();
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
        }
    }

    private Graph<Integer, DefaultEdge> buildGraphFromLine(String line) {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        edgeFrequencyMap = new HashMap<>();
        vertexFrequencyMap = new HashMap<>();
        int[][][] combinedGen = GroupExplorer.parseOperationsArr(line);
        for (int[][] cycle : combinedGen) {
            for (int[] polygon : cycle) {
                for (int vertex : polygon) {
                    graph.addVertex(vertex);
                    vertexFrequencyMap.put(vertex, vertexFrequencyMap.getOrDefault(vertex, 0) + 1);
                }
                for (int i = 0; i < polygon.length; i++) {
                    int a = polygon[i];
                    int b = polygon[(i + 1) % polygon.length];
                    String key = Math.min(a, b) + "-" + Math.max(a, b);
                    if (polygon.length > 2 || i == 0) {
                        edgeFrequencyMap.put(key, edgeFrequencyMap.getOrDefault(key, 0) + 1);
                    }
                    graph.addEdge(a, b);
                }
            }
        }
        return graph;
    }

    private String buildLayoutCacheKey(String currentLine) {
        return currentLine + "|seed=" + seedTextField.getText()
                + "|iters=" + itersTextField.getText()
                + "|JavaNetworkx";
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    private void showTorusCoordinatesDialog(Stage owner) {
        StringBuilder sb = new StringBuilder();
        if (!torusMode) {
            sb.append("# free 3D layout: vertex x y z\n");
            List<Integer> keys = new ArrayList<>(cachedBasePositions.keySet());
            Collections.sort(keys);
            for (int v : keys) {
                double[] p = cachedBasePositions.get(v);
                sb.append(v).append(' ').append(p[0]).append(' ').append(p[1]).append(' ')
                        .append(p.length > 2 ? p[2] : 0).append('\n');
            }
        } else {
            int nu = getMajorColumns();
            int nv = getMinorColumns();
            sb.append("# torus R=").append(majorRadius).append(" r=").append(minorRadius)
                    .append(" lattice ").append(nu).append('x').append(nv).append('\n');
            sb.append("# vertex u v cellU cellV x y z\n");
            List<Integer> keys = new ArrayList<>(surfacePositions.keySet());
            Collections.sort(keys);
            for (int v : keys) {
                double[] s = surfacePositions.get(v);
                double[] w = torusPoint(s[0], s[1]);
                long cu = Math.floorMod(Math.round(s[0] / (TWO_PI / nu)), nu);
                long cv = Math.floorMod(Math.round(s[1] / (TWO_PI / nv)), nv);
                sb.append(v).append(' ').append(s[0]).append(' ').append(s[1]).append(' ')
                        .append(cu).append(' ').append(cv).append(' ')
                        .append(w[0]).append(' ').append(w[1]).append(' ').append(w[2]).append('\n');
            }
        }
        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setPrefSize(560, 420);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        VBox box = new VBox(10, area, copy);
        box.setStyle("-fx-padding: 10;");
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Torus Coordinates");
        dialog.setScene(new Scene(box));
        dialog.show();
    }

    private void showEditCoordinatesDialog(Stage owner) {
        if (!torusMode) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Edit Coordinates");
            alert.setHeaderText(null);
            alert.setContentText("Coordinate editing is available in Torus Mode (u v per vertex).");
            alert.showAndWait();
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<Integer> keys = new ArrayList<>(surfacePositions.keySet());
        Collections.sort(keys);
        for (int v : keys) {
            double[] s = surfacePositions.get(v);
            sb.append(v).append(' ').append(s[0]).append(' ').append(s[1]).append('\n');
        }
        TextArea area = new TextArea(sb.toString());
        area.setPrefSize(500, 400);
        Button apply = new Button("Apply");
        Stage dialog = new Stage();
        apply.setOnAction(e -> {
            try {
                Map<Integer, double[]> parsed = parseSurfaceCoordinates(area.getText());
                if (!parsed.keySet().equals(surfacePositions.keySet())) {
                    throw new IllegalArgumentException("Vertex set must match the graph.");
                }
                surfacePositions = parsed;
                updateGraph();
                dialog.close();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Coordinates");
                alert.setHeaderText(null);
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });
        VBox box = new VBox(10, new Label("vertex u v  (radians, wrapped to [0, 2pi))"), area, apply);
        box.setStyle("-fx-padding: 10;");
        dialog.initOwner(owner);
        dialog.setTitle("Edit Surface Coordinates");
        dialog.setScene(new Scene(box));
        dialog.show();
    }

    private Map<Integer, double[]> parseSurfaceCoordinates(String text) {
        Map<Integer, double[]> result = new HashMap<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash).trim();
            }
            String[] parts = line.replace(',', ' ').split("\\s+");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Expected: vertex u v");
            }
            result.put(Integer.parseInt(parts[0]), new double[] {
                    wrap2pi(Double.parseDouble(parts[1])),
                    wrap2pi(Double.parseDouble(parts[2]))
            });
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Appearance / selection helpers
    // -------------------------------------------------------------------------

    private void applyVertexAppearance(int vertex, Circle circle) {
        if (selectedVertices.contains(vertex)) {
            circle.setFill(Color.DODGERBLUE.deriveColor(0, 1, 1, 0.6));
            circle.setStroke(Color.DODGERBLUE);
            circle.setStrokeWidth(2.0);
        } else {
            int freq = vertexFrequencyMap.getOrDefault(vertex, 1);
            Color color = getVertexColor(freq);
            circle.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5));
            circle.setStroke(null);
            circle.setStrokeWidth(0);
        }
    }

    private Color getVertexColor(int frequency) {
        if (frequency >= 4) {
            return Color.DARKGREEN;
        } else if (frequency == 3) {
            return Color.DARKORANGE;
        } else if (frequency == 2) {
            return Color.DARKRED;
        }
        return Color.BLACK;
    }

    private void refreshVertexHighlights() {
        for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
            applyVertexAppearance(entry.getKey(), entry.getValue());
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
        marqueeActive = false;
        if (marqueeRectangle != null) {
            double x = marqueeRectangle.getX();
            double y = marqueeRectangle.getY();
            double w = marqueeRectangle.getWidth();
            double h = marqueeRectangle.getHeight();
            selectedVertices.clear();
            for (Map.Entry<Integer, Circle> entry : currentVertexCircleMap.entrySet()) {
                Circle c = entry.getValue();
                if (c.getCenterX() >= x && c.getCenterX() <= x + w
                        && c.getCenterY() >= y && c.getCenterY() <= y + h) {
                    selectedVertices.add(entry.getKey());
                }
            }
            graphPane.getChildren().remove(marqueeRectangle);
        }
        refreshVertexHighlights();
    }

    // -------------------------------------------------------------------------
    // Math / util
    // -------------------------------------------------------------------------

    private int getMajorColumns() {
        return Math.max(3, (int) Math.round(
                parseDouble(majorColumnsTextField.getText(), DEFAULT_MAJOR_COLUMNS)));
    }

    private int getMinorColumns() {
        return Math.max(3, (int) Math.round(
                parseDouble(minorColumnsTextField.getText(), DEFAULT_MINOR_COLUMNS)));
    }

    /** Shortest signed offset for a quantity living on a circle of the given period. */
    private static double wrapSpan(double value, double period) {
        return value - period * Math.round(value / period);
    }

    private static double wrapPositive(double value, double period) {
        return value - period * Math.floor(value / period);
    }

    private static double wrap2pi(double angle) {
        return wrapPositive(angle, TWO_PI);
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[][] identity3() {
        return new double[][] { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
    }

    private static double[] apply3(double[][] m, double[] p) {
        return new double[] {
                m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2],
                m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2],
                m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2]
        };
    }

    private double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1.0 - c;
        double[][] r = new double[3][3];
        r[0][0] = t * ax * ax + c;
        r[0][1] = t * ax * ay - s * az;
        r[0][2] = t * ax * az + s * ay;
        r[1][0] = t * ay * ax + s * az;
        r[1][1] = t * ay * ay + c;
        r[1][2] = t * ay * az - s * ax;
        r[2][0] = t * az * ax - s * ay;
        r[2][1] = t * az * ay + s * ax;
        r[2][2] = t * az * az + c;
        return r;
    }

    private double[][] multiply3(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return c;
    }

    private static double[][] transpose3(double[][] m) {
        return new double[][] {
                { m[0][0], m[1][0], m[2][0] },
                { m[0][1], m[1][1], m[2][1] },
                { m[0][2], m[1][2], m[2][2] }
        };
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

    private static class ShadedPolygonWrapper {
        final javafx.scene.shape.Polygon polygon;
        final int[] vertices;

        ShadedPolygonWrapper(javafx.scene.shape.Polygon polygon, int[] vertices) {
            this.polygon = polygon;
            this.vertices = vertices;
        }
    }

    private static class VertexDragContext {
        final double startLocalX;
        final double startLocalY;
        final Map<Integer, double[]> startScreen;
        final Map<Integer, double[]> startSurface;
        final Map<Integer, double[]> startRotated;
        final double[] startCentroid;
        final Set<Integer> dragVertices;

        VertexDragContext(double startLocalX, double startLocalY,
                Map<Integer, double[]> startScreen, Map<Integer, double[]> startSurface,
                Map<Integer, double[]> startRotated, double[] startCentroid,
                Set<Integer> dragVertices) {
            this.startLocalX = startLocalX;
            this.startLocalY = startLocalY;
            this.startScreen = startScreen;
            this.startSurface = startSurface;
            this.startRotated = startRotated;
            this.startCentroid = startCentroid;
            this.dragVertices = dragVertices;
        }
    }
}
