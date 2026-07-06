package io.chandler.gap.graph.flatten;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Live monitoring window for a running {@link GridFlattenSolver}.
 *
 * Shows the current best grid: vertices on cells, edges colored by generator
 * (red/green/blue), illegal pairs highlighted, junction midpoints marked, and
 * a scrolling status log. The Stop button cancels the solve cooperatively.
 */
public class FlattenMonitor {

    private static final Color[] EDGE_COLORS = {
            Color.rgb(220, 50, 50), Color.rgb(40, 160, 60), Color.rgb(60, 90, 220)};

    private final ColoredGraph graph;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Stage stage;
    private Canvas canvas;
    private Label scoreLabel;
    private TextArea logArea;
    private CheckBox showLabels;

    private volatile GridState shownState;
    private volatile SearchScore shownScore;
    private long lastDraw = 0;

    public FlattenMonitor(ColoredGraph graph) {
        this.graph = graph;
    }

    public boolean isCancelled() { return cancelled.get(); }

    /** Must be called on the FX thread. */
    public void show(String title) {
        stage = new Stage();
        stage.setTitle("Grid Flatten: " + title);

        canvas = new Canvas(900, 700);
        scoreLabel = new Label("waiting for first snapshot...");
        scoreLabel.setFont(Font.font("Monospaced", 12));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setFont(Font.font("Monospaced", 11));

        Button stopBtn = new Button("Stop");
        stopBtn.setOnAction(e -> {
            cancelled.set(true);
            appendLog("stop requested; finishing current window...");
        });
        Button copyBtn = new Button("Copy grid text");
        copyBtn.setOnAction(e -> {
            GridState s = shownState;
            if (s != null) {
                javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(s.toGridText());
                cb.setContent(cc);
                appendLog("grid text copied to clipboard");
            }
        });
        showLabels = new CheckBox("Vertex labels");
        showLabels.setSelected(true);
        showLabels.setOnAction(e -> redraw());

        HBox controls = new HBox(10, stopBtn, copyBtn, showLabels, scoreLabel);
        controls.setPadding(new Insets(6));

        BorderPane root = new BorderPane();
        root.setTop(controls);
        root.setCenter(canvas);
        root.setBottom(logArea);

        stage.setScene(new Scene(root));
        stage.setOnCloseRequest(e -> cancelled.set(true));
        stage.show();

        // Redraw when resized
        canvas.widthProperty().bind(root.widthProperty().subtract(16));
        canvas.heightProperty().bind(root.heightProperty()
                .subtract(controls.heightProperty()).subtract(logArea.heightProperty()).subtract(16));
        canvas.widthProperty().addListener(o -> redraw());
        canvas.heightProperty().addListener(o -> redraw());
    }

    /** Thread-safe: marshals to FX thread, throttled to ~5 fps. */
    public void postSnapshot(GridState state, SearchScore score) {
        long now = System.currentTimeMillis();
        if (now - lastDraw < 200) return;
        lastDraw = now;
        shownState = state;
        shownScore = score;
        Platform.runLater(this::redraw);
    }

    /** Thread-safe status log line. */
    public void postStatus(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void appendLog(String message) {
        if (logArea == null) return;
        logArea.appendText(message + "\n");
    }

    private void redraw() {
        GridState state = shownState;
        SearchScore score = shownScore;
        if (canvas == null || state == null) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, W, H);

        int minX = state.minX(), maxX = state.maxX(), minY = state.minY(), maxY = state.maxY();
        int gw = maxX - minX + 3, gh = maxY - minY + 3;
        double cell = Math.min(W / gw, H / gh);
        double ox = (W - gw * cell) / 2 + cell * 1.5 - minX * cell;
        double oy = (H - gh * cell) / 2 + cell * 1.5 - minY * cell;

        // Grid lines
        g.setStroke(Color.rgb(230, 230, 230));
        g.setLineWidth(1);
        for (int x = minX - 1; x <= maxX + 1; x++) {
            g.strokeLine(ox + x * cell, oy + (minY - 1) * cell, ox + x * cell, oy + (maxY + 1) * cell);
        }
        for (int y = minY - 1; y <= maxY + 1; y++) {
            g.strokeLine(ox + (minX - 1) * cell, oy + y * cell, ox + (maxX + 1) * cell, oy + y * cell);
        }

        // Edges
        for (ColoredEdge e : graph.edges()) {
            int[] a = state.get(e.u), b = state.get(e.v);
            if (a == null || b == null) continue;
            g.setStroke(EDGE_COLORS[e.color % EDGE_COLORS.length]);
            g.setLineWidth(Math.max(1.2, cell * 0.06));
            g.strokeLine(ox + a[0] * cell, oy + a[1] * cell, ox + b[0] * cell, oy + b[1] * cell);
        }

        // Violations highlighted
        if (score != null) {
            g.setStroke(Color.ORANGE);
            g.setLineWidth(Math.max(2.5, cell * 0.12));
            for (SearchScore.PairViolation pv : score.pairViolations) {
                for (ColoredEdge e : new ColoredEdge[]{pv.a, pv.b}) {
                    int[] a = state.get(e.u), b = state.get(e.v);
                    g.strokeLine(ox + a[0] * cell, oy + a[1] * cell, ox + b[0] * cell, oy + b[1] * cell);
                }
            }
            for (SearchScore.PassThrough pt : score.passThroughs) {
                int[] p = state.get(pt.vertex);
                g.strokeOval(ox + p[0] * cell - cell * 0.45, oy + p[1] * cell - cell * 0.45,
                        cell * 0.9, cell * 0.9);
            }
            for (ColoredEdge e : score.oversizeEdges) {
                int[] a = state.get(e.u), b = state.get(e.v);
                g.strokeLine(ox + a[0] * cell, oy + a[1] * cell, ox + b[0] * cell, oy + b[1] * cell);
            }
            // Junction midpoints
            g.setFill(Color.rgb(90, 90, 90, 0.9));
            for (SearchScore.Junction j : score.junctions) {
                double jx = ox + j.mx2 / 2.0 * cell, jy = oy + j.my2 / 2.0 * cell;
                double r = Math.max(3, cell * 0.12);
                g.fillOval(jx - r, jy - r, 2 * r, 2 * r);
            }
        }

        // Vertices
        boolean labels = showLabels != null && showLabels.isSelected();
        double vr = Math.max(3, cell * (labels ? 0.32 : 0.14));
        g.setFont(Font.font("Monospaced", Math.max(7, cell * 0.28)));
        for (int v : graph.vertices()) {
            int[] p = state.get(v);
            if (p == null) continue;
            double px = ox + p[0] * cell, py = oy + p[1] * cell;
            g.setFill(Color.WHITE);
            g.fillOval(px - vr, py - vr, 2 * vr, 2 * vr);
            g.setStroke(Color.BLACK);
            g.setLineWidth(1);
            g.strokeOval(px - vr, py - vr, 2 * vr, 2 * vr);
            if (labels) {
                g.setFill(Color.BLACK);
                String s = Integer.toString(v);
                g.fillText(s, px - s.length() * cell * 0.09, py + cell * 0.10);
            }
        }

        if (score != null) {
            scoreLabel.setText(String.format("score=%d  edge=%d  junc=%d  violations=%d  oversize=%d  %s",
                    score.total, score.edgeCost, score.junctionCost,
                    score.violationCount(), score.oversizeEdges.size(),
                    score.isLegal() ? "LEGAL" : ""));
        }
    }
}
