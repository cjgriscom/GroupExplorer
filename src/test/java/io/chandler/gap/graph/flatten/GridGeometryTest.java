package io.chandler.gap.graph.flatten;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GridGeometryTest {

    @Test
    void segRelationProperCrossOfUnitDiagonals() {
        assertEquals(GridGeometry.CROSS, GridGeometry.segRelation(0, 0, 1, 1, 0, 1, 1, 0));
    }

    @Test
    void segRelationLengthTwoAxisEdgesCrossAtIntegerPoint() {
        assertEquals(GridGeometry.CROSS, GridGeometry.segRelation(0, 0, 2, 0, 1, -1, 1, 1));
    }

    @Test
    void segRelationSharedEndpointOnly() {
        assertEquals(GridGeometry.SHARED_ENDPOINT_ONLY, GridGeometry.segRelation(0, 0, 3, 0, 0, 0, 0, 2));
    }

    @Test
    void segRelationCollinearOverlap() {
        assertEquals(GridGeometry.OVERLAP, GridGeometry.segRelation(0, 0, 4, 0, 2, 0, 6, 0));
    }

    @Test
    void segRelationCollinearDisjoint() {
        assertEquals(GridGeometry.DISJOINT, GridGeometry.segRelation(0, 0, 2, 0, 4, 0, 6, 0));
    }

    @Test
    void segRelationEndpointTouchesInteriorIsCross() {
        assertEquals(GridGeometry.CROSS, GridGeometry.segRelation(0, 0, 4, 0, 2, 0, 2, 2));
    }

    @Test
    void segRelationIdenticalSegmentsOverlap() {
        assertEquals(GridGeometry.OVERLAP, GridGeometry.segRelation(1, 2, 5, 2, 1, 2, 5, 2));
    }

    @Test
    void interiorLatticePoints() {
        assertArrayEquals(new int[][] { { 1, 0 } }, GridGeometry.interiorLatticePoints(2, 0));
        assertArrayEquals(new int[][] { { 1, 1 } }, GridGeometry.interiorLatticePoints(2, 2));
        assertArrayEquals(new int[][] { { 2, 1 } }, GridGeometry.interiorLatticePoints(4, 2));
        assertArrayEquals(new int[][] { { 1, 1 }, { 2, 2 } }, GridGeometry.interiorLatticePoints(3, 3));
        assertArrayEquals(new int[0][], GridGeometry.interiorLatticePoints(1, 1));
        assertArrayEquals(
                new int[][] { { 1, 0 }, { 2, 0 }, { 3, 0 }, { 4, 0 } },
                GridGeometry.interiorLatticePoints(5, 0));
    }

    @Test
    void segRelationParallelOffsetSegmentsDisjoint() {
        assertEquals(GridGeometry.DISJOINT, GridGeometry.segRelation(7, 6, 9, 7, 4, 3, 5, 4));
    }

    @Test
    void edgePenaltyExamples() {
        assertEquals(0, GridGeometry.edgePenalty(1, 0));
        assertEquals(1, GridGeometry.edgePenalty(1, 1));
        assertEquals(16, GridGeometry.edgePenalty(2, 1));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.edgePenalty(0, 0));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.edgePenalty(6, 0));
    }
}
