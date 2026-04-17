package com.caseroot.loganalyser.core.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LineSampler {

    public List<String> sample(Path path, int maxLines) {
        return sample(List.of(path), maxLines);
    }

    public List<String> sample(List<Path> paths, int maxLines) {
        List<String> lines = new ArrayList<>(maxLines);

        try {
            for (Path path : paths) {
                try (BufferedReader reader = TextInputSupport.openReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null && lines.size() < maxLines) {
                        lines.add(line);
                    }
                }
                if (lines.size() >= maxLines) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to sample input paths " + paths, exception);
        }

        return List.copyOf(lines);
    }
}
