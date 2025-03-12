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
    // Computes a spring (force-directed) layout using a simple Fruchterman-Reingold algorithm.
    // - It computes repulsive and attractive forces per standard formulas.
    // - It calculates an initial temperature (t) based on the position range,
    //   decrements t by dt each iteration, and breaks early if average movement < threshold.
    public static Map<Integer, double[]> springLayout(Graph g, int iterations, int dim, long seed) {
        Set<Integer> nodeSet = g.getNodes();
        int n = nodeSet.size();
        Map<Integer, Integer> nodeIndex = new HashMap<>();
        List<Integer> nodes = new ArrayList<>(nodeSet);
        for (int i = 0; i < nodes.size(); i++) {
            nodeIndex.put(nodes.get(i), i);
        }

        // positions and displacements arrays (each row is a node's position vector)
        double[][] pos = new double[n][dim];
        double[][] disp = new double[n][dim];
        Random random = new Random(seed);
        // Initialize positions to random values in [0,1)
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < dim; d++) {
                pos[i][d] = random.nextDouble();
            }
        }
        
        // Set optimal distance between nodes: k = 1/sqrt(n)
        double k = 1.0 / Math.sqrt(n);        // optimal distance between nodes (as in networkx: k = 1/sqrt(n))
        
        // Compute initial temperature t as 10% of the max range of positions (for 2D, range=1 typically)
        double t = 0.1;
        double dt = t / (iterations + 1);
        
        // Iterative force computation.
        for (int iter = 0; iter < iterations; iter++) {
            // Reset displacements.
            for (int i = 0; i < n; i++) {
                Arrays.fill(disp[i], 0.0);
            }
            
            // Repulsive forces: for each pair (i, j)
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
                    // Repulsive force: f_r(d) = k^2 / d.
                    double force = (k * k) / distance;
                    for (int d = 0; d < dim; d++) {
                        double repForce = (delta[d] / distance) * force;
                        disp[i][d] += repForce;
                        disp[j][d] -= repForce;
                    }
                }
            }
            
            // Attractive forces: for each edge, pull connected nodes closer.
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
                // Attractive force: f_a(d) = d^2 / k.
                double force = (distance * distance) / k;
                for (int d = 0; d < dim; d++) {
                    double attrForce = (delta[d] / distance) * force;
                    disp[i][d] -= attrForce;
                    disp[j][d] += attrForce;
                }
            }
            
            // Update positions; compute total displacement to check for convergence.
            double totalDisp = 0.0;
            for (int i = 0; i < n; i++) {
                double dispLength = 0.0;
                for (int d = 0; d < dim; d++) {
                    dispLength += disp[i][d] * disp[i][d];
                }
                dispLength = Math.sqrt(dispLength);
                // Enforce minimum displacement to avoid division by zero.
                if (dispLength < EPS) {
                    dispLength = EPS;
                }
                // Move the node by (t/dispLength) * displacement, but not more than the displacement itself.
                for (int d = 0; d < dim; d++) {
                    double deltaDisp = (disp[i][d] / dispLength) * Math.min(dispLength, t);
                    pos[i][d] += deltaDisp;
                }
                totalDisp += dispLength;
            }
            
            // Decrease temperature.
            t = t - dt;
            // Average displacement.
            if (totalDisp / n < THRESHOLD) {
                break;
            }
        }
        
        // Build result map: Map each node id to its position vector.
        Map<Integer, double[]> positions = new HashMap<>();
        for (int i = 0; i < n; i++) {
            positions.put(nodes.get(i), pos[i]);
        }
        return positions;
    }
} 