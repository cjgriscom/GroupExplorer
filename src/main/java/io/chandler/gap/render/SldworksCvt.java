package io.chandler.gap.render;

import java.util.ArrayList;
import java.util.Scanner;

public class SldworksCvt {
	
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ArrayList<Float> coordinates = new ArrayList<>();

        System.out.println("Enter coordinates in the format 'X: value mm Y: value mm Z: value mm', one point per line. Type 'end' to finish.");

        while (scanner.hasNextLine()) {
            String line0 = scanner.nextLine();
            String line1 = scanner.nextLine();
            String line2 = scanner.nextLine();
            if (line0.equalsIgnoreCase("end")) {
                break;
            }
            String[] parts0 = line0.split("\\s+");
            String[] parts1 = line1.split("\\s+");
            String[] parts2 = line2.split("\\s+");
            try {
                float x = Float.parseFloat(parts0[1].replace("mm", ""));
                float y = Float.parseFloat(parts1[1].replace("mm", ""));
                float z = Float.parseFloat(parts2[1].replace("mm", ""));
                coordinates.add(x);
                coordinates.add(y);
                coordinates.add(z);
            } catch (Exception e) {
                System.out.println("Invalid input format. Please enter the coordinates correctly.");
            }
        }

        System.out.println("float[] points = {");
        for (int i = 0; i < coordinates.size(); i++) {
            System.out.print(coordinates.get(i) + "f");
            if (i < coordinates.size() - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("};");

        scanner.close();
    }
}
