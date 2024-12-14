package io.chandler.gap.render;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.embed.swing.SwingFXUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Renderer extends Application {
    private static final double SCENE_WIDTH = 1280;
    private static final double SCENE_HEIGHT = 900;
    private static final double SIDEBAR_WIDTH = 400;

    private double anchorX, anchorY, anchorAngleX = 0, anchorAngleY = 0;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate rotateXL = new Rotate(-81, Rotate.X_AXIS);
    private final Rotate rotateYL = new Rotate(45, Rotate.Y_AXIS);

    // Create materials for different colors
    final PhongMaterial blueMaterial = createMaterial(Color.rgb(100, 100, 255));
    final PhongMaterial redMaterial = createMaterial(Color.rgb(255, 100, 100));
    final PhongMaterial greenMaterial = createMaterial(Color.rgb(100, 255, 100));

    private static final double LIGHT_DISTANCE = 1000.0; // Adjust this to scale all lights

    private final Random random = new Random();

    private Solid solid;
    private Group solidsGroup; // Group to hold solids for easy switching

    // UI Components
    private ComboBox<String> solidsComboBox;
    private TextArea parseTextArea;
    private Button parseButton;
    private Button prevButton;
    private Button nextButton;
    private Label paginationLabel;
    private Label descriptionLabel;

    // Parse Results
    private List<String> parseResults = new ArrayList<>();
    private int currentParseIndex = 0;
    private Stage stage; // Class-level variable
    private SubScene subScene; // Class-level variable
    
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage; // Assign to class-level variable

        // Initialize solids group
        solidsGroup = new Group();

        // Create and position camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-30);
        camera.setFieldOfView(10);

        // Initialize solid
        solid = new Icosahedron();
        solidsGroup.getChildren().add(solid);

        // Set up mouse control
        solidsGroup.getTransforms().addAll(rotateX, rotateY);
        Group group2 = new Group();
        group2.getTransforms().addAll(rotateXL, rotateYL);

        setupLights(group2);

        Group allGroups = new Group(solidsGroup, group2);

        // Create the 3D content pane
        subScene = new SubScene(allGroups, SCENE_WIDTH - SIDEBAR_WIDTH, SCENE_HEIGHT, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);

        // Create Sidebar
        VBox sidebar = createSidebar();

        // Layout using BorderPane
        BorderPane root = new BorderPane();
        root.setCenter(subScene);
        root.setRight(sidebar);

        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT, true);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Group Visualizer");
        primaryStage.show();

        initMouseControl(solidsGroup, primaryStage);
    }


    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setPadding(new Insets(20));
        sidebar.setSpacing(20);
        sidebar.setStyle("-fx-background-color: #2E2E2E;");

        // --- Controls Title ---
        Label title = new Label("Controls");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // --- Dropdown for Solids ---
        HBox solidsBox = new HBox();
        solidsBox.setSpacing(10);
        Label solidsLabel = new Label("Select Solid:");
        solidsLabel.setTextFill(Color.WHITE);
        solidsComboBox = new ComboBox<>();
        solidsComboBox.getItems().addAll(
                "Icosahedron",
                "Snub Cube"
        );
        solidsComboBox.setValue("Icosahedron"); // Default selection
        solidsComboBox.setOnAction(e -> handleSolidSelection());

        solidsBox.getChildren().addAll(solidsLabel, solidsComboBox);

        // --- Randomize Colors Button ---
        Button randomizeButton = new Button("Randomize Colors");
        randomizeButton.setPrefWidth(200);
        randomizeButton.setStyle("-fx-font-size: 16px;");
        randomizeButton.setOnAction(e -> randomizeColors());

        // --- TextArea for Parsing ---
        Label parseLabel = new Label("Input Text:");
        parseLabel.setTextFill(Color.WHITE);
        parseTextArea = new TextArea();
        parseTextArea.setPrefHeight(150);
        parseTextArea.setWrapText(true);

        // --- Parse Button ---
        parseButton = new Button("Parse");
        parseButton.setPrefWidth(100);
        parseButton.setOnAction(e -> handleParse());

        // --- Pagination Controls ---
        HBox paginationBox = new HBox();
        paginationBox.setSpacing(10);
        paginationBox.setPadding(new Insets(10, 0, 0, 0));

        prevButton = new Button("<");
        prevButton.setPrefWidth(50);
        prevButton.setOnAction(e -> handlePrevious());

        paginationLabel = new Label("(0 / 0)");
        paginationLabel.setTextFill(Color.WHITE);
        paginationLabel.setPrefWidth(100);
        paginationLabel.setStyle("-fx-font-size: 14px; -fx-alignment: center;");

        nextButton = new Button(">");
        nextButton.setPrefWidth(50);
        nextButton.setOnAction(e -> handleNext());

        paginationBox.getChildren().addAll(prevButton, paginationLabel, nextButton);

        // Disable pagination controls initially
        prevButton.setDisable(true);
        nextButton.setDisable(true);

		descriptionLabel = new Label();
		descriptionLabel.setTextFill(Color.WHITE);
		descriptionLabel.setPrefHeight(800);
		descriptionLabel.setWrapText(true);

        // Add all components to sidebar
        sidebar.getChildren().addAll(
                title,
                solidsBox,
                randomizeButton,
                parseLabel,
                parseTextArea,
                parseButton,
                paginationBox,
				descriptionLabel
        );

        return sidebar;
    }

    private void handleSolidSelection() {
        String selectedSolid = solidsComboBox.getValue();
        Solid newSolid;

        switch (selectedSolid) {
            case "Snub Cube":
                newSolid = new SnubCube();
                break;
            case "Icosahedron":
            default:
                newSolid = new Icosahedron();
                break;
        }

        // Replace the current solid with the new one
        solidsGroup.getChildren().remove(solid);
        solid = newSolid;
        solidsGroup.getChildren().add(solid);
    }

    private void handleParse() {
        String inputText = parseTextArea.getText();
        parseResults = processText(inputText);
        currentParseIndex = 0;
		if (parseResults.size() > 0) {
			onParseResultSelected(currentParseIndex);
		}
        updatePaginationLabel();
        updatePaginationButtons();
    }

    private void handlePrevious() {
        if (currentParseIndex > 0) {
            currentParseIndex--;
            updatePaginationLabel();
            updatePaginationButtons();
            // Call handler with the new selection
            onParseResultSelected(currentParseIndex);
        }
    }

    private void handleNext() {
        if (currentParseIndex < parseResults.size() - 1) {
            currentParseIndex++;
            updatePaginationLabel();
            updatePaginationButtons();
            // Call handler with the new selection
            onParseResultSelected(currentParseIndex);
        }
    }

    private void updatePaginationLabel() {
        if (parseResults.isEmpty()) {
            paginationLabel.setText("(0 / 0)");
        } else {
            paginationLabel.setText("(" + (currentParseIndex + 1) + " / " + parseResults.size() + ")");
        }
    }

    private void updatePaginationButtons() {
        prevButton.setDisable(currentParseIndex <= 0);
        nextButton.setDisable(currentParseIndex >= parseResults.size() - 1);
    }

    /**
     * Processes the input text and returns a list of results.
     *
     * @param text The input text to process.
     * @return A list of parsed results.
     */
    private List<String> processText(String text) {
        List<String> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains("[")) {
				results.add(line.trim());
			}
        }
        return results;
    }

    /**
     * Handler called when a parse result is selected.
     *
     * @param index The index of the selected parse result.
     */
    private void onParseResultSelected(int index) {
        if (index >= 0 && index < parseResults.size()) {
            String selectedResult = parseResults.get(index);

			SimpleStringProperty description1 = new SimpleStringProperty();
			List<Color> colorList = ResultListParser.getColorList(solid, selectedResult, description1);
            
            System.out.println("Selected Parse Result: " + selectedResult);
			descriptionLabel.setText(selectedResult + "\n" + description1.get());

			for (int i = 0; i < solid.getMeshViews().size(); i++) {
				solid.getMeshViews().get(i).setMaterial(createMaterial(colorList.get(i)));
			}
        }
    }

    private void randomizeColors() {
        for (MeshView meshView : solid.getMeshViews()) {
            double rand = random.nextDouble();

            if (rand <= 0.333) meshView.setMaterial(blueMaterial);
            else if (rand <= 0.666) meshView.setMaterial(redMaterial);
            else meshView.setMaterial(greenMaterial);
        }
    }

    private PhongMaterial createMaterial(Color color) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.rgb(30, 30, 30));
        material.setSpecularPower(10.0);
        return material;
    }



    private void setupLights(Group group) {
        // Brighter ambient light for better base visibility
        AmbientLight ambient = new AmbientLight(Color.rgb(50, 50, 50, 0.7));

        // Softer top light
        PointLight topLight = new PointLight(Color.rgb(255, 255, 255, 0.3));
        topLight.setTranslateX(0);
        topLight.setTranslateY(-0.5 * LIGHT_DISTANCE);
        topLight.setTranslateZ(-0.5 * LIGHT_DISTANCE);

        // Brighter base lights with better spread
        PointLight baseLight1 = new PointLight(Color.rgb(200, 200, 255, 0.3));
        baseLight1.setTranslateX(-LIGHT_DISTANCE);
        baseLight1.setTranslateY(LIGHT_DISTANCE);
        baseLight1.setTranslateZ(-0.577 * LIGHT_DISTANCE);

        PointLight baseLight2 = new PointLight(Color.rgb(255, 200, 200, 0.3));
        baseLight2.setTranslateX(LIGHT_DISTANCE);
        baseLight2.setTranslateY(LIGHT_DISTANCE);
        baseLight2.setTranslateZ(-0.577 * LIGHT_DISTANCE);

        PointLight baseLight3 = new PointLight(Color.rgb(200, 255, 200, 0.3));
        baseLight3.setTranslateX(0);
        baseLight3.setTranslateY(LIGHT_DISTANCE);
        baseLight3.setTranslateZ(1.155 * LIGHT_DISTANCE);

        group.getChildren().addAll(ambient, topLight, baseLight1, baseLight2, baseLight3);
    }

    private void initMouseControl(Group group, Stage stage) {
        Scene scene = stage.getScene();
        scene.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });

        scene.setOnMouseDragged(event -> {
            rotateX.setAngle(anchorAngleX - (anchorY - event.getSceneY()));
            rotateY.setAngle(anchorAngleY + anchorX - event.getSceneX());
        });


        // Adding key control for rotation around the visual vertical axis
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.RIGHT) {
                rotateAroundVisualYAxis(group, 10);
            } else if (event.getCode() == KeyCode.LEFT) {
                rotateAroundVisualYAxis(group, -10);
            } else if (event.getCode() == KeyCode.G) {
                // Start capturing snapshots and generating GIF
                captureAndCreateGif(group);
            }
        });
    }

    /**
     * Rotates the group around the visual vertical axis (screen's Y-axis).
     *
     * @param group The group to rotate.
     * @param angleDegrees The angle in degrees to rotate by.
     */
    private void rotateAroundVisualYAxis(Group group, double angleDegrees) {
        // Get the current transformation matrix of the group
        Affine currentTransform = new Affine(group.getLocalToSceneTransform());

        // The visual vertical axis in scene coordinates (up direction on the screen)
        Point3D visualYAxis = new Point3D(0, -1, 0); // Negative Y because JavaFX Y-axis goes downwards

        // Transform the visualYAxis to the group's local coordinate space
        Point3D axisInLocal = null;
        try {
            axisInLocal = currentTransform.inverseTransform(visualYAxis);
        } catch (NonInvertibleTransformException e) {
            e.printStackTrace();
            return;
        }

        // Create a rotation transform around the calculated axis
        Rotate rotateTransform = new Rotate(angleDegrees, axisInLocal);

        // Apply the rotation to the group
        group.getTransforms().add(rotateTransform);
    }
    
    private void captureAndCreateGif(Group group) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // [Same code as before, but wrap rotations and snapshots in Platform.runLater]
                List<RenderedImage> frames = new ArrayList<>();
    
                for (int i = 0; i < 36; i++) {
                    final int index = i;
                    Platform.runLater(() -> {
                        // Rotate the group by 10 degrees around the visual Y-axis
                        rotateAroundVisualYAxis(group, 10);
    
                        // Capture the snapshot
                        WritableImage snapshot = new WritableImage((int) (SCENE_WIDTH - SIDEBAR_WIDTH), (int) SCENE_HEIGHT);
                        subScene.snapshot(null, snapshot);
    
                        // Convert to BufferedImage and add to frames
                        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
                        frames.add(bufferedImage);
                    });
    
                    // Wait for the UI thread to process the event
                    Thread.sleep(100);
                }
    
                // Open save dialog and create GIF (also wrap in Platform.runLater)
                Platform.runLater(() -> {
                    
                    // Open save dialog
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save Animated GIF");
                    fileChooser.getExtensionFilters().add(new ExtensionFilter("GIF Image", "*.gif"));
                    File file = fileChooser.showSaveDialog(stage);

                    if (file != null) {
                        // Create the animated GIF
                        try {
                            createAnimatedGif(frames, file);
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(AlertType.ERROR);
                                alert.setTitle("Error");
                                alert.setHeaderText("Failed to create animated GIF");
                                alert.setContentText("An error occurred while creating the animated GIF. Please try again.");
                                alert.showAndWait();
                            });
                        }
                    }
                });
    
                return null;
            }
        };
    
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void createAnimatedGif(List<RenderedImage> frames, File output) throws Exception {
        ImageWriter gifWriter = ImageIO.getImageWritersByFormatName("gif").next();
        ImageOutputStream ios = new FileImageOutputStream(output);
        gifWriter.setOutput(ios);

        // Configure GIF sequence with desired parameters
        gifWriter.prepareWriteSequence(null);

        int delayTime = 100; // Time between frames in hundredths of a second (e.g., 100 = 1 second)

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage img = (BufferedImage) frames.get(i);
            ImageWriteParam param = gifWriter.getDefaultWriteParam();
            IIOMetadata metadata = gifWriter.getDefaultImageMetadata(new ImageTypeSpecifier(img), param);

            // Set the delay time and loop setting
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            // Configure the GraphicsControlExtension node
            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delayTime / 10)); // Convert to 1/100s units
            graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
            graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
            graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
            graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

            // Configure the ApplicationExtensions node to make the GIF loop
            if (i == 0) {
                IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
                IIOMetadataNode appExtensionNode = new IIOMetadataNode("ApplicationExtension");

                appExtensionNode.setAttribute("applicationID", "NETSCAPE");
                appExtensionNode.setAttribute("authenticationCode", "2.0");

                byte[] appExtensionBytes = new byte[]{
                        0x1, // Sub-block index (always 1)
                        0x0, 0x0 // Loop count (0 means infinite loop)
                };
                appExtensionNode.setUserObject(appExtensionBytes);
                appExtensionsNode.appendChild(appExtensionNode);
            }

            metadata.setFromTree(metaFormatName, root);

            IIOImage frame = new IIOImage(img, null, metadata);
            gifWriter.writeToSequence(frame, param);
        }

        gifWriter.endWriteSequence();
        ios.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        NodeList nodes = rootNode.getElementsByTagName(nodeName);

        if (nodes.getLength() > 0) {
            return (IIOMetadataNode) nodes.item(0);
        } else {
            IIOMetadataNode node = new IIOMetadataNode(nodeName);
            rootNode.appendChild(node);
            return node;
        }
    }
}