# CaseRoot LogAnalyser

This workspace now contains a modular Spring Boot Phase 4 log analysis microservice that can sit beside CaseRoot.

## Module layout

- `loganalyser-domain`
  Shared domain records and enums with no Spring dependency.
- `loganalyser-spi`
  Extension points for parsers, storage, retention, and CaseRoot export.
- `loganalyser-core`
  Plain Java orchestration, parser registry, streaming ingestion, multiline reconstruction, summary generation, and in-memory job repository.
- `loganalyser-parser-java`
  Legacy Java, JUL, WebLogic, WebSphere, and generic timestamped parser plugins.
- `loganalyser-parser-polyglot`
  Python logging/traceback and .NET text/JSON parser plugins.
- `loganalyser-export-parquet`
  Optional Parquet export for parsed event artifacts.
- `loganalyser-storage-filesystem`
  Filesystem-backed raw, parsed, Parquet, summary, and CaseRoot artifact allocation.
- `loganalyser-caseroot-export`
  CaseRoot bundle builder and bundle file writer.
- `loganalyser-persistence-jdbc`
  Optional JDBC aggregate persistence for summary tables without storing raw log blobs in the database.
- `loganalyser-app`
  Thin Spring Boot microservice that wires the modules, serves the API, and hosts the Phase 4 dashboard page.

## Why this shape

- keeps the Spring Boot runtime thin
- keeps core parsing logic reusable outside the web layer
- makes parser and storage implementations replaceable
- reduces future coupling with CaseRoot while keeping the output contract close

## Phase 4 Capabilities

- file-path job creation
- multipart upload job creation
- directory batch ingestion
- asynchronous job execution with pollable job status
- streaming line-by-line file reading
- multiline event reconstruction for Java stack traces
- Log4j / Logback style parsing
- JUL parsing
- WebLogic parsing
- WebSphere parsing
- Python logging and traceback parsing
- .NET text log parsing
- .NET JSON log parsing
- generic timestamped log parsing
- message normalization and signature clustering
- exception family aggregation
- gap and timeline statistics
- canonical event NDJSON artifact generation
- optional Parquet artifact generation for downstream analytics
- summary JSON generation
- CaseRoot bundle JSON generation
- filtered event query API over parsed artifacts
- multi-job comparison API for shared signatures and shared exceptions
- dashboard overview API for recent jobs and aggregate runtime/application metrics
- built-in dashboard UI served from the Spring Boot app root
- configurable filesystem-backed artifact retention locations
- optional JDBC persistence of compact summary aggregates for PostgreSQL-style deployments
- optional JDBC persistence of job state for restart-tolerant polling

## Build

```powershell
mvn clean package
```

## Run

```powershell
mvn -pl loganalyser-app spring-boot:run
```

Default upload sizing:

- browser and API multipart uploads are configured up to `1 GB`
- for larger files or operational batch runs, `FILE_PATH` or `DIRECTORY` mode is still the better choice because it avoids HTTP upload overhead

The default HTTP base path is:

- `POST /api/v1/jobs`
- `POST /api/v1/jobs/upload`
- `POST /api/v1/compare`
- `GET /api/v1/jobs`
- `GET /api/v1/jobs/{jobId}`
- `GET /api/v1/jobs/{jobId}/summary`
- `GET /api/v1/jobs/{jobId}/events`
- `GET /api/v1/jobs/{jobId}/artifacts/{artifactKey}`
- `GET /api/v1/dashboard/overview`
- `GET /api/v1/modules`

The dashboard UI is available at:

- `GET /`

The UI now supports:

- file upload from the browser
- file-path and directory-path job submission
- recent job inspection
- artifact download links
- event search and job comparison

## Sample Request

```json
{
  "sourceType": "DIRECTORY",
  "sourceLocation": "C:\\logs\\batch",
  "application": "order-service",
  "environment": "prod",
  "requestedParserProfile": "log4j_pattern"
}
```

## Generated Artifacts

By default the service writes artifacts under `var/loganalyser`:

- `raw/<job-id>/...`
- `parsed/<job-id>/events.ndjson.gz`
- `parsed/<job-id>/events.parquet` when `loganalyser.output.parquet-enabled=true`
- `summary/<job-id>/summary.json`
- `caseroot/<job-id>/caseroot_input.json`

For directory jobs, the raw artifact path is a staged manifest and the copied source files sit beside it under the job-specific raw directory.

## Event Query

Filter parsed events without reprocessing the source logs:

```text
GET /api/v1/jobs/{jobId}/events?level=ERROR&sourceFile=app-1.log&contains=Failed&limit=50
```

Supported filters:

- `level`
- `parseStatus`
- `loggerContains`
- `exceptionClass`
- `sourceFile`
- `contains`
- `limit`

## Artifact Download

Download generated evidence for a completed or running job:

```text
GET /api/v1/jobs/{jobId}/artifacts/{artifactKey}
```

Common keys:

- `raw`
- `parsed-events`
- `parquet-events` when enabled
- `summary`
- `caseroot-bundle`

## Job Comparison

Compare multiple completed jobs without reprocessing artifacts:

```text
POST /api/v1/compare
```

```json
{
  "jobIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

The response includes:

- per-job event totals
- aggregated level counts across the selected jobs
- common normalized signatures
- common exception and root-cause families

## Dashboard Overview

Fetch the aggregate dashboard model used by the built-in UI:

```text
GET /api/v1/dashboard/overview
```

The response includes:

- total jobs by lifecycle state
- total events across completed jobs
- job counts by application
- job counts by runtime family
- recent jobs ready for comparison

## Optional Parquet Export

Enable Parquet artifact generation when downstream analytics or batch comparison flows benefit from columnar output:

```yaml
loganalyser:
  output:
    parquet-enabled: true
```

## Optional Summary Store

Set the following properties to persist aggregate-only Phase 2 summary data into PostgreSQL-compatible tables:

```yaml
loganalyser:
  summary-store:
    enabled: true
    jdbc-url: jdbc:postgresql://localhost:5432/loganalyser
    username: loganalyser
    password: change-me
    driver-class-name: org.postgresql.Driver
    table-prefix: loganalyser_
    initialize-schema: true
```

This persists only summary rows, level counts, gap buckets, signature summaries, and exception summaries. Raw logs and parsed event artifacts remain on filesystem storage for retention-controlled access by CaseRoot.
