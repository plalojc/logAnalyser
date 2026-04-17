package com.caseroot.loganalyser.app.config;

import com.caseroot.loganalyser.caseroot.export.DefaultCaseRootExportBuilder;
import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.DefaultAnalysisApplicationService;
import com.caseroot.loganalyser.core.ingest.FileAnalysisProcessor;
import com.caseroot.loganalyser.core.ingest.LineSampler;
import com.caseroot.loganalyser.core.parser.DefaultParserRegistry;
import com.caseroot.loganalyser.core.parser.RawUnclassifiedParserPlugin;
import com.caseroot.loganalyser.core.persistence.NoOpAnalysisSummaryStore;
import com.caseroot.loganalyser.core.query.ArtifactEventQueryService;
import com.caseroot.loganalyser.core.query.GzipArtifactEventQueryService;
import com.caseroot.loganalyser.core.repository.InMemoryJobRepository;
import com.caseroot.loganalyser.core.retention.FixedRetentionPolicyResolver;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.export.parquet.NoOpParquetArtifactExporter;
import com.caseroot.loganalyser.export.parquet.ParquetArtifactExporterImpl;
import com.caseroot.loganalyser.persistence.jdbc.JdbcJobRepository;
import com.caseroot.loganalyser.persistence.jdbc.JdbcAnalysisSummaryStore;
import com.caseroot.loganalyser.parser.javaimpl.GenericTimestampParserPlugin;
import com.caseroot.loganalyser.parser.javaimpl.JulParserPlugin;
import com.caseroot.loganalyser.parser.javaimpl.LegacyJavaParserPlugin;
import com.caseroot.loganalyser.parser.javaimpl.WebLogicParserPlugin;
import com.caseroot.loganalyser.parser.javaimpl.WebSphereParserPlugin;
import com.caseroot.loganalyser.parser.polyglot.DotNetJsonParserPlugin;
import com.caseroot.loganalyser.parser.polyglot.DotNetTextParserPlugin;
import com.caseroot.loganalyser.parser.polyglot.PythonLoggingParserPlugin;
import com.caseroot.loganalyser.spi.ArtifactStorage;
import com.caseroot.loganalyser.spi.AnalysisSummaryStore;
import com.caseroot.loganalyser.spi.CaseRootExportBuilder;
import com.caseroot.loganalyser.spi.JobRepository;
import com.caseroot.loganalyser.spi.ParquetArtifactExporter;
import com.caseroot.loganalyser.spi.ParserPlugin;
import com.caseroot.loganalyser.spi.ParserRegistry;
import com.caseroot.loganalyser.spi.RetentionPolicyResolver;
import com.caseroot.loganalyser.storage.filesystem.FileSystemArtifactStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

@Configuration
@EnableConfigurationProperties({
        LogAnalyserStorageProperties.class,
        LogAnalyserRetentionProperties.class,
        LogAnalyserExecutionProperties.class,
        LogAnalyserOutputProperties.class,
        LogAnalyserSummaryStoreProperties.class
})
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ParserPlugin legacyJavaParserPlugin() {
        return new LegacyJavaParserPlugin();
    }

    @Bean
    ParserPlugin genericTimestampParserPlugin() {
        return new GenericTimestampParserPlugin();
    }

    @Bean
    ParserPlugin julParserPlugin() {
        return new JulParserPlugin();
    }

    @Bean
    ParserPlugin webLogicParserPlugin() {
        return new WebLogicParserPlugin();
    }

    @Bean
    ParserPlugin webSphereParserPlugin() {
        return new WebSphereParserPlugin();
    }

    @Bean
    ParserPlugin pythonLoggingParserPlugin() {
        return new PythonLoggingParserPlugin();
    }

    @Bean
    ParserPlugin dotNetTextParserPlugin() {
        return new DotNetTextParserPlugin();
    }

    @Bean
    ParserPlugin dotNetJsonParserPlugin() {
        return new DotNetJsonParserPlugin();
    }

    @Bean
    ParserPlugin rawUnclassifiedParserPlugin() {
        return new RawUnclassifiedParserPlugin();
    }

    @Bean
    ParserRegistry parserRegistry(List<ParserPlugin> parserPlugins) {
        return new DefaultParserRegistry(parserPlugins);
    }

    @Bean
    @ConditionalOnProperty(prefix = "loganalyser.summary-store", name = "enabled", havingValue = "true")
    JobRepository jdbcJobRepository(
            DriverManagerDataSource dataSource,
            LogAnalyserSummaryStoreProperties properties
    ) {
        return new JdbcJobRepository(
                new JdbcTemplate(dataSource),
                properties.getTablePrefix(),
                properties.isInitializeSchema()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "loganalyser.summary-store", name = "enabled", havingValue = "false", matchIfMissing = true)
    JobRepository jobRepository() {
        return new InMemoryJobRepository();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService analysisExecutorService(LogAnalyserExecutionProperties properties) {
        return Executors.newFixedThreadPool(Math.max(1, properties.getWorkerThreads()));
    }

    @Bean
    Executor analysisExecutor(ExecutorService analysisExecutorService) {
        return analysisExecutorService;
    }

    @Bean
    @ConditionalOnProperty(prefix = "loganalyser.summary-store", name = "enabled", havingValue = "true")
    DriverManagerDataSource analysisSummaryDataSource(LogAnalyserSummaryStoreProperties properties) {
        if (properties.getJdbcUrl() == null || properties.getJdbcUrl().isBlank()) {
            throw new IllegalStateException("loganalyser.summary-store.jdbc-url is required when summary-store is enabled.");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }

    @Bean
    @ConditionalOnBean(DriverManagerDataSource.class)
    AnalysisSummaryStore jdbcAnalysisSummaryStore(
            DriverManagerDataSource dataSource,
            LogAnalyserSummaryStoreProperties properties
    ) {
        return new JdbcAnalysisSummaryStore(
                new JdbcTemplate(dataSource),
                properties.getTablePrefix(),
                properties.isInitializeSchema()
        );
    }

    @Bean
    @ConditionalOnMissingBean(AnalysisSummaryStore.class)
    AnalysisSummaryStore analysisSummaryStore() {
        return new NoOpAnalysisSummaryStore();
    }

    @Bean
    ArtifactStorage artifactStorage(LogAnalyserStorageProperties properties, Clock clock) {
        return new FileSystemArtifactStorage(properties.getBasePath(), clock);
    }

    @Bean
    CaseRootExportBuilder caseRootExportBuilder() {
        return new DefaultCaseRootExportBuilder();
    }

    @Bean
    RetentionPolicyResolver retentionPolicyResolver(LogAnalyserRetentionProperties properties) {
        return new FixedRetentionPolicyResolver(new RetentionPolicy(
                properties.getRawLogDays(),
                properties.getParsedArtifactDays(),
                properties.getCaseRootBundleDays(),
                properties.getMetadataDays()
        ));
    }

    @Bean
    LineSampler lineSampler() {
        return new LineSampler();
    }

    @Bean
    FileAnalysisProcessor fileAnalysisProcessor(LogAnalyserOutputProperties properties) {
        return new FileAnalysisProcessor(properties.getLargeGapHighlightThreshold());
    }

    @Bean
    ArtifactEventQueryService artifactEventQueryService() {
        return new GzipArtifactEventQueryService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "loganalyser.output", name = "parquet-enabled", havingValue = "true")
    ParquetArtifactExporter parquetArtifactExporter() {
        return new ParquetArtifactExporterImpl();
    }

    @Bean
    @ConditionalOnMissingBean(ParquetArtifactExporter.class)
    ParquetArtifactExporter noOpParquetArtifactExporter() {
        return new NoOpParquetArtifactExporter();
    }

    @Bean
    AnalysisApplicationService analysisApplicationService(
            ParserRegistry parserRegistry,
            ArtifactStorage artifactStorage,
            CaseRootExportBuilder caseRootExportBuilder,
            JobRepository jobRepository,
            AnalysisSummaryStore analysisSummaryStore,
            RetentionPolicyResolver retentionPolicyResolver,
            Clock clock,
            Executor analysisExecutor,
            LineSampler lineSampler,
            FileAnalysisProcessor fileAnalysisProcessor,
            ArtifactEventQueryService artifactEventQueryService,
            ParquetArtifactExporter parquetArtifactExporter
    ) {
        return new DefaultAnalysisApplicationService(
                parserRegistry,
                artifactStorage,
                caseRootExportBuilder,
                jobRepository,
                analysisSummaryStore,
                retentionPolicyResolver,
                clock,
                analysisExecutor,
                lineSampler,
                fileAnalysisProcessor,
                artifactEventQueryService,
                parquetArtifactExporter
        );
    }
}
