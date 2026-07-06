package io.chandler.gap.graph.flatten;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;

class OrToolsSmokeTest {

    @Test
    void cpSatSmokeTest() {
        Loader.loadNativeLibraries();

        CpModel model = new CpModel();
        IntVar x = model.newIntVar(0, 10, "x");
        IntVar y = model.newIntVar(0, 10, "y");
        model.addEquality(LinearExpr.sum(new IntVar[] { x, y }), 7);
        model.maximize(x);

        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        assertEquals(CpSolverStatus.OPTIMAL, status);
        assertEquals(7, solver.objectiveValue());
    }

}
