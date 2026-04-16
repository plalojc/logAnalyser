package com.caseroot.loganalyser.core.parser;

import com.caseroot.loganalyser.spi.ParserPlugin;
import com.caseroot.loganalyser.spi.ParserRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DefaultParserRegistry implements ParserRegistry {

    private final List<ParserPlugin> plugins;

    public DefaultParserRegistry(List<ParserPlugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

    @Override
    public ParserPlugin resolve(Optional<String> requestedProfile, List<String> sampleLines) {
        if (requestedProfile.isPresent()) {
            String profile = requestedProfile.get();
            return plugins.stream()
                    .filter(plugin -> plugin.supportsProfile(profile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parser profile: " + profile));
        }

        return plugins.stream()
                .max(Comparator.comparingInt(plugin -> plugin.detectionConfidence(sampleLines)))
                .orElseThrow(() -> new IllegalStateException("No parser plugins registered."));
    }

    @Override
    public List<ParserPlugin> plugins() {
        return plugins;
    }
}
