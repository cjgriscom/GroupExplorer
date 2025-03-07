package io.chandler.gap.render;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
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
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Renderer is responsible for setting up the JavaFX application,
 * handling user interactions, and rendering the 3D models.
 */
public class Renderer extends Application {
    private static final double SCENE_WIDTH = 2000;
    private static final double SCENE_HEIGHT = 1000;
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

    private Solid solid;
    private Group solidsGroup; // Group to hold solids for easy switching

    // Main Pagination
    private ComboBox<String> solidsComboBox;
    private TextArea parseTextArea;
    private Button parseButton;
    private Button prevButton;
    private Button nextButton;
    private Label paginationLabel;
    private Label descriptionLabel;

    // Auto Rotate
    private CheckBox autoRotateCheckBox;

    // Parse Results
    private List<String> parseResults = new ArrayList<>();
    private int currentParseIndex = 0;
    private Stage stage; // Class-level variable
    private SubScene subScene; // Class-level variable

    // Individual Sets
    private CheckBox isolateCheckBox;
    private Button setPrevButton;
    private Button setNextButton;
    private Label setPaginationLabel;
    private int currentSetIndex = 0;
    private int nColors = 0;
    private int[][][] generator;
    private List<Pair<Integer, Color>> currentColorList = new ArrayList<>();

    private GifWriter gifWriter = new GifWriter();

    // AnimationTimer for Auto Rotation
    private AnimationTimer autoRotateTimer;
    private AnimationTimer rotationAxesTimer;
    private static final int AUTO_ROTATE_DEGS_PER_SECOND = 60;
    private static final int ROTATION_AXES_DEGS_PER_SECOND = 120;
    private long lastUpdate = 0;
    private long lastRotationAxesUpdate = 0;
    // New Checkboxes
    private CheckBox showVerticesCheckBox = new CheckBox("Show vertices");
    private CheckBox animateVerticesCheckBox;

    // List to hold vertex MeshViews
    private List<MeshView> vertexMeshes = new ArrayList<>();
    private List<Point3D> rotationAxes = new ArrayList<>();
    private List<Rotate> vertexRotations = new ArrayList<>();
    private SimpleBooleanProperty[] vertexVisible = new SimpleBooleanProperty[0];

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
        solidsGroup.getChildren().addAll(solid.getMeshViews());
        reloadVertices();

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

        // Initialize Auto Rotate AnimationTimer
        initAutoRotateTimer();
        initRotationAxesTimer();

    }

    /**
     * Initializes the AnimationTimer for auto-rotation.
     */
    private void initAutoRotateTimer() {
        autoRotateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastUpdate > 0) {
                    double deltaSeconds = (now - lastUpdate) / 1_000_000_000.0;
                    double angle = AUTO_ROTATE_DEGS_PER_SECOND * deltaSeconds; // 5 degrees per second
                    rotateAroundVisualYAxis(solidsGroup, angle);
                }
                lastUpdate = now;
            }
        };
    }

    private void initRotationAxesTimer() {
        rotationAxesTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastRotationAxesUpdate > 0) {
                    double deltaSeconds = (now - lastRotationAxesUpdate) / 1_000_000_000.0;
                    double angle = ROTATION_AXES_DEGS_PER_SECOND * deltaSeconds; // 5 degrees per second
                    
                    for (int i = 0; i < vertexRotations.size(); i++) {
                        if (vertexRotations.get(i).getAngle() != 0) {
                            double sign = Math.signum(vertexRotations.get(i).getAngle());
                            vertexRotations.get(i).setAngle(vertexRotations.get(i).getAngle() + sign * angle);
                        }
                    }
                }
                lastRotationAxesUpdate = now;
            }
        };
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

        // --- Pagination Controls for Parse Results ---
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

        descriptionLabel = new Label();
        descriptionLabel.setTextFill(Color.WHITE);
        descriptionLabel.setPrefHeight(800);
        descriptionLabel.setWrapText(true);

        // Disable pagination controls initially
        prevButton.setDisable(true);
        nextButton.setDisable(true);

        // --- Individual Sets Controls ---
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #555555;");

        Label individualSetsLabel = new Label("Individual Sets:");
        individualSetsLabel.setTextFill(Color.WHITE);
        individualSetsLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Isolate Set Checkbox
        isolateCheckBox = new CheckBox("Isolate set");
        isolateCheckBox.setTextFill(Color.WHITE);
        isolateCheckBox.setOnAction(e -> updateColorDisplay());

        // Pagination for Individual Sets

        HBox setPaginationBox = new HBox();
        setPaginationBox.setSpacing(10);
        setPaginationBox.setPadding(new Insets(10, 0, 0, 0));

        setPrevButton = new Button("<");
        setPrevButton.setPrefWidth(30);
        setPrevButton.setOnAction(e -> handleSetPagination(-1));

        setPaginationLabel = new Label("(0 / 0)");
        setPaginationLabel.setTextFill(Color.WHITE);
        setPaginationLabel.setPrefWidth(80);
        setPaginationLabel.setStyle("-fx-font-size: 14px; -fx-alignment: center;");

        setNextButton = new Button(">");
        setNextButton.setPrefWidth(30);
        setNextButton.setOnAction(e -> handleSetPagination(1));

        setPaginationBox.getChildren().addAll(setPrevButton, setPaginationLabel, setNextButton);

        // Initialize Individual Sets pagination
        setPrevButton.setDisable(true);
        setNextButton.setDisable(true);
        setPaginationLabel.setText("(0 / 0)");

        // Show Vertices Checkbox
        showVerticesCheckBox.setTextFill(Color.WHITE);

        // Animate Vertices Checkbox
        animateVerticesCheckBox = new CheckBox("Animate vertices");
        animateVerticesCheckBox.setTextFill(Color.WHITE);
        animateVerticesCheckBox.setOnAction(e -> handleAnimateVerticesToggle());

        autoRotateCheckBox = new CheckBox("Auto Rotate");
        autoRotateCheckBox.setTextFill(Color.WHITE);
        autoRotateCheckBox.setOnAction(e -> handleAutoRotateToggle());

        // Add all components to sidebar
        sidebar.getChildren().addAll(
                title,
                solidsBox,
                parseLabel,
                parseTextArea,
                parseButton,
                paginationBox,
                separator,
                individualSetsLabel,
                isolateCheckBox,
                setPaginationBox,
                showVerticesCheckBox,
                animateVerticesCheckBox,
                autoRotateCheckBox,
                descriptionLabel
        );

        return sidebar;
    }

    /**
     * Handles the toggling of the Auto Rotate checkbox.
     * Starts or stops the AnimationTimer based on the checkbox state.
     */
    private void handleAutoRotateToggle() {
        if (autoRotateCheckBox.isSelected()) {
            // Start the AnimationTimer
            lastUpdate = 0;
            autoRotateTimer.start();
        } else {
            // Stop the AnimationTimer
            autoRotateTimer.stop();
        }
    }

    private void handleAnimateVerticesToggle() {
        if (animateVerticesCheckBox.isSelected()) {
            lastRotationAxesUpdate = 0;
            rotationAxesTimer.start();
        } else {
            rotationAxesTimer.stop();
            for (Rotate rotation : vertexRotations) {
                rotation.setAngle(Math.signum(rotation.getAngle()) * 0.001);
            }
        }
    }

    private void reloadVertices() {
        unloadVertices();
        loadVertices();
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
        solidsGroup.getChildren().clear();
        solidsGroup.getChildren().addAll(newSolid.getMeshViews());

        // Update the reference
        solid = newSolid;

        // Update vertex models
        reloadVertices();

        // Clear existing color lists
        currentColorList.clear();
        updateColorDisplay();
    }

    private void handleParse() {
        String inputText = parseTextArea.getText();
        parseResults = processText(inputText);
        currentParseIndex = 0;
        if (parseResults.size() > 0) {
            onParseResultSelected(currentParseIndex);
        } else {
            descriptionLabel.setText("");
            // Clear colors if no parse results
            currentColorList.clear();
            updateColorDisplay();
        }
        updatePaginationLabel();
        updatePaginationButtons();
        handleSetPagination(0);
    }

    private void handlePrevious() {
        if (currentParseIndex > 0) {
            currentParseIndex--;
            onParseResultSelected(currentParseIndex);
            updatePaginationLabel();
            updatePaginationButtons();
            handleSetPagination(0);
        }
    }

    private void handleNext() {
        if (currentParseIndex < parseResults.size() - 1) {
            currentParseIndex++;
            onParseResultSelected(currentParseIndex);
            updatePaginationLabel();
            updatePaginationButtons();
            handleSetPagination(0);
        }
    }

    /**
     * Consolidated method to handle pagination for Individual Sets.
     *
     * @param direction The direction to paginate. Use -1 for previous and 1 for next.
     */
    private void handleSetPagination(int direction) {
        if (direction < 0 && currentSetIndex > 0) {
            currentSetIndex--;
        } else if (direction > 0 && currentSetIndex < nColors - 1) {
            currentSetIndex++;
        }

        setPaginationLabel.setText("(" + (currentSetIndex + 1) + " / " + nColors + ")");
        setPrevButton.setDisable(currentSetIndex <= 0);
        setNextButton.setDisable(currentSetIndex >= nColors - 1);
        updateColorDisplay();

        for (Rotate rotation : vertexRotations) {
            rotation.setAngle(0);
        }

        if (nColors > 0) {
            int[][] set = generator[currentSetIndex];
            for (int[] face : set) {
                int faceISigned = solid.getPosOrNegFaceFromGenerator(face);
                int faceI = Math.abs(faceISigned) - 1;
                for (int vertex : face) {
                    vertexRotations.get(vertex-1).setAngle(0.001 * Math.signum(faceISigned));
                    vertexRotations.get(vertex-1).setAxis(this.rotationAxes.get(faceI));
                }
            }
        }
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

            this.generator = ResultListParser.parseGenerator(selectedResult);

            HashSet<Integer> usedVertices = new HashSet<>();
            for (int[][] cycle : generator) {
                for (int[] face : cycle) {
                    for (int vertex : face) {
                        usedVertices.add(vertex);
                    }
                }
            }

            for (int i = 0; i < vertexVisible.length; i++) {
                vertexVisible[i].set(usedVertices.contains(i+1));
            }

            currentColorList = ResultListParser.getColorList(solid, generator, description1);

            HashSet<Integer> colors = new HashSet<>();
            for (Pair<Integer, Color> pair : currentColorList) {
                if (pair.getKey() >= 0) colors.add(pair.getKey());
            }
            this.nColors = colors.size();
            System.out.println("Selected Parse Result: " + selectedResult);
            descriptionLabel.setText(selectedResult + "\n" + description1.get());

            // Initialize pagination for individual sets
            currentSetIndex = 0; // Reset to first index
            setPaginationLabel.setText("(" + (currentSetIndex + 1) + " / " + nColors + ")");
            setPrevButton.setDisable(currentSetIndex <= 0);
            setNextButton.setDisable(currentSetIndex >= nColors - 1);

            updateColorDisplay(); // Update color display based on the current selection
        }
    }

    /**
     * Updates the color display based on the current settings.
     */
    private void updateColorDisplay() {
        if (currentColorList == null || currentColorList.isEmpty()) {
            return;
        }

        for (int i = 0; i < solid.getMeshViews().size(); i++) {
            boolean isIsolated = isolateCheckBox.isSelected();
            if (i >= currentColorList.size()) {
                solid.getMeshViews().get(i).setMaterial(createMaterial(Color.GRAY));
                continue;
            }
            Pair<Integer, Color> color = currentColorList.get(i);
            if (!isIsolated || (color.getKey() == currentSetIndex || color.getKey() == -1)) {
                solid.getMeshViews().get(i).setMaterial(createMaterial(color.getValue()));
            } else {
                solid.getMeshViews().get(i).setMaterial(createMaterial(Color.GRAY));
            }
        }
    }

    private PhongMaterial createMaterial(Color color) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.rgb(30, 30, 30));
        material.setSpecularPower(10.0);
        return material;
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

    /**
     * Replaces the existing captureAndCreateGif method to use GifWriter.
     *
     * @param group The 3D group to rotate and capture.
     */
    private void captureAndCreateGif(Group group) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                List<BufferedImage> frames = new ArrayList<>();

                for (int i = 0; i < 36; i++) {
                    //final int index = i;
                    Platform.runLater(() -> {
                        // Rotate the group by 10 degrees around the visual Y-axis
                        rotateAroundVisualYAxis(group, 10);

                        // Capture the snapshot
                        WritableImage snapshot = new WritableImage((int) (SCENE_WIDTH - SIDEBAR_WIDTH), (int) SCENE_HEIGHT);
                        subScene.snapshot(null, snapshot);

                        // Convert to BufferedImage and add to frames
                        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
                        synchronized (frames) {
                            frames.add(bufferedImage);
                        }
                    });

                    // Wait for the UI thread to process the event
                    Thread.sleep(100);
                }

                // Wait until all frames are captured
                while (frames.size() < 36) {
                    Thread.sleep(50);
                }

                // Open save dialog and create GIF
                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save Animated GIF");
                    fileChooser.getExtensionFilters().add(new ExtensionFilter("GIF Image", "*.gif"));
                    File file = fileChooser.showSaveDialog(stage);

                    if (file != null) {
                        // Delegate GIF creation to GifWriter
                        Task<Void> gifTask = new Task<Void>() {
                            @Override
                            protected Void call() {
                                try {
                                    gifWriter.createAnimatedGif(frames, file);
                                    Platform.runLater(() -> {
                                        Alert alert = new Alert(AlertType.INFORMATION);
                                        alert.setTitle("Success");
                                        alert.setHeaderText("GIF Created Successfully");
                                        alert.setContentText("The animated GIF has been saved successfully.");
                                        alert.showAndWait();
                                    });
                                } catch (Exception e) {
                                    Platform.runLater(() -> {
                                        Alert alert = new Alert(AlertType.ERROR);
                                        alert.setTitle("Error");
                                        alert.setHeaderText("Failed to create animated GIF");
                                        alert.setContentText("An error occurred while creating the animated GIF. Please try again.");
                                        alert.showAndWait();
                                    });
                                }
                                return null;
                            }
                        };
                        new Thread(gifTask).start();
                    }
                });

                return null;
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stops the Auto Rotate AnimationTimer when the application is closed.
     */
    @Override
    public void stop() {
        if (autoRotateTimer != null) {
            autoRotateTimer.stop();
        }
        if (rotationAxesTimer != null) {
            rotationAxesTimer.stop();
        }
    }

    /**
     * Loads the vertices by executing the STL loading code.
     */
    private void loadVertices() {
        try {
            unloadVertices();
            rotationAxes.addAll(solid.getRotationAxes());

            vertexVisible = new SimpleBooleanProperty[solid.nFaces()];
            for (int i = 0; i < solid.nFaces(); i++) {
                vertexVisible[i] = new SimpleBooleanProperty(true);
            }
            
            List<MeshView> stlModels = solid.getVertexMeshObjects();
            if (stlModels == null) return;
            int i = 0;
            for (MeshView stlModel : stlModels) {
                Rotate rotate = new Rotate(0, new Point3D(1, 0, 0));
                this.vertexRotations.add(rotate);
                stlModel.getTransforms().add(0,rotate);
                stlModel.visibleProperty().bind(showVerticesCheckBox.selectedProperty().and(vertexVisible[i]));
                stlModel.setMaterial(createMaterial(Color.GRAY));
                solidsGroup.getChildren().add(stlModel);
                vertexMeshes.add(stlModel); // Add to the vertices list
                i++;
            }
        } catch (Exception e) {
            System.err.println("Failed to load STL vertices: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Unloads the vertices by removing them from the solidsGroup and clearing the list.
     */
    private void unloadVertices() {
        for (MeshView stlModel : vertexMeshes) {
            stlModel.visibleProperty().unbind();
        }
        vertexVisible = new SimpleBooleanProperty[0];
        solidsGroup.getChildren().removeAll(vertexMeshes);
        vertexMeshes.clear();
        rotationAxes.clear();
        vertexRotations.clear();
    }

    public static void main(String[] args) {
        launch(args);
    }
}