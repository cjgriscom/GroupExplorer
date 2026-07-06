package io.chandler.gap.graph.flatten;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GridState {
    private final Int2ObjectMap<int[]> placements;

    public GridState() {
        placements = new Int2ObjectOpenHashMap<>();
    }

    private GridState(Int2ObjectMap<int[]> placements) {
        this.placements = new Int2ObjectOpenHashMap<>(placements.size());
        for (Int2ObjectMap.Entry<int[]> e : placements.int2ObjectEntrySet()) {
            this.placements.put(e.getIntKey(), e.getValue().clone());
        }
    }

    public int[] get(int v) {
        return placements.get(v);
    }

    public void put(int v, int x, int y) {
        placements.put(v, new int[] { x, y });
    }

    public boolean isPlaced(int v) {
        return placements.containsKey(v);
    }

    public Set<Integer> placedVertices() {
        return Collections.unmodifiableSet(new HashSet<>(placements.keySet()));
    }

    /** Shifts every vertex with coordinate &gt; threshold on the given axis by delta. */
    public void shiftBeyond(int axis, int threshold, int delta) {
        for (int[] p : placements.values()) {
            if (p[axis] > threshold) p[axis] += delta;
        }
    }

    public GridState copy() {
        return new GridState(placements);
    }

    public int minX() {
        int min = Integer.MAX_VALUE;
        for (int[] p : placements.values()) {
            min = Math.min(min, p[0]);
        }
        return min;
    }

    public int maxX() {
        int max = Integer.MIN_VALUE;
        for (int[] p : placements.values()) {
            max = Math.max(max, p[0]);
        }
        return max;
    }

    public int minY() {
        int min = Integer.MAX_VALUE;
        for (int[] p : placements.values()) {
            min = Math.min(min, p[1]);
        }
        return min;
    }

    public int maxY() {
        int max = Integer.MIN_VALUE;
        for (int[] p : placements.values()) {
            max = Math.max(max, p[1]);
        }
        return max;
    }

    public static GridState parseGridText(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("((") && cleaned.endsWith("))")) {
            cleaned = cleaned.substring(2, cleaned.length() - 2);
        } else if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        GridState state = new GridState();
        String[] rows = cleaned.split("\\),\\s*\\(");
        Pattern tokenPattern = Pattern.compile("'?(x|\\d+)'?", Pattern.CASE_INSENSITIVE);

        for (int y = 0; y < rows.length; y++) {
            String row = rows[y].trim();
            if (row.startsWith("(")) {
                row = row.substring(1);
            }
            if (row.endsWith(")")) {
                row = row.substring(0, row.length() - 1);
            }
            row = row.trim();
            if (row.isEmpty()) {
                continue;
            }

            String[] rawTokens = row.split(",");
            int x = 0;
            for (String rawToken : rawTokens) {
                String token = rawToken.trim();
                if (token.isEmpty()) {
                    continue;
                }
                Matcher matcher = tokenPattern.matcher(token);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Invalid grid token: " + token);
                }
                String value = matcher.group(1);
                if (!"x".equalsIgnoreCase(value)) {
                    int vertex = Integer.parseInt(value);
                    state.put(vertex, x, y);
                }
                x++;
            }
        }
        return state;
    }

    public String toGridText() {
        if (placements.isEmpty()) {
            return "(())";
        }

        int minX = minX();
        int minY = minY();
        int maxX = maxX();
        int maxY = maxY();

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;

        int[][] cell = new int[height][width];
        boolean[][] occupied = new boolean[height][width];
        for (Int2ObjectMap.Entry<int[]> entry : placements.int2ObjectEntrySet()) {
            int[] p = entry.getValue();
            int y = p[1] - minY;
            int x = p[0] - minX;
            cell[y][x] = entry.getIntKey();
            occupied[y][x] = true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("((");
        for (int y = 0; y < height; y++) {
            if (y > 0) {
                sb.append(",\n");
            }
            sb.append('(');
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(',');
                }
                if (occupied[y][x]) {
                    sb.append(cell[y][x]);
                } else {
                    sb.append('x');
                }
            }
            sb.append(')');
        }
        sb.append("))");
        return sb.toString();
    }

    public String toPrettyString() {
        if (placements.isEmpty()) {
            return "(empty grid)";
        }

        int minX = minX();
        int minY = minY();
        int maxX = maxX();
        int maxY = maxY();

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;

        int[][] cell = new int[height][width];
        boolean[][] occupied = new boolean[height][width];
        int maxDigits = 1;
        for (Int2ObjectMap.Entry<int[]> entry : placements.int2ObjectEntrySet()) {
            int[] p = entry.getValue();
            int y = p[1] - minY;
            int x = p[0] - minX;
            cell[y][x] = entry.getIntKey();
            occupied[y][x] = true;
            maxDigits = Math.max(maxDigits, Integer.toString(entry.getIntKey()).length());
        }

        String empty = String.format("%" + maxDigits + "s", "x");
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) {
                sb.append('\n');
            }
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    sb.append(' ');
                }
                if (occupied[y][x]) {
                    sb.append(String.format("%" + maxDigits + "d", cell[y][x]));
                } else {
                    sb.append(empty);
                }
            }
        }
        return sb.toString();
    }

    int posX(int vertex) {
        int[] p = placements.get(vertex);
        return p == null ? 0 : p[0];
    }

    int posY(int vertex) {
        int[] p = placements.get(vertex);
        return p == null ? 0 : p[1];
    }

    Int2ObjectMap<int[]> rawPlacements() {
        return placements;
    }
}
