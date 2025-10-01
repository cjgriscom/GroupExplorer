package io.chandler.gap;

public class GFGenerator {

	public static byte[][] identity(int length) {
		byte[][] result = new byte[length][length];
		for (int i = 0; i < length; i++) {
			result[i][i] = 1;
		}
		return result;
	}

	public static class GF2_8x8_Cache {
		// the 8x8 matrix
		public final long value;

		// This is used to later recover the sequences of generator moves
		public final int bitstring; // lsb byte is length, next 3 bytes are the bitstring

		private GF2_8x8_Cache(byte[][] matrix, int bitstring) {
			// Convert matrix to long
			this.bitstring = bitstring;

			long v = 0L;
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					if ((matrix[i][j] & ~1) != 0) {
						throw new IllegalArgumentException("Matrix entries must be 0/1");
					}
					v |= ((long) matrix[i][j] & 0x1L) << (i * 8 + j);
				}
			}
			this.value = v;
		}

		public int getOrder() {
			// Order is determined by multiplying the matrix by itself until it equals the identity matrix
			byte[][] matrix = toMatrix();
			int MAXIMUM = 255;
			for (int i = 0; i < MAXIMUM; i++) {
				if (isIdentity()) {
					return i + 1;
				}
				matrix = multiply(matrix, matrix, 2);
			}
			return -1;
		}

		public boolean isIdentity() {
			byte[][] matrix = toMatrix();
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					if (matrix[i][j] != (i == j ? 1 : 0)) {
						return false;
					}
				}
			}
			return true;
		}

		public boolean equals(Object o) {
			if (o instanceof GF2_8x8_Cache) {
				GF2_8x8_Cache other = (GF2_8x8_Cache) o;
				return value == other.value;
			}
			return false;
		}

		public int hashCode() {
			return (int) (value ^ (value >>> 32));
		}

		public GF2_8x8_Cache identityCache() {
			return new GF2_8x8_Cache(identity(8), 0);
		}

		public GF2_8x8_Cache copyAndUpdate(boolean bit, byte[][] newMatrix) {
			// Shift this cache's bitstring, then construct a new cache with the new matrix
			int newBitstring = (bitstring & 0xFFFFFF00) << 1;
			if (bit) {
				newBitstring |= 1;
			}
			int size = bitstring & 0xFF;
			if (size >= 255) throw new IllegalArgumentException("Bitstring size is too large");
			newBitstring |= ((size + 1) & 0xFF);
			return new GF2_8x8_Cache(newMatrix, newBitstring);
		}

		public byte[][] toMatrix() {
			byte[][] result = new byte[8][8];
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					result[i][j] = (byte) ((value >> (i * 8 + j)) & 0x1L);
				}
			}
			return result;
		}

		public int bitstringLength() {
			return bitstring & 0xFF;
		}

		public byte[] toBitstring() {
			byte[] out = new byte[bitstring & 0xFF];
			for (int i = 0; i < out.length; i++) {
				out[i] = (byte) ((bitstring >> (i * 8)) & 0xFF);
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
