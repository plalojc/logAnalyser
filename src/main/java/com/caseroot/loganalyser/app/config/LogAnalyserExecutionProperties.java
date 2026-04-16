package com.caseroot.loganalyser.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loganalyser.execution")
public class LogAnalyserExecutionProperties {

    private int workerThreads = 2;

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }
}
