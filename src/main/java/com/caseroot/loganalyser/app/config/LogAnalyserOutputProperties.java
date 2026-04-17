package com.caseroot.loganalyser.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "loganalyser.output")
public class LogAnalyserOutputProperties {

    private boolean parquetEnabled;
    private Duration largeGapHighlightThreshold = Duration.ofMinutes(1);

    public boolean isParquetEnabled() {
        return parquetEnabled;
    }

    public void setParquetEnabled(boolean parquetEnabled) {
        this.parquetEnabled = parquetEnabled;
    }

    public Duration getLargeGapHighlightThreshold() {
        return largeGapHighlightThreshold;
    }

    public void setLargeGapHighlightThreshold(Duration largeGapHighlightThreshold) {
        this.largeGapHighlightThreshold = largeGapHighlightThreshold;
    }
}
