package io.chandler.gap.graph;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Exports node label images rendered the same way as {@link GraphVisualizer}
 * for template matching against screenshots.
 */
public class NumberTemplateExporter extends Application {

    /** Must stay in sync with GraphVisualizer.NODE_RADIUS. */
    private static final double NODE_RADIUS = 20;
    private static int paneSize = 65;
    private static double fontSize = 12;
    private static double renderScale = 1.0;

    private static String outputDir = "assets/number_templates";
    private static int maxNumber = 176;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && i + 1 < args.length) {
                outputDir = args[++i];
            } else if ("--max".equals(args[i]) && i + 1 < args.length) {
                maxNumber = Integer.parseInt(args[++i]);
            } else if ("--pane-size".equals(args[i]) && i + 1 < args.length) {
                paneSize = Integer.parseInt(args[++i]);
            } else if ("--font-size".equals(args[i]) && i + 1 < args.length) {
                fontSize = Double.parseDouble(args[++i]);
            } else if ("--render-scale".equals(args[i]) && i + 1 < args.length) {
                renderScale = Double.parseDouble(args[++i]);
            }
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            exportAll();
            System.out.println("Wrote templates to " + new File(outputDir).getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Platform.exit();
        }
    }

    private void exportAll() throws IOException {
        File root = new File(outputDir);
        File textDir = new File(root, "text");
        File nodeDir = new File(root, "node");
        textDir.mkdirs();
        nodeDir.mkdirs();

        double cx = paneSize / 2.0;
        double cy = paneSize / 2.0;
        double radius = NODE_RADIUS * renderScale;

        for (int n = 1; n <= maxNumber; n++) {
            String name = String.format("%03d.png", n);
            writeSnapshot(textDir, name, buildTextOnly(cx, cy, radius, n), true);
            for (int freq = 1; freq <= 4; freq++) {
                File freqDir = new File(nodeDir, "f" + freq);
                freqDir.mkdirs();
                writeSnapshot(freqDir, name, buildNode(cx, cy, radius, n, freq), false);
            }
        }

        writeManifest(root, maxNumber);
    }

    private Group buildTextOnly(double cx, double cy, double radius, int number) {
        Text text = createLabel(cx, cy, radius, number);
        return new Group(text);
    }

    private Group buildNode(double cx, double cy, double radius, int number, int frequency) {
        Circle circle = new Circle(cx, cy, radius);
        Color color = vertexColor(frequency);
        circle.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.5));
        Text text = createLabel(cx, cy, radius, number);
        return new Group(circle, text);
    }

    private Text createLabel(double cx, double cy, double radius, int number) {
        Text text = new Text(cx - radius / 2, cy + radius / 2, String.valueOf(number));
        text.setFont(Font.font(fontSize * renderScale));
        text.setFill(Color.BLACK);
        return text;
    }

    /** Mirrors GraphVisualizer.getVertexColor. */
    private static Color vertexColor(int frequency) {
        if (frequency >= 4) {
            return Color.DARKGREEN;
        } else if (frequency == 3) {
            return Color.DARKORANGE;
        } else if (frequency == 2) {
            return Color.DARKRED;
        }
        return Color.BLACK;
    }

    private void writeSnapshot(File dir, String name, Group content, boolean transparent) throws IOException {
        Pane pane = new Pane(content);
        pane.setPrefSize(paneSize, paneSize);
        pane.setMinSize(paneSize, paneSize);
        pane.setMaxSize(paneSize, paneSize);
        pane.setClip(new Rectangle(paneSize, paneSize));

        Scene scene = new Scene(pane, paneSize, paneSize);
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        SnapshotParameters params = new SnapshotParameters();
        if (transparent) {
            params.setFill(Color.TRANSPARENT);
        } else {
            params.setFill(Color.web("#f4f4f4"));
        }

        WritableImage image = pane.snapshot(params, null);
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(dir, name));
    }

    private void writeManifest(File root, int max) throws IOException {
        File manifest = new File(root, "manifest.txt");
        try (java.io.PrintWriter out = new java.io.PrintWriter(manifest)) {
            out.println("node_radius=" + NODE_RADIUS);
            out.println("pane_size=" + paneSize);
            out.println("font_size=" + fontSize);
            out.println("render_scale=" + renderScale);
            out.println("max_number=" + max);
            out.println("text_dir=text");
            out.println("node_dir=node");
            out.println("text_position=x=center-" + (NODE_RADIUS / 2) + ",y=center+" + (NODE_RADIUS / 2));
        }
    }
}
