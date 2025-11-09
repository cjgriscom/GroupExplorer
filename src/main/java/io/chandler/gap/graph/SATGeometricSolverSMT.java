package io.chandler.gap.graph;

import io.chandler.gap.GroupExplorer;

import java.nio.file.*;
import java.util.*;

/**
 * Determines whether a generator is "geometrically solvable" by emitting an
 * SMT-LIB2 file and invoking dReal (via Docker) or Z3 (via command line).
 *
 * Each operation in the generator defines a set of regular polygons (or line
 * segments) that must be stacked perpendicular to a shared axis passing through
 * the origin. The solver checks whether a consistent 3D point assignment exists.
 *
 * The first axis is locked to Z and the first polygon on that axis is
 * pre-eliminated analytically, dramatically reducing the variable count.
 */
public class SATGeometricSolverSMT {

    public enum Solver { DREAL_DOCKER, Z3_CLI }

    private static final int TIMEOUT_S = 60;
    private static final double PRECISION = 0.001;
    private static final double BOUND = 10.0;

    public static void main(String[] args) throws Exception {
        // Simple test
        test("[(1,2,3)(4,5,6),(2,7,8)]");
        // Original generator
        test("[(1,8,2)(3,10,12)(4,15,14)(9,11,13),(1,9)(4,7)(6,13)(10,15),(2,10)(4,5)(6,12)(9,14)]");
    }

    private static void test(String raw) throws Exception {
        String gen = GroupExplorer.renumberGeneratorNotation(raw);
        System.out.println("\n=== Generator: " + gen + " ===");
        long t0 = System.currentTimeMillis();
        String result = checkSolvability(gen, Solver.DREAL_DOCKER);
        System.out.println("Result: " + result + "  (" + (System.currentTimeMillis() - t0) + " ms)");
    }

    /**
     * @return "SAT", "UNSAT", or "UNKNOWN"
     */
    public static String checkSolvability(String generatorS, Solver solver) throws Exception {
        int[][][] generator = GroupExplorer.parseOperationsArr(generatorS);

        // Collect all unique vertex IDs
        Set<Integer> allVertices = new TreeSet<>();
        for (int[][] op : generator)
            for (int[] poly : op)
                for (int v : poly)
                    allVertices.add(v);

        // Build the SMT-LIB2 content
        StringBuilder smt = new StringBuilder();
        smt.append("(set-logic QF_NRA)\n");

        // Track which vertices have been pre-eliminated (assigned constant values)
        Map<Integer, double[]> fixedPoints = new HashMap<>();

        // --- Pre-eliminate the first polygon on the locked axis ---
        // Axis 0 is locked to Z = (0,0,1) through origin.
        // The first polygon of operation 0 contains point 1 (the generator is renumbered).
        // For an equilateral triangle centered at origin in the z=0 plane with vertex 1 at (1,0,0),
        // all vertices are analytically determined.
        int[][] firstOp = generator[0];
        int[] firstPoly = firstOp[0];
        int firstPolySize = firstPoly.length;

        // Compute the analytical vertices for the first polygon
        // It sits in z=0, centered at origin, with first vertex at (1, 0, 0)
        for (int k = 0; k < firstPolySize; k++) {
            double angle = 2.0 * Math.PI * k / firstPolySize;
            double x = Math.cos(angle);
            double y = Math.sin(angle);
            fixedPoints.put(firstPoly[k], new double[]{x, y, 0.0});
        }

        // Declare variables for non-fixed points
        for (int v : allVertices) {
            if (fixedPoints.containsKey(v)) {
                double[] pos = fixedPoints.get(v);
                smt.append(comment("P" + v + " = (" + fmt(pos[0]) + ", " + fmt(pos[1]) + ", " + fmt(pos[2]) + ") [pre-computed]"));
                smt.append(define("p" + v + "x", pos[0]));
                smt.append(define("p" + v + "y", pos[1]));
                smt.append(define("p" + v + "z", pos[2]));
            } else {
                for (String c : new String[]{"x", "y", "z"}) {
                    String name = "p" + v + c;
                    smt.append(declare(name));
                    smt.append(assertBound(name, -BOUND, BOUND));
                }
            }
        }

        // Break reflection symmetry: first non-fixed point's y >= 0
        for (int v : allVertices) {
            if (!fixedPoints.containsKey(v)) {
                smt.append(assertion("(>= p" + v + "y 0)"));
                break;
            }
        }

        // --- Per-operation constraints ---
        for (int opIdx = 0; opIdx < generator.length; opIdx++) {
            int[][] op = generator[opIdx];
            int polySize = op[0].length;

            smt.append(comment("--- Operation " + opIdx + " (polySize=" + polySize + ", nPolys=" + op.length + ") ---"));

            if (opIdx == 0) {
                // Axis 0 is locked to Z; emit as constants
                smt.append(define("d0x", 0));
                smt.append(define("d0y", 0));
                smt.append(define("d0z", 1));
            } else {
                String dx = "d" + opIdx + "x", dy = "d" + opIdx + "y", dz = "d" + opIdx + "z";
                smt.append(declare(dx));
                smt.append(declare(dy));
                smt.append(declare(dz));
                smt.append(assertBound(dx, -1, 1));
                smt.append(assertBound(dy, -1, 1));
                smt.append(assertBound(dz, -1, 1));
                // Unit length
                smt.append(assertion("(= (+ (* " + dx + " " + dx + ") (* " + dy + " " + dy + ") (* " + dz + " " + dz + ")) 1)"));
                // Break sign symmetry
                smt.append(assertion("(>= " + dz + " 0)"));
            }

            String dx = "d" + opIdx + "x", dy = "d" + opIdx + "y", dz = "d" + opIdx + "z";

            for (int polyIdx = 0; polyIdx < op.length; polyIdx++) {
                int[] poly = op[polyIdx];

                // Skip the first polygon on the locked axis (already pre-eliminated)
                boolean allFixed = true;
                for (int v : poly) if (!fixedPoints.containsKey(v)) { allFixed = false; break; }
                if (allFixed && opIdx == 0 && polyIdx == 0) {
                    smt.append(comment("Polygon " + Arrays.toString(poly) + " fully pre-computed, skipping"));
                    continue;
                }

                smt.append(comment("Polygon " + Arrays.toString(poly)));

                // Center on axis through origin: cross(sum, d) == 0  (2 independent components)
                String sumX = sumExpr(poly, "x");
                String sumY = sumExpr(poly, "y");
                String sumZ = sumExpr(poly, "z");
                // cross(sum, d) components:
                // cx0 = sumY*dz - sumZ*dy
                // cx1 = sumZ*dx - sumX*dz
                smt.append(assertion("(= (- (* " + sumY + " " + dz + ") (* " + sumZ + " " + dy + ")) 0)"));
                smt.append(assertion("(= (- (* " + sumZ + " " + dx + ") (* " + sumX + " " + dz + ")) 0)"));

                // Perpendicularity: all vertices same projection onto axis
                // dot(p_k, d) == dot(p_0, d)  for k=1..n-1
                String proj0 = dotExpr("p" + poly[0], dx, dy, dz);
                for (int k = 1; k < polySize; k++) {
                    String projK = dotExpr("p" + poly[k], dx, dy, dz);
                    smt.append(assertion("(= " + projK + " " + proj0 + ")"));
                }

                if (polySize >= 3) {
                    // Regular polygon: all edge lengths squared are equal
                    String firstSq = distSqExpr(poly[0], poly[1]);
                    for (int k = 1; k < polySize; k++) {
                        String edgeSq = distSqExpr(poly[k], poly[(k + 1) % polySize]);
                        smt.append(assertion("(= " + firstSq + " " + edgeSq + ")"));
                    }
                    // Non-degenerate
                    smt.append(assertion("(>= " + firstSq + " (/ 1 10))"));
                } else {
                    // Line segment: non-degenerate
                    smt.append(assertion("(>= " + distSqExpr(poly[0], poly[1]) + " (/ 1 10))"));
                }
            }
        }

        smt.append("(check-sat)\n");
        smt.append("(exit)\n");

        // Write to temp file
        Path smt2File = Files.createTempFile("geom_", ".smt2");
        Files.writeString(smt2File, smt.toString());

        System.out.println("Generated " + smt2File + " (" + smt.length() + " chars)");

        // Invoke solver
        String result;
        if (solver == Solver.DREAL_DOCKER) {
            result = runDReal(smt2File);
        } else {
            result = runZ3(smt2File);
        }

        // Clean up
        Files.deleteIfExists(smt2File);
        return result;
    }

    // ---- Solver invocation ----

    private static String runDReal(Path smt2File) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", smt2File.getParent().toString() + ":/work",
                "dreal/dreal4",
                "dreal", "--precision", String.valueOf(PRECISION),
                "/work/" + smt2File.getFileName().toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String output = new String(proc.getInputStream().readAllBytes()).trim();
        boolean exited = proc.waitFor(TIMEOUT_S + 10, java.util.concurrent.TimeUnit.SECONDS);
        if (!exited) {
            proc.destroyForcibly();
            return "UNKNOWN (timeout)";
        }

        System.out.println("dReal output: " + output);
        if (output.contains("unsat")) return "UNSAT";
        if (output.contains("delta-sat") || output.contains("sat")) return "SAT";
        return "UNKNOWN";
    }

    private static String runZ3(Path smt2File) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("z3", "-T:" + TIMEOUT_S, smt2File.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String output = new String(proc.getInputStream().readAllBytes()).trim();
        boolean exited = proc.waitFor(TIMEOUT_S + 10, java.util.concurrent.TimeUnit.SECONDS);
        if (!exited) {
            proc.destroyForcibly();
            return "UNKNOWN (timeout)";
        }

        System.out.println("Z3 output: " + output);
        if (output.startsWith("unsat")) return "UNSAT";
        if (output.startsWith("sat")) return "SAT";
        return "UNKNOWN";
    }

    // ---- SMT-LIB2 expression helpers ----

    private static String sumExpr(int[] poly, String coord) {
        if (poly.length == 1) return "p" + poly[0] + coord;
        StringBuilder sb = new StringBuilder("(+");
        for (int v : poly) sb.append(" p").append(v).append(coord);
        sb.append(")");
        return sb.toString();
    }

    private static String dotExpr(String prefix, String dx, String dy, String dz) {
        return "(+ (* " + prefix + "x " + dx + ") (* " + prefix + "y " + dy + ") (* " + prefix + "z " + dz + "))";
    }

    private static String distSqExpr(int a, int b) {
        String ax = "p" + a + "x", ay = "p" + a + "y", az = "p" + a + "z";
        String bx = "p" + b + "x", by = "p" + b + "y", bz = "p" + b + "z";
        return "(+ (* (- " + ax + " " + bx + ") (- " + ax + " " + bx + "))" +
               "  (* (- " + ay + " " + by + ") (- " + ay + " " + by + "))" +
               "  (* (- " + az + " " + bz + ") (- " + az + " " + bz + ")))";
    }

    // ---- SMT-LIB2 formatting helpers ----

    private static String comment(String text) { return "; " + text + "\n"; }

    private static String declare(String name) {
        return "(declare-fun " + name + " () Real)\n";
    }

    private static String define(String name, double value) {
        return "(define-fun " + name + " () Real " + fmt(value) + ")\n";
    }

    private static String assertBound(String name, double lo, double hi) {
        return "(assert (>= " + name + " " + fmt(lo) + "))\n" +
               "(assert (<= " + name + " " + fmt(hi) + "))\n";
    }

    private static String assertion(String expr) {
        return "(assert " + expr + ")\n";
    }

    private static String fmt(double v) {
        if (v == 0.0) return "0";
        if (v == 1.0) return "1";
        if (v == -1.0) return "(- 1)";
        if (v == (int) v) {
            int iv = (int) v;
            return iv < 0 ? "(- " + (-iv) + ")" : String.valueOf(iv);
        }
        // For irrational pre-computed values, use high-precision decimal
        String s = String.format("%.17g", Math.abs(v));
        return v < 0 ? "(- " + s + ")" : s;
    }
}
