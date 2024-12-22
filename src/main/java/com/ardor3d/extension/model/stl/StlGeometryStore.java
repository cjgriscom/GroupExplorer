/**
 * Copyright (c) 2008-2014 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

 package com.ardor3d.extension.model.stl;

 import java.nio.FloatBuffer;
 import java.nio.IntBuffer;
 
 import com.ardor3d.math.ColorRGBA;
 import com.ardor3d.math.Vector3;
 import com.ardor3d.math.type.ReadOnlyColorRGBA;
 import com.ardor3d.scenegraph.Mesh;
 import com.ardor3d.scenegraph.MeshData;
 import com.ardor3d.scenegraph.Node;
 import com.ardor3d.util.geom.BufferUtils;
 
 public class StlGeometryStore {
 
	 private final StlDataStore _dataStore;
 
	 private final Node _root;
 
	 private String _currentObjectName;
 
	 public StlGeometryStore() {
		 super();
		 _dataStore = new StlDataStore();
		 _root = new Node();
	 }
 
	 public StlDataStore getDataStore() {
		 return _dataStore;
	 }
 
	 public Node getScene() {
		 return _root;
	 }
 
	 void setCurrentObjectName(final String name) {
		 commitObjects();
		 _currentObjectName = name;
	 }
 
	 void commitObjects() {
		 if (!_dataStore.getNormals().isEmpty()) {
			 final String name;
			 if (_currentObjectName == null) {
				 name = "stl_mesh";
			 } else {
				 name = _currentObjectName;
			 }
 
			 // mesh object to return
			 final Mesh mesh = new Mesh(name);
			 final MeshData meshData = mesh.getMeshData();
 
			 // allocate buffers
			 final int numberTriangles = _dataStore.getNormals().size();
			 final int vertexBufferSize = 3 * numberTriangles;
			 final FloatBuffer vertexBuffer = BufferUtils.createVector3Buffer(vertexBufferSize);
			 final FloatBuffer normalBuffer = BufferUtils.createVector3Buffer(vertexBufferSize);
			 final FloatBuffer colourBuffer = BufferUtils.createColorBuffer(vertexBufferSize);
 
			 // fill buffers
			 int vertexCount = 0;
			 int normalCount = 0;
			 int colourCount = 0;
			 Vector3 v0;
			 Vector3 v1;
			 Vector3 v2;
			 Vector3 n;
			 final ReadOnlyColorRGBA defaultColour = ColorRGBA.WHITE;
			 for (int i = 0; i < numberTriangles; i++) {
				 // triangle properties
				 v0 = _dataStore.getVertices().get(3 * i + 0);
				 v1 = _dataStore.getVertices().get(3 * i + 1);
				 v2 = _dataStore.getVertices().get(3 * i + 2);
				 n = _dataStore.getNormals().get(i);
				 // vertices
				 BufferUtils.setInBuffer(v0, vertexBuffer, vertexCount++);
				 BufferUtils.setInBuffer(v1, vertexBuffer, vertexCount++);
				 BufferUtils.setInBuffer(v2, vertexBuffer, vertexCount++);
				 // normals - 1 foreach vertex
				 BufferUtils.setInBuffer(n, normalBuffer, normalCount++);
				 BufferUtils.setInBuffer(n, normalBuffer, normalCount++);
				 BufferUtils.setInBuffer(n, normalBuffer, normalCount++);
				 // colours - 1 foreach vertex
				 BufferUtils.setInBuffer(defaultColour, colourBuffer, colourCount++);
				 BufferUtils.setInBuffer(defaultColour, colourBuffer, colourCount++);
				 BufferUtils.setInBuffer(defaultColour, colourBuffer, colourCount++);
			 }
 
			 meshData.setVertexBuffer(vertexBuffer);
			 meshData.setNormalBuffer(normalBuffer);
			 meshData.setColorBuffer(colourBuffer);
 
			 // indices buffer
			 final int[] indices = new int[vertexBufferSize];
			 for (int i = 0; i < vertexBufferSize; i++) {
				 indices[i] = i;
			 }
			 final IntBuffer iBuffer = BufferUtils.createIntBuffer(indices.length);
			 iBuffer.put(indices);
			 meshData.setIndexBuffer(iBuffer);
 
			 _root.attachChild(mesh);
		 }
	 }
 
	 void cleanup() {
		 _currentObjectName = null;
	 }
 }