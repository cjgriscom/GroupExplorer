#!/usr/bin/env python3
import sys
import json
import networkx as nx

def main():
    if len(sys.argv) < 2:
        print("Error: no edge data provided", file=sys.stderr)
        sys.exit(1)
    edge_data = sys.argv[1]
    # Default algorithm and seed.
    algorithm = "planar"
    seed = 42
    if len(sys.argv) >= 3:
        algorithm = sys.argv[2]
    if len(sys.argv) >= 4:
        try:
            seed = int(sys.argv[3])
        except ValueError:
            seed = 42

    # edge_data format: "u,v;u,v;..."
    G = nx.Graph()
    for edge_str in edge_data.split(";"):
        if edge_str:
            parts = edge_str.split(",")
            if len(parts) == 2:
                u = int(parts[0])
                v = int(parts[1])
                G.add_edge(u, v)
    if algorithm == "planar":
        try:
            pos = nx.planar_layout(G)
        except Exception as e:
            # Fallback if planar layout fails.
            pos = nx.spring_layout(G, seed=seed, dim=3)
    elif algorithm == "spring":
        pos = nx.spring_layout(G, seed=seed, iterations=1000, dim=3)
    else:
        # Default to planar layout.
        try:
            pos = nx.planar_layout(G)
        except Exception as e:
            pos = nx.spring_layout(G, seed=seed, dim=3)
    first_key = next(iter(pos))
    dims = len(pos[first_key])
    if dims == 3:
        min_x = min(p[0] for p in pos.values())
        max_x = max(p[0] for p in pos.values())
        min_y = min(p[1] for p in pos.values())
        max_y = max(p[1] for p in pos.values())
        min_z = min(p[2] for p in pos.values())
        max_z = max(p[2] for p in pos.values())
        for key in pos:
            x, y, z = pos[key]
            if max_x - min_x > 0:
                x = (x - min_x) / (max_x - min_x) * 0.8 + 0.1
            else:
                x = 0.5
            if max_y - min_y > 0:
                y = (y - min_y) / (max_y - min_y) * 0.8 + 0.1
            else:
                y = 0.5
            if max_z - min_z > 0:
                z = (z - min_z) / (max_z - min_z) * 0.8 + 0.1
            else:
                z = 0.5
            pos[key] = [x, y, z]
    else:
        # Force 3D output: treat 2D values with z = 0.
        min_x = min(p[0] for p in pos.values())
        max_x = max(p[0] for p in pos.values())
        min_y = min(p[1] for p in pos.values())
        max_y = max(p[1] for p in pos.values())
        for key in pos:
            x, y = pos[key]
            if max_x - min_x > 0:
                x = (x - min_x) / (max_x - min_x) * 0.8 + 0.1
            else:
                x = 0.5
            if max_y - min_y > 0:
                y = (y - min_y) / (max_y - min_y) * 0.8 + 0.1
            else:
                y = 0.5
            pos[key] = [x, y, 0.0]
    print(json.dumps(pos))

if __name__ == "__main__":
    main() 