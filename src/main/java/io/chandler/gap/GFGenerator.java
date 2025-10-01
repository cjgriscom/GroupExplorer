package io.chandler.gap;

import java.util.HashSet;
import java.util.Set;

public class GFGenerator {

	private static final byte[][] IDENTITY_8 = makeIdentity(8);

	public static byte[][] identity(int length) {
		if (length == 8) {
			return cloneMatrix(IDENTITY_8);
		}
		return makeIdentity(length);
	}

	private static byte[][] makeIdentity(int length) {
		byte[][] result = new byte[length][length];
		for (int i = 0; i < length; i++) {
			result[i][i] = 1;
		}
		return result;
	}

	private static byte[][] cloneMatrix(byte[][] matrix) {
		byte[][] copy = new byte[matrix.length][matrix[0].length];
		for (int i = 0; i < matrix.length; i++) {
			System.arraycopy(matrix[i], 0, copy[i], 0, matrix[i].length);
		}
		return copy;
	}

	public static class GF2_8x8_Cache {
		private static final int SIZE = 8;
		private static final long IDENTITY_VALUE = computeIdentityValue();

		// the 8x8 matrix encoded as a bitset
		public final long value;

		// Sequence of generator choices as packed bits (LSB-first) of arbitrary length
		public final byte[] bitPath;
		public final int bitLength;

		private GF2_8x8_Cache(long value, byte[] bitPath, int bitLength) {
			this.value = value;
			this.bitPath = bitPath;
			this.bitLength = bitLength;
		}

		private GF2_8x8_Cache(byte[][] matrix, byte[] bitPath, int bitLength) {
			validateMatrix(matrix);
			this.value = encode(matrix);
			this.bitPath = bitPath;
			this.bitLength = bitLength;
		}

		public static GF2_8x8_Cache identity() {
			return new GF2_8x8_Cache(IDENTITY_VALUE, new byte[0], 0);
		}

		public static GF2_8x8_Cache fromMatrix(byte[][] matrix) {
			return new GF2_8x8_Cache(matrix, new byte[]{}, 0);
		}

		static int size() {
			return SIZE;
		}

		static long identityValue() {
			return IDENTITY_VALUE;
		}

		static boolean isIdentity(long encodedMatrix) {
			return encodedMatrix == IDENTITY_VALUE;
		}

		static long encode(byte[][] matrix) {
			validateMatrix(matrix);
			long v = 0L;
			for (int i = 0; i < SIZE; i++) {
				for (int j = 0; j < SIZE; j++) {
					v |= ((long) matrix[i][j] & 0x1L) << (i * SIZE + j);
				}
			}
			return v;
		}

		public static GF2_8x8_Cache fromEncoded(long value) {
			return new GF2_8x8_Cache(value, new byte[0], 0);
		}

		public static long multiplyEncoded(long a, long b) {
			// Treat each row as a byte. Row i of result is XOR of rows k of B where bit k in row i of A is 1.
			long result = 0L;
			for (int i = 0; i < SIZE; i++) {
				int aRow = (int) ((a >>> (i * SIZE)) & 0xFFL);
				int outRow = 0;
				int mask = aRow;
				while (mask != 0) {
					int k = Integer.numberOfTrailingZeros(mask);
					mask &= mask - 1;
					int bRowK = (int) ((b >>> (k * SIZE)) & 0xFFL);
					outRow ^= bRowK;
				}
				result |= ((long) (outRow & 0xFF)) << (i * SIZE);
			}
			return result;
		}

		private static void validateMatrix(byte[][] matrix) {
			if (matrix == null || matrix.length != SIZE) {
				throw new IllegalArgumentException("Matrix must be " + SIZE + "x" + SIZE);
			}
			for (int i = 0; i < SIZE; i++) {
				if (matrix[i] == null || matrix[i].length != SIZE) {
					throw new IllegalArgumentException("Matrix must be " + SIZE + "x" + SIZE);
				}
				for (int j = 0; j < SIZE; j++) {
					int entry = matrix[i][j];
					if ((entry & ~1) != 0) {
						throw new IllegalArgumentException("Matrix entries must be 0/1");
					}
				}
			}
		}

		private static long computeIdentityValue() {
			return encode(makeIdentity(SIZE));
		}

		public int getOrder() {
			return getOrder(Integer.MAX_VALUE);
		}

		public int getOrder(int maxOrder) {
			if (isIdentity()) {
				return 1;
			}
			long original = value;
			long power = IDENTITY_VALUE;
			int order = 0;
			Set<Long> seen = new HashSet<>();
			while (true) {
				power = multiplyEncoded(power, original);
				order++;
				if (order > maxOrder) {
					return -1;
				}
				if (isIdentity(power)) {
					return order;
				}
				if (!seen.add(power)) {
					throw new IllegalStateException("Failed to compute order; cycle detected without reaching identity.");
				}
			}
		}

		public boolean isIdentity() {
			return isIdentity(value);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof GF2_8x8_Cache)) {
				return false;
			}
			GF2_8x8_Cache other = (GF2_8x8_Cache) o;
			return value == other.value;
		}

		@Override
		public int hashCode() {
			return (int) (value ^ (value >>> 32));
		}

		public GF2_8x8_Cache copyAndUpdate(boolean bit, byte[][] newMatrix) {
			return copyAndUpdateEncoded(bit, encode(newMatrix));
		}

		public GF2_8x8_Cache copyAndUpdateEncoded(boolean bit, long newValue) {
			byte[] newPath;
			int newLength = bitLength + 1;
			int oldBytes = (bitLength + 7) >> 3;
			int newBytes = (newLength + 7) >> 3;
			if (newBytes == oldBytes) {
				newPath = new byte[oldBytes];
				System.arraycopy(bitPath, 0, newPath, 0, oldBytes);
			} else {
				newPath = new byte[newBytes];
				System.arraycopy(bitPath, 0, newPath, 0, oldBytes);
			}
			if (bit) {
				int byteIndex = bitLength >> 3;
				int bitOffset = bitLength & 7;
				newPath[byteIndex] = (byte) (newPath[byteIndex] | (1 << bitOffset));
			}
			return new GF2_8x8_Cache(newValue, newPath, newLength);
		}

		public byte[][] toMatrix() {
			byte[][] result = new byte[SIZE][SIZE];
			for (int i = 0; i < SIZE; i++) {
				for (int j = 0; j < SIZE; j++) {
					result[i][j] = (byte) ((value >> (i * SIZE + j)) & 0x1L);
				}
			}
			return result;
		}

		public int bitstringLength() { return bitLength; }

		public byte[] toBitstring() {
			return toBitstring(0, bitstringLength());
		}

		public byte[] toBitstring(int off, int length) {
			byte[] out = new byte[length];
			for (int i = off; i < off + length; i++) {
				int byteIndex = i >> 3;
				int bitOffset = i & 7;
				out[i] = (byte) (((bitPath[byteIndex] >> bitOffset) & 1) != 0 ? 1 : 0);
			}
			return out;
		}

		public boolean[] operations() {
			boolean[] out = new boolean[bitLength];
			for (int i = 0; i < bitLength; i++) {
				int byteIndex = i >> 3;
				int bitOffset = i & 7;
				out[i] = ((bitPath[byteIndex] >> bitOffset) & 1) != 0;
			}
			return out;
		}
	}

	public static void main(String[] args) {
		int gf = 2;
		String gen1 = "[1,0,0,1,0,0,1,1],[0,1,0,0,0,0,0,0],[0,0,1,1,0,0,1,1],[0,0,0,1,0,0,0,0],[0,0,0,0,1,0,0,0],[0,0,0,0,0,1,0,0],[0,0,0,1,0,0,0,1],[0,0,0,1,0,0,1,0]";
		String gen2 = "[0,1,0,1,1,0,0,1],[0,0,1,0,1,0,0,0],[1,1,0,0,1,0,1,0],[0,1,0,1,0,0,0,1],[0,1,1,0,1,1,0,1],[1,0,0,0,0,0,0,1],[1,0,0,0,1,0,0,0],[1,1,1,1,0,0,1,1]";
		GFGenerator gfGenerator = new GFGenerator(gf, gen1, gen2);
		System.out.println("Generator matrices:");
		for (int i = 0; i < gfGenerator.matrices.length; i++) {
			System.out.println("Matrix " + i + ":");
			printMatrix(gfGenerator.matrices[i]);
			System.out.println();
		}

		if (gfGenerator.matrices.length >= 2) {
			byte[][] product = multiply(gfGenerator.matrices[0], gfGenerator.matrices[1], gfGenerator.field);
			System.out.println("Product of matrix 0 and 1 in GF(" + gf + "):");
			printMatrix(product);
		}

		byte[] bitstring = new byte[] { (byte) 0b10101010, (byte) 0b11001100 };
		byte[][] result = gfGenerator.applyByBitstring(1, bitstring);
		System.out.println("Result of applying bitstring to identity matrix:");
		printMatrix(result);
	}

	int field;
	byte[][][] matrices;
	public GFGenerator(int field, String... generator) {
		if (field <= 1) {
			throw new IllegalArgumentException("Field order must be greater than 1");
		}
		if (generator == null || generator.length == 0) {
			throw new IllegalArgumentException("At least one generator matrix must be provided");
		}
		this.field = field;
		this.matrices = new byte[generator.length][][];
		for (int i = 0; i < generator.length; i++) {
			this.matrices[i] = parseMatrix(generator[i]);
		}
		ensureSameDimensions(this.matrices);
	}

	public byte[][] applyByBitstring(int length, byte[] bitstring) {
		// Start with identity matrix
		byte[][] result = identity(matrices[0].length);
		for (int i = 0; i < length; i++) {
			byte b = bitstring[i / 8];
			byte bit = (byte) (1 << (i % 8));
			if ((b & bit) != 0) {
				result = multiply(result, matrices[1], field);
			} else {
				result = multiply(result, matrices[0], field);
			}
		}
		return result;
	}

	public static byte[][] multiply(byte[][] a, byte[][] b, int field) {
		if (a == null || b == null) {
			throw new IllegalArgumentException("Matrices must not be null");
		}
		if (a.length == 0 || b.length == 0) {
			throw new IllegalArgumentException("Matrices must not be empty");
		}
		if (a[0].length != b.length) {
			throw new IllegalArgumentException("Matrix dimensions are incompatible for multiplication");
		}
		int rows = a.length;
		int cols = b[0].length;
		int shared = b.length;
		byte[][] result = new byte[rows][cols];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				byte sum = 0;
				for (int k = 0; k < shared; k++) {
					sum += a[i][k] * b[k][j];
				}
				result[i][j] = (byte)Math.floorMod(sum, field);
			}
		}
		return result;
	}

	private byte[][] parseMatrix(String definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Matrix definition must not be null");
		}
		String trimmed = definition.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Matrix definition must not be empty");
		}
		if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
			throw new IllegalArgumentException("Matrix definition must start with '[' and end with ']': " + definition);
		}
		String interior = trimmed.substring(1, trimmed.length() - 1);
		String[] rowStrings = interior.split("\\],\\[");
		if (rowStrings.length == 0) {
			throw new IllegalArgumentException("Matrix definition does not contain any rows: " + definition);
		}
		byte[][] matrix = new byte[rowStrings.length][];
		for (int i = 0; i < rowStrings.length; i++) {
			String rowStr = rowStrings[i].trim();
			String[] valueStrings = rowStr.split(",");
			matrix[i] = new byte[valueStrings.length];
			for (int j = 0; j < valueStrings.length; j++) {
				String value = valueStrings[j].trim();
				try {
					matrix[i][j] = (byte)Integer.parseInt(value);
				} catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Invalid integer '" + value + "' in matrix definition: " + definition, ex);
				}
			}
		}
		return enforceField(matrix);
	}

	private byte[][] enforceField(byte[][] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				matrix[i][j] = (byte)Math.floorMod(matrix[i][j], field);
			}
		}
		return matrix;
	}

	private static void ensureSameDimensions(byte[][][] mats) {
		if (mats.length == 0) {
			return;
		}
		int rows = mats[0].length;
		int cols = mats[0][0].length;
		for (byte[][] mat : mats) {
			if (mat.length != rows) {
				throw new IllegalArgumentException("All matrices must have the same number of rows");
			}
			for (byte[] row : mat) {
				if (row.length != cols) {
					throw new IllegalArgumentException("All matrices must have the same number of columns");
				}
			}
		}
	}

	private static void printMatrix(byte[][] matrix) {
		for (byte[] row : matrix) {
			StringBuilder builder = new StringBuilder();
			builder.append('[');
			for (int j = 0; j < row.length; j++) {
				if (j > 0) {
					builder.append(',');
				}
				builder.append(row[j]);
			}
			builder.append(']');
			System.out.println(builder);
		}
	}
}
