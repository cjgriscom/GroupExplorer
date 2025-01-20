package io.chandler.gap;
import java.util.*;

public class HurwitzQuaternions {
    private static final Map<Character, Quaternion> letterToQuaternion = new HashMap<>();
    private static final Map<Quaternion, Character> quaternionToLetter = new HashMap<>();
    private static final Set<Quaternion> hurwitzSet = new HashSet<>();

    public HurwitzQuaternions() {
        initializeQuaternions();
    }

    private void initializeQuaternions() {
        // Define all 24 Hurwitz quaternions
        List<Quaternion> quaternions = new ArrayList<>();

        // Unit quaternions
        quaternions.add(new Quaternion(1, 0, 0, 0));
        quaternions.add(new Quaternion(-1, 0, 0, 0));
        quaternions.add(new Quaternion(0, 1, 0, 0));
        quaternions.add(new Quaternion(0, -1, 0, 0));
        quaternions.add(new Quaternion(0, 0, 1, 0));
        quaternions.add(new Quaternion(0, 0, -1, 0));
        quaternions.add(new Quaternion(0, 0, 0, 1));
        quaternions.add(new Quaternion(0, 0, 0, -1));

        // 1/2 quaternions
        double half = 0.5;
        char[] signs = {'+', '-'};

        for (char s1 : signs) {
            for (char s2 : signs) {
                for (char s3 : signs) {
                    for (char s4 : signs) {
                        double a = s1 == '+' ? half : -half;
                        double b = s2 == '+' ? half : -half;
                        double c = s3 == '+' ? half : -half;
                        double d = s4 == '+' ? half : -half;
                        Quaternion q = new Quaternion(a, b, c, d);
                        quaternions.add(q);
                    }
                }
            }
        }

        // Assign letters A to X
        char letter = 'A';
        for (Quaternion q : quaternions) {
            letterToQuaternion.put(letter, q);
            quaternionToLetter.put(q, letter);
            hurwitzSet.add(q);
            letter++;
        }
    }

    /**
     * Static method to check if a quaternion is a Hurwitz quaternion.
     * @param q Quaternion to check
     * @return true if q is a Hurwitz quaternion, false otherwise
     */
    public static boolean isHurwitz(Quaternion q) {
        return hurwitzSet.contains(q);
    }

    /**
     * Get the letter assigned to a quaternion.
     * @param q Quaternion
     * @return Optional containing the letter if present
     */
    public Optional<Character> getLetter(Quaternion q) {
        return Optional.ofNullable(quaternionToLetter.get(q));
    }

    // Display all quaternions with their assigned letters and orders
    public void displayQuaternions() {
        System.out.println("Hurwitz Quaternions:");
        for (Map.Entry<Character, Quaternion> entry : letterToQuaternion.entrySet()) {
            char letter = entry.getKey();
            Quaternion q = entry.getValue();
            int order = q.computeOrder();
            System.out.printf("%c: %s, Order: %d%n", letter, q.toString(), order);
        }
    }

    // Multiply multiple quaternions based on their assigned letters
    public char multiplyQuaternions(String input) {
        if (input.length() < 2) {
            System.out.println("Please enter at least two letters (e.g., ABC).");
            return ' ';
        }

        Character lastResult = input.charAt(0);
        Quaternion accumulator = letterToQuaternion.get(input.charAt(0));
        if (accumulator == null) {
            System.out.println("Invalid letter entered: " + input.charAt(0));
            return ' ';
        }

        StringBuilder multiplicationSteps = new StringBuilder();


        for (int i = 1; i < input.length(); i++) {
            multiplicationSteps.append(lastResult);
            char currentChar = input.charAt(i);
            Quaternion nextQuaternion = letterToQuaternion.get(currentChar);
            if (nextQuaternion == null) {
                System.out.println("Invalid letter entered: " + currentChar);
                return ' ';
            }

            Quaternion product = accumulator.multiply(nextQuaternion);
            lastResult = quaternionToLetter.get(product);

            if (lastResult != null) {
                multiplicationSteps.append(currentChar).append('=').append(lastResult).append(": ")
                                   .append(accumulator.toString()).append(" * ")
                                   .append(nextQuaternion.toString()).append(" = ")
                                   .append(product.toString()).append("\n");
                accumulator = product;
            } else {
                multiplicationSteps.append(currentChar)
                                   .append(" resulted in an unknown quaternion: ")
                                   .append(product.toString()).append("\n");
                System.out.println(multiplicationSteps.toString());
                return ' ';
            }
        }

        System.out.println(multiplicationSteps.toString());
        Character finalResultLetter = quaternionToLetter.get(accumulator);
        if (finalResultLetter != null) {
            System.out.printf("Final Result: %c%n", finalResultLetter);
            return finalResultLetter;
        } else {
            System.out.println("Final product quaternion not found in the list: " + accumulator);
            return ' ';
        }
    }

    public static void main(String[] args) {
        HurwitzQuaternions hq = new HurwitzQuaternions();

        Scanner scanner = new Scanner(System.in);
        char lastResult = ' ';
        hq.displayQuaternions();
        while (true) {
            System.out.print("\nEnter two letters to multiply (or 'exit' to quit): ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.equals("EXIT")) break;
            hq.displayQuaternions();
            lastResult = hq.multiplyQuaternions(input);
        }
        scanner.close();
    }
}

class Quaternion {
    private double a, b, c, d;

    public Quaternion(double a, double b, double c, double d) {
        this.a = Math.round(a * 100) / 100.0;
        this.b = Math.round(b * 100) / 100.0;
        this.c = Math.round(c * 100) / 100.0;
        this.d = Math.round(d * 100) / 100.0;
    }

    // Quaternion multiplication
    public Quaternion multiply(Quaternion q) {
        double newA = this.a * q.a - this.b * q.b - this.c * q.c - this.d * q.d;
        double newB = this.a * q.b + this.b * q.a + this.c * q.d - this.d * q.c;
        double newC = this.a * q.c - this.b * q.d + this.c * q.a + this.d * q.b;
        double newD = this.a * q.d + this.b * q.c - this.c * q.b + this.d * q.a;
        return new Quaternion(newA, newB, newC, newD);
    }

    // Check if two quaternions are equal
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Quaternion)) return false;
        Quaternion q = (Quaternion) obj;
        return Double.compare(a, q.a) == 0 &&
               Double.compare(b, q.b) == 0 &&
               Double.compare(c, q.c) == 0 &&
               Double.compare(d, q.d) == 0;
    }

    // Compute the order of the quaternion
    public int computeOrder() {
        Quaternion result = new Quaternion(a, b, c, d);
        for (int i = 1; i <= 24; i++) { // 24 is the maximum order
            if (result.equals(new Quaternion(1, 0, 0, 0))) {
                return i;
            }
            result = result.multiply(this);
        }
        return -1; // Undefined order within 24 multiplications
    }

    /**
     * toString method to format quaternions based on their type.
     * - Hurwitz Quaternion:
     *   - Discrete: e.g., "-j"
     *   - 1/2 Quaternion: e.g., "0.5(+1,+i,+j,+k)"
     * - Non-Hurwitz Quaternion: Default format, e.g., "1.00+2.00i+3.00j+4.00k"
     */
    @Override
    public String toString() {
        if (HurwitzQuaternions.isHurwitz(this)) {
            // Check if it's a discrete quaternion (only one component is non-zero)
            int nonZeroComponents = 0;
            if (a != 0) nonZeroComponents++;
            if (b != 0) nonZeroComponents++;
            if (c != 0) nonZeroComponents++;
            if (d != 0) nonZeroComponents++;

            if (nonZeroComponents == 1) {
                StringBuilder sb = new StringBuilder();
                if (a != 0) {
                    sb.append(a > 0 ? "+1" : "-1");
                }
                if (b != 0) {
                    sb.append(b > 0 ? "+i" : "-i");
                }
                if (c != 0) {
                    sb.append(c > 0 ? "+j" : "-j");
                }
                if (d != 0) {
                    sb.append(d > 0 ? "+k" : "-k");
                }
                return sb.toString();
            } else {
                // It's a 1/2 quaternion
                StringBuilder sb = new StringBuilder("0.5(");
                boolean first = true;

                if (a != 0) {
                    if (!first) sb.append(",");
                    sb.append(a > 0 ? "+1" : "-1");
                    first = false;
                }
                if (b != 0) {
                    if (!first) sb.append(",");
                    sb.append(b > 0 ? "+i" : "-i");
                    first = false;
                }
                if (c != 0) {
                    if (!first) sb.append(",");
                    sb.append(c > 0 ? "+j" : "-j");
                    first = false;
                }
                if (d != 0) {
                    if (!first) sb.append(",");
                    sb.append(d > 0 ? "+k" : "-k");
                }
                sb.append(")");
                return sb.toString();
            }
        } else {
            // Not a Hurwitz quaternion, use default formatting
            return String.format("%.2f%+.2fi%+.2fj%+.2fk", a, b, c, d);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new double[] {a, b, c, d});
    }
}