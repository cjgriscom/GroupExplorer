package io.chandler.gap;

import java.util.LinkedList;
import java.util.Scanner;
import java.util.Stack;

import io.chandler.gap.Trapentrix.Move;
import io.chandler.gap.Trapentrix.SolveOpts;

public class TrapentrixExplorer {
	static LinkedList<String> moveList = new LinkedList<>();
	static Trapentrix t = new Trapentrix();
	static boolean alt = false;
	static int grip1Offset = 0;
	static int grip2Offset = 0;
	
	public static void main(String[] args) {
		
		@SuppressWarnings("resource")
		Scanner in = new Scanner(System.in);
		
		while (true) {
			grip1Offset %= 3;
			grip2Offset %= 3;
			char g1o = '_', g2o = '_';
			if (grip1Offset == 1) g1o = 'U';
			if (grip2Offset == 1) g2o = 'U';
			if (grip1Offset == 2) g1o = 'D';
			if (grip2Offset == 2) g2o = 'D';
			System.out.print("[" + moveList.size() + " " + g1o + "" + g2o + "]> ");
			String cmd = in.nextLine().trim().toUpperCase();
			if (cmd.startsWith("G") || cmd.startsWith("N")) moveList.add(cmd);
			handle(cmd);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void handle(String cmd) {
		if (cmd.contains(" ")) {
			String[] seq = cmd.split(" ");
			if (seq[0].startsWith("N")) {
				try{
					for (int i = 0; i < Integer.parseInt(seq[0].substring(1)); i++) {
						for (int j = 1; j < seq.length; j++) {
							handle(seq[j]);
						}
					}
				} catch (Exception e) {
					System.out.println("Error");
					return;
				}
			} else {
				for (String s : seq) handle(s);
			}
		} else switch(cmd) {
		// Print help
		case ("H"):
			System.out.println("Commands:");
			System.out.println("    h: Print help");
			System.out.println("    u: Undo last move");
			System.out.println("    a: Switch to alt naming");
			System.out.println("    s: Print puzzle state (named)");
			System.out.println("    p: Print puzzle state (colors)");
			System.out.println("    ?: Print puzzle state (what-changed)");
			System.out.println("    c: Print cycle state");
			System.out.println("    m: Print move list");
			System.out.println("    ss{n}: Attempt solve in max n moves");
			System.out.println("    rrr: Reset");
			System.out.println("Puzzle Moves:");
			System.out.println(" * Accepts space-delimited sequences i.e. 'G1U G2D'");
			System.out.println(" * Accepts repeat directives i.e. 'N12 G1U G2D'");
			System.out.println("    G1U: Move grip 1 up");
			System.out.println("    G1D: Move grip 1 down");
			System.out.println("    G2U: Move grip 2 up");
			System.out.println("    G2D: Move grip 2 down");
			break;
		// Undo last move
		case ("U"):
			if (moveList.size() == 0) break;
			String rem = moveList.removeLast();
			String n = "N1";
			if (rem.startsWith("N")) {
				n = rem.substring(0, rem.indexOf(" "));
				rem = rem.substring(rem.indexOf(" ") + 1);
			}
			String newList = n;
			String[] oldList = rem.split(" ");
			for (int i = oldList.length - 1; i >= 0; i--) {
				String c = oldList[i];
				if (c.equals("G1U")) newList += " G1D";
				if (c.equals("G1D")) newList += " G1U";
				if (c.equals("G2U")) newList += " G2D";
				if (c.equals("G2D")) newList += " G2U";
			}
			handle(newList);
			
			break;
		// Print named states
		case ("A"): 
			alt = !alt;
			System.out.println(alt ? "Using alt." : "Not using alt.");
			break;
		// Print named states
		case ("S"): 
			if (alt) System.out.print(t.altString());
			else System.out.print(t.toString());
			break;
		// Print color states
		case ("P"): 
			System.out.print(t.colorString());
			break;
		// Print changed pieces state
		case ("?"): 
			System.out.print(t.changedString());
			break;
		// Print cycle state list
		case ("C"): 
			if (alt) System.out.print(t.stateAsCycleListAlt());
			else System.out.print(t.stateAsCycleList());
			break;
		// Print move list
		case ("M"): 
			for (String m : moveList) System.out.print(m + " ");
			System.out.println();
			break;
		// Reset
		case ("RRR"): 
			System.out.println("Reset.");
			moveList.clear();
			t = new Trapentrix();
			grip1Offset = 0;
			grip2Offset = 0;
			break;
		// Moves
		case ("G1U"): t.move(Trapentrix.grip1Up); grip1Offset += 1; break;
		case ("G1D"): t.move(Trapentrix.grip1Down); grip1Offset += 2; break;
		case ("G2U"): t.move(Trapentrix.grip2Up); grip2Offset += 1; break;
		case ("G2D"): t.move(Trapentrix.grip2Down); grip2Offset += 2; break;
		default:
			if (cmd.startsWith("SS") || cmd.startsWith("S1") || cmd.startsWith("S2")) {
				SolveOpts solveOpts;
				switch (cmd.substring(0, 2)) {
					case "S1": solveOpts = new SolveOpts(false, true, false); break;
					case "S2": solveOpts = new SolveOpts(false, true, true); break;
					case "S3": solveOpts = new SolveOpts(true, false, false); break;
					default: solveOpts = new SolveOpts(false, false, false); break;
				}
				String nmvs = cmd.substring(2);
				if (nmvs.isEmpty()) nmvs = "30";
				t.trySolve(Integer.parseInt(nmvs), solveOpts, (moves) -> {
					moves = (Stack<Move>) moves.clone();
					int i = 0;
					int o = 0;
					System.out.println("Forward in alt(1) notation:");
					while (!moves.isEmpty()) {
						Move m = moves.pop();
						if (m == Trapentrix.grip1Down) {
							System.out.print("U");
						}
						if (m == Trapentrix.grip2Down) {
							if (i == 0) {System.out.print("X");o=1;}
							System.out.print("U");
						}
						if (m == Trapentrix.grip1Up) {
							System.out.print("D");
						}
						if (m == Trapentrix.grip2Up) {
							if (i == 0) {System.out.print("X");o=1;}
							System.out.print("D");
						}
						i++;
						if ((o+i) % 2 == 0) System.out.print(" ");
					}
					System.out.println();
					System.out.println(i + " moves");
					return false; // Keep looking
				});
			} else {
				System.out.println("Unknown command");
			}
		}
	}
}
