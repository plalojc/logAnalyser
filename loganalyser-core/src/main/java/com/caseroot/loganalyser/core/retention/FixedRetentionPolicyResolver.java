package com.caseroot.loganalyser.core.retention;

import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.SourceType;
import com.caseroot.loganalyser.spi.RetentionPolicyResolver;

public final class FixedRetentionPolicyResolver implements RetentionPolicyResolver {

    private final RetentionPolicy retentionPolicy;

    public FixedRetentionPolicyResolver(RetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    @Override
    public RetentionPolicy resolve(SourceType sourceType) {
        return retentionPolicy;
    }
}

