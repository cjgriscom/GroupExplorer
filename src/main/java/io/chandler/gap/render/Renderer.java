package io.chandler.gap.render;

import java.util.Random;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
public class Renderer extends Application {
    private static final double SCENE_WIDTH = 1024;
    private static final double SCENE_HEIGHT = 768;
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

    @Override
    public void start(Stage primaryStage) {
        // Create and position camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-30);
        camera.setFieldOfView(10);

        // Create solid
        solid = new Icosahedron();

        // Set up mouse control
        Group group = new Group(solid);
        group.getTransforms().addAll(rotateX, rotateY);
        Group group2 = new Group();
        group2.getTransforms().addAll(rotateXL, rotateYL);

        setupLights(group2);

        Group allGroups = new Group(group, group2);

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
        primaryStage.setTitle("Renderer with Sidebar");
        primaryStage.show();

        initMouseControl(group, primaryStage);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setPadding(new Insets(20));
        sidebar.setSpacing(20);
        sidebar.setStyle("-fx-background-color: #2E2E2E;");

        Label title = new Label("Controls");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Button randomizeButton = new Button("Randomize Colors");
        randomizeButton.setPrefWidth(200);
        randomizeButton.setStyle("-fx-font-size: 16px;");
        randomizeButton.setOnAction(e -> randomizeColors());

        sidebar.getChildren().addAll(title, randomizeButton);

        return sidebar;
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

    /**
     * Randomizes the colors of all faces.
     */
    private void randomizeColors() {
        for (MeshView meshView : solid.getMeshViews()) {
            double rand = random.nextDouble();

			if (rand <= 0.333) meshView.setMaterial(blueMaterial);
			else if (rand <= 0.666) meshView.setMaterial(redMaterial);
			else meshView.setMaterial(greenMaterial);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}