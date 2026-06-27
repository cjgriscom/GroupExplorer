/*
 * This file is ported from NetworkX library's spring_layout function.
 *
 * NetworkX is a Python package for the creation, manipulation, and study of the structure, dynamics, and functions of complex networks.
 *
 * This Java version is intended to provide similar functionality to the spring_layout function in NetworkX.
 *
 * Original NetworkX License:
 *
 *
   Copyright (C) 2004-2024, NetworkX Developers
   Aric Hagberg <hagberg@lanl.gov>
   Dan Schult <dschult@colgate.edu>
   Pieter Swart <swart@lanl.gov>
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.

     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.

     * Neither the name of the NetworkX Developers nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package networkx;

import java.util.*;
import java.lang.Math;

public class SpringLayout {

    private static final double EPS = 0.01; // small value to avoid division by zero
    private static final double THRESHOLD = 1e-4;

    /**
     * Mutable spring-layout state for incremental checkpointed iteration.
     */
    public static final class SpringLayoutState {
        private final Graph graph;
        private final List<Integer> nodes;
        private final Map<Integer, Integer> nodeIndex;
        private final double[][] pos;
        private final double[][] disp;
        private final int n;
        private final int dim;
        private final double k;
        private final double dt;
        private final int totalIterations;
        private double t;
        private int currentIteration;
        private boolean converged;

        private SpringLayoutState(Graph g, int dim, long seed, int totalIterations) {
            this.graph = g;
            this.dim = dim;
            this.totalIterations = totalIterations;
            Set<Integer> nodeSet = g.getNodes();
            this.n = nodeSet.size();
            this.nodes = new ArrayList<>(nodeSet);
            this.nodeIndex = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                nodeIndex.put(nodes.get(i), i);
            }
            this.pos = new double[n][dim];
            this.disp = new double[n][dim];
            Random random = new Random(seed);
            for (int i = 0; i < n; i++) {
                for (int d = 0; d < dim; d++) {
                    pos[i][d] = random.nextDouble();
                }
            }
            this.k = 1.0 / Math.sqrt(n);
            this.t = 0.1;
            this.dt = t / (totalIterations + 1);
            this.currentIteration = 0;
            this.converged = false;
        }

        public int getCurrentIteration() {
            return currentIteration;
        }

        public boolean isConverged() {
            return converged;
        }
    }

    public static SpringLayoutState createState(Graph g, int dim, long seed, int totalIterations) {
        return new SpringLayoutState(g, dim, seed, totalIterations);
    }

    public static void advance(SpringLayoutState state, int targetIteration) {
        if (state.converged) {
            state.currentIteration = Math.max(state.currentIteration, targetIteration);
            return;
        }
        int target = Math.min(targetIteration, state.totalIterations);
        while (state.currentIteration < target && !state.converged) {
            runOneIteration(state);
            state.currentIteration++;
        }
        if (state.currentIteration < targetIteration) {
            state.currentIteration = targetIteration;
        }
    }

    public static Map<Integer, double[]> getPositions(SpringLayoutState state) {
        Map<Integer, double[]> positions = new HashMap<>();
        for (int i = 0; i < state.n; i++) {
            positions.put(state.nodes.get(i), Arrays.copyOf(state.pos[i], state.dim));
        }
        return positions;
    }

    // Computes a spring (force-directed) layout using a simple Fruchterman-Reingold algorithm.
    // - It computes repulsive and attractive forces per standard formulas.
    // - It calculates an initial temperature (t) based on the position range,
    //   decrements t by dt each iteration, and breaks early if average movement < threshold.
    public static Map<Integer, double[]> springLayout(Graph g, int iterations, int dim, long seed) {
        SpringLayoutState state = createState(g, dim, seed, iterations);
        advance(state, iterations);
        return getPositions(state);
    }

    private static void runOneIteration(SpringLayoutState state) {
        Graph g = state.graph;
        int n = state.n;
        int dim = state.dim;
        double k = state.k;
        double[][] pos = state.pos;
        double[][] disp = state.disp;
        Map<Integer, Integer> nodeIndex = state.nodeIndex;

        for (int i = 0; i < n; i++) {
            Arrays.fill(disp[i], 0.0);
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double[] delta = new double[dim];
                double distance = 0.0;
                for (int d = 0; d < dim; d++) {
                    delta[d] = pos[i][d] - pos[j][d];
                    distance += delta[d] * delta[d];
                }
                distance = Math.sqrt(distance);
                if (distance < EPS) {
                    distance = EPS;
                }
                double force = (k * k) / distance;
                for (int d = 0; d < dim; d++) {
                    double repForce = (delta[d] / distance) * force;
                    disp[i][d] += repForce;
                    disp[j][d] -= repForce;
                }
            }
        }

        for (Graph.Edge edge : g.getEdges()) {
            int u = edge.u;
            int v = edge.v;
            int i = nodeIndex.get(u);
            int j = nodeIndex.get(v);
            double[] delta = new double[dim];
            double distance = 0.0;
            for (int d = 0; d < dim; d++) {
                delta[d] = pos[i][d] - pos[j][d];
                distance += delta[d] * delta[d];
            }
            distance = Math.sqrt(distance);
            if (distance < EPS) {
                distance = EPS;
            }
            double force = (distance * distance) / k;
            for (int d = 0; d < dim; d++) {
                double attrForce = (delta[d] / distance) * force;
                disp[i][d] -= attrForce;
                disp[j][d] += attrForce;
            }
        }

        double totalDisp = 0.0;
        double t = state.t;
        for (int i = 0; i < n; i++) {
            double dispLength = 0.0;
            for (int d = 0; d < dim; d++) {
                dispLength += disp[i][d] * disp[i][d];
            }
            dispLength = Math.sqrt(dispLength);
            if (dispLength < EPS) {
                dispLength = EPS;
            }
            for (int d = 0; d < dim; d++) {
                double deltaDisp = (disp[i][d] / dispLength) * Math.min(dispLength, t);
                pos[i][d] += deltaDisp;
            }
            totalDisp += dispLength;
        }

        state.t = t - state.dt;
        if (totalDisp / n < THRESHOLD) {
            state.converged = true;
        }
    }
} 