package io.chandler.gap.render;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    @Override
    public void start(Stage primaryStage) {
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
        SubScene subScene = new SubScene(allGroups, SCENE_WIDTH - SIDEBAR_WIDTH, SCENE_HEIGHT, true, SceneAntialiasing.BALANCED);
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
		descriptionLabel.setPrefHeight(150);
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
    }

    public static void main(String[] args) {
        launch(args);
    }
}