package io.chandler.gap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.HashSet;
import java.util.Arrays;

class PermuTest {

    @Test
    void testGeneratePermutationsNK() {
        // Test case 1: n=3, k=2
        List<int[]> perms1 = Permu.generatePermutations(3, 2);
        assertEquals(6, perms1.size()); // 3P2 = 6
        assertPermutationsNK(perms1, 3, 2);
        
        // Test case 2: n=4, k=2
        List<int[]> perms2 = Permu.generatePermutations(4, 2);
        assertEquals(12, perms2.size()); // 4P2 = 12
        assertPermutationsNK(perms2, 4, 2);
        
        // Test case 3: n=3, k=3 (full permutation)
        List<int[]> perms3 = Permu.generatePermutations(3, 3);
        assertEquals(6, perms3.size()); // 3P3 = 6
        assertPermutationsNK(perms3, 3, 3);
        
        // Test case 4: n=2, k=1
        List<int[]> perms4 = Permu.generatePermutations(2, 1);
        assertEquals(2, perms4.size()); // 2P1 = 2
        assertPermutationsNK(perms4, 2, 1);
        
        // Test case 5: edge case k=0
        List<int[]> perms5 = Permu.generatePermutations(3, 0);
        assertEquals(1, perms5.size()); // Should return empty permutation
        assertEquals(0, perms5.get(0).length);
    }

    private void assertPermutationsNK(List<int[]> permutations, int n, int k) {
        // Check that all permutations are of length k
        for (int[] perm : permutations) {
            assertEquals(k, perm.length);
            
            // Check that all numbers are between 1 and n
            for (int num : perm) {
                assertTrue(num >= 1 && num <= n);
            }
            
            // Check that all numbers in each permutation are unique
            HashSet<Integer> set = new HashSet<>();
            for (int num : perm) {
                set.add(num);
            }
            assertEquals(k, set.size());
        }
        
        // Check that all permutations are unique
        HashSet<String> uniquePerms = new HashSet<>();
        for (int[] perm : permutations) {
            uniquePerms.add(Arrays.toString(perm));
        }
        assertEquals(permutations.size(), uniquePerms.size());
    }
} 