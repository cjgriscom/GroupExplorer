/**
 * Copyright (c) 2008-2016 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

 package com.ardor3d.extension.model.util;

 import java.io.BufferedReader;
 import java.io.DataInputStream;
 import java.io.FileInputStream;
 import java.io.InputStream;
 import java.io.InputStreamReader;
 import java.nio.ByteBuffer;
 import java.nio.CharBuffer;
 import java.nio.charset.CharacterCodingException;
 import java.nio.charset.CharsetDecoder;
 import java.nio.charset.StandardCharsets;
 
 import com.ardor3d.util.resource.ResourceSource;
 
 public class FileHelper {
 
	 /**
	  * Tests whether or not the specified string is pure ASCII. Uses the method discussed at:
	  *
	  * <pre>
	  * http://www.rgagnon.com/javadetails/java-0536.html
	  * http://stackoverflow.com/questions/3585053/in-java-is-it-possible-to-check-if-a-string-is-only-ascii
	  * </pre>
	  *
	  * @param string
	  *            String to test.
	  * @return Logical-true if pure ASCII, else logical-false.
	  */
	 public boolean isStringPureAscii(final String string) {
		 final byte bytearray[] = string.getBytes();
		 final CharsetDecoder d = StandardCharsets.US_ASCII.newDecoder();
		 try {
			 final CharBuffer r = d.decode(ByteBuffer.wrap(bytearray));
			 r.toString();
		 } catch (final CharacterCodingException e) {
			 return false;
		 }
		 return true;
	 }
 
	 /**
	  * Tests whether or not the file with the specified filename is pure ASCII. The method used is to read the file a
	  * line at a time and test if each line is ASCII.
	  *
	  * @param filename
	  *            File name.
	  * @return Logical-true if pure ASCII, else logical-false.
	  */
	 public boolean isFilePureAscii(final String filename) {
		 try (final FileInputStream fstream = new FileInputStream(filename);
				 final DataInputStream in = new DataInputStream(fstream);
				 final BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
			 String strLine;
			 // read file a line at a time
			 while ((strLine = br.readLine()) != null) {
				 final boolean isAscii = isStringPureAscii(strLine);
				 if (!isAscii) {
					 return false;
				 }
			 }
		 } catch (final Exception e) {
			 return false;
		 }
		 return true;
	 }
 
	 /**
	  * Tests whether or not the file with the specified filename is pure ASCII. The method used is to read the file a
	  * line at a time and test if each line is ASCII.
	  *
	  * @param resource
	  *            the name of the resource to find.
	  * @return Logical-true if pure ASCII, else logical-false.
	  */
	 public boolean isFilePureAscii(final ResourceSource resource) {
		 try (final InputStream inputStream = resource.openStream();
				 final DataInputStream in = new DataInputStream(inputStream);
				 final BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
			 String strLine;
			 // read file a line at a time
			 while ((strLine = br.readLine()) != null) {
				 final boolean isAscii = isStringPureAscii(strLine);
				 if (!isAscii) {
					 return false;
				 }
			 }
		 } catch (final Exception e) {
			 return false;
		 }
		 return true;
	 }
 }