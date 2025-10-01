package io.chandler.gap.misc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;

import io.chandler.gap.Generators;
import io.chandler.gap.GroupExplorer;
import io.chandler.gap.GroupExplorer.MemorySettings;
import io.chandler.gap.cache.State;

public class L3_3_Puzzle {
	
    public static int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

	// 24, 10, 96 (fudged up by 4)
	//oops 10 doesnt work

	// 12 6 48

    public static void main(String[] args) {
		/*
        for (int A = 2; A <= 100; A += 2) { // A is even
            for (int B = 3; B <= 100; B += 3) {
                double ratio = (double) B / A;
                if (ratio >= 10.0 / 36.0 && ratio <= 18.0 / 36.0) {
                    double C = (1.5 * A + B) * 2;
					double r1 = C/gcd(A/2, (int)(C));
					double r2 = (C+2)/gcd(A/2, (int)(C) +2);
					double r3 = (C-2)/gcd(A/2, (int)(C)-2);
					double r4 = C < 50 ? 10000 : (C+4)/gcd(A/2, (int)(C) +4);
					double r5 = C < 50 ? 10000 : (C-4)/gcd(A/2, (int)(C)-4);
					double min = Arrays.stream(new double[] {r1, r2, r3, r4, r5}).min().getAsDouble();
                    System.out.println("A: " + A + ", B: " + B + ", C: " + (C) + ", C/gcd: " + (int)r1 + ", " + (int)min);
                }
            }
        }
			*/

        HashSet<State> states = new HashSet<>();
        GroupExplorer g = new GroupExplorer("[(1,8)(3,11)(5,10)(7,12),(1,4)(2,6)(3,7)(8,9),(1,11)(4,10)(5,13)(6,9)]", MemorySettings.COMPACT, states, new HashSet<>(), new HashSet<>(), true);
        Generators.exploreGroup(g, (state, description) -> { 
            
        });
	}
}
