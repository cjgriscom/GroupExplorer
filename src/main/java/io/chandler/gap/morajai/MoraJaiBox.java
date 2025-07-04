package io.chandler.gap.morajai;

public class MoraJaiBox {
	// Constants for press results
	public static final int PRESS_OK = 0;
	public static final int PRESS_COMPLETED = 1;
	public static final int PRESS_RESET = 2;

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

	// State variables
	private boolean initialized = false;
	private final Color[] targetColors = new Color[4];
	private final boolean[] outerState = new boolean[4];
	private final Color[] initTileColors = new Color[9];
	private final Color[] tileColors = new Color[9];

	// Constructor
	public MoraJaiBox() {
		// Initialize arrays with default values
		for (int i = 0; i < 4; i++) {
			targetColors[i] = Color.C_GY;
			outerState[i] = false;
		}
		for (int i = 0; i < 9; i++) {
			initTileColors[i] = Color.C_GY;
			tileColors[i] = Color.C_GY;
		}
	}

	public void init(Color[] targetColors, Color[] tileColors) {
		if (targetColors.length != 4 || tileColors.length != 9) {
			throw new IllegalArgumentException("Invalid array lengths");
		}
		
		System.arraycopy(targetColors, 0, this.targetColors, 0, 4);
		System.arraycopy(tileColors, 0, this.initTileColors, 0, 9);
		reset();
		initialized = true;
	}
	
	public void initFromState(Color[] targetColors, int state) {
		if (targetColors.length != 4) {
			throw new IllegalArgumentException("Invalid array lengths");
		}

		System.arraycopy(targetColors, 0, this.targetColors, 0, 4);

		for (int i = 0; i < 9; i++) {
			initTileColors[i] = COLOR_VALUES[(state % 10)];
			state /= 10;
		}

		reset();
		initialized = true;
	}

	public int getState() {
		int state = 0;
		int multiplier = 1;
		for (int i = 0; i < 9; i++) {
			state += tileColors[i].ordinal() * multiplier;   // tile 0 → 10^0, tile 1 → 10^1, …
			multiplier *= 10;
		}
		return state;
	}

	public void reset() {
		System.arraycopy(initTileColors, 0, tileColors, 0, 9);
		for (int i = 0; i < 4; i++) {
			outerState[i] = false;
		}
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
		Color temp = tileColors[tile1];
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

	private Color getOffsetColor(int tile, int offsetX, int offsetY) {
		int offsetTile = getOffsetTileIdx(tile, offsetX, offsetY);
		if (offsetTile == -1) return null;
		return tileColors[offsetTile];
	}

	public Color getTileColor(int tile) {
		if (!initialized || tile >= 9) return Color.C_GY;
		return tileColors[tile];
	}

	public Color getOuterColor(int outer) {
		if (!initialized || outer >= 4) return Color.C_GY;
		return outerState[outer] ? targetColors[outer] : Color.C_GY;
	}

	public boolean isSolved() {
		if (!initialized) return false;
		for (boolean state : outerState) {
			if (!state) return false;
		}
		return true;
	}

	// Tile press action methods
	private void subPressGray(int tile) {
		// No action
	}

	private void subPressRed(int tile) {
		// Turn all white tiles black, and all black tiles red
		for (int i = 0; i < 9; i++) {
			if (tileColors[i] == Color.C_WH) {
				tileColors[i] = Color.C_BK;
			} else if (tileColors[i] == Color.C_BK) {
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
		int[] colorCounts = new int[Color.N_COLORS + 1];

		// Count colors of neighbors
		countNeighborColor(colorCounts, getOffsetColor(tile, -1, 0));  // left
		countNeighborColor(colorCounts, getOffsetColor(tile, 1, 0));   // right
		countNeighborColor(colorCounts, getOffsetColor(tile, 0, -1));  // up
		countNeighborColor(colorCounts, getOffsetColor(tile, 0, 1));   // down

		// Find the maximum
		int maxCount = 0;
		Color maxColor = null;
		int maxColorCount = 0;

		for (Color color : Color.values()) {
			int count = colorCounts[color.ordinal() + 1];
			if (count > maxCount) {
				maxCount = count;
				maxColor = color;
				maxColorCount = 1;
			} else if (count == maxCount) {
				maxColorCount++;
			}
		}

		if (maxColorCount == 1) {
			tileColors[tile] = maxColor;
		}
	}

	private void countNeighborColor(int[] colorCounts, Color color) {
		if (color != null) {
			colorCounts[color.ordinal() + 1]++;
		}
	}

	private void subPressPink(int tile) {
		// Rotate clockwise all adjacent (inc. diagonal) tiles, skipping anything off edge
		int[] offsetTiles = new int[8];
		int totalTiles = 0;

		// Collect valid offset tiles
		int[][] offsets = {
			{-1, 0}, {-1, 1}, {0, 1}, {1, 1},
			{1, 0}, {1, -1}, {0, -1}, {-1, -1}
		};

		for (int[] offset : offsets) {
			int offsetTile = getOffsetTileIdx(tile, offset[0], offset[1]);
			if (offsetTile != -1) {
				offsetTiles[totalTiles++] = offsetTile;
			}
		}

		if (totalTiles > 0) {
			// Rotate the tiles
			Color temp = tileColors[offsetTiles[0]];
			for (int i = 0; i < totalTiles - 1; i++) {
				tileColors[offsetTiles[i]] = tileColors[offsetTiles[i + 1]];
			}
			tileColors[offsetTiles[totalTiles - 1]] = temp;
		}
	}

	private void subPressWhite(int tile) {
		// Invert (grey -> white, white -> grey) adjacent grey tiles and self
		int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
		Color selfColor = tileColors[tile]; // C_WH, except use self to propagate blue behavior

		for (int[] offset : offsets) {
			int offsetTile = getOffsetTileIdx(tile, offset[0], offset[1]);
			if (offsetTile != -1) {
				if (tileColors[offsetTile] == Color.C_GY) {
					tileColors[offsetTile] = selfColor;
				} else if (tileColors[offsetTile] == selfColor) {
					tileColors[offsetTile] = Color.C_GY;
				}
			}
		}
		tileColors[tile] = Color.C_GY;
	}

	private void subPressBlack(int tile) {
		// Rotate the row to the right
		int tile0 = (tile / 3) * 3;
		int tile1 = tile0 + 1;
		int tile2 = tile0 + 2;
		Color temp = tileColors[tile0];
		tileColors[tile0] = tileColors[tile2];
		tileColors[tile2] = tileColors[tile1];
		tileColors[tile1] = temp;
	}

	private void subPressBlue(int tile) {
		// Perform action of center tile
		switch (tileColors[4]) {
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

	public int pressTile(int tile) {
		if (tile >= 9) return PRESS_OK;
		if (isSolved()) return PRESS_COMPLETED;

		switch (tileColors[tile]) {
			case C_GY: subPressGray(tile); break;
			case C_RD: subPressRed(tile); break;
			case C_GN: subPressGreen(tile); break;
			case C_BK: subPressBlack(tile); break;
			case C_WH: subPressWhite(tile); break;
			case C_PI: subPressPink(tile); break;
			case C_PU: subPressPurple(tile); break;
			case C_YE: subPressYellow(tile); break;
			case C_BU: subPressBlue(tile); break;
			case C_OR: subPressOrange(tile); break;
		}

		// Check if any completed outers got messed up
		for (int i = 0; i < 4; i++) {
			int checkTile = getTileToCheck(i);
			if (outerState[i] && tileColors[checkTile] != targetColors[i]) {
				outerState[i] = false;
			}
		}

		return PRESS_OK;
	}

	public int pressOuter(int outer) {
		if (outer >= 4) return PRESS_OK;

		int checkTile = getTileToCheck(outer);

		if (tileColors[checkTile] == targetColors[outer]) {
			outerState[outer] = true;
			return isSolved() ? PRESS_COMPLETED : PRESS_OK;
		} else {
			outerState[outer] = false;
			reset();
			return PRESS_RESET;
		}
	}
}
