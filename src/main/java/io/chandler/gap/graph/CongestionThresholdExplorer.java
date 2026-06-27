package io.chandler.gap.graph;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Explores CongestionBatch CSV output to suggest per-checkpoint prune thresholds.
 * Selects a percentile range by cell tally at the final checkpoint, then plots
 * how those values spread across earlier checkpoints for that selected set.
 */
public class CongestionThresholdExplorer extends Application {

    private enum CellTally {
        MIN("Min"),
        AVG("Avg"),
        MEDIAN("Median"),
        MAX("Max");

        private final String label;

        CellTally(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static String initialCsvPath = "results.csv";

    private TextField fileField;
    private Slider percentileLowSlider;
    private Slider percentileHighSlider;
    private TextField percentileLowField;
    private TextField percentileHighField;
    private boolean syncingPercentileControls;
    private ToggleGroup cellTallyGroup;
    private CellTally cellTally = CellTally.MAX;
    private Label summaryLabel;
    private LineChart<Number, Number> chart;
    private XYChart.Series<Number, Number> minSeries;
    private XYChart.Series<Number, Number> p25Series;
    private XYChart.Series<Number, Number> medianSeries;
    private XYChart.Series<Number, Number> p75Series;
    private XYChart.Series<Number, Number> maxSeries;

    private CsvData loadedData;

    public static void main(String[] args) {
        if (args.length >= 1) {
            initialCsvPath = args[0];
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Congestion Threshold Explorer");

        fileField = new TextField(initialCsvPath);
        fileField.setPrefWidth(420);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> chooseFile(stage));

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> loadAndAnalyze());

        percentileLowSlider = createPercentileSlider(0.0);
        percentileHighSlider = createPercentileSlider(1.0);

        percentileLowField = new TextField(formatPercentileValue(0.0));
        percentileLowField.setPrefWidth(56);
        percentileHighField = new TextField(formatPercentileValue(1.0));
        percentileHighField.setPrefWidth(56);

        percentileLowSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingPercentileControls) {
                return;
            }
            double low = newVal.doubleValue();
            if (low > percentileHighSlider.getValue()) {
                syncingPercentileControls = true;
                percentileHighSlider.setValue(low);
                percentileHighField.setText(formatPercentileValue(low));
                syncingPercentileControls = false;
            }
            syncingPercentileControls = true;
            percentileLowField.setText(formatPercentileValue(low));
            syncingPercentileControls = false;
            refreshAnalysis();
        });

        percentileHighSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingPercentileControls) {
                return;
            }
            double high = newVal.doubleValue();
            if (high < percentileLowSlider.getValue()) {
                syncingPercentileControls = true;
                percentileLowSlider.setValue(high);
                percentileLowField.setText(formatPercentileValue(high));
                syncingPercentileControls = false;
            }
            syncingPercentileControls = true;
            percentileHighField.setText(formatPercentileValue(high));
            syncingPercentileControls = false;
            refreshAnalysis();
        });

        percentileLowField.setOnAction(e -> applyPercentileRangeFromFields());
        percentileHighField.setOnAction(e -> applyPercentileRangeFromFields());
        percentileLowField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                applyPercentileRangeFromFields();
            }
        });
        percentileHighField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                applyPercentileRangeFromFields();
            }
        });

        HBox fileRow = new HBox(8, new Label("CSV:"), fileField, browseButton, loadButton);
        fileRow.setPadding(new Insets(8));

        HBox percentileLowRow = new HBox(8,
                new Label("Percentile from:"),
                percentileLowSlider,
                percentileLowField,
                new Label("% (best)"));
        percentileLowRow.setPadding(new Insets(0, 8, 4, 8));
        HBox.setHgrow(percentileLowSlider, Priority.ALWAYS);

        HBox percentileHighRow = new HBox(8,
                new Label("Percentile to:"),
                percentileHighSlider,
                percentileHighField,
                new Label("% (worst)"));
        percentileHighRow.setPadding(new Insets(0, 8, 8, 8));
        HBox.setHgrow(percentileHighSlider, Priority.ALWAYS);

        cellTallyGroup = new ToggleGroup();
        RadioButton minRadio = new RadioButton(CellTally.MIN.label());
        RadioButton avgRadio = new RadioButton(CellTally.AVG.label());
        RadioButton medianRadio = new RadioButton(CellTally.MEDIAN.label());
        RadioButton maxRadio = new RadioButton(CellTally.MAX.label());
        minRadio.setToggleGroup(cellTallyGroup);
        avgRadio.setToggleGroup(cellTallyGroup);
        medianRadio.setToggleGroup(cellTallyGroup);
        maxRadio.setToggleGroup(cellTallyGroup);
        maxRadio.setSelected(true);

        cellTallyGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == minRadio) {
                cellTally = CellTally.MIN;
            } else if (newToggle == avgRadio) {
                cellTally = CellTally.AVG;
            } else if (newToggle == medianRadio) {
                cellTally = CellTally.MEDIAN;
            } else if (newToggle == maxRadio) {
                cellTally = CellTally.MAX;
            }
            if (loadedData != null) {
                refreshAnalysis();
            }
        });

        HBox tallyRow = new HBox(12,
                new Label("Per-cell tally:"),
                minRadio, avgRadio, medianRadio, maxRadio);
        tallyRow.setPadding(new Insets(0, 8, 8, 8));

        summaryLabel = new Label("Load a CongestionBatch CSV to begin.");
        summaryLabel.setWrapText(true);
        summaryLabel.setPadding(new Insets(0, 8, 8, 8));

        chart = createChart();

        VBox top = new VBox(4, fileRow, percentileLowRow, percentileHighRow, tallyRow, summaryLabel);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(chart);

        Scene scene = new Scene(root, 960, 640);
        stage.setScene(scene);
        stage.show();

        if (Files.exists(Paths.get(initialCsvPath))) {
            Platform.runLater(this::loadAndAnalyze);
        }
    }

    private void chooseFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open CongestionBatch CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file != null) {
            fileField.setText(file.getAbsolutePath());
            loadAndAnalyze();
        }
    }

    private void loadAndAnalyze() {
        Path path = Paths.get(fileField.getText().trim());
        try {
            loadedData = CsvData.load(path);
            refreshAnalysis();
        } catch (IOException ex) {
            loadedData = null;
            summaryLabel.setText("Failed to load CSV: " + ex.getMessage());
            clearChart();
        }
    }

    private Slider createPercentileSlider(double initialValue) {
        Slider slider = new Slider(0.0, 100.0, initialValue);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(10.0);
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(0.1);
        slider.setSnapToTicks(false);
        slider.setMinWidth(180);
        slider.setPrefWidth(280);
        return slider;
    }

    private void refreshAnalysis() {
        updateAnalysis(percentileLowSlider.getValue(), percentileHighSlider.getValue());
    }

    private void updateAnalysis(double lowPercentile, double highPercentile) {
        if (loadedData == null) {
            return;
        }

        List<RowStats> selected = loadedData.selectByFinalScoreRange(lowPercentile, highPercentile, cellTally);
        if (selected.isEmpty()) {
            summaryLabel.setText(String.format(Locale.US,
                    "No rows selected for percentile range %s–%s.",
                    formatPercentile(lowPercentile), formatPercentile(highPercentile)));
            clearChart();
            return;
        }

        int numCheckpoints = loadedData.checkpoints.length;
        double[][] spread = new double[numCheckpoints][5]; // min, p25, median, p75, max

        for (int c = 0; c < numCheckpoints; c++) {
            double[] values = new double[selected.size()];
            for (int i = 0; i < selected.size(); i++) {
                values[i] = selected.get(i).values[c];
            }
            Arrays.sort(values);
            spread[c][0] = values[0];
            spread[c][1] = percentile(values, 0.25);
            spread[c][2] = percentile(values, 0.50);
            spread[c][3] = percentile(values, 0.75);
            spread[c][4] = values[values.length - 1];
        }

        updateChart(spread);
        updateChartLabels();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "Selected set: %d / %d rows (percentile %s–%s by %s at checkpoint %d).%n",
                selected.size(), loadedData.rows.size(),
                formatPercentile(lowPercentile), formatPercentile(highPercentile),
                cellTally.label().toLowerCase(Locale.US), loadedData.finalCheckpoint));
        sb.append(String.format(Locale.US,
                "Suggested thresholds (max %s among selected, keeps all in range): ",
                cellTally.label().toLowerCase(Locale.US)));
        for (int c = 0; c < numCheckpoints; c++) {
            if (c > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.US, "%d→%.4f", loadedData.checkpoints[c], spread[c][4]));
        }
        summaryLabel.setText(sb.toString());
    }

    private LineChart<Number, Number> createChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Checkpoint iteration");
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Congestion (max per cell)");
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Selected spread by checkpoint");
        lineChart.setLegendSide(Side.BOTTOM);
        lineChart.setCreateSymbols(true);

        minSeries = new XYChart.Series<>();
        minSeries.setName("Min");

        p25Series = new XYChart.Series<>();
        p25Series.setName("P25");

        medianSeries = new XYChart.Series<>();
        medianSeries.setName("Median");

        p75Series = new XYChart.Series<>();
        p75Series.setName("P75");

        maxSeries = new XYChart.Series<>();
        maxSeries.setName("Max (keep-all threshold)");

        lineChart.getData().addAll(minSeries, p25Series, medianSeries, p75Series, maxSeries);
        return lineChart;
    }

    private void updateChart(double[][] spread) {
        minSeries.getData().clear();
        p25Series.getData().clear();
        medianSeries.getData().clear();
        p75Series.getData().clear();
        maxSeries.getData().clear();

        for (int c = 0; c < loadedData.checkpoints.length; c++) {
            int checkpoint = loadedData.checkpoints[c];
            minSeries.getData().add(new XYChart.Data<>(checkpoint, spread[c][0]));
            p25Series.getData().add(new XYChart.Data<>(checkpoint, spread[c][1]));
            medianSeries.getData().add(new XYChart.Data<>(checkpoint, spread[c][2]));
            p75Series.getData().add(new XYChart.Data<>(checkpoint, spread[c][3]));
            maxSeries.getData().add(new XYChart.Data<>(checkpoint, spread[c][4]));
        }
    }

    private void clearChart() {
        minSeries.getData().clear();
        p25Series.getData().clear();
        medianSeries.getData().clear();
        p75Series.getData().clear();
        maxSeries.getData().clear();
    }

    private void updateChartLabels() {
        String perCell = cellTally.label().toLowerCase(Locale.US) + " per cell";
        chart.setTitle("Selected " + cellTally.label() + " spread by checkpoint");
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        yAxis.setLabel("Congestion (" + perCell + ")");
        maxSeries.setName("Max " + cellTally.label() + " (keep-all threshold)");
        minSeries.setName("Min " + cellTally.label());
    }

    private void applyPercentileRangeFromFields() {
        if (syncingPercentileControls) {
            return;
        }
        try {
            double low = parsePercentileField(percentileLowField);
            double high = parsePercentileField(percentileHighField);
            if (low > high) {
                double swap = low;
                low = high;
                high = swap;
            }
            low = clamp(low, 0.0, 100.0);
            high = clamp(high, 0.0, 100.0);

            syncingPercentileControls = true;
            percentileLowSlider.setValue(low);
            percentileHighSlider.setValue(high);
            percentileLowField.setText(formatPercentileValue(low));
            percentileHighField.setText(formatPercentileValue(high));
            syncingPercentileControls = false;
            refreshAnalysis();
        } catch (NumberFormatException ex) {
            percentileLowField.setText(formatPercentileValue(percentileLowSlider.getValue()));
            percentileHighField.setText(formatPercentileValue(percentileHighSlider.getValue()));
        }
    }

    private static double parsePercentileField(TextField field) {
        return Double.parseDouble(field.getText().trim().replace("%", ""));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatPercentileValue(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatPercentile(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = p * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted[lo];
        }
        double weight = rank - lo;
        return sorted[lo] * (1.0 - weight) + sorted[hi] * weight;
    }

    private static final class RowStats {
        final int index;
        final double[] values;

        RowStats(int index, double[] values) {
            this.index = index;
            this.values = values;
        }

        double finalValue(int finalIndex) {
            return values[finalIndex];
        }
    }

    private static final class RawRow {
        final int index;
        final double[][] cellScores;

        RawRow(int index, double[][] cellScores) {
            this.index = index;
            this.cellScores = cellScores;
        }

        RowStats toRowStats(CellTally tally) {
            double[] values = new double[cellScores.length];
            for (int c = 0; c < cellScores.length; c++) {
                values[c] = tallyCell(cellScores[c], tally);
            }
            return new RowStats(index, values);
        }
    }

    private static double tallyCell(double[] scores, CellTally tally) {
        if (scores == null || scores.length == 0) {
            return Double.NaN;
        }
        switch (tally) {
            case MIN:
                return Arrays.stream(scores).min().orElse(Double.NaN);
            case MAX:
                return Arrays.stream(scores).max().orElse(Double.NaN);
            case AVG:
                return Arrays.stream(scores).average().orElse(Double.NaN);
            case MEDIAN:
                double[] sorted = scores.clone();
                Arrays.sort(sorted);
                return percentile(sorted, 0.50);
            default:
                throw new IllegalArgumentException("Unknown tally: " + tally);
        }
    }

    private static final class CsvData {
        final int[] checkpoints;
        final int finalCheckpoint;
        final int finalIndex;
        final List<RawRow> rows;

        CsvData(int[] checkpoints, List<RawRow> rows) {
            this.checkpoints = checkpoints;
            this.rows = rows;
            this.finalIndex = checkpoints.length - 1;
            this.finalCheckpoint = checkpoints[finalIndex];
        }

        static CsvData load(Path path) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
                String header = reader.readLine();
                if (header == null) {
                    throw new IOException("Empty file");
                }

                String[] headerParts = header.split(",", -1);
                if (headerParts.length < 3) {
                    throw new IOException("Expected header: generator_index,checkpoint_*...");
                }

                int[] checkpoints = new int[headerParts.length - 1];
                for (int i = 1; i < headerParts.length; i++) {
                    checkpoints[i - 1] = parseCheckpointHeader(headerParts[i].trim());
                }

                List<RawRow> rows = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    RawRow row = parseRow(line, checkpoints.length);
                    if (row != null) {
                        rows.add(row);
                    }
                }

                if (rows.isEmpty()) {
                    throw new IOException("No data rows found");
                }
                return new CsvData(checkpoints, rows);
            }
        }

        List<RowStats> selectByFinalScoreRange(double lowPercentile, double highPercentile, CellTally tally) {
            List<RowStats> complete = rows.stream()
                    .map(r -> r.toRowStats(tally))
                    .filter(r -> !Double.isNaN(r.finalValue(finalIndex)))
                    .sorted(Comparator.comparingDouble(r -> r.finalValue(finalIndex)))
                    .collect(Collectors.toList());

            if (complete.isEmpty()) {
                return complete;
            }

            int n = complete.size();
            int startIdx = (int) Math.floor(n * lowPercentile / 100.0);
            int endIdx = (int) Math.ceil(n * highPercentile / 100.0);
            startIdx = clampIndex(startIdx, 0, n);
            endIdx = clampIndex(endIdx, 0, n);
            if (endIdx <= startIdx) {
                return List.of();
            }
            return complete.subList(startIdx, endIdx);
        }

        private static int clampIndex(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static int parseCheckpointHeader(String header) {
            if (header.startsWith("checkpoint_")) {
                return Integer.parseInt(header.substring("checkpoint_".length()));
            }
            throw new IllegalArgumentException("Unrecognized checkpoint column: " + header);
        }

        private static RawRow parseRow(String line, int numCheckpoints) {
            String[] parts = line.split(",", -1);
            if (parts.length < numCheckpoints + 1) {
                return null;
            }

            int index = Integer.parseInt(parts[0].trim());
            double[][] cellScores = new double[numCheckpoints][];

            for (int c = 0; c < numCheckpoints; c++) {
                String cell = parts[c + 1].trim();
                if (cell.isEmpty()) {
                    cellScores[c] = new double[0];
                } else {
                    cellScores[c] = parseScoresInCell(cell);
                }
            }

            return new RawRow(index, cellScores);
        }

        private static double[] parseScoresInCell(String cell) {
            String[] tokens = cell.trim().split("\\s+");
            List<Double> scores = new ArrayList<>();
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                scores.add(Double.parseDouble(token));
            }
            double[] result = new double[scores.size()];
            for (int i = 0; i < scores.size(); i++) {
                result[i] = scores.get(i);
            }
            return result;
        }
    }
}
