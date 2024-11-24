package io.chandler.gap.render;

public class RenderUtil {
	
    public static float[] calculateTextureCoordinates(float[] points) {
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
