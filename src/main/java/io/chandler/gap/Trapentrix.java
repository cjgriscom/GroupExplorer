package io.chandler.gap;

import static io.chandler.gap.Trapentrix.Piece.B1C;
import static io.chandler.gap.Trapentrix.Piece.B1D;
import static io.chandler.gap.Trapentrix.Piece.B1g;
import static io.chandler.gap.Trapentrix.Piece.B1m;
import static io.chandler.gap.Trapentrix.Piece.B2C;
import static io.chandler.gap.Trapentrix.Piece.B2D;
import static io.chandler.gap.Trapentrix.Piece.B2g;
import static io.chandler.gap.Trapentrix.Piece.B2m;
import static io.chandler.gap.Trapentrix.Piece.B_U;
import static io.chandler.gap.Trapentrix.Piece.F1C;
import static io.chandler.gap.Trapentrix.Piece.F1D;
import static io.chandler.gap.Trapentrix.Piece.F1g;
import static io.chandler.gap.Trapentrix.Piece.F1m;
import static io.chandler.gap.Trapentrix.Piece.F2C;
import static io.chandler.gap.Trapentrix.Piece.F2D;
import static io.chandler.gap.Trapentrix.Piece.F2g;
import static io.chandler.gap.Trapentrix.Piece.F2m;
import static io.chandler.gap.Trapentrix.Piece.F_U;

import java.util.Arrays;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Function;

public class Trapentrix {
	
	public static Orbit grip1_close_low  = new Orbit(F1D, F1g, B1C);
	public static Orbit grip1_close_high = new Orbit(F1C, B1g, B1D);
	public static Orbit grip1_far_low    = new Orbit(F2D, F1m, B_U);
	public static Orbit grip1_far_high   = new Orbit(F_U, B1m, B2D);
	
	public static Orbit grip2_close_low  = new Orbit(F2D, F2g, B2C);
	public static Orbit grip2_close_high = new Orbit(F2C, B2g, B2D);
	public static Orbit grip2_far_low    = new Orbit(F1D, F2m, B_U);
	public static Orbit grip2_far_high   = new Orbit(F_U, B2m, B1D);
	
	public static Orbit orbitF = new Orbit(F1D, F2D, F1m, F2m, F1g, F2g, B_U, B1C, B2C);
	public static Orbit orbitB = new Orbit(B1D, B2D, B1m, B2m, B1g, B2g, F_U, F1C, F2C);

	public static Move grip1Down = new Move(4, -1, 0)
			.add(grip1_close_low, 1)
			.add(grip1_close_high, 1)
			.add(grip1_far_low, 1)
			.add(grip1_far_high, 1);
	public static Move grip1Up = new Move(4, 1, 0)
			.add(grip1_close_low, -1)
			.add(grip1_close_high, -1)
			.add(grip1_far_low, -1)
			.add(grip1_far_high, -1);
	public static Move grip2Down = new Move(4, 0, -1)
			.add(grip2_close_low, 1)
			.add(grip2_close_high, 1)
			.add(grip2_far_low, 1)
			.add(grip2_far_high, 1);
	public static Move grip2Up = new Move(4, 0, 1)
			.add(grip2_close_low, -1)
			.add(grip2_close_high, -1)
			.add(grip2_far_low, -1)
			.add(grip2_far_high, -1);
	
	public String stateAsCycleListAlt() {
		String src = this.stateAsCycleList();
		for (Piece p : reference) src = src.replaceAll(p.name(), p.altName);
		return src;
	}
	
	public String stateAsCycleList() {
		StringBuilder list = new StringBuilder();
		TreeSet<Piece> unchecked = new TreeSet<Piece>();
		unchecked.addAll(Arrays.asList(reference));
		// Remove unchanged pieces
		//for (Piece p : reference) if (in(p) == p) accountedFor.remove(p);
		while (!unchecked.isEmpty()) {
			Piece original = unchecked.first();
			Piece now = in(original);
			unchecked.remove(original);
			if (!original.equals(now)) {
				list.insert(0,"\n");
				while (!now.equals(original)) {
					list.insert(0,now);
					list.insert(0," -> ");
					unchecked.remove(now);
					now = in(now);
				}
				list.insert(0,original);
				list.insert(0,Trapentrix.orbitF.contains(original) ? "F: " : "B: ");
			}
		}
		return list.toString();
	}
	
	public static class SolveOpts {
		public final boolean ignoreFirstOrbit;
		public final boolean ignoreSecondOrbit;
		public final boolean ignoreGrips;
		public SolveOpts(boolean ignoreFirstOrbit, boolean ignoreSecondOrbit, boolean ignoreGrips) {
			this.ignoreFirstOrbit = ignoreFirstOrbit;
			this.ignoreSecondOrbit = ignoreSecondOrbit;
			this.ignoreGrips = ignoreGrips;
		}
	}
	private Stack<Move> solveState = new Stack<>();
	private Function<Stack<Move>, Boolean> onSolutionFound;
	public Stack<Move> trySolve(int maxDepth, SolveOpts solveOpts, Function<Stack<Move>, Boolean> onSolutionFound) {
		solveState.clear();
		this.onSolutionFound = onSolutionFound;
		if (solve(maxDepth, solveOpts)) return solveState;
		return null;
	}
	private boolean solve(int maxDepth, SolveOpts solveOpts) {
		if (solved(solveOpts) && onSolutionFound.apply(solveState)) return true;
		if (maxDepth == 0) return false;
		maxDepth--;
		if (solveState.isEmpty() || solveState.peek() == grip2Down || solveState.peek() == grip2Up) {
			move(grip1Down);
			solveState.push(grip1Down);
			if (solve(maxDepth, solveOpts)) return true;
			move(grip1Down);
			solveState.pop(); 
			solveState.push(grip1Up);
			if (solve(maxDepth, solveOpts)) return true;
			move(grip1Down);
			solveState.pop(); 
		}
		if (solveState.isEmpty() || solveState.peek() == grip1Down || solveState.peek() == grip1Up) {
			move(grip2Down);
			solveState.push(grip2Down);
			if (solve(maxDepth, solveOpts)) return true;
			move(grip2Down);
			solveState.pop(); 
			solveState.push(grip2Up);
			if (solve(maxDepth, solveOpts)) return true;
			move(grip2Down);
			solveState.pop(); 
		}
		return false;
	}
	
	public Trapentrix move(Move m) {
		for (int oi = 0; oi < m.entered; oi++) {
			Orbit orbit = m.orbits[oi];
			int dir = m.directions[oi];
			int len = orbit.members.length;
			for (int i = 0; i < len; i++) {
				orbit.buffer[i] = in(orbit.members[i]);
			}
			for (int i = 0; i < len; i++) {
				setIn(orbit.members[i], orbit.buffer[(i + dir + len) % len]);
			}
		}
		this.g1Rotations += m.g1Rot;
		this.g2Rotations += m.g2Rot;
		return this;
	}
	
	public String altString() {
		String src = this.toString();
		for (Piece p : reference) src = src.replaceAll(p.name(), p.altName);
		return src;
	}
	
	public String colorString() {
		String src = this.toString();
		for (Piece p : reference) src = src.replaceAll(p.name(), " " + p.color + " ");
		return src;
	}
	
	public String changedString() {
		String src = this.toString();
		for (Piece p : reference) src = src.replaceAll(p.name(), in(p).equals(p) ? " . " : " * ");
		return src;
	}
	
	public String toString() {
		StringBuilder state = new StringBuilder();
		state.append("   ").append(in(B1D)).append(" ").append(in(B2D)).append("\n");
		state.append(" ").append(in(B1C)).append("     ").append(in(B2C)).append("\n");
		state.append("     ").append(in(B_U)).append("\n");
		state.append("\n");

		state.append("   ").append(in(B1m)).append(" ").append(in(B2m)).append("\n");
		state.append(" ").append(in(B1g)).append("     ").append(in(B2g)).append("\n");
		state.append(" ").append(in(F1g)).append("     ").append(in(F2g)).append("\n");
		state.append("   ").append(in(F1m)).append(" ").append(in(F2m)).append("\n");
		state.append("\n");
		
		state.append("     ").append(in(F_U)).append("\n");
		state.append(" ").append(in(F1C)).append("     ").append(in(F2C)).append("\n");
		state.append("   ").append(in(F1D)).append(" ").append(in(F2D)).append("\n");
		state.append("\n");
		return state.toString();
	}
	
	public Piece in(Piece loc) {
		return state[loc.ordinal()];
	}
	
	public void setIn(Piece loc, Piece newPiece) {
		state[loc.ordinal()] = newPiece;
	}
	
	public boolean solved(SolveOpts solveOpts) {
		if (!solveOpts.ignoreGrips) {
			if (Math.abs(g1Rotations) % 3 != 0 || Math.abs(g2Rotations) % 3 != 0) return false;
		}
		for (int i= 0; i < reference.length; i++) {
			if (solveOpts.ignoreSecondOrbit && reference[i].isSecondOrbit()) continue;
			if (solveOpts.ignoreFirstOrbit && !reference[i].isSecondOrbit()) continue;
			if (!reference[i].equals(state[i])) return false;
		}
		return true;
	}
	
	public static final Piece[] reference = Piece.values().clone();
	public Piece[] state = Piece.values().clone();
	public int g1Rotations = 0;
	public int g2Rotations = 0;
	
	public static class Move {
		public final Orbit[] orbits;
		public final int[] directions;
		private int entered = 0;
		private final int g1Rot, g2Rot;
		public Move(int num, int g1Rot, int g2Rot) {
			this.orbits = new Orbit[num];
			this.directions = new int[num];
			this.g1Rot = g1Rot; this.g2Rot = g2Rot;
		}
		public Move add(Orbit o, int dir) {
			orbits[entered] = o;
			directions[entered++] = dir;
			return this;
		}
	}
	
	public static class Orbit {
		public final Piece[] buffer;
		public final Piece[] members;
		public Orbit(Piece... members) {this.members = members;this.buffer = new Piece[members.length];}
		public boolean contains(Piece p) {
			for (Piece ps : members) if (ps == p) return true;
			return false;
		}
	}
	
	public static enum Piece {
		F_U('r', " "),
		F1D('r', "A"),
		F1C('r', " "),
		F2D('r', "B"),
		F2C('r', " "),
		F1m('b', "M"),
		F1g('b', "L"),
		F2m('g', "N"),
		F2g('g', "R"),
		B1m('b', " "),
		B1g('b', " "),
		B2m('g', " "),
		B2g('g', " "),
		B_U('o', "Y"),
		B1D('o', " "),
		B1C('o', "X"),
		B2D('o', " "),
		B2C('o', "Z"),
		;
		final char color;
		final String altName;
		Piece(char color, String altName) {
			this.color = color;
			this.altName = altName;
		}
		public boolean isSecondOrbit() {
			return altName.trim().isEmpty();
		}
	}
}
