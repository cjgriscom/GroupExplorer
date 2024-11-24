package io.chandler.gap.render;

import java.util.Arrays;

import io.chandler.gap.CycleInverter;
import io.chandler.gap.Dodecahedron;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

public class Renderer extends Application {
    private static final double WIDTH = 1024;
    private static final double HEIGHT = 768;
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

    @Override
    public void start(Stage primaryStage) {
        // Create and position camera
		
        PerspectiveCamera camera = new PerspectiveCamera(true);
		
        camera.setTranslateZ(-30);
		System.out.println(camera.getFieldOfView());
		camera.setFieldOfView(10);

        // Create solid
        Group icosaGroup = createIcosahedron();

		for (int i = 0; i < icosaGroup.getChildren().size(); i++) {
			MeshView faceMeshView = (MeshView) icosaGroup.getChildren().get(i);
            // Assign color based on some condition (example)
            if (i < 7) {
                faceMeshView.setMaterial(redMaterial);
            } else if (i < 14) {
                faceMeshView.setMaterial(blueMaterial);
            } else {
                faceMeshView.setMaterial(greenMaterial);
            }
		}


        // Set up mouse control
        Group group = new Group(icosaGroup);
        group.getTransforms().addAll(rotateX, rotateY);
        Group group2 = new Group();
        group2.getTransforms().addAll(rotateXL, rotateYL);

		setupLights(group2);

		Group allGroups = new Group(group, group2);

        // Scene and stage setup
        Scene scene = new Scene(allGroups, WIDTH, HEIGHT, true);
        primaryStage.setScene(scene);
        scene.setFill(Color.BLACK);
        scene.setCamera(camera);

        initMouseControl(group, primaryStage);
        primaryStage.setTitle("Renderer");
        primaryStage.show();
    }

    private Group createIcosahedron() {
        Group icosaGroup = new Group();
        
        // Get the base geometry data
        float[] allPoints = getIcosahedronPoints();
        float[] texCoords = calculateTextureCoordinates(allPoints);
        
        // Create individual faces
        for (int i = 0; i < 20; i++) {
            int[] faceVertices = CycleInverter.invertArray(Dodecahedron.vertexFaces[i]);
            
            // Create a new mesh for this face
            TriangleMesh faceMesh = new TriangleMesh();
            faceMesh.setVertexFormat(VertexFormat.POINT_TEXCOORD);
            
            // Add only the points for this face
            float[] facePoints = new float[9]; // 3 vertices * 3 coordinates
            for (int j = 0; j < 3; j++) {
                int vertexIndex = faceVertices[j] - 1;
                System.arraycopy(allPoints, vertexIndex * 3, facePoints, j * 3, 3);
            }
            
            // Create face indices (always 0,1,2 since we only have 3 vertices)
            int[] faces = {0,0, 1,1, 2,2};
            
            faceMesh.getPoints().setAll(facePoints);
            faceMesh.getTexCoords().setAll(texCoords);
            faceMesh.getFaces().setAll(faces);
            
            MeshView faceMeshView = new MeshView(faceMesh);
            
            
            icosaGroup.getChildren().add(faceMeshView);
        }
        
        return icosaGroup;
    }

    private PhongMaterial createMaterial(Color color) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.rgb(30, 30, 30));
        material.setSpecularPower(10.0);
        return material;
    }

    private float[] getIcosahedronPoints() {
        float[] points = {
            0.01f, 13.32f, -26.66f,
            25.36f, 13.31f, -8.22f,
            15.65f, 13.32f, 21.58f,
            -15.68f, 13.31f, 21.56f,
            -25.35f, 13.31f, -8.25f,
            -0.02f, -13.34f, 26.65f,
            -25.34f, -13.34f, 8.23f,
            -15.64f, -13.37f, -21.56f,
            15.68f, -13.35f, -21.54f,
            25.3f, -13.36f, 8.34f,
            -0.03f, -29.8f, -0.02f,
            -0.05f, 29.8f, 0.03f
        };

        // Scale
        for (int i = 0; i < points.length; i++) {
            points[i] /= 29.8 / 2;
        }
        return points;
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

	private void setupLights(Group group) {
		
        // Brighter ambient light for better base visibility
        AmbientLight ambient = new AmbientLight(Color.rgb(80, 80, 80, 0.9));
        
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

    public static void main(String[] args) {
        launch(args);
    }

	private float[] calculateTextureCoordinates(float[] points) {
		
		// Define texture coordinates (u, v)
		// This example uses spherical projection for texture mapping
		float[] texCoords = new float[points.length / 3 * 2];
		for (int i = 0; i < points.length; i += 3) {
			float x = points[i];
			float y = points[i + 1];
			float z = points[i + 2];
			double longitude = Math.atan2(z, x);
			double latitude = Math.acos(y / Math.sqrt(x * x + y * y + z * z));

			// Normalize longitude and latitude to [0, 1]
			float u = (float) ((longitude + Math.PI) / (2 * Math.PI));
			float v = (float) (latitude / Math.PI);

			texCoords[i / 3 * 2] = u;
			texCoords[i / 3 * 2 + 1] = v;
		}
		return texCoords;
	}
}
