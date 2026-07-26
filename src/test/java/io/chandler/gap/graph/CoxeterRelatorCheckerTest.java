package io.chandler.gap.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.graph.CoxeterRelatorChecker.MatchResult;

class CoxeterRelatorCheckerTest {

	@Test
	void f4SpecHasExpectedEdgeOrders() {
		CoxeterDynkinSpec spec = CoxeterDynkinSpec.f4();
		assertEquals(3, spec.requiredOrder(0, 1));
		assertEquals(4, spec.requiredOrder(1, 2));
		assertEquals(3, spec.requiredOrder(2, 3));
		assertEquals(2, spec.requiredOrder(0, 2));
		assertEquals(2, spec.requiredOrder(0, 3));
		assertEquals(2, spec.requiredOrder(1, 3));
	}

	@Test
	void rejectsWrongGeneratorCount() {
		int[][][] twoGens = GroupExplorer.parseOperationsArr(Generators.wf4);
		MatchResult result = CoxeterRelatorChecker.match(twoGens, CoxeterDynkinSpec.f4());
		assertFalse(result.matches);
	}

	@Test
	void stripsInlineCommentsBeforeParsing() {
		String line = "[(1,2),(3,4)] # cong=1.5";
		assertFalse(new DynkinDiagramFilter(CoxeterDynkinSpec.f4()).filterLine(line).matches);
	}

	@Test
	void acceptsKnownF4LineFromWf4Study() {
		String line = "[(2,3)(4,7)(5,6)(9,12)(11,13)(14,19)(15,17)(16,21)(22,23),"
				+ "(1,2)(6,10)(7,8)(9,11)(12,21)(13,16)(14,20)(15,18)(23,24),"
				+ "(1,8)(5,16)(6,21)(11,14)(13,19)(18,24),"
				+ "(4,5)(6,7)(8,10)(14,15)(17,19)(18,20)]";
		MatchResult result = CoxeterRelatorChecker.match(
				GroupExplorer.parseOperationsArr(line), CoxeterDynkinSpec.f4());
		assertTrue(result.matches);
		assertEquals("A,B,C,D", result.comment);
	}
}
