package com.caseroot.loganalyser.parser.polyglot;

import com.caseroot.loganalyser.domain.model.StackFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PolyglotParserSupport {

    private static final Pattern PYTHON_TRACEBACK_EXCEPTION = Pattern.compile("^([A-Za-z_][\\w.]*(?:Error|Exception)):\\s*(.*)$");
    private static final Pattern DOTNET_EXCEPTION = Pattern.compile("^([A-Za-z_][\\w.]*Exception):\\s*(.*)$");
    private static final Pattern PYTHON_FRAME = Pattern.compile("^\\s*File\\s+\"([^\"]+)\",\\s+line\\s+(\\d+),\\s+in\\s+(.+)$");

    private PolyglotParserSupport() {
    }

    static String extractPythonExceptionClass(String rawEvent) {
        String[] lines = rawEvent.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            Matcher matcher = PYTHON_TRACEBACK_EXCEPTION.matcher(lines[index].trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String extractDotNetExceptionClass(String rawEvent) {
        for (String line : rawEvent.split("\\R")) {
            Matcher matcher = DOTNET_EXCEPTION.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static List<StackFrame> extractPythonFrames(String rawEvent) {
        List<StackFrame> frames = new ArrayList<>();
        for (String line : rawEvent.split("\\R")) {
            Matcher matcher = PYTHON_FRAME.matcher(line);
            if (matcher.matches()) {
                frames.add(new StackFrame(
                        matcher.group(1),
                        matcher.group(3),
                        matcher.group(1),
                        Integer.parseInt(matcher.group(2))
                ));
            }
        }
        return frames;
    }
}
