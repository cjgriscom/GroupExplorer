package io.chandler.gap;

import java.io.*;
import java.lang.ProcessBuilder.Redirect;
import java.util.*;

public class GapInterface {
    private static final GapConfig gapConfig = findGapExecutable();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    private enum LaunchMode {
        DIRECT,
        CYGWIN_BASH
    }

    /**
     * Configuration for launching GAP
     */
    private static class GapConfig {
        final LaunchMode mode;
        final String command;
        final File gapRootDir;
        String gapExePath;  // MSYS-style path for gap.exe (e.g., /opt/gap-4.15.1/gap.exe)

        GapConfig(LaunchMode mode, String command, File gapRootDir) {
            this.mode = mode;
            this.command = command;
            this.gapRootDir = gapRootDir;
        }
    }

    public GapInterface() throws IOException {
        reset();
    }
    
    /**
     * Finds the GAP executable by searching common installation locations.
     * @return GapConfig with information on how to launch GAP
     */
    private static GapConfig findGapExecutable() {
        
        // On Windows, search Program Files for GAP installations
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            String[] programFilesDirs = {
                System.getenv("ProgramFiles"),        // C:\Program Files
                System.getenv("ProgramFiles(x86)"),   // C:\Program Files (x86)
                "C:\\Program Files",
                "C:\\Program Files (x86)"
            };
            
            for (String programFilesDir : programFilesDirs) {
                if (programFilesDir == null) continue;
                
                File dir = new File(programFilesDir);
                if (!dir.exists() || !dir.isDirectory()) continue;
                
                // Look for GAP-* directories
                try {
                    File[] gapDirs = dir.listFiles((d, name) -> 
                        name.toLowerCase().startsWith("gap-") || name.toLowerCase().equals("gap"));
                    
                    if (gapDirs != null && gapDirs.length > 0) {
                        // Sort by version number (descending) to get the latest version
                        Arrays.sort(gapDirs, (a, b) -> b.getName().compareTo(a.getName()));
                        
                        for (File gapDir : gapDirs) {
                            // Check for MSYS/Cygwin bash launcher (Windows installer version)
                            File runtimeDir = new File(gapDir, "runtime");
                            File bashExe = new File(runtimeDir, "bin\\bash.exe");

                            if (bashExe.exists()) {
                                // Look for gap.exe in runtime/opt/gap*/gap.exe
                                File optDir = new File(runtimeDir, "opt");
                                if (optDir.exists() && optDir.isDirectory()) {
                                    File[] optGapDirs = optDir.listFiles((d, name) -> name.toLowerCase().startsWith("gap"));
                                    if (optGapDirs != null && optGapDirs.length > 0) {
                                        // Sort to get the latest version
                                        Arrays.sort(optGapDirs, (a, b) -> b.getName().compareTo(a.getName()));
                                        for (File optGapDir : optGapDirs) {
                                            File gapExe = new File(optGapDir, "gap.exe");
                                            if (gapExe.exists()) {
                                                // Convert to MSYS path format: /opt/gap-4.15.1/gap.exe
                                                String gapExePath = "/opt/" + optGapDir.getName() + "/gap.exe";
                                                System.out.println("Found GAP at: " + gapDir.getAbsolutePath());
                                                System.out.println("  GAP executable: " + gapExePath);
                                                System.out.println("  Using bash launcher: " + bashExe.getAbsolutePath());
                                                
                                                GapConfig config = new GapConfig(LaunchMode.CYGWIN_BASH, bashExe.getAbsolutePath(), gapDir);
                                                config.gapExePath = gapExePath;
                                                return config;
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                } catch (Exception e) {
                    // Continue searching
                }
            }
            
            System.err.println("WARNING: GAP not found in Program Files. Falling back to 'gap' command.");
            System.err.println("Please ensure GAP is installed or add it to your PATH.");
            System.err.println("Download GAP from: https://www.gap-system.org/");
        }
        
        // Fallback to 'gap'
        return new GapConfig(LaunchMode.DIRECT, "gap", null);
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
        ProcessBuilder pb;
        
        if (gapConfig.mode == LaunchMode.CYGWIN_BASH) {
            // Windows GAP with bundled MSYS bash launcher
            File gapRoot = Objects.requireNonNull(gapConfig.gapRootDir, "GAP root directory not set");
            File runtimeDir = new File(gapRoot, "runtime");
            String gapExePath = Objects.requireNonNull(gapConfig.gapExePath, "GAP executable path not set");
            
            commands.add(gapConfig.command);           // runtime/bin/bash.exe
            commands.add("--login");
            commands.add("-c");
            commands.add(gapExePath + " -q --width 1000000");
            
            pb = new ProcessBuilder(commands);
            pb.directory(runtimeDir);                 // Set working directory to runtime dir
            pb.redirectError(Redirect.INHERIT);
            Map<String, String> env = pb.environment();
            env.put("CHERE_INVOKING", "1");          // Don't cd to home on --login
            env.put("MSYS2_ARG_CONV_EXCL", "*");     // Don't mangle paths
            System.out.println("Launching GAP with bash from: " + runtimeDir.getAbsolutePath());
            System.out.println("  Executing: " + gapExePath + " -q --width 1000000");
        } else {
            commands.add(gapConfig.command);
            commands.add("-q"); // Quiet mode, less output
            commands.add("--width");
            commands.add("1000000"); // Avoid line breaks
            
            pb = new ProcessBuilder(commands);
            if (gapConfig.gapRootDir != null) {
                pb.directory(gapConfig.gapRootDir);
            }
            pb.redirectError(Redirect.INHERIT);
        }
        
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
