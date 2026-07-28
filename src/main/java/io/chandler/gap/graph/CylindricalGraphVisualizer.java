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
 * Networkx-only graph visualizer with irreversible cylindrical snap mode.
 *
 * Phase A (free 3D): trackball + shift-scroll to align the force-directed cloud
 * with a preview cylinder.
 * Phase B (cylinder): project onto a vertical cylinder; yaw-only spin; drag on
 * the surface; optional snap to an N-column lattice. Back faces are culled.
 */
public class CylindricalGraphVisualizer extends Application {

    private static final double NODE_RADIUS = 8.0;
    /** Half-thickness of shaded 2-cycle strips; kept in proportion to node handles. */
    private static final double SHADED_EDGE_HALF_WIDTH = NODE_RADIUS * 0.55;
    private static final double MIN_CYLINDER_RADIUS = 20.0;
    private static final int DEFAULT_CIRC_COLUMNS = 24;
    private static final String DEFAULT_FILE =
            "PlanarStudy/td42/d60-np-2-cycles-2-cycles-2-cycles_R1-filtered.txt.pbin.survivors.txt";

    private String filePath;
    private List<String> graphLines = Collections.emptyList();
    private PbinFile pbinFile;
    private int currentGraphIndex = 0;

    private Map<String, Integer> edgeFrequencyMap;
    private Map<Integer, Integer> vertexFrequencyMap;

    private Pane graphPane;
    private Label pageLabel;
    private TextField pageIndexTextField;
    private TextField seedTextField;
    private TextField itersTextField;
    private TextField circColumnsTextField;

    private CheckBox showCirclesCheckBox;
    private CheckBox showLabelsCheckBox;
    private CheckBox snapToGridCheckBox;
    private CheckBox showCylinderGridCheckBox;
    private CheckBox cylinderModeCheckBox;

    private final JavaNetworkx layoutAlgo = new JavaNetworkx();

    /** Accumulated free-mode trackball (identity after entering cylinder mode). */
    private double[][] trackballRotation = identity3();
    private Double lastMouseX = null;
    private Double lastMouseY = null;

    private double scale = 1.0;

    private String cachedGraphKey = null;
    private Map<Integer, double[]> cachedBasePositions = new HashMap<>();

    // --- Cylinder state ---
    private boolean cylinderMode = false;
    private double cylinderRadius = 100.0;
    private double cylinderYaw = 0.0;
    /** Continuous surface coords: [theta, y] per vertex. */
    private Map<Integer, double[]> surfacePositions = new HashMap<>();
    /** World-space 3D after yaw: [x, y, z] for culling / drawing. */
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

    public static void main(String[] args) {
        if (args.length > 0) {
            launch(args);
        } else if (new File(DEFAULT_FILE).exists()) {
            launch(new String[] { DEFAULT_FILE });
        } else {
            launch(new String[] {});
        }
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

        seedTextField = new TextField("6307");
        itersTextField = new TextField("7000");
        circColumnsTextField = new TextField(String.valueOf(DEFAULT_CIRC_COLUMNS));
        for (TextField tf : new TextField[] { seedTextField, itersTextField, circColumnsTextField }) {
            tf.setPrefWidth(70);
        }
        seedTextField.setOnKeyReleased(e -> {
            if (!cylinderMode) {
                updateGraph();
            }
        });
        itersTextField.setOnKeyReleased(e -> {
            if (!cylinderMode) {
                updateGraph();
            }
        });
        circColumnsTextField.setOnKeyReleased(e -> updateGraph());

        showCirclesCheckBox = new CheckBox("Show Circles");
        showCirclesCheckBox.setSelected(true);
        showCirclesCheckBox.setOnAction(e -> updateGraph());

        showLabelsCheckBox = new CheckBox("Show Labels");
        showLabelsCheckBox.setSelected(false);
        showLabelsCheckBox.setOnAction(e -> updateGraph());

        snapToGridCheckBox = new CheckBox("Snap to Grid");
        snapToGridCheckBox.setSelected(false);

        showCylinderGridCheckBox = new CheckBox("Show Cylinder Grid");
        showCylinderGridCheckBox.setSelected(true);
        showCylinderGridCheckBox.setOnAction(e -> updateGraph());

        cylinderModeCheckBox = new CheckBox("Cylinder Mode");
        cylinderModeCheckBox.setSelected(false);
        cylinderModeCheckBox.setOnAction(e -> {
            if (cylinderModeCheckBox.isSelected() && !cylinderMode) {
                enterCylinderMode();
            } else if (!cylinderModeCheckBox.isSelected() && cylinderMode) {
                // Irreversible: force the checkbox back on.
                cylinderModeCheckBox.setSelected(true);
            }
        });

        Button randomizeButton = new Button("Randomize");
        randomizeButton.setOnAction(e -> {
            if (cylinderMode) {
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
                resetCylinderState();
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

        Button coords3DButton = new Button("3D Coordinates");
        coords3DButton.setOnAction(e -> show3DCoordinatesDialog(primaryStage));

        Button controlsButton = new Button("Controls");
        controlsButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Controls");
            alert.setHeaderText(null);
            alert.setContentText(
                    "Free 3D mode:\n"
                    + "  Right-drag: trackball rotate\n"
                    + "  Shift+scroll: scale coordinates\n"
                    + "  Scroll: canvas zoom\n"
                    + "  Flip Cylinder Mode to project onto cylinder (irreversible)\n\n"
                    + "Cylinder mode:\n"
                    + "  Right-drag left/right: spin cylinder (yaw)\n"
                    + "  Left-drag node: slide on cylinder surface\n"
                    + "  Snap to Grid: snap to N-column lattice\n"
                    + "  Changing N only changes grid density");
            alert.showAndWait();
        });

        HBox topControls = new HBox(10, randomizeButton, loadButton, exportButton,
                editCoordsButton, coords3DButton, controlsButton);
        topControls.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setTop(topControls);

        VBox sidebar = new VBox(10,
                new Label("Layout: Java Networkx"),
                new Label("Seed:"), seedTextField,
                new Label("Iters:"), itersTextField,
                new Label("N (circ columns):"), circColumnsTextField,
                showCirclesCheckBox,
                showLabelsCheckBox,
                snapToGridCheckBox,
                showCylinderGridCheckBox,
                cylinderModeCheckBox);
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-padding: 10;");
        root.setRight(sidebar);

        Button prevButton = new Button("Previous");
        Button nextButton = new Button("Next");
        prevButton.setOnAction(e -> {
            if (currentGraphIndex > 0) {
                resetCylinderState();
                currentGraphIndex--;
                pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
                updateGraph();
            }
        });
        nextButton.setOnAction(e -> {
            if (currentGraphIndex < graphLineCount() - 1) {
                resetCylinderState();
                currentGraphIndex++;
                pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
                updateGraph();
            }
        });
        pageIndexTextField.setOnAction(e -> {
            try {
                int idx = Integer.parseInt(pageIndexTextField.getText().trim()) - 1;
                if (idx >= 0 && idx < graphLineCount()) {
                    resetCylinderState();
                    currentGraphIndex = idx;
                    updateGraph();
                }
            } catch (NumberFormatException ignored) {
            }
        });

        HBox paginator = new HBox(10, prevButton, pageIndexTextField, pageLabel, nextButton);
        paginator.setStyle("-fx-padding: 10; -fx-alignment: center;");
        root.setBottom(paginator);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Cylindrical Graph Visualizer");
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
            if (!e.isSecondaryButtonDown()) {
                lastMouseX = null;
                lastMouseY = null;
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
            deltaX = -deltaX;
            double sensitivity = 0.01;

            if (cylinderMode) {
                // Yaw only: spin cylinder + graph around Y.
                cylinderYaw += deltaX * sensitivity;
                updateGraph();
            } else {
                double[][] Ry = rotationFromAxisAngle(0, 1, 0, deltaX * sensitivity);
                double[][] Rx = rotationFromAxisAngle(1, 0, 0, deltaY * sensitivity);
                trackballRotation = multiply3(Rx, multiply3(Ry, trackballRotation));
                updateGraph();
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
    // Cylinder mode entry
    // -------------------------------------------------------------------------

    private void resetCylinderState() {
        cylinderMode = false;
        cylinderYaw = 0.0;
        surfacePositions = new HashMap<>();
        worldPositions = new HashMap<>();
        trackballRotation = identity3();
        if (cylinderModeCheckBox != null) {
            cylinderModeCheckBox.setSelected(false);
            cylinderModeCheckBox.setDisable(false);
        }
    }

    private void enterCylinderMode() {
        if (graphLinesEmpty() || cachedBasePositions.isEmpty()) {
            cylinderModeCheckBox.setSelected(false);
            return;
        }

        Map<Integer, double[]> rotated = rotateAndCenter3D(cachedBasePositions);
        double sumR = 0;
        int count = 0;
        for (double[] p : rotated.values()) {
            sumR += Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            count++;
        }
        cylinderRadius = count == 0 ? MIN_CYLINDER_RADIUS
                : Math.max(MIN_CYLINDER_RADIUS, sumR / count);

        surfacePositions = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : rotated.entrySet()) {
            double[] p = entry.getValue();
            double theta = Math.atan2(p[0], p[2]); // x = R sin θ, z = R cos θ
            surfacePositions.put(entry.getKey(), new double[] { theta, p[1] });
        }

        cylinderMode = true;
        cylinderYaw = 0.0;
        trackballRotation = identity3();
        cylinderModeCheckBox.setDisable(true);
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

        if (!cylinderMode) {
            String cacheKey = buildLayoutCacheKey(currentLine);
            if (cachedGraphKey == null || !cachedGraphKey.equals(cacheKey)) {
                EnumMap<LayoutAlgoArg, Double> args = new EnumMap<>(LayoutAlgoArg.class);
                args.put(LayoutAlgoArg.SEED, parseDouble(seedTextField.getText(), 6307));
                args.put(LayoutAlgoArg.ITERS, parseDouble(itersTextField.getText(), 7000));
                double box = Math.min(graphPane.getWidth(), graphPane.getHeight());
                if (box < 10) {
                    box = 600;
                }
                layoutAlgo.performLayout(box, currentLine, currentGraph, args);
                cachedBasePositions = copyPositions(layoutAlgo.getResult());
                cachedGraphKey = cacheKey;
            }
        }

        Map<Integer, double[]> screenPositions;
        Map<Integer, Boolean> frontFacing = new HashMap<>();

        if (cylinderMode) {
            rebuildWorldFromSurface();
            screenPositions = projectWorldToScreen(worldPositions, frontFacing);
        } else {
            Map<Integer, double[]> rotated = rotateAndCenter3D(cachedBasePositions);
            estimatePreviewRadius(rotated);
            screenPositions = new HashMap<>();
            for (Map.Entry<Integer, double[]> entry : rotated.entrySet()) {
                double[] p = entry.getValue();
                frontFacing.put(entry.getKey(), p[2] >= 0);
                double[] screen = worldToScreen(p[0], p[1], p[2]);
                screenPositions.put(entry.getKey(), screen);
            }
        }

        selectedVertices.retainAll(screenPositions.keySet());
        marqueeActive = false;
        graphPane.getChildren().clear();

        if (showCylinderGridCheckBox.isSelected()) {
            drawCylinderOverlay();
        }

        pageIndexTextField.setText(String.valueOf(currentGraphIndex + 1));
        pageLabel.setText(" / " + graphLineCount());

        drawGraph(currentGraph, currentLine, screenPositions, frontFacing);
    }

    private void rebuildWorldFromSurface() {
        worldPositions = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : surfacePositions.entrySet()) {
            double theta = entry.getValue()[0] + cylinderYaw;
            double y = entry.getValue()[1];
            double x = cylinderRadius * Math.sin(theta);
            double z = cylinderRadius * Math.cos(theta);
            worldPositions.put(entry.getKey(), new double[] { x, y, z });
        }
    }

    private Map<Integer, double[]> projectWorldToScreen(Map<Integer, double[]> world,
            Map<Integer, Boolean> frontFacingOut) {
        Map<Integer, double[]> screen = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : world.entrySet()) {
            double[] p = entry.getValue();
            frontFacingOut.put(entry.getKey(), p[2] >= 0);
            screen.put(entry.getKey(), worldToScreen(p[0], p[1], p[2]));
        }
        return screen;
    }

    private double[] worldToScreen(double x, double y, double z) {
        double cx = graphPane.getWidth() / 2.0;
        double cy = graphPane.getHeight() / 2.0;
        return new double[] { cx + x, cy + y };
    }

    private Map<Integer, double[]> rotateAndCenter3D(Map<Integer, double[]> base) {
        Map<Integer, double[]> rotated = new HashMap<>();
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (Map.Entry<Integer, double[]> entry : base.entrySet()) {
            double[] p = entry.getValue();
            double x = p[0], y = p[1], z = p.length > 2 ? p[2] : 0;
            double rx = trackballRotation[0][0] * x + trackballRotation[0][1] * y + trackballRotation[0][2] * z;
            double ry = trackballRotation[1][0] * x + trackballRotation[1][1] * y + trackballRotation[1][2] * z;
            double rz = trackballRotation[2][0] * x + trackballRotation[2][1] * y + trackballRotation[2][2] * z;
            rotated.put(entry.getKey(), new double[] { rx, ry, rz });
            sumX += rx;
            sumY += ry;
            sumZ += rz;
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

    private void estimatePreviewRadius(Map<Integer, double[]> rotatedCentered) {
        double sumR = 0;
        int count = 0;
        for (double[] p : rotatedCentered.values()) {
            sumR += Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            count++;
        }
        if (count > 0) {
            cylinderRadius = Math.max(MIN_CYLINDER_RADIUS, sumR / count);
        }
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    private void drawCylinderOverlay() {
        double cx = graphPane.getWidth() / 2.0;
        double cy = graphPane.getHeight() / 2.0;
        double R = cylinderRadius;
        int n = getCircColumns();

        double yMin = -graphPane.getHeight() / 2.0;
        double yMax = graphPane.getHeight() / 2.0;
        if (cylinderMode && !surfacePositions.isEmpty()) {
            yMin = Double.POSITIVE_INFINITY;
            yMax = Double.NEGATIVE_INFINITY;
            for (double[] s : surfacePositions.values()) {
                yMin = Math.min(yMin, s[1]);
                yMax = Math.max(yMax, s[1]);
            }
            double pad = Math.max(40, (yMax - yMin) * 0.1 + 20);
            yMin -= pad;
            yMax += pad;
        } else if (!cachedBasePositions.isEmpty()) {
            Map<Integer, double[]> rotated = rotateAndCenter3D(cachedBasePositions);
            yMin = Double.POSITIVE_INFINITY;
            yMax = Double.NEGATIVE_INFINITY;
            for (double[] p : rotated.values()) {
                yMin = Math.min(yMin, p[1]);
                yMax = Math.max(yMax, p[1]);
            }
            double pad = Math.max(40, (yMax - yMin) * 0.1 + 20);
            yMin -= pad;
            yMax += pad;
        }

        Color wire = Color.GRAY.deriveColor(0, 1, 1, 0.45);
        // Left/right silhouette generators (at x = ±R, z = 0 → front equator is x=0,z=R)
        Line left = new Line(cx - R, cy + yMin, cx - R, cy + yMax);
        Line right = new Line(cx + R, cy + yMin, cx + R, cy + yMax);
        left.setStroke(wire);
        right.setStroke(wire);
        left.setStrokeWidth(1.0);
        right.setStrokeWidth(1.0);
        left.setMouseTransparent(true);
        right.setMouseTransparent(true);
        graphPane.getChildren().addAll(left, right);

        // A few latitude rings as thin ellipses approximated by polylines (front half only).
        int ringCount = 6;
        for (int ri = 0; ri <= ringCount; ri++) {
            double y = yMin + (yMax - yMin) * ri / (double) ringCount;
            javafx.scene.shape.Polyline ring = new javafx.scene.shape.Polyline();
            for (int i = 0; i <= 32; i++) {
                double t = -Math.PI / 2 + Math.PI * i / 32.0; // front hemisphere θ in [-π/2, π/2] with z=R cos, x=R sin
                // Use θ from -π/2 to π/2 where z = R cos(θ) >= 0 when θ in [-π/2,π/2] if cos is...
                // Our convention: x = R sin θ, z = R cos θ → front z>=0 ⇒ θ in [-π/2, π/2]
                double x = R * Math.sin(t);
                double z = R * Math.cos(t);
                if (z < 0) {
                    continue;
                }
                ring.getPoints().addAll(cx + x, cy + y);
            }
            ring.setStroke(wire);
            ring.setStrokeWidth(0.7);
            ring.setFill(null);
            ring.setMouseTransparent(true);
            graphPane.getChildren().add(ring);
        }

        // Grid dots on front hemisphere.
        double pitch = (2.0 * Math.PI * R) / Math.max(1, n);
        int jMin = (int) Math.floor(yMin / pitch) - 1;
        int jMax = (int) Math.ceil(yMax / pitch) + 1;
        for (int k = 0; k < n; k++) {
            double theta = cylinderMode ? (2.0 * Math.PI * k / n + cylinderYaw) : (2.0 * Math.PI * k / n);
            double x = R * Math.sin(theta);
            double z = R * Math.cos(theta);
            if (z < 0) {
                continue;
            }
            for (int j = jMin; j <= jMax; j++) {
                double y = j * pitch;
                if (y < yMin || y > yMax) {
                    continue;
                }
                Circle dot = new Circle(cx + x, cy + y, 1.5);
                dot.setFill(Color.DARKSLATEGRAY.deriveColor(0, 1, 1, 0.55));
                dot.setMouseTransparent(true);
                graphPane.getChildren().add(dot);
            }
        }
    }

    private void drawGraph(Graph<Integer, DefaultEdge> graph,
            String sourceLine,
            Map<Integer, double[]> screenPositions,
            Map<Integer, Boolean> frontFacing) {

        // Generator-group shaded edges/polygons (same colors as GraphVisualizer).
        List<ShadedPolygonWrapper> shadedPolygons = new ArrayList<>();
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

        Map<DefaultEdge, Line> edgeLineMap = new HashMap<>();
        for (DefaultEdge edge : graph.edgeSet()) {
            int source = graph.getEdgeSource(edge);
            int target = graph.getEdgeTarget(edge);
            boolean srcFront = frontFacing.getOrDefault(source, true);
            boolean tgtFront = frontFacing.getOrDefault(target, true);
            if (!srcFront || !tgtFront) {
                continue;
            }
            double[] sourcePos = screenPositions.get(source);
            double[] targetPos = screenPositions.get(target);
            if (sourcePos == null || targetPos == null) {
                continue;
            }
            Line line = new Line(sourcePos[0], sourcePos[1], targetPos[0], targetPos[1]);
            int min = Math.min(source, target);
            int max = Math.max(source, target);
            Integer freq = edgeFrequencyMap.get(min + "-" + max);
            if (freq != null && freq > 1) {
                line.setStroke(Color.RED);
                line.setStrokeWidth(2.0);
            } else {
                line.setStroke(Color.BLACK);
                line.setStrokeWidth(1.0);
            }
            edgeLineMap.put(edge, line);
            graphPane.getChildren().add(line);
        }

        Map<Integer, Circle> vertexCircleMap = new HashMap<>();
        Map<Integer, Text> vertexLabelMap = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : screenPositions.entrySet()) {
            int vertex = entry.getKey();
            if (!frontFacing.getOrDefault(vertex, true)) {
                continue;
            }
            double[] pos = entry.getValue();
            Circle circle = new Circle(pos[0], pos[1], NODE_RADIUS);
            circle.setVisible(showCirclesCheckBox.isSelected());
            applyVertexAppearance(vertex, circle);
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
                if (!cylinderMode) {
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
                    if (cylinderMode && surfacePositions.containsKey(dv)) {
                        double[] s = surfacePositions.get(dv);
                        startSurface.put(dv, new double[] { s[0], s[1] });
                    }
                }
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                circle.setUserData(new VertexDragContext(
                        local.getX(),
                        local.getY(),
                        startScreen,
                        startSurface,
                        startRotated,
                        dragCentroid,
                        dragVertices));
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
                if (cylinderMode) {
                    dragOnCylinder(ctx, local);
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
                if (cylinderMode) {
                    if (snapToGridCheckBox.isSelected()) {
                        snapVerticesToCylinderGrid(ctx.dragVertices);
                    }
                    // Full redraw so back-face culling / edges stay consistent.
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
        javafx.scene.shape.Polygon shadedPoly = new javafx.scene.shape.Polygon();
        if (polygon.length == 2) {
            double[] sourcePos = screenPositions.get(polygon[0]);
            double[] targetPos = screenPositions.get(polygon[1]);
            if (sourcePos == null || targetPos == null) {
                return shadedPoly;
            }
            double dx = targetPos[0] - sourcePos[0];
            double dy = targetPos[1] - sourcePos[1];
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length < 1e-9) {
                return shadedPoly;
            }
            double nx = dx / length;
            double ny = dy / length;
            double px = -ny * SHADED_EDGE_HALF_WIDTH;
            double py = nx * SHADED_EDGE_HALF_WIDTH;
            shadedPoly.getPoints().addAll(
                    sourcePos[0] + px, sourcePos[1] + py,
                    sourcePos[0] - px, sourcePos[1] - py,
                    targetPos[0] - px, targetPos[1] - py,
                    targetPos[0] + px, targetPos[1] + py);
        } else {
            for (int vertex : polygon) {
                double[] pos = screenPositions.get(vertex);
                if (pos != null) {
                    shadedPoly.getPoints().addAll(pos[0], pos[1]);
                }
            }
        }
        return shadedPoly;
    }

    private void updateShadedPolygons() {
        for (ShadedPolygonWrapper wrapper : currentShadedPolygons) {
            javafx.scene.shape.Polygon poly = wrapper.polygon;
            poly.getPoints().clear();
            if (wrapper.vertices.length == 2) {
                Circle sourceCircle = currentVertexCircleMap.get(wrapper.vertices[0]);
                Circle targetCircle = currentVertexCircleMap.get(wrapper.vertices[1]);
                if (sourceCircle == null || targetCircle == null) {
                    continue;
                }
                double[] sourcePos = { sourceCircle.getCenterX(), sourceCircle.getCenterY() };
                double[] targetPos = { targetCircle.getCenterX(), targetCircle.getCenterY() };
                double dx = targetPos[0] - sourcePos[0];
                double dy = targetPos[1] - sourcePos[1];
                double length = Math.sqrt(dx * dx + dy * dy);
                if (length < 1e-9) {
                    continue;
                }
                double nx = dx / length;
                double ny = dy / length;
                double px = -ny * SHADED_EDGE_HALF_WIDTH;
                double py = nx * SHADED_EDGE_HALF_WIDTH;
                poly.getPoints().addAll(
                        sourcePos[0] + px, sourcePos[1] + py,
                        sourcePos[0] - px, sourcePos[1] - py,
                        targetPos[0] - px, targetPos[1] - py,
                        targetPos[0] + px, targetPos[1] + py);
            } else {
                for (int vertex : wrapper.vertices) {
                    Circle c = currentVertexCircleMap.get(vertex);
                    if (c != null) {
                        poly.getPoints().addAll(c.getCenterX(), c.getCenterY());
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dragging
    // -------------------------------------------------------------------------

    private void dragOnCylinder(VertexDragContext ctx, Point2D local) {
        double deltaX = local.getX() - ctx.startLocalX;
        double deltaY = local.getY() - ctx.startLocalY;
        // Horizontal motion → dθ; vertical → dy. Arc length ≈ R dθ ⇒ dθ = dx / R
        double dTheta = deltaX / Math.max(1.0, cylinderRadius);
        for (int v : ctx.dragVertices) {
            double[] start = ctx.startSurface.get(v);
            if (start == null) {
                continue;
            }
            double theta = start[0] + dTheta;
            double y = start[1] + deltaY;
            surfacePositions.put(v, new double[] { theta, y });
        }
        // Live update without full layout recompute: rebuild world/screen for dragged set.
        rebuildWorldFromSurface();
        for (int v : ctx.dragVertices) {
            double[] w = worldPositions.get(v);
            if (w == null) {
                continue;
            }
            boolean front = w[2] >= 0;
            Circle c = currentVertexCircleMap.get(v);
            Text t = currentVertexLabelMap.get(v);
            if (!front) {
                if (c != null) {
                    c.setVisible(false);
                }
                if (t != null) {
                    t.setVisible(false);
                }
                continue;
            }
            double[] screen = worldToScreen(w[0], w[1], w[2]);
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
        updateEdgeEndpoints();
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
            setVertexScreenPosition(v, nx, ny);

            // rotateAndCenter: R*base - centroid. Invert with frozen start centroid/z.
            double absX = (nx - cx) + centroid[0];
            double absY = (ny - cy) + centroid[1];
            double absZ = rp[2] + centroid[2];
            cachedBasePositions.put(v, new double[] {
                    inv[0][0] * absX + inv[0][1] * absY + inv[0][2] * absZ,
                    inv[1][0] * absX + inv[1][1] * absY + inv[1][2] * absZ,
                    inv[2][0] * absX + inv[2][1] * absY + inv[2][2] * absZ
            });
        }
        updateEdgeEndpoints();
    }

    private double[] computeRotatedCentroid(Map<Integer, double[]> base) {
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        for (double[] p : base.values()) {
            double x = p[0], y = p[1], z = p.length > 2 ? p[2] : 0;
            sumX += trackballRotation[0][0] * x + trackballRotation[0][1] * y + trackballRotation[0][2] * z;
            sumY += trackballRotation[1][0] * x + trackballRotation[1][1] * y + trackballRotation[1][2] * z;
            sumZ += trackballRotation[2][0] * x + trackballRotation[2][1] * y + trackballRotation[2][2] * z;
            count++;
        }
        if (count == 0) {
            return new double[] { 0, 0, 0 };
        }
        return new double[] { sumX / count, sumY / count, sumZ / count };
    }

    private void setVertexScreenPosition(int vertex, double x, double y) {
        Circle c = currentVertexCircleMap.get(vertex);
        if (c != null) {
            c.setCenterX(x);
            c.setCenterY(y);
        }
        Text t = currentVertexLabelMap.get(vertex);
        if (t != null) {
            t.setX(x - NODE_RADIUS / 2);
            t.setY(y + NODE_RADIUS / 2);
        }
    }

    private void updateEdgeEndpoints() {
        if (currentGraph == null) {
            return;
        }
        for (Map.Entry<DefaultEdge, Line> entry : currentEdgeLineMap.entrySet()) {
            DefaultEdge edge = entry.getKey();
            Line line = entry.getValue();
            int s = currentGraph.getEdgeSource(edge);
            int t = currentGraph.getEdgeTarget(edge);
            Circle cs = currentVertexCircleMap.get(s);
            Circle ct = currentVertexCircleMap.get(t);
            if (cs == null || ct == null) {
                continue;
            }
            line.setStartX(cs.getCenterX());
            line.setStartY(cs.getCenterY());
            line.setEndX(ct.getCenterX());
            line.setEndY(ct.getCenterY());
        }
        updateShadedPolygons();
    }

    private void snapVerticesToCylinderGrid(Set<Integer> vertices) {
        int n = getCircColumns();
        double pitch = (2.0 * Math.PI * cylinderRadius) / Math.max(1, n);
        double step = 2.0 * Math.PI / Math.max(1, n);
        for (int v : vertices) {
            double[] s = surfacePositions.get(v);
            if (s == null) {
                continue;
            }
            double theta = s[0];
            double y = s[1];
            // Snap relative to yaw=0 lattice (surface θ without yaw).
            int k = (int) Math.round(theta / step);
            int j = (int) Math.round(y / pitch);
            surfacePositions.put(v, new double[] { k * step, j * pitch });
        }
    }

    // -------------------------------------------------------------------------
    // Scale
    // -------------------------------------------------------------------------

    private void scalePointCoordinates(double factor) {
        if (cylinderMode) {
            // Scale radius and y coords about y-centroid; θ unchanged.
            double sumY = 0;
            int count = 0;
            for (double[] s : surfacePositions.values()) {
                sumY += s[1];
                count++;
            }
            double cy = count == 0 ? 0 : sumY / count;
            cylinderRadius = Math.max(MIN_CYLINDER_RADIUS, cylinderRadius * factor);
            for (double[] s : surfacePositions.values()) {
                s[1] = cy + (s[1] - cy) * factor;
            }
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
        if (count == 0) {
            return;
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

    private void show3DCoordinatesDialog(Stage owner) {
        StringBuilder sb = new StringBuilder();
        Map<Integer, double[]> src = cylinderMode ? worldPositions : cachedBasePositions;
        List<Integer> keys = new ArrayList<>(src.keySet());
        Collections.sort(keys);
        for (int v : keys) {
            double[] p = src.get(v);
            if (cylinderMode) {
                double[] s = surfacePositions.get(v);
                sb.append(v).append(' ')
                        .append(p[0]).append(' ')
                        .append(p[1]).append(' ')
                        .append(p[2])
                        .append("  # θ=").append(s != null ? s[0] : "?")
                        .append(" y=").append(s != null ? s[1] : "?")
                        .append('\n');
            } else {
                sb.append(v).append(' ')
                        .append(p[0]).append(' ')
                        .append(p[1]).append(' ')
                        .append(p.length > 2 ? p[2] : 0)
                        .append('\n');
            }
        }
        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setPrefSize(500, 400);
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
        dialog.setTitle("3D Coordinates");
        dialog.setScene(new Scene(box));
        dialog.show();
    }

    private void showEditCoordinatesDialog(Stage owner) {
        if (!cylinderMode) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Edit Coordinates");
            alert.setHeaderText(null);
            alert.setContentText("Coordinate editing is available in Cylinder Mode (θ y per vertex).");
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
        VBox box = new VBox(10, new Label("vertex θ y"), area, apply);
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
                throw new IllegalArgumentException("Expected: vertex θ y");
            }
            int v = Integer.parseInt(parts[0]);
            double theta = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            result.put(v, new double[] { theta, y });
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
            color = Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5);
            circle.setFill(color);
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

    private int getCircColumns() {
        int n = (int) Math.round(parseDouble(circColumnsTextField.getText(), DEFAULT_CIRC_COLUMNS));
        return Math.max(3, n);
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

    private double[][] rotationFromAxisAngle(double ax, double ay, double az, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1.0 - c;
        double[][] R = new double[3][3];
        R[0][0] = t * ax * ax + c;
        R[0][1] = t * ax * ay - s * az;
        R[0][2] = t * ax * az + s * ay;
        R[1][0] = t * ay * ax + s * az;
        R[1][1] = t * ay * ay + c;
        R[1][2] = t * ay * az - s * ax;
        R[2][0] = t * az * ax - s * ay;
        R[2][1] = t * az * ay + s * ax;
        R[2][2] = t * az * az + c;
        return R;
    }

    private double[][] multiply3(double[][] A, double[][] B) {
        double[][] C = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                C[i][j] = A[i][0] * B[0][j] + A[i][1] * B[1][j] + A[i][2] * B[2][j];
            }
        }
        return C;
    }

    private static double[][] transpose3(double[][] M) {
        return new double[][] {
                { M[0][0], M[1][0], M[2][0] },
                { M[0][1], M[1][1], M[2][1] },
                { M[0][2], M[1][2], M[2][2] }
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
