package io.chandler.gap.alg.drawing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Improved 3D octree for indexing during force calculations in the Fruchterman-Reingold force-directed layout algorithm.
 *
 */
public class FROctree {

    private Node root;
    
    // Minimum allowable dimension for splitting to prevent infinite loop.
    private static final double MIN_BOX_SIZE = 1e-6;

    /**
     * Create a new octree for the given cubic volume.
     * @param box the bounding volume as a Box3D
     */
    public FROctree(Box3D box) {
        this.root = new Node(box);
    }

    /**
     * Inserts a new 3D point into the octree.
     *
     * If the current node is a leaf:
     *   - If it is empty, the point is simply added.
     *   - If it is not empty but the node's region is too small to split further, the point is added to the same leaf.
     *   - Otherwise, the node is split and the point (along with all already stored points) is redistributed.
     *
     * @param p the 3D point to insert.
     */
    public void insert(Point3D p) {
        Node cur = root;
        while (true) {
            if (cur.isLeaf()) {
                // If the leaf is empty, add the point.
                if (cur.points.isEmpty()) {
                    cur.points.add(p);
                    cur.centroid = p;
                    cur.totalPoints = 1;
                    return;
                }
                // If the node's box is too small to split further, add the point in the same leaf.
                if (cur.box.getWidth() <= MIN_BOX_SIZE ||
                    cur.box.getHeight() <= MIN_BOX_SIZE ||
                    cur.box.getDepth() <= MIN_BOX_SIZE) {
                    cur.points.add(p);
                    cur.totalPoints++;
                    cur.centroid = updateCentroid(cur.centroid, p, cur.totalPoints);
                    return;
                }
                // Otherwise, split the leaf into children so as to properly separate points.
                split(cur);
            }
            // Update the centroid and count at the current node.
            cur.totalPoints++;
            cur.centroid = updateCentroid(cur.centroid, p, cur.totalPoints);
            int childIndex = getChildIndex(cur.box, p);
            // If by any chance the corresponding child does not exist, add here.
            if (cur.children == null || cur.children[childIndex] == null) {
                if (cur.points == null) {
                    cur.points = new ArrayList<>();
                }
                cur.points.add(p);
                return;
            }
            cur = cur.children[childIndex];
        }
    }

    /**
     * Helper method to update a centroid when a new point is added.
     *
     * @param oldCentroid the previous centroid; may be null
     * @param newPoint the new point being added
     * @param count the new total number of points
     * @return the updated centroid
     */
    private Point3D updateCentroid(Point3D oldCentroid, Point3D newPoint, int count) {
        if (oldCentroid == null) return newPoint;
        double newX = (oldCentroid.getX() * (count - 1) + newPoint.getX()) / count;
        double newY = (oldCentroid.getY() * (count - 1) + newPoint.getY()) / count;
        double newZ = (oldCentroid.getZ() * (count - 1) + newPoint.getZ()) / count;
        return Point3D.of(newX, newY, newZ);
    }

    /**
     * Splits a leaf node into 8 children and redistributes its stored points.
     *
     * After creating the 8 sub-boxes, all points stored in this node are reinserted into the appropriate child.
     *
     * @param node the node to split.
     */
    private void split(Node node) {
        Box3D box = node.box;
        double halfW = box.getWidth() / 2.0;
        double halfH = box.getHeight() / 2.0;
        double halfD = box.getDepth() / 2.0;
        double minX = box.getMinX();
        double minY = box.getMinY();
        double minZ = box.getMinZ();
        double midX = minX + halfW;
        double midY = minY + halfH;
        double midZ = minZ + halfD;
        
        node.children = new Node[8];
        node.children[0] = new Node(new Box3D(minX, minY, minZ, halfW, halfH, halfD));
        node.children[1] = new Node(new Box3D(midX, minY, minZ, halfW, halfH, halfD));
        node.children[2] = new Node(new Box3D(minX, midY, minZ, halfW, halfH, halfD));
        node.children[3] = new Node(new Box3D(midX, midY, minZ, halfW, halfH, halfD));
        node.children[4] = new Node(new Box3D(minX, minY, midZ, halfW, halfH, halfD));
        node.children[5] = new Node(new Box3D(midX, minY, midZ, halfW, halfH, halfD));
        node.children[6] = new Node(new Box3D(minX, midY, midZ, halfW, halfH, halfD));
        node.children[7] = new Node(new Box3D(midX, midY, midZ, halfW, halfH, halfD));
        
        // Save the old points and then clear this node to mark it as internal.
        List<Point3D> oldPoints = node.points;
        node.points = null;
        node.totalPoints = 0;
        node.centroid = null;
        
        // Reinsert each stored point into this node's children.
        for (Point3D pt : oldPoints) {
            insertHelper(node, pt);
        }
    }
    
    /**
     * Helper method for recursively inserting a point into an internal node's children.
     *
     * @param node the internal node (already split)
     * @param p the point to insert.
     */
    private void insertHelper(Node node, Point3D p) {
        int pos = getChildIndex(node.box, p);
        Node child = node.children[pos];
        if (child.isLeaf()) {
            if (child.points.isEmpty() ||
                child.box.getWidth() <= MIN_BOX_SIZE ||
                child.box.getHeight() <= MIN_BOX_SIZE ||
                child.box.getDepth() <= MIN_BOX_SIZE) {
                child.points.add(p);
                child.totalPoints++;
                child.centroid = updateCentroid(child.centroid, p, child.totalPoints);
            } else {
                split(child);
                insertHelper(child, p);
            }
        } else {
            child.totalPoints++;
            child.centroid = updateCentroid(child.centroid, p, child.totalPoints);
            int childPos = getChildIndex(child.box, p);
            if (child.children == null || child.children[childPos] == null) {
                if (child.points == null) {
                    child.points = new ArrayList<>();
                }
                child.points.add(p);
            } else {
                insertHelper(child, p);
            }
        }
    }
    
    /**
     * Determines the index of the child node into which a point should be inserted.
     *
     * This method now uses the correct midpoints for Y and Z.
     *
     * @param box the bounding box.
     * @param p the point.
     * @return an integer from 0 to 7 indicating the child index.
     */
    private int getChildIndex(Box3D box, Point3D p) {
        double midX = box.getMinX() + box.getWidth() / 2.0;
        double midY = box.getMinY() + box.getHeight() / 2.0;
        double midZ = box.getMinZ() + box.getDepth() / 2.0;
        int index = 0;
        if (p.getX() > midX) index |= 1;  // right half
        if (p.getY() > midY) index |= 2;  // upper half
        if (p.getZ() > midZ) index |= 4;  // back half
        return index;
    }
    
    /**
     * Returns the root node of the octree.
     * @return the root node.
     */
    public Node getRoot() {
        return root;
    }
    
    /**
     * Represents a node in the octree.
     *
     * A node is either a leaf (if points != null) or an internal node (if points == null and children != null).
     */
    static class Node {
        Box3D box;
        int totalPoints;
        Point3D centroid;
        Node[] children;
        List<Point3D> points;
        
        Node(Box3D box) {
            this.box = Objects.requireNonNull(box);
            this.points = new ArrayList<>();
            this.totalPoints = 0;
            this.centroid = null;
            this.children = null;
        }
        
        boolean isLeaf() {
            return points != null;
        }

        /**
         * Returns all points contained in this node (recursively if internal).
         * @return a list of points
         */
        public List<Point3D> getPoints() {
            if (points != null) {
                return points;
            } else {
                List<Point3D> res = new ArrayList<>();
                for (Node child : children) {
                    res.addAll(child.getPoints());
                }
                return res;
            }
        }

        /**
         * Returns the number of points contained in this node.
         * @return the number of points
         */
        public int getNumberOfPoints() {
            if (points != null) {
                return points.size();
            } else {
                return totalPoints;
            }
        }

        /**
         * Returns the bounding box of this node.
         * @return the Box3D representing the node region
         */
        public Box3D getBox() {
            return box;
        }

        /**
         * Returns the children of this node as a list.
         * @return a list of child nodes
         */
        public List<Node> getChildren() {
            if (children == null) return Collections.emptyList();
            return Arrays.asList(children);
        }

        /**
         * Returns true if this node contains any points.
         * For leaf nodes, it checks if the points list is non-empty; for internal nodes, it returns true if totalPoints > 0.
         * @return true if the node has any points, false otherwise
         */
        public boolean hasPoints() {
            return getNumberOfPoints() > 0;
        }

        /**
         * Returns the centroid of all points contained in this node.
         * For internal nodes, this is the computed centroid; for leaf nodes, if not set, it computes the average of stored points.
         * @return the centroid as a Point3D, or null if no points exist
         */
        public Point3D getCentroid() {
            if (centroid != null) {
                return centroid;
            }
            if (points != null && !points.isEmpty()) {
                double sumX = 0, sumY = 0, sumZ = 0;
                for (Point3D p : points) {
                    sumX += p.getX();
                    sumY += p.getY();
                    sumZ += p.getZ();
                }
                int n = points.size();
                return Point3D.of(sumX / n, sumY / n, sumZ / n);
            }
            return null;
        }
    }
} 