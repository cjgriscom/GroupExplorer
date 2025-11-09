package io.chandler.gap.graph;

import fi.tkk.ics.jbliss.AbstractGraph;
import io.chandler.gap.GroupExplorer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Minimal 14-vertex graph viewer with load + canonize flow.
 * Layout is a fixed 2x7 grid; zoom is the only interaction.
 */
public class SimpleGraphVisualizer extends Application {

    private static final double NODE_RADIUS = 20.0;
    private static final double GRID_H_SPACING = 200.0;
    private static final double GRID_V_SPACING = 110.0;
    private static final double GRID_START_X = 140.0;
    private static final double GRID_START_Y = 120.0;

    private final Color[] palette = createPalette();

    private final Map<Integer, Integer> slotToLabel = new LinkedHashMap<>();
    private final Map<Integer, Integer> labelToSlot = new HashMap<>();
    private final Map<Integer, Point2D> slotPositions = new HashMap<>();

    private int[][][] currentGenerator = null;

    private Pane graphPane;
    private Label statusLabel;
    private double scale = 1.0;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Simple Graph Visualizer (JBliss)");

        graphPane = new Pane();
        graphPane.setPrefSize(640, 880);
        graphPane.setStyle("-fx-background-color: #ffffff;");

        attachZoomHandler();

        Button loadButton = new Button("Load");
        loadButton.setOnAction(evt -> loadGraph(stage));

        Button canonizeButton = new Button("Canonize");
        canonizeButton.setOnAction(evt -> canonizeGraph());
        canonizeButton.setDisable(true);

        statusLabel = new Label("Load a graph file to begin.");

        HBox controls = new HBox(12, loadButton, canonizeButton);
        controls.setStyle("-fx-padding: 12; -fx-alignment: center;");

        BorderPane root = new BorderPane();
        root.setTop(controls);
        root.setCenter(graphPane);
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new javafx.geometry.Insets(8));

        initializeSlots();

        Scene scene = new Scene(root, 560, 960);
        stage.setScene(scene);
        stage.show();
    }

    private void attachZoomHandler() {
        graphPane.addEventFilter(ScrollEvent.SCROLL, evt -> {
            double factor = evt.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            scale = clamp(scale * factor, 0.4, 3.0);
            graphPane.setScaleX(scale);
            graphPane.setScaleY(scale);
            evt.consume();
        });
    }

    private void loadGraph(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Graph File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        List<String> lines = readGraphLines(file);
        if (lines.isEmpty()) {
            showError("No graph data found in " + file.getName());
            return;
        }

        String selected = lines.size() == 1
                ? lines.get(0)
                : promptForLine(lines);

        if (selected == null || selected.isEmpty()) {
            return;
        }

        try {
            currentGenerator = GroupExplorer.parseOperationsArr(selected);
            resetSlotLabels();
            renderGraph();
            statusLabel.setText("Loaded " + file.getName());
            ((Button)((HBox)((BorderPane)graphPane.getParent()).getTop()).getChildren().get(1)).setDisable(false);
        } catch (RuntimeException ex) {
            showError("Failed to parse graph: " + ex.getMessage());
        }
    }

    private void canonizeGraph() {
        if (currentGenerator == null) {
            return;
        }

        try {
            AbstractGraph<Integer> jblissGraph =
                    PlanarStudy.buildJblissGraphFromCombinedGen(currentGenerator, false);

            Map<Integer, Integer> rawMap = jblissGraph.canonical_labeling();
            Map<Integer, Integer> newLabels = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : rawMap.entrySet()) {
                newLabels.put(entry.getKey(), entry.getValue() + 1); // convert to 1-based
            }

            relabelGenerator(newLabels);
            relabelSlots(newLabels);
            renderGraph();
            statusLabel.setText("Graph canonized with JBliss.");
        } catch (Exception ex) {
            showError("Canonization failed: " + ex.getMessage());
        }
    }

    private void renderGraph() {
        graphPane.getChildren().clear();
        if (currentGenerator == null) {
            return;
        }

        Map<String, Line> renderedEdges = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (int[][] cycle : currentGenerator) {
            for (int[] polygon : cycle) {
                if (polygon.length < 2) {
                    continue;
                }
                for (int i = 0; i < polygon.length; i++) {
                    int labelA = polygon[i];
                    int labelB = polygon[(i + 1) % polygon.length];

                    Integer slotA = labelToSlot.get(labelA);
                    Integer slotB = labelToSlot.get(labelB);
                    if (slotA == null || slotB == null || slotA.equals(slotB)) {
                        continue;
                    }

                    int lo = Math.min(slotA, slotB);
                    int hi = Math.max(slotA, slotB);
                    String key = lo + ":" + hi;
                    if (visited.add(key)) {
                        Point2D pa = slotPositions.get(slotA);
                        Point2D pb = slotPositions.get(slotB);
                        Line edge = new Line(pa.getX(), pa.getY(), pb.getX(), pb.getY());
                        edge.setStroke(Color.BLACK);
                        edge.setStrokeWidth(2.0);
                        renderedEdges.put(key, edge);
                    }
                }
            }
        }

        graphPane.getChildren().addAll(renderedEdges.values());

        for (int slot = 1; slot <= 14; slot++) {
            Point2D pos = slotPositions.get(slot);
            int label = slotToLabel.get(slot);
            Color fill = palette[(label - 1) % palette.length];

            Circle circle = new Circle(pos.getX(), pos.getY(), NODE_RADIUS, fill);
            circle.setStroke(Color.DARKGRAY);
            circle.setStrokeWidth(1.5);

            Text text = new Text(Integer.toString(label));
            text.setFont(Font.font(16));
            text.setFill(Color.WHITE);
            double textWidth = text.getBoundsInLocal().getWidth();
            double textHeight = text.getBoundsInLocal().getHeight();
            text.setX(pos.getX() - textWidth / 2);
            text.setY(pos.getY() + textHeight / 4);

            graphPane.getChildren().addAll(circle, text);
        }
    }

    private void initializeSlots() {
        slotToLabel.clear();
        labelToSlot.clear();
        slotPositions.clear();

        for (int slot = 1; slot <= 14; slot++) {
            int row = (slot - 1) / 2;
            int col = (slot - 1) % 2;
            double x = GRID_START_X + col * GRID_H_SPACING;
            double y = GRID_START_Y + row * GRID_V_SPACING;
            slotPositions.put(slot, new Point2D(x, y));
            slotToLabel.put(slot, slot);
            labelToSlot.put(slot, slot);
        }
    }

    private void resetSlotLabels() {
        for (int slot = 1; slot <= 14; slot++) {
            slotToLabel.put(slot, slot);
            labelToSlot.put(slot, slot);
        }
    }

    private void relabelSlots(Map<Integer, Integer> newLabels) {
        for (Map.Entry<Integer, Integer> entry : slotToLabel.entrySet()) {
            int oldLabel = entry.getValue();
            int relabeled = newLabels.getOrDefault(oldLabel, oldLabel);
            entry.setValue(relabeled);
        }
        labelToSlot.clear();
        for (Map.Entry<Integer, Integer> entry : slotToLabel.entrySet()) {
            labelToSlot.put(entry.getValue(), entry.getKey());
        }
    }

    private void relabelGenerator(Map<Integer, Integer> newLabels) {
        for (int i = 0; i < currentGenerator.length; i++) {
            int[][] cycle = currentGenerator[i];
            for (int j = 0; j < cycle.length; j++) {
                int[] polygon = cycle[j];
                for (int k = 0; k < polygon.length; k++) {
                    int value = polygon[k];
                    polygon[k] = newLabels.getOrDefault(value, value);
                }
            }
        }
    }

    private static List<String> readGraphLines(File file) {
        List<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath(), e);
        }
        return lines;
    }

    private static String promptForLine(List<String> lines) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Multiple Graphs Detected");
        alert.setHeaderText("Using the first non-empty line.");
        alert.setContentText("The simplified viewer only displays a single graph at a time.");
        alert.showAndWait();
        return lines.get(0);
    }

    private static Color[] createPalette() {
        return new Color[]{
                Color.web("#d32f2f"),
                Color.web("#1976d2"),
                Color.web("#388e3c"),
                Color.web("#fbc02d"),
                Color.web("#7b1fa2"),
                Color.web("#00796b"),
                Color.web("#f57c00"),
                Color.web("#5d4037"),
                Color.web("#c2185b"),
                Color.web("#303f9f"),
                Color.web("#0097a7"),
                Color.web("#689f38"),
                Color.web("#ffa000"),
                Color.web("#455a64")
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Graph Visualizer");
        alert.setHeaderText("Operation failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}