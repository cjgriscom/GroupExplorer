package io.chandler.gap;

import io.chandler.gap.GroupExplorer.MemorySettings;

public class Scratch {
	public static void main(String[] args) {
		// Q8 : PSL(2,8) : C3
		// (A1)(A3)(A5)(A6),(B1)(B2)(B5)(B6)
		String gen = "[(1,2,3)(7,8,9)(13,14,15)(16,17,18),(8,14,5)(10,1,18)(3,19,16)(22,6,13)]";
		System.out.println(GroupExplorer.renumberGeneratorNotation(gen));

		GroupExplorer ge = new GroupExplorer(gen, MemorySettings.FASTEST);
		Generators.exploreGroup(ge, null);
	}
}
