package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.SourceType;

public interface RetentionPolicyResolver {

    RetentionPolicy resolve(SourceType sourceType);
}
