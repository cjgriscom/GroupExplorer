package io.chandler.gap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience helpers for the PBIN format.
 * For large files use {@link PbinFile} directly.
 */
public class PbinReader {

    /** Materializes every generator into memory. Avoid for very large files. */
    public static List<String> readPbinFile(String filePath) throws IOException {
        try (PbinFile pf = PbinFile.open(filePath)) {
            List<String> lines = new ArrayList<>(pf.size());
            for (int i = 0; i < pf.size(); i++)
                lines.add(pf.get(i));
            return lines;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PbinReader <file.pbin>");
            System.exit(1);
        }
        try (PbinFile pf = PbinFile.open(args[0])) {
            for (int i = 0; i < pf.size(); i++)
                System.out.println(pf.get(i));
        }
    }
}
