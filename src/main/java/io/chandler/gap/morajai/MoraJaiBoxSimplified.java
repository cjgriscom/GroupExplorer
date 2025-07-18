package io.chandler.gap.morajai;

import java.util.Arrays;

public class MoraJaiBoxSimplified {

	// Color enum to replace the C color_t type
	public static enum Color {
		C_GY, // Gray
		C_RD, // Red
		C_GN, // Green
		C_BK, // Black
		C_WH, // White
		C_PI, // Pink
		C_PU, // Purple
		C_YE, // Yellow
		C_BU, // Blue
		C_OR; // Orange

		public static final int N_COLORS = values().length;
	}

	static final Color[] COLOR_VALUES = Color.values();

	public static final int[] targetColors = new int[4];

	// State variables
	private boolean initialized = false;
	private final int[] initTileColors = new int[9];
	private final int[] tileColors = new int[9];
	private final int[] scratchpad = new int[Math.max(8, Color.N_COLORS + 1)];

	// Constructor
	public MoraJaiBoxSimplified() {
		for (int i = 0; i < 9; i++) {
			initTileColors[i] = Color.C_GY.ordinal();
			tileColors[i] = Color.C_GY.ordinal();
		}
	}

	public void initFromState(int state) {
		for (int i = 0; i < 9; i++) {
			initTileColors[i] = state % 10;
			state /= 10;
		}

		reset();
		initialized = true;
	}

	public int getState() {
		int state = 0;
		int multiplier = 1;
		for (int i = 0; i < 9; i++) {
			state += tileColors[i] * multiplier;   // tile 0 → 10^0, tile 1 → 10^1, …
			multiplier *= 10;
		}
		return state;
	}

	public void reset() {
		System.arraycopy(initTileColors, 0, tileColors, 0, 9);
	}

	private int getTileToCheck(int outer) {
		switch (outer) {
			case 1: return 2;
			case 2: return 8;
			case 3: return 6;
			default: return outer;
		}
	}

	public boolean areInnerMatchingOuter() {
		for (int i = 0; i < 4; i++) {
			if (tileColors[getTileToCheck(i)] != targetColors[i]) {
				return false;
			}
		}
		return true;
	}

	private void swapTiles(int tile1, int tile2) {
		int temp = tileColors[tile1];
		tileColors[tile1] = tileColors[tile2];
		tileColors[tile2] = temp;
	}

	private int getOffsetTileIdx(int tile, int offsetX, int offsetY) {
		int curX = tile % 3;
		int curY = tile / 3;
		if (curX + offsetX < 0 || curX + offsetX >= 3) return -1;
		if (curY + offsetY < 0 || curY + offsetY >= 3) return -1;
		return tile + offsetX + offsetY * 3;
	}

	private int getOffsetColor(int tile, int offsetX, int offsetY) {
		int offsetTile = getOffsetTileIdx(tile, offsetX, offsetY);
		if (offsetTile == -1) return -1;
		return tileColors[offsetTile];
	}

	public Color getTileColor(int tile) {
		if (!initialized || tile >= 9) return Color.C_GY;
		return COLOR_VALUES[tileColors[tile]];
	}

	// Tile press action methods
	private void subPressGray(int tile) {
		// No action
	}

	private void subPressRed(int tile) {
		// Turn all white tiles black, and all black tiles red
		for (int i = 0; i < 9; i++) {
			if (tileColors[i] == Color.C_WH.ordinal()) {
				tileColors[i] = Color.C_BK.ordinal();
			} else if (tileColors[i] == Color.C_BK.ordinal()) {
				tileColors[i] = tileColors[tile]; // C_RD, except use self to propagate blue behavior
			}
		}
	}

	private void subPressGreen(int tile) {
		// Swap with opposing tile
		swapTiles(tile, 8 - tile);
	}

	private void subPressYellow(int tile) {
		// Tile moves north
		if (tile < 3) return;
		swapTiles(tile, tile - 3);
	}

	private void subPressPurple(int tile) {
		// Tile moves south
		if (tile >= 6) return;
		swapTiles(tile, tile + 3);
	}

	private void subPressOrange(int tile) {
		// Tile switches to the color of the majority of its neighbors
		int[] colorCounts = scratchpad(); // new int[Color.N_COLORS + 1];

		// Count colors of neighbors
		colorCounts[getOffsetColor(tile, -1, 0) + 1]++;
		colorCounts[getOffsetColor(tile, 1, 0) + 1]++;
		colorCounts[getOffsetColor(tile, 0, -1) + 1]++;
		colorCounts[getOffsetColor(tile, 0, 1) + 1]++;

		// Find the maximum
		int maxCount = 0;
		int maxColor = -1;
		int maxColorCount = 0;

		for (int i = 1; i < Color.N_COLORS + 1; i++) {
			int count = colorCounts[i];
			if (count > maxCount) {
				maxCount = count;
				maxColor = i - 1;
				maxColorCount = 1;
			} else if (count == maxCount) {
				maxColorCount++;
			}
		}

		if (maxColorCount == 1) {
			tileColors[tile] = maxColor;
		}
	}

	private int[] scratchpad() {
		Arrays.fill(scratchpad, 0);
		return scratchpad;
	}

	private void subPressPink(int tile) {
		// Rotate clockwise all adjacent (inc. diagonal) tiles, skipping anything off edge
		int[] offsetTiles = scratchpad(); // new int[8];
		int totalTiles = 0;

		int offsetTile = getOffsetTileIdx(tile, -1, 0);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, -1, 1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, 0, 1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, 1, 1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, 1, 0);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, 1, -1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, 0, -1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;
		offsetTile = getOffsetTileIdx(tile, -1, -1);
		if (offsetTile != -1) offsetTiles[totalTiles++] = offsetTile;

		if (totalTiles > 0) {
			// Rotate the tiles
			int temp = tileColors[offsetTiles[0]];
			for (int i = 0; i < totalTiles - 1; i++) {
				tileColors[offsetTiles[i]] = tileColors[offsetTiles[i + 1]];
			}
			tileColors[offsetTiles[totalTiles - 1]] = temp;
		}
	}

	private static final int[][] WHITE_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
	private void subPressWhite(int tile) {
		// Invert (grey -> white, white -> grey) adjacent grey tiles and self
		int selfColor = tileColors[tile]; // C_WH, except use self to propagate blue behavior

		for (int[] offset : WHITE_OFFSETS) {
			int offsetTile = getOffsetTileIdx(tile, offset[0], offset[1]);
			if (offsetTile != -1) {
				if (tileColors[offsetTile] == Color.C_GY.ordinal()) {
					tileColors[offsetTile] = selfColor;
				} else if (tileColors[offsetTile] == selfColor) {
					tileColors[offsetTile] = Color.C_GY.ordinal();
				}
			}
		}
		tileColors[tile] = Color.C_GY.ordinal();
	}

	private void subPressBlack(int tile) {
		// Rotate the row to the right
		int tile0 = (tile / 3) * 3;
		int tile1 = tile0 + 1;
		int tile2 = tile0 + 2;
		int temp = tileColors[tile0];
		tileColors[tile0] = tileColors[tile2];
		tileColors[tile2] = tileColors[tile1];
		tileColors[tile1] = temp;
	}

	public void pressTile(int tile) {
		if (tile >= 9) return;

		Color color = COLOR_VALUES[tileColors[tile]];
		if (color == Color.C_BU) {
			color = COLOR_VALUES[tileColors[4]];
		}

		switch (color) {
			case C_GY: subPressGray(tile); break;
			case C_RD: subPressRed(tile); break;
			case C_GN: subPressGreen(tile); break;
			case C_BK: subPressBlack(tile); break;
			case C_WH: subPressWhite(tile); break;
			case C_PI: subPressPink(tile); break;
			case C_PU: subPressPurple(tile); break;
			case C_YE: subPressYellow(tile); break;
			case C_BU: break;
			case C_OR: subPressOrange(tile); break;
		}
	}

}
