package io.chandler.gap;

import java.io.*;

public class M13 {
    // Constants --- these correspond to the macros and #defines in the C code
    static final int IND_SZ = 4826809;
    static final int MAXPATH = 100;
    static final int M13_SZ = 1235520;
    static final int NOFLIP = 0;
    static final int FLIP = 1;
    
    // Global arrays representing the projective plane (from mathieu.h)
    static int[][] lno;     // lno[a][b] is the number of the unique line through a and b (or -1 if a == b)
    static int[][] p3;      // p3[i] holds the 4 points on the ith line
    static char[][] i2c;   // i2c[i][s] represents point i with sign s (NOFLIP or FLIP)
    
    // Global variables used in the search
    static int[] Perm = new int[13];     // permutation array: Perm[0]...Perm[12]
    static int[] Path = new int[MAXPATH];  // current path
    static int Leng, COUNT;
    static boolean[] done;               // flags for already found permutations (by their index)
    static PrintWriter out;              // file output
    
    /**
     * Initializes the geometry arrays lno, p3, and i2c as in mathieu.h.
     */
    public static void init() {
        // Allocate arrays
        lno = new int[13][13];
        p3 = new int[13][4];
        i2c = new char[13][2];
        
        // Diagonals for lno (points are not collinear with themselves)
        lno[0][0] = -1;        lno[1][1] = -1;         lno[2][2] = -1;    
        lno[3][3] = -1;        lno[4][4] = -1;         lno[5][5] = -1;    
        lno[6][6] = -1;        lno[7][7] = -1;         lno[8][8] = -1;    
        lno[9][9] = -1;        lno[10][10] = -1;
        lno[11][11] = -1;      lno[12][12] = -1;
        
        // lno assignments for line 0
        lno[0][1] = 0;   lno[1][0] = 0;   lno[2][0] = 0;   lno[3][0] = 0;
        lno[0][2] = 0;   lno[1][2] = 0;   lno[2][1] = 0;   lno[3][1] = 0;
        lno[0][3] = 0;   lno[1][3] = 0;   lno[2][3] = 0;   lno[3][2] = 0;
        
        // lno assignments for line 1
        lno[0][4] = 1;   lno[4][0] = 1;   lno[5][0] = 1;   lno[6][0] = 1;
        lno[0][5] = 1;   lno[4][5] = 1;   lno[5][4] = 1;   lno[6][4] = 1;
        lno[0][6] = 1;   lno[4][6] = 1;   lno[5][6] = 1;   lno[6][5] = 1;
        
        // lno assignments for line 2
        lno[0][9]  = 2;  lno[9][0]  = 2;  lno[10][0] = 2;  lno[11][0] = 2;
        lno[0][10] = 2;  lno[9][10] = 2;  lno[10][9] = 2;  lno[11][9] = 2;
        lno[0][11] = 2;  lno[9][11] = 2;  lno[10][11] = 2; lno[11][10] = 2;
        
        // lno assignments for line 3
        lno[0][7]  = 3;  lno[7][0]  = 3;  lno[8][0]  = 3;  lno[12][0] = 3;
        lno[0][8]  = 3;  lno[7][8]  = 3;  lno[8][7]  = 3;  lno[12][7] = 3;
        lno[0][12] = 3;  lno[7][12] = 3;  lno[8][12] = 3;  lno[12][8] = 3;
        
        // lno assignments for line 4
        lno[1][4] = 4;   lno[4][1] = 4;   lno[8][1] = 4;   lno[9][1] = 4;
        lno[1][8] = 4;   lno[4][8] = 4;   lno[8][4] = 4;   lno[9][4] = 4;
        lno[1][9] = 4;   lno[4][9] = 4;   lno[8][9] = 4;   lno[9][8] = 4;
        
        // lno assignments for line 5
        lno[1][6]  = 5;  lno[6][1]  = 5;  lno[7][1]  = 5;  lno[11][1] = 5;
        lno[1][7]  = 5;  lno[6][7]  = 5;  lno[7][6]  = 5;  lno[11][6] = 5;
        lno[1][11] = 5;  lno[6][11] = 5;  lno[7][11] = 5;  lno[11][7] = 5;
        
        // lno assignments for line 6
        lno[1][5]  = 6;  lno[5][1]  = 6;  lno[10][1] = 6;  lno[12][1] = 6;
        lno[1][10] = 6;  lno[5][10] = 6;  lno[10][5] = 6;  lno[12][5] = 6;
        lno[1][12] = 6;  lno[5][12] = 6;  lno[10][12] = 6; lno[12][10] = 6;
        
        // lno assignments for line 7
        lno[3][5]  = 7;  lno[5][3]  = 7;  lno[8][3]  = 7;  lno[11][3] = 7;
        lno[3][8]  = 7;  lno[5][8]  = 7;  lno[8][5]  = 7;  lno[11][5] = 7;
        lno[3][11] = 7;  lno[5][11] = 7;  lno[8][11] = 7;  lno[11][8] = 7;
        
        // lno assignments for line 8
        lno[3][4]  = 8;  lno[4][3]  = 8;  lno[7][3]  = 8;  lno[10][3] = 8;
        lno[3][7]  = 8;  lno[4][7]  = 8;  lno[7][4]  = 8;  lno[10][4] = 8;
        lno[3][10] = 8;  lno[4][10] = 8;  lno[7][10] = 8;  lno[10][7] = 8;
        
        // lno assignments for line 9
        lno[2][4]  = 9;  lno[4][2]  = 9;  lno[11][2] = 9;  lno[12][2] = 9;
        lno[2][11] = 9;  lno[4][11] = 9;  lno[11][4] = 9;  lno[12][4] = 9;
        lno[2][12] = 9;  lno[4][12] = 9;  lno[11][12] = 9;  lno[12][11] = 9;
        
        // lno assignments for line 10
        lno[2][6]  = 10; lno[6][2]  = 10; lno[8][2]  = 10; lno[10][2] = 10;
        lno[2][8]  = 10; lno[6][8]  = 10; lno[8][6]  = 10; lno[10][6] = 10;
        lno[2][10] = 10; lno[6][10] = 10; lno[8][10] = 10; lno[10][8] = 10;
        
        // lno assignments for line 11
        lno[2][5]  = 11; lno[5][2]  = 11; lno[7][2]  = 11; lno[9][2]  = 11;
        lno[2][7]  = 11; lno[5][7]  = 11; lno[7][5]  = 11; lno[9][5]  = 11;
        lno[2][9]  = 11; lno[5][9]  = 11; lno[7][9]  = 11; lno[9][7]  = 11;
        
        // lno assignments for line 12
        lno[3][6]  = 12; lno[6][3]  = 12; lno[9][3]  = 12; lno[12][3] = 12;
        lno[3][9]  = 12; lno[6][9]  = 12; lno[9][6]  = 12; lno[12][6] = 12;
        lno[3][12] = 12; lno[6][12] = 12; lno[9][12] = 12; lno[12][9] = 12;
        
        // p3 array assignments --- each row gives the four points on one line
        p3[0][0] = 0;  p3[0][1] = 1;  p3[0][2] = 2;  p3[0][3] = 3;
        p3[1][0] = 0;  p3[1][1] = 4;  p3[1][2] = 5;  p3[1][3] = 6;
        p3[2][0] = 0;  p3[2][1] = 9;  p3[2][2] = 10; p3[2][3] = 11;
        p3[3][0] = 0;  p3[3][1] = 7;  p3[3][2] = 8;  p3[3][3] = 12;
        p3[4][0] = 1;  p3[4][1] = 4;  p3[4][2] = 8;  p3[4][3] = 9;
        p3[5][0] = 1;  p3[5][1] = 6;  p3[5][2] = 7;  p3[5][3] = 11;
        p3[6][0] = 1;  p3[6][1] = 5;  p3[6][2] = 10; p3[6][3] = 12;
        p3[7][0] = 3;  p3[7][1] = 5;  p3[7][2] = 8;  p3[7][3] = 11;
        p3[8][0] = 3;  p3[8][1] = 4;  p3[8][2] = 7;  p3[8][3] = 10;
        p3[9][0] = 2;  p3[9][1] = 4;  p3[9][2] = 11; p3[9][3] = 12;
        p3[10][0] = 2; p3[10][1] = 6; p3[10][2] = 8; p3[10][3] = 10;
        p3[11][0] = 2; p3[11][1] = 5; p3[11][2] = 7; p3[11][3] = 9;
        p3[12][0] = 3; p3[12][1] = 6; p3[12][2] = 9; p3[12][3] = 12;
        
        // i2c assignments: mapping a point (number) to its NOFLIP/FLIP character.
        i2c[0][NOFLIP]  = 'A';  i2c[0][FLIP]  = 'a';
        i2c[1][NOFLIP]  = 'B';  i2c[1][FLIP]  = 'b';
        i2c[2][NOFLIP]  = 'C';  i2c[2][FLIP]  = 'c';
        i2c[3][NOFLIP]  = 'D';  i2c[3][FLIP]  = 'd';
        i2c[4][NOFLIP]  = 'E';  i2c[4][FLIP]  = 'e';
        i2c[5][NOFLIP]  = 'F';  i2c[5][FLIP]  = 'f';
        i2c[6][NOFLIP]  = 'G';  i2c[6][FLIP]  = 'g';
        i2c[7][NOFLIP]  = 'H';  i2c[7][FLIP]  = 'h';
        i2c[8][NOFLIP]  = 'I';  i2c[8][FLIP]  = 'i';
        i2c[9][NOFLIP]  = 'J';  i2c[9][FLIP]  = 'j';
        i2c[10][NOFLIP] = 'K';  i2c[10][FLIP] = 'k';
        i2c[11][NOFLIP] = 'L';  i2c[11][FLIP] = 'l';
        i2c[12][NOFLIP] = 'M';  i2c[12][FLIP] = 'm';
    }
    
    /**
     * Computes the index of a permutation.
     * In C this was a macro: index(A,B,C,D,E,F) = F + 13*(E + 13*(D + 13*(C + 13*(B + 13*A))))
     */
    public static int index(int A, int B, int C, int D, int E, int F) {
        return F + 13 * (E + 13 * (D + 13 * (C + 13 * (B + 13 * A))));
    }
    
    /**
     * Computes the permutation produced by the current path.
     * This method follows the same logic as the C function permute().
     */
    public static void permute() {
        // Start with the identity permutation
        for (int i = 0; i < 13; i++) {
            Perm[i] = i;
        }
        // Process each move in the current path.
        for (int i = 0; i < Leng; i++) {
            int line = lno[Path[i]][Path[i + 1]];
            int xIndex = 0;
            while (p3[line][xIndex] == Path[i] || p3[line][xIndex] == Path[i + 1]) {
                xIndex++;
            }
            int yIndex = 3;
            while (p3[line][yIndex] == Path[i] || p3[line][yIndex] == Path[i + 1]) {
                yIndex--;
            }
            int x = p3[line][xIndex];
            int y = p3[line][yIndex];
            // Apply the transposition and the swaps corresponding to the move
            for (int j = 0; j < 13; j++) {
                if (Perm[j] == x)
                    Perm[j] = y;
                else if (Perm[j] == y)
                    Perm[j] = x;
                else if (Perm[j] == Path[i])
                    Perm[j] = Path[i + 1];
                else if (Perm[j] == Path[i + 1])
                    Perm[j] = Path[i];
            }
        }
    }
    
    /**
     * Writes the current path’s data to the output file.
     * It prints first the length, then the permutation (using the no‐flip characters),
     * then the path (starting from Path[1] to Path[Leng]).
     */
    public static void output() {
        out.print(Leng + " ");
        for (int i = 0; i < 13; i++) {
            out.print(i2c[Perm[i]][NOFLIP]);
        }
        out.print(" ");
        for (int i = 1; i <= Leng; i++) {
            out.print(i2c[Path[i]][NOFLIP]);
        }
        out.println();
    }
    
    /**
     * Advances to the next possible path.
     * Returns true if all choices at the current depth were exhausted.
     */
    public static boolean iterate() {
        int i = Leng + 1;
        do {
            i--;
            Path[i] = (Path[i] + 1) % 13;
        } while (Path[i] == 0 && i > 1);
        return (i == 1 && Path[1] == 0);
    }
    
    /**
     * Produces the next nondegenerate path.
     * This method repeatedly calls iterate() and then checks the nondegeneracy conditions.
     */
    public static void next_path() {
        boolean degen;
        do {
            degen = iterate();
            if (degen) {
                // If we have exhausted all paths at this depth, then increase depth.
                Leng++;
                System.out.println("Starting depth " + Leng + "; COUNT = " + COUNT);
            } else {
                // Test for trivial moves: a move that leaves the hole in the same place.
                for (int i = 0; i <= Leng - 1; i++) {
                    if (Path[i] == Path[i + 1]) {
                        degen = true;
                        break;
                    }
                }
                // Test for collinearity: two consecutive moves along the same line.
                if (!degen) {
                    for (int i = 0; i <= Leng - 2; i++) {
                        if (lno[Path[i]][Path[i + 1]] == lno[Path[i + 1]][Path[i + 2]]) {
                            degen = true;
                            break;
                        }
                    }
                }
            }
        } while (degen);
    }
    
    /**
     * The main search loop.
     * It initializes the geometry, the starting path, and then iterates over all nondegenerate paths,
     * computing the corresponding permutation and writing new ones to disk.
     */
    public static void main(String[] args) throws IOException {
        init();
        
        // Set the starting position of the hole.
        Path[0] = 12;
        for (int i = 1; i < MAXPATH; i++) {
            Path[i] = 0;
        }
        
        out = new PrintWriter(new BufferedWriter(new FileWriter("M13")));
        out.println("0 ABCDEFGHIJKLM ");
        
        done = new boolean[IND_SZ];
        // Mark the identity permutation (given by index(12,0,1,2,3,4)) as already seen.
        done[index(12, 0, 1, 2, 3, 4)] = true;
        COUNT = 1;
        Leng = 1;
        
        while (COUNT < M13_SZ) {
            permute();
            int idx = index(Perm[12], Perm[0], Perm[1], Perm[2], Perm[3], Perm[4]);
            if (!done[idx]) {
                COUNT++;
                done[idx] = true;
                output();
            }
            next_path();
        }
        out.close();
    }
}