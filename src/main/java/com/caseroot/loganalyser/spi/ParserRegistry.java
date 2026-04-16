package com.caseroot.loganalyser.spi;

import java.util.List;
import java.util.Optional;

public interface ParserRegistry {

    ParserPlugin resolve(Optional<String> requestedProfile, List<String> sampleLines);

    List<ParserPlugin> plugins();
}
