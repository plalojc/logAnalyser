package com.caseroot.loganalyser.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loganalyser.output")
public class LogAnalyserOutputProperties {

    private boolean parquetEnabled;

    public boolean isParquetEnabled() {
        return parquetEnabled;
    }

    public void setParquetEnabled(boolean parquetEnabled) {
        this.parquetEnabled = parquetEnabled;
    }
}
