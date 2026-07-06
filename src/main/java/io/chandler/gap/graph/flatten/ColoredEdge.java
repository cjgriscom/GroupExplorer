package io.chandler.gap.graph.flatten;

import java.util.Objects;

public final class ColoredEdge {
    public final int u;
    public final int v;
    public final int color;

    public ColoredEdge(int u, int v, int color) {
        if (u < v) {
            this.u = u;
            this.v = v;
        } else {
            this.u = v;
            this.v = u;
        }
        this.color = color;
    }

    public boolean sharesVertex(ColoredEdge o) {
        return u == o.u || u == o.v || v == o.u || v == o.v;
    }

    private static char colorLetter(int color) {
        switch (color) {
            case 0:
                return 'r';
            case 1:
                return 'g';
            case 2:
                return 'b';
            default:
                return '?';
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ColoredEdge)) {
            return false;
        }
        ColoredEdge that = (ColoredEdge) o;
        return u == that.u && v == that.v && color == that.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(u, v, color);
    }

    @Override
    public String toString() {
        return u + "-" + v + "#" + colorLetter(color);
    }
}
