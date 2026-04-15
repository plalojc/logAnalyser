package com.caseroot.loganalyser.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loganalyser.retention")
public class LogAnalyserRetentionProperties {

    private int rawLogDays = 15;
    private int parsedArtifactDays = 30;
    private int caseRootBundleDays = 30;
    private int metadataDays = 90;

    public int getRawLogDays() {
        return rawLogDays;
    }

    public void setRawLogDays(int rawLogDays) {
        this.rawLogDays = rawLogDays;
    }

    public int getParsedArtifactDays() {
        return parsedArtifactDays;
    }

    public void setParsedArtifactDays(int parsedArtifactDays) {
        this.parsedArtifactDays = parsedArtifactDays;
    }

    public int getCaseRootBundleDays() {
        return caseRootBundleDays;
    }

    public void setCaseRootBundleDays(int caseRootBundleDays) {
        this.caseRootBundleDays = caseRootBundleDays;
    }

    public int getMetadataDays() {
        return metadataDays;
    }

    public void setMetadataDays(int metadataDays) {
        this.metadataDays = metadataDays;
    }
}

