package io.chandler.gap.graph.flatten;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ScoreReport {
    public final boolean legal;
    public final long totalPenalty;
    public final long edgePenalty;
    public final long junctionPenalty;
    public final List<String> violations;
    public final int[] chebHistogram;
    public final int axisCount;
    public final int diag45Count;
    public final int otherAngleCount;
    public final List<Junction> junctions;

    public static final class Junction {
        public final int color;
        public final long mx2;
        public final long my2;
        public final List<ColoredEdge> edges;

        Junction(int color, long mx2, long my2, List<ColoredEdge> edges) {
            this.color = color;
            this.mx2 = mx2;
            this.my2 = my2;
            this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        }

        @Override
        public String toString() {
            return "Junction{color=" + color + ", midpoint2=(" + mx2 + "," + my2 + "), edges=" + edges + "}";
        }
    }

    ScoreReport(
            boolean legal,
            long totalPenalty,
            long edgePenalty,
            long junctionPenalty,
            List<String> violations,
            int[] chebHistogram,
            int axisCount,
            int diag45Count,
            int otherAngleCount,
            List<Junction> junctions) {
        this.legal = legal;
        this.totalPenalty = totalPenalty;
        this.edgePenalty = edgePenalty;
        this.junctionPenalty = junctionPenalty;
        this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
        this.chebHistogram = chebHistogram.clone();
        this.axisCount = axisCount;
        this.diag45Count = diag45Count;
        this.otherAngleCount = otherAngleCount;
        this.junctions = Collections.unmodifiableList(new ArrayList<>(junctions));
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("legal=").append(legal).append('\n');
        sb.append("totalPenalty=").append(totalPenalty).append('\n');
        sb.append("edgePenalty=").append(edgePenalty).append('\n');
        sb.append("junctionPenalty=").append(junctionPenalty).append('\n');
        sb.append("axisCount=").append(axisCount)
                .append(" diag45Count=").append(diag45Count)
                .append(" otherAngleCount=").append(otherAngleCount).append('\n');
        sb.append("chebHistogram=");
        for (int i = 0; i < chebHistogram.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(chebHistogram[i]);
        }
        sb.append('\n');
        sb.append("junctionCount=").append(junctions.size()).append('\n');
        for (Junction junction : junctions) {
            sb.append("  ").append(junction).append('\n');
        }
        if (!violations.isEmpty()) {
            sb.append("violations:\n");
            for (String violation : violations) {
                sb.append("  - ").append(violation).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
