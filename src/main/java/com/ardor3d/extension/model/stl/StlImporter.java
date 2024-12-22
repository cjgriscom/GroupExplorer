/**
 * Copyright (c) 2008-2016 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

 package com.ardor3d.extension.model.stl;

 import java.io.BufferedReader;
 import java.io.IOException;
 import java.io.InputStream;
 import java.io.InputStreamReader;
 import java.io.Reader;
 import java.io.StreamTokenizer;
 import java.nio.ByteBuffer;
 import java.nio.ByteOrder;
 import java.util.logging.Level;
 import java.util.logging.Logger;
 
 import com.ardor3d.extension.model.util.FileHelper;
 import com.ardor3d.math.Vector3;
 import com.ardor3d.util.resource.ResourceLocator;
 import com.ardor3d.util.resource.ResourceLocatorTool;
 import com.ardor3d.util.resource.ResourceSource;
 
 /**
  * Reads an STL (STereoLithography) file and builds an Ardor3D Mesh. The STL format consists entirely of triangles and
  * as a result is a simple format to handle. Also, it is widely supported by the CAD/CAM community.
  *
  * This class supports both ASCII and Binary formats and files residing either locally or on a network.
  *
  * Refer to <a href="http://en.wikipedia.org/wiki/STL_(file_format)" target="_blank>Wikipedia</a>. Several STL models
  * can be downloaded freely from <a href="http://grabcad.com" target="_blank">GrabCAD</a>.
  *
  * @author gmseed
  * @see StlFileParser
  */
 public class StlImporter {
 
	 /**
	  * Extends StreamTokenizer for parsing STL files. The STL format for Ascii files is as follows:
	  *
	  * <pre>
	  * solid name
	  * ...
	  * facet normal ni nj nk
	  * outer loop
	  * vertex v1x v1y v1z
	  * vertex v2x v2y v2z
	  * vertex v3x v3y v3z
	  * endloop
	  * endfacet
	  * ...
	  * endsolid name
	  * </pre>
	  *
	  * @author gmseed
	  */
	 public static class StlFileParser extends StreamTokenizer {
 
		 /**
		  * Constructor.
		  *
		  * @param reader
		  *            The Reader.
		  */
		 public StlFileParser(final Reader reader) {
			 super(reader);
			 resetSyntax();
			 eolIsSignificant(true);
			 lowerCaseMode(true);
 
			 // all printable ascii characters
			 wordChars('!', '~');
 
			 whitespaceChars(' ', ' ');
			 whitespaceChars('\n', '\n');
			 whitespaceChars('\r', '\r');
			 whitespaceChars('\t', '\t');
		 }
 
		 /**
		  * Gets a number from the stream. Need to extract numbers since they may be in scientific notation. The number
		  * is returned in nval.
		  *
		  * @return Logical-true if successful, else logical-false.
		  */
		 protected boolean getNumber() {
			 try {
				 nextToken();
				 if (ttype != StreamTokenizer.TT_WORD) {
					 return false;
				 }
				 nval = Double.valueOf(sval).doubleValue();
			 } catch (final IOException e) {
				 System.err.println(e.getMessage());
				 return false;
			 } catch (final NumberFormatException e) {
				 System.err.println(e.getMessage());
				 return false;
			 }
			 return true;
		 }
 
	 }
 
	 // logger
	 private static final Logger LOGGER = Logger.getLogger(StlImporter.class.getName());
 
	 private static final String SOLID_KEYWORD = "solid";
	 private static final String[] END_SOLID_KEYWORD_PARTS = new String[] { "end", "solid" };
	 private static final String ENDSOLID_KEYWORD = "endsolid";
	 private static final String FACET_KEYWORD = "facet";
	 private static final String NORMAL_KEYWORD = "normal";
	 private static final String[] OUTER_LOOP_KEYWORD_PARTS = new String[] { "outer", "loop" };
	 private static final String VERTEX_KEYWORD = "vertex";
	 private static final String ENDLOOP_KEYWORD = "endloop";
	 private static final String ENDFACET_KEYWORD = "endfacet";
 
	 private ResourceLocator _modelLocator;
 
	 /**
	  * Constructor.
	  */
	 public StlImporter() {
		 super();
	 }
 
	 /**
	  * Reads a STL file from the given resource
	  *
	  * @param resource
	  *            the name of the resource to find.
	  * @return a StlGeometryStore data object containing the scene and other useful elements.
	  */
	 public StlGeometryStore load(final String resource) {
		 return load(resource, new FileHelper());
	 }
 
	 /**
	  * Reads a STL file from the given resource
	  *
	  * @param resource
	  *            the name of the resource to find.
	  * @param fileHelper
	  *            the file helper used to determine whether the resource is an Ascii file or a binary file
	  *
	  * @return a StlGeometryStore data object containing the scene and other useful elements.
	  */
	 public StlGeometryStore load(final String resource, final FileHelper fileHelper) {
		 final ResourceSource source;
		 if (_modelLocator == null) {
			 source = ResourceLocatorTool.locateResource(ResourceLocatorTool.TYPE_MODEL, resource);
		 } else {
			 source = _modelLocator.locateResource(resource);
		 }
 
		 if (source == null) {
			 throw new Error("Unable to locate '" + resource + "'");
		 }
 
		 return load(source, fileHelper);
	 }
 
	 /**
	  * Reads a STL file from the given resource
	  *
	  * @param resource
	  *            the resource to find.
	  * @return a StlGeometryStore data object containing the scene and other useful elements.
	  */
	 public StlGeometryStore load(final ResourceSource resource) {
		 return load(resource, new FileHelper());
	 }
 
	 /**
	  * Reads a STL file from the given resource
	  *
	  * @param resource
	  *            the resource to find.
	  * @param fileHelper
	  *            the file helper used to determine whether the resource is an Ascii file or a binary file
	  *
	  * @return a StlGeometryStore data object containing the scene and other useful elements.
	  */
	 public StlGeometryStore load(final ResourceSource resource, final FileHelper fileHelper) {
		 final boolean isAscii = fileHelper.isFilePureAscii(resource);
		 final StlGeometryStore store = new StlGeometryStore();
		 if (isAscii) { // Ascii file
			 try (final BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
				 final StlFileParser parser = new StlFileParser(reader);
				 try {
					 parser.nextToken();
					 // read "solid"
					 if (parser.sval != null && StlImporter.SOLID_KEYWORD.equals(parser.sval)) {
						 StlImporter.LOGGER.log(Level.INFO, "solid keyword on line " + parser.lineno());
					 } else {
						 StlImporter.LOGGER.log(Level.SEVERE,
								 "Ascii file but no solid keyword on line " + parser.lineno());
					 }
					 parser.nextToken();
					 // read object name if any
					 if (parser.ttype != StreamTokenizer.TT_WORD) {
						 StlImporter.LOGGER.log(Level.WARNING,
								 "Format Warning: expecting the (optional) object name on line " + parser.lineno());
					 } else {
						 final String objectName = parser.sval;
						 store.setCurrentObjectName(objectName);
						 // Reads the EOL for verifying that the file has a correct format
						 parser.nextToken();
						 if (parser.ttype != StreamTokenizer.TT_EOL) {
							 StlImporter.LOGGER.log(Level.SEVERE,
									 "Format Error: expecting End Of Line on line " + parser.lineno());
						 }
					 }
					 // read the rest of the file
					 parser.nextToken();
					 boolean endSolidFound = false;
					 // read all the facets
					 while (parser.ttype != StreamTokenizer.TT_EOF && !endSolidFound) {
						 endSolidFound = false;
						 // reads "endsolid"
						 if (StlImporter.ENDSOLID_KEYWORD.equals(parser.sval)) {
							 StlImporter.LOGGER.log(Level.INFO, "endsolid keyword on line " + parser.lineno());
							 endSolidFound = true;
						 } else {
							 // reads "end solid"
							 if (StlImporter.END_SOLID_KEYWORD_PARTS[0].equals(parser.sval)) {
								 parser.nextToken();
								 if (parser.ttype != StreamTokenizer.TT_WORD
										 || !StlImporter.END_SOLID_KEYWORD_PARTS[1].equals(parser.sval)) {
									 StlImporter.LOGGER.log(Level.SEVERE,
											 "Format Error:expecting 'end solid' on line " + parser.lineno());
								 } else {
									 StlImporter.LOGGER.log(Level.INFO, "end solid keyword on line " + parser.lineno());
									 endSolidFound = true;
								 }
							 } else {
								 // Reads "facet"
								 if (parser.ttype != StreamTokenizer.TT_WORD
										 || (!StlImporter.FACET_KEYWORD.equals(parser.sval)
												 && !StlImporter.END_SOLID_KEYWORD_PARTS[0].equals(parser.sval))) {
									 StlImporter.LOGGER.log(Level.SEVERE,
											 "Format Error:expecting 'facet' on line " + parser.lineno());
								 } else {
									 parser.nextToken();
									 // Reads a normal
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.NORMAL_KEYWORD.equals(parser.sval)) {
										 StlImporter.LOGGER.log(Level.SEVERE,
												 "Format Error:expecting 'normal' on line " + parser.lineno());
									 } else {
										 if (parser.getNumber()) {
											 final Vector3 normal = new Vector3();
											 normal.setX(parser.nval);
 
											 if (parser.getNumber()) {
												 normal.setY(parser.nval);
 
												 if (parser.getNumber()) {
													 normal.setZ(parser.nval);
 
													 store.getDataStore().getNormals().add(normal);
													 // Reads the EOL for verifying that the file has a correct format
													 parser.nextToken();
													 if (parser.ttype != StreamTokenizer.TT_EOL) {
														 StlImporter.LOGGER.log(Level.SEVERE,
																 "Format Error: expecting End Of Line on line "
																		 + parser.lineno());
													 }
												 } else {
													 StlImporter.LOGGER.log(Level.SEVERE,
															 "Format Error: expecting normal z-component on line "
																	 + parser.lineno());
												 }
											 } else {
												 StlImporter.LOGGER.log(Level.SEVERE,
														 "Format Error: expecting normal y-component on line "
																 + parser.lineno());
											 }
										 } else {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting normal x-component on line "
															 + parser.lineno());
										 }
									 }
 
									 parser.nextToken();
									 // Reads "outer loop" then EOL
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.OUTER_LOOP_KEYWORD_PARTS[0].equals(parser.sval)) {
										 StlImporter.LOGGER.log(Level.SEVERE,
												 "Format Error: expecting 'outer' on line " + parser.lineno());
									 } else {
										 parser.nextToken();
										 if (parser.ttype != StreamTokenizer.TT_WORD
												 || !StlImporter.OUTER_LOOP_KEYWORD_PARTS[1].equals(parser.sval)) {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error:expecting 'loop' on line " + parser.lineno());
										 } else {
											 // Reads the EOL for verifying that the file has a correct format
											 parser.nextToken();
											 if (parser.ttype != StreamTokenizer.TT_EOL) {
												 StlImporter.LOGGER.log(Level.SEVERE,
														 "Format Error: expecting End Of Line on line "
																 + parser.lineno());
											 }
										 }
									 }
 
									 parser.nextToken();
									 // Reads the first vertex
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.VERTEX_KEYWORD.equals(parser.sval)) {
										 System.err
												 .println("Format Error:expecting 'vertex' on line " + parser.lineno());
									 } else {
										 if (parser.getNumber()) {
											 final Vector3 vertex = new Vector3();
											 vertex.setX(parser.nval);
 
											 if (parser.getNumber()) {
												 vertex.setY(parser.nval);
 
												 if (parser.getNumber()) {
													 vertex.setZ(parser.nval);
 
													 store.getDataStore().getVertices().add(vertex);
													 // Reads the EOL for verifying that the file has a correct format
													 parser.nextToken();
													 if (parser.ttype != StreamTokenizer.TT_EOL) {
														 StlImporter.LOGGER.log(Level.SEVERE,
																 "Format Error: expecting End Of Line on line "
																		 + parser.lineno());
													 }
												 } else {
													 StlImporter.LOGGER.log(Level.SEVERE,
															 "Format Error: expecting vertex z-component on line "
																	 + parser.lineno());
												 }
											 } else {
												 StlImporter.LOGGER.log(Level.SEVERE,
														 "Format Error: expecting vertex y-component on line "
																 + parser.lineno());
											 }
										 } else {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting vertex x-component on line "
															 + parser.lineno());
										 }
									 }
 
									 parser.nextToken();
									 // Reads the second vertex
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.VERTEX_KEYWORD.equals(parser.sval)) {
										 System.err
												 .println("Format Error:expecting 'vertex' on line " + parser.lineno());
									 } else {
										 if (parser.getNumber()) {
											 final Vector3 vertex = new Vector3();
											 vertex.setX(parser.nval);
 
											 if (parser.getNumber()) {
												 vertex.setY(parser.nval);
 
												 if (parser.getNumber()) {
													 vertex.setZ(parser.nval);
 
													 store.getDataStore().getVertices().add(vertex);
													 // Reads the EOL for verifying that the file has a correct format
													 parser.nextToken();
													 if (parser.ttype != StreamTokenizer.TT_EOL) {
														 StlImporter.LOGGER.log(Level.SEVERE,
																 "Format Error: expecting End Of Line on line "
																		 + parser.lineno());
													 }
												 } else {
													 StlImporter.LOGGER.log(Level.SEVERE,
															 "Format Error: expecting vertex z-component on line "
																	 + parser.lineno());
												 }
											 } else {
												 StlImporter.LOGGER.log(Level.SEVERE,
														 "Format Error: expecting vertex y-component on line "
																 + parser.lineno());
											 }
										 } else {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting vertex x-component on line "
															 + parser.lineno());
										 }
									 }
 
									 parser.nextToken();
									 // Reads the third vertex
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.VERTEX_KEYWORD.equals(parser.sval)) {
										 System.err
												 .println("Format Error:expecting 'vertex' on line " + parser.lineno());
									 } else {
										 if (parser.getNumber()) {
											 final Vector3 vertex = new Vector3();
											 vertex.setX(parser.nval);
 
											 if (parser.getNumber()) {
												 vertex.setY(parser.nval);
 
												 if (parser.getNumber()) {
													 vertex.setZ(parser.nval);
 
													 store.getDataStore().getVertices().add(vertex);
													 // Reads the EOL for verifying that the file has a correct format
													 parser.nextToken();
													 if (parser.ttype != StreamTokenizer.TT_EOL) {
														 StlImporter.LOGGER.log(Level.SEVERE,
																 "Format Error: expecting End Of Line on line "
																		 + parser.lineno());
													 }
												 } else {
													 StlImporter.LOGGER.log(Level.SEVERE,
															 "Format Error: expecting vertex z-component on line "
																	 + parser.lineno());
												 }
											 } else {
												 StlImporter.LOGGER.log(Level.SEVERE,
														 "Format Error: expecting vertex y-component on line "
																 + parser.lineno());
											 }
										 } else {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting vertex x-component on line "
															 + parser.lineno());
										 }
									 }
 
									 parser.nextToken();
									 // Reads "endloop" then EOL
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.ENDLOOP_KEYWORD.equals(parser.sval)) {
										 StlImporter.LOGGER.log(Level.SEVERE,
												 "Format Error: expecting 'endloop' on line " + parser.lineno());
									 } else {
										 // Reads the EOL for verifying that the file has a correct format
										 parser.nextToken();
										 if (parser.ttype != StreamTokenizer.TT_EOL) {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting End Of Line on line " + parser.lineno());
										 }
									 }
 
									 parser.nextToken();
									 // Reads "endfacet" then EOL
									 if (parser.ttype != StreamTokenizer.TT_WORD
											 || !StlImporter.ENDFACET_KEYWORD.equals(parser.sval)) {
										 StlImporter.LOGGER.log(Level.SEVERE,
												 "Format Error:expecting 'endfacet' on line " + parser.lineno());
									 } else {
										 // Reads the EOL for verifying that the file has a correct format
										 parser.nextToken();
										 if (parser.ttype != StreamTokenizer.TT_EOL) {
											 StlImporter.LOGGER.log(Level.SEVERE,
													 "Format Error: expecting End Of Line on line " + parser.lineno());
										 }
									 }
								 }
							 }
						 }
						 parser.nextToken();
					 }
				 } catch (final IOException e) {
					 StlImporter.LOGGER.log(Level.SEVERE, "IO Error on line " + parser.lineno() + ": " + e.getMessage());
				 }
			 } catch (final Throwable t) {
				 throw new Error("Unable to load stl resource from URL: " + resource, t);
			 }
		 } else { // Binary file
			 try (final InputStream data = resource.openStream()) {
				 ByteBuffer dataBuffer; // To read in the correct endianness
				 final byte[] info = new byte[80]; // Header data
				 final byte[] numberFaces = new byte[4]; // the number of faces
				 byte[] faceData; // face data
				 int numberTriangles; // First info (after the header) on the file
 
				 // the first 80 bytes aren't important (except if you want to support non standard colors)
				 if (80 != data.read(info)) {
					 throw new IOException("Format Error: 80 bytes expected");
				 } else {
					 // read number of faces, setting the correct order
					 data.read(numberFaces);
					 dataBuffer = ByteBuffer.wrap(numberFaces);
					 dataBuffer.order(ByteOrder.nativeOrder());
 
					 // allocate buffer for face data, with each face requiring 50 bytes
					 numberTriangles = dataBuffer.getInt();
					 faceData = new byte[50 * numberTriangles];
 
					 // read face data
					 data.read(faceData);
					 dataBuffer = ByteBuffer.wrap(faceData);
					 dataBuffer.order(ByteOrder.nativeOrder());
 
					 // read each facet noting that after each fact there are 2 bytes without information
					 // no need to skip for last iteration
					 for (int index = 0; index < numberTriangles; index++) {
						 try {
							 // Reads a facet from a binary file.
							 // normal
							 store.getDataStore().getNormals().add(
									 new Vector3(dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()));
							 // 3 vertices
							 store.getDataStore().getVertices().add(
									 new Vector3(dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()));
							 store.getDataStore().getVertices().add(
									 new Vector3(dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()));
							 store.getDataStore().getVertices().add(
									 new Vector3(dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()));
							 if (index != numberTriangles - 1) {
								 dataBuffer.get();
								 dataBuffer.get();
							 }
						 } catch (final Throwable t) {
							 throw new Exception("Format Error: iteration number " + index, t);
						 }
					 }
				 }
			 } catch (final Throwable t) {
				 throw new Error("Unable to load stl resource from URL: " + resource, t);
			 }
		 }
 
		 store.commitObjects();
		 store.cleanup();
		 return store;
 
	 }
 
	 public void setModelLocator(final ResourceLocator locator) {
		 _modelLocator = locator;
	 }
 }