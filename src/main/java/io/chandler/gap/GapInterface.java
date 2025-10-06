package io.chandler.gap;

import java.io.*;
import java.util.*;

public class GapInterface {
    private static final String gapPath = "gap";
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public GapInterface() throws IOException {
        reset();
    }

    public static void main(String[] args) throws IOException {

        GapInterface gap = new GapInterface();
        String conjugacyClasses = gap.getConjugacyClasses(Generators.l2_11);
        gap.close();
        System.out.println(conjugacyClasses);
    }

    public void reset() throws IOException {
        if (process != null) try {close();} catch (IOException e) {}
        List<String> commands = new ArrayList<>();
        commands.add(gapPath);
        commands.add("-q"); // Quiet mode, less output
        commands.add("--width");
        commands.add("1000000"); // Avoid line breaks

        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectErrorStream(true);
        process = pb.start();

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

    }

    public void close() throws IOException {
        writer.append((char)0xd);
        writer.flush();
        writer.close();
        try {
            process.waitFor();
        } catch (InterruptedException e) {e.printStackTrace();}
        process.destroy();
    }

    public int count(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            idx += sub.length();
            count++;
        }
        return count;
    }

    public String getConjugacyClasses(String generator) {
        try {
            writer.write("g := Group(" + generator + ");;");
            writer.newLine();
            writer.write("Print(ConjugacyClasses(g),\"\\n\");");
            writer.newLine();
            writer.flush();

            int foundL = -1;

            // Read output from GAP
            String line = "";
            String out = "";
            while (foundL != 0) {
                line = reader.readLine().replaceAll(" ", "").trim();
                if (line.isEmpty()) continue;
                int countL = count(line, "[");
                int countR = count(line, "]");
                if (countL > 0) { foundL = Math.max(foundL, 0); foundL += countL; }
                if (countR > 0) { foundL -= countR; }
                out += line;
            }
            out = out.substring(1, out.length() - 1);
            return extractCyclesList(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> runGapCommands(String generator, int readNLines) {
        try {
            // Send commands to GAP
            writer.write("g := Group(" + generator + ");");
            writer.newLine();
            writer.write("Print(Size(g), \"\\n\");");
            writer.newLine();
            writer.write("Print(StructureDescription(g), \"\\n\");");
            writer.newLine();
            writer.flush();

            // Read output from GAP
            List<String> lines = new ArrayList<>();
            String line;
            for (int i = 0; i < readNLines; i++) {
                line = reader.readLine();
                if (line.trim().isEmpty()) {
                    i--;
                    continue;
                }
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> runGapSizeCommand(String generator, int readNLines) {
        try {
            // Send commands to GAP
            writer.write("g := Group(" + generator + ");");
            writer.newLine();
            writer.write("Print(Size(g), \"\\n\");");
            writer.newLine();
            writer.flush();

            // Read output from GAP
            List<String> lines = new ArrayList<>();
            String line;
            for (int i = 0; i < readNLines; i++) {
                line = reader.readLine();
                if (line.trim().isEmpty()) {
                    i--;
                    continue;
                }
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }




    public static String extractCyclesList(String input) {
        StringBuilder out = new StringBuilder();
        out.append("[");
        int from = 0;
        boolean first = true;
        final String marker = "ConjugacyClass(";

        while (true) {
            int start = input.indexOf(marker, from);
            if (start < 0) break;

            int next = input.indexOf(marker, start + marker.length());
            int chunkEnd = (next < 0) ? input.length() : next;
            String chunk = input.substring(start, chunkEnd);

            int sep = chunk.indexOf("]),");
            if (sep >= 0) {
                int arg2Start = sep + 3; // after "]),"
                int arg2End = chunk.lastIndexOf(')');
                if (arg2End > arg2Start) {
                    String arg2 = chunk.substring(arg2Start, arg2End).trim();
                    if (!arg2.equals("()") && !arg2.isEmpty()) {
                        if (!first) out.append(",");
                        out.append(arg2);
                        first = false;
                    }
                }
            }
            from = (next < 0) ? input.length() : next;
        }

        out.append("]");
        return out.toString();
    }
}
