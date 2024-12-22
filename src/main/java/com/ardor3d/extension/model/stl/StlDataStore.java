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

 import java.util.ArrayList;
 import java.util.List;
 
 import com.ardor3d.math.Vector3;
 
 public class StlDataStore {
	 private final List<Vector3> _vertices;
	 private final List<Vector3> _normals;
 
	 public StlDataStore() {
		 super();
		 _vertices = new ArrayList<>();
		 _normals = new ArrayList<>();
	 }
 
	 public List<Vector3> getVertices() {
		 return _vertices;
	 }
 
	 public List<Vector3> getNormals() {
		 return _normals;
	 }
 }