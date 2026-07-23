package io.chandler.gap.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import io.chandler.gap.GroupExplorer;

class GraphSymmTest {

    /**
     * Known globally symmetric geometry from PlanarStudy survivors (136 verts).
     * Full |Aut(G)|=2; the undirected simple 1-WL quotient has |Aut(Q)|=4.
     */
    private static final String GLOBAL_SYMM_GEN =
            "[(1,84)(2,101)(3,27)(4,68)(5,58)(6,134)(7,8)(9,19)(10,23)(11,99)(12,136)"
            + "(13,38)(14,60)(15,78)(16,126)(17,87)(18,43)(21,114)(22,70)(24,69)(25,59)"
            + "(26,56)(28,48)(29,121)(30,50)(31,112)(32,65)(33,93)(34,83)(35,51)(36,42)"
            + "(37,86)(39,115)(40,61)(41,64)(44,75)(45,95)(46,128)(49,122)(52,85)"
            + "(53,116)(54,135)(55,82)(57,81)(62,72)(63,113)(71,120)(73,130)(74,91)"
            + "(76,90)(79,127)(80,108)(88,109)(92,125)(94,117)(96,132)(98,111)"
            + "(100,131)(102,104)(103,105)(106,110)(107,118)(119,124)(123,133),"
            + "(1,117)(2,95)(3,69)(4,113)(5,33)(7,114)(9,32)(10,89)(11,103)(12,122)"
            + "(13,125)(14,130)(15,116)(16,78)(17,22)(18,108)(19,59)(20,42)(21,49)"
            + "(23,68)(24,106)(26,66)(28,36)(29,75)(30,60)(31,52)(34,79)(35,53)"
            + "(37,105)(38,124)(39,94)(41,85)(43,131)(44,96)(45,118)(46,98)(47,119)"
            + "(48,83)(50,84)(51,97)(54,93)(55,81)(56,70)(57,91)(58,121)(61,87)"
            + "(63,72)(64,104)(65,135)(67,102)(71,92)(73,110)(74,128)(77,136)"
            + "(80,82)(88,123)(90,107)(99,109)(101,133)(111,134),"
            + "(1,79)(2,30)(3,69)(5,107)(6,40)(7,128)(8,25)(9,81)(10,50)(11,124)"
            + "(13,125)(14,109)(15,63)(16,68)(17,80)(18,133)(19,85)(20,61)(21,134)"
            + "(22,82)(23,78)(24,77)(26,66)(27,126)(28,36)(31,52)(32,55)(33,90)"
            + "(34,117)(35,44)(38,103)(39,121)(41,59)(42,87)(43,71)(45,51)(46,98)"
            + "(49,111)(53,96)(56,102)(58,94)(60,95)(62,76)(64,73)(65,135)(67,70)"
            + "(72,116)(74,114)(84,89)(86,120)(88,123)(92,131)(97,118)(99,130)"
            + "(100,129)(101,108)(104,110)(106,136)(112,132)(115,127)]";

    @Test
    void starLocalSymmetryIsQuotientedAway() {
        Graph<Integer, DefaultEdge> star = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i <= 4; i++) star.addVertex(i);
        for (int i = 1; i <= 4; i++) star.addEdge(0, i);

        assertEquals(BigInteger.valueOf(24),
                GraphSymm.automorphismGroupOrder(star, false,
                        GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES));
        assertEquals(BigInteger.ONE,
                GraphSymm.quotientAutomorphismGroupOrder(star,
                        GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES));
    }

    @Test
    void pathLocalEndSwapIsQuotientedAway() {
        Graph<Integer, DefaultEdge> path = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 1; i <= 3; i++) path.addVertex(i);
        path.addEdge(1, 2);
        path.addEdge(2, 3);

        assertEquals(BigInteger.TWO,
                GraphSymm.automorphismGroupOrder(path, false,
                        GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES));
        assertEquals(BigInteger.ONE,
                GraphSymm.quotientAutomorphismGroupOrder(path,
                        GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES));
    }

    @Test
    void cycleVertexTransitiveFallsBackToFullAut() {
        Graph<Integer, DefaultEdge> c5 = new SimpleGraph<>(DefaultEdge.class);
        for (int i = 0; i < 5; i++) c5.addVertex(i);
        for (int i = 0; i < 5; i++) c5.addEdge(i, (i + 1) % 5);

        BigInteger aut = GraphSymm.automorphismGroupOrder(c5, false,
                GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES);
        BigInteger qaut = GraphSymm.quotientAutomorphismGroupOrder(c5,
                GraphSymm.DEFAULT_DREADNAUT_PATH, GraphSymm.DEFAULT_USE_TRACES);
        assertEquals(BigInteger.TEN, aut);
        assertEquals(aut, qaut);
    }

    @Test
    void knownGlobalGeometryHasNontrivialQuotientAut() {
        int[][][] ops = GroupExplorer.parseOperationsArr(GLOBAL_SYMM_GEN);

        BigInteger aut = GraphSymm.automorphismGroupOrder(ops, false);
        BigInteger qaut = GraphSymm.quotientAutomorphismGroupOrder(ops);

        assertEquals(BigInteger.TWO, aut);
        assertEquals(BigInteger.valueOf(4), qaut);
        assertTrue(qaut.compareTo(aut) >= 0,
                "global quotient score should not understate Aut for this geometry");
    }
}
