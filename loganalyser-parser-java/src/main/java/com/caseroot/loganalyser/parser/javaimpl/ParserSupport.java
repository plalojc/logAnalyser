package com.caseroot.loganalyser.parser.javaimpl;

import com.caseroot.loganalyser.domain.model.StackFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ParserSupport {

    private static final Pattern STACK_FRAME_PATTERN = Pattern.compile(
            "^\\s*at\\s+([\\w.$]+)\\.([\\w$<>]+)\\(([^:()]+)(?::(\\d+))?\\)\\s*$"
    );
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile("^Caused by:\\s+([\\w.$]+).*");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("^([\\w.$]+(?:Exception|Error))(?::\\s*(.*))?$");

    private ParserSupport() {
    }

    static List<StackFrame> extractStackFrames(String rawEvent) {
        List<StackFrame> frames = new ArrayList<>();
        for (String line : rawEvent.split("\\R")) {
            Matcher matcher = STACK_FRAME_PATTERN.matcher(line);
            if (matcher.matches()) {
                Integer lineNumber = matcher.group(4) == null ? null : Integer.parseInt(matcher.group(4));
                frames.add(new StackFrame(
                        matcher.group(1),
                        matcher.group(2),
                        matcher.group(3),
                        lineNumber
                ));
            }
        }
        return frames;
    }

    static String extractExceptionClass(String rawEvent) {
        for (String line : rawEvent.split("\\R")) {
            Matcher matcher = EXCEPTION_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String extractRootCauseClass(String rawEvent) {
        String rootCause = null;
        for (String line : rawEvent.split("\\R")) {
            Matcher matcher = CAUSED_BY_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                rootCause = matcher.group(1);
            }
        }
        return rootCause;
    }
}

