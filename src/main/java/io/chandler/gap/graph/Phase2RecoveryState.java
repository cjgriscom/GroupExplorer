package io.chandler.gap.graph;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Serializable Phase 2 checkpoint written when interactive {@code recovery_on} is active.
 * <p>
 * serialVersionUID {@code 2} uses {@link CanonicalGraphHashSet} for the iso cache.
 * Pre-compact recovery files ({@code PlanarStudy$Phase2RecoveryState}, uid 1, String hashes)
 * must be upgraded with {@link ConvertPhase2Recovery}.
 */
public final class Phase2RecoveryState implements Serializable {
    private static final long serialVersionUID = 2L;

    public final String groupName;
    public final String baseFileName;
    public final int round;
    public final int candidateIndex;
    public final int roundCount;
    public final int acceptedCount;
    public final CanonicalGraphHashSet canonicalGraphs;
    public final ArrayList<int[][][]> currentCandidates;
    public final ArrayList<int[][][]> newCandidates;

    public Phase2RecoveryState(
            String groupName,
            String baseFileName,
            int round,
            int candidateIndex,
            int roundCount,
            int acceptedCount,
            CanonicalGraphHashSet canonicalGraphs,
            ArrayList<int[][][]> currentCandidates,
            ArrayList<int[][][]> newCandidates) {
        this.groupName = groupName;
        this.baseFileName = baseFileName;
        this.round = round;
        this.candidateIndex = candidateIndex;
        this.roundCount = roundCount;
        this.acceptedCount = acceptedCount;
        this.canonicalGraphs = canonicalGraphs;
        this.currentCandidates = currentCandidates;
        this.newCandidates = newCandidates;
    }
}
