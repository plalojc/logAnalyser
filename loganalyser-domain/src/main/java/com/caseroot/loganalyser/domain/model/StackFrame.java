package com.caseroot.loganalyser.domain.model;

public record StackFrame(
        String className,
        String methodName,
        String fileName,
        Integer lineNumber
) {
}
