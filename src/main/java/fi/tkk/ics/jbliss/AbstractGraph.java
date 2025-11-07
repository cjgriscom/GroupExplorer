package fi.tkk.ics.jbliss;

import java.io.PrintStream;
import java.util.Map;

/**
 * A common interface for (un)directed graphs with colored vertices.
 * Implementations may interpret edges as directed or undirected.
 *
 * @param <V> vertex identifier type
 */
public interface AbstractGraph<V extends Comparable>
{
	/**
	 * @return the number of vertices in the graph
	 */
	int nof_vertices();

	/**
	 * Output the graph in graphviz dot format.
	 */
	void write_dot(PrintStream stream);

	/**
	 * Add a new vertex with default color 0.
	 * @return true if inserted
	 */
	boolean add_vertex(V v);

	/**
	 * Add a new vertex with a color.
	 * @return true if inserted
	 */
	boolean add_vertex(V v, int color);

	/**
	 * Delete a vertex.
	 * @return true if it existed
	 */
	boolean del_vertex(V v);

	/**
	 * Add an edge between v1 and v2.
	 * Implementations define directedness.
	 */
	void add_edge(V v1, V v2);

	/**
	 * Delete an edge between v1 and v2.
	 * Implementations define directedness.
	 */
	void del_edge(V v1, V v2);

	/**
	 * Find automorphisms; implementations define directedness.
	 */
	void find_automorphisms(Reporter reporter, Object reporter_param);

	/**
	 * Canonical labeling; implementations define directedness.
	 */
	Map<V,Integer> canonical_labeling();

	/**
	 * Canonical labeling with reporting; implementations define directedness.
	 */
	Map<V,Integer> canonical_labeling(Reporter reporter, Object reporter_param);

	/**
	 * Copy this graph.
	 */
	AbstractGraph<V> copy();

	/**
	 * Relabel this graph.
	 */
	<W extends Comparable> AbstractGraph<W> relabel(Map<V,W> labeling);
}