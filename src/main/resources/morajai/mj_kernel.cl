#define N_COLORS 10

enum Color {
    C_GY, C_RD, C_GN, C_BK, C_WH, C_PI, C_PU, C_YE, C_BU, C_OR
};

void initFromState(int state, int* tileColors) {
    for (int i = 0; i < 9; i++) {
        tileColors[i] = state % 10;
        state /= 10;
    }
}

int getState(int* tileColors) {
    int state = 0;
    int multiplier = 1;
    for (int i = 0; i < 9; i++) {
        state += tileColors[i] * multiplier;
        multiplier *= 10;
    }
    return state;
}

void swapTiles(int* tileColors, int tile1, int tile2) {
    int temp = tileColors[tile1];
    tileColors[tile1] = tileColors[tile2];
    tileColors[tile2] = temp;
}

int getOffsetTileIdx(int tile, int offsetX, int offsetY) {
    int curX = tile % 3;
    int curY = tile / 3;
    if (curX + offsetX < 0 || curX + offsetX >= 3) return -1;
    if (curY + offsetY < 0 || curY + offsetY >= 3) return -1;
    return tile + offsetX + offsetY * 3;
}

int getOffsetColor(int* tileColors, int tile, int offsetX, int offsetY) {
    int offsetTile = getOffsetTileIdx(tile, offsetX, offsetY);
    if (offsetTile == -1) return -1;
    return tileColors[offsetTile];
}

void pressTile(int tile, int* tileColors) {
    int tileColor = tileColors[tile];

    /* BLUE tiles adopt the centre-tile’s colour when pressed */
    if (tileColor == C_BU) {
        tileColor = tileColors[4];
    }

    switch (tileColor) {
        /* ──────────────────────────  GRAY  ────────────────────────── */
        case C_GY: /* no-op */
            break;

        /* ───────────────────────────  RED  ────────────────────────── */
        case C_RD:
            for (int i = 0; i < 9; i++) {
                if (tileColors[i] == C_WH)
                    tileColors[i] = C_BK;          /* white → black  */
                else if (tileColors[i] == C_BK)
                    tileColors[i] = tileColors[tile]; /* black → red */
            }
            break;

        /* ─────────────────────────  GREEN  ────────────────────────── */
        case C_GN:
            swapTiles(tileColors, tile, 8 - tile);  /* swap with opposite */
            break;

        /* ──────────────────────────  BLACK  ───────────────────────── */
        case C_BK: {                                /* rotate row right   */
            int tile0 = (tile / 3) * 3;             /* leftmost in row    */
            int temp   = tileColors[tile0];
            tileColors[tile0]     = tileColors[tile0 + 2];
            tileColors[tile0 + 2] = tileColors[tile0 + 1];
            tileColors[tile0 + 1] = temp;
            break;
        }

        /* ──────────────────────────  WHITE  ───────────────────────── */
        case C_WH:
            for (int i = 0; i < 4; i++) {           /* N,S,E,W neighbours */
                int ox = (i == 0) ? -1 : (i == 1) ?  1 : 0;
                int oy = (i == 2) ? -1 : (i == 3) ?  1 : 0;
                int n  = getOffsetTileIdx(tile, ox, oy);
                if (n != -1) {
                    if (tileColors[n] == C_GY)
                        tileColors[n] = tileColors[tile];     /* gray → white */
                    else if (tileColors[n] == tileColors[tile])
                        tileColors[n] = C_GY;                 /* white → gray */
                }
            }
            tileColors[tile] = C_GY;                 /* self becomes gray  */
            break;

        /* ──────────────────────────  PINK  ────────────────────────── */
        case C_PI: {
            int neighbours[8];       /* clockwise list of valid neighbours */
            int count = 0;

            /* collect neighbours clockwise starting West */
            int n = getOffsetTileIdx(tile, -1,  0); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile, -1,  1); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile,  0,  1); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile,  1,  1); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile,  1,  0); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile,  1, -1); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile,  0, -1); if (n != -1) neighbours[count++] = n;
                n = getOffsetTileIdx(tile, -1, -1); if (n != -1) neighbours[count++] = n;

            /* clockwise rotation */
            if (count > 0) {
                int tmp = tileColors[neighbours[0]];
                for (int i = 0; i < count - 1; i++)
                    tileColors[neighbours[i]] = tileColors[neighbours[i + 1]];
                tileColors[neighbours[count - 1]] = tmp;
            }
            break;
        }

        /* ─────────────────────────  PURPLE  ───────────────────────── */
        case C_PU:                                 /* move south */
            if (tile < 6) swapTiles(tileColors, tile, tile + 3);
            break;

        /* ─────────────────────────  YELLOW  ───────────────────────── */
        case C_YE:                                 /* move north */
            if (tile >= 3) swapTiles(tileColors, tile, tile - 3);
            break;

        /* ──────────────────────────  BLUE  ────────────────────────── */
        case C_BU:                                 /* already handled */
            break;

        /* ───────────────────────── ORANGE  ───────────────────────── */
        case C_OR: {
            int counts[N_COLORS + 1] = {0};        /* slot 0 for out-of-bounds */

            counts[getOffsetColor(tileColors, tile, -1,  0) + 1]++;
            counts[getOffsetColor(tileColors, tile,  1,  0) + 1]++;
            counts[getOffsetColor(tileColors, tile,  0, -1) + 1]++;
            counts[getOffsetColor(tileColors, tile,  0,  1) + 1]++;

            int max = 0, bestColor = -1, bestCount = 0;
            for (int i = 1; i < N_COLORS + 1; i++) {
                if (counts[i] > max) {                /* new leader */
                    max       = counts[i];
                    bestColor = i - 1;
                    bestCount = 1;
                } else if (counts[i] == max) {        /* tie */
                    bestCount++;
                }
            }
            if (bestCount == 1)                      /* unique majority */
                tileColors[tile] = bestColor;
            break;
        }
    }
}

bool isSet(global const uchar* depths, int state) {
    /* reinterpret the byte buffer as an array of 32-bit words */
    const global uint* depths32 = (const global uint*)depths;
    uint word = depths32[state >> 5];                 /* state / 32 */
    return (word & (1u << (state & 31))) != 0;        /* bit inside word */
}

void set(global uchar* depths, int state) {
    /* atomic OR the target bit to avoid lost updates when multiple work-items hit the same byte */
    global volatile uint* depths32 = (global volatile uint*)depths;
    uint mask = 1u << (state & 31);
    atomic_or(&depths32[state >> 5], mask);
}

__kernel void mj_solve(
    __global const uchar* reached,
    __global const uchar* current,
    __global uchar* next,
    const int offset
) {
    int state = get_global_id(0) + offset;

    if (isSet(reached, state) || isSet(current, state)) {
        return;
    }

    int tileColors[9];
    int initTileColors[9];
    initFromState(state, initTileColors);

    for (int i = 0; i < 9; i++) {
        for (int j=0; j<9; j++) tileColors[j] = initTileColors[j];
        pressTile(i, tileColors);
        int newState = getState(tileColors);

        if (isSet(current, newState)) {
            set(next, state);
            return;
        }
    }
} 