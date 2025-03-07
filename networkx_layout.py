#!/usr/bin/env python3
import sys
import json
import networkx as nx

def main():
    if len(sys.argv) < 2:
        print("Error: no edge data provided", file=sys.stderr)
        sys.exit(1)
    edge_data = sys.argv[1]
    # edge_data format: "u,v;u,v;..."
    G = nx.Graph()
    for edge_str in edge_data.split(";"):
        if edge_str:
            parts = edge_str.split(",")
            if len(parts) == 2:
                u = int(parts[0])
                v = int(parts[1])
                G.add_edge(u, v)
    try:
        pos = nx.planar_layout(G)
    except Exception as e:
        # Fallback to spring layout
        pos = nx.spring_layout(G, seed=42)
    # Normalize positions so that all coordinates are in [0.1, 0.9]
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
        pos[key] = (x, y)
    print(json.dumps(pos))

if __name__ == "__main__":
    main() 