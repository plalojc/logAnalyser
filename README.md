# CaseRoot LogAnalyser

CaseRoot LogAnalyser is a Spring Boot microservice that parses application logs, preserves every log statement, produces structured evidence, and generates a CaseRoot-ready bundle for downstream code correlation and LLM response building.

It is designed for:
- legacy Java applications first
- future support for Python, .NET, and other runtimes
- large-file parsing with filesystem-backed artifacts
- zero-drop parsing where unclassified lines are still preserved
- CaseRoot integration through `caseroot_input.json`

## What This Tool Does

Given a log file, the service will:
- detect the log format automatically
- reconstruct multiline events such as Java stack traces
- parse events into a canonical structure
- group events by package instead of only by message text
- normalize repeated messages and cluster related errors
- calculate gap and timeline statistics
- generate `summary.json`
- generate `caseroot_input.json`
- keep raw and parsed artifacts for retention-controlled download

This tool is the log-analysis layer.  
CaseRoot is the reasoning layer.

The normal flow is:
1. upload or submit a log source
2. LogAnalyser parses and summarizes it
3. LogAnalyser writes a compact CaseRoot evidence bundle
4. CaseRoot reads that bundle and correlates it with the codebase

## Current Architecture

This project is now a single Spring Boot application with one root source tree:

- `src/main/java`
- `src/main/resources`
- `src/test/java`

There is no longer a Maven multi-module structure.

## Main Capabilities

- browser-based log upload
- server file-path and directory submission
- async job execution with pollable status
- legacy Java parsing
- JUL parsing
- WebLogic parsing
- WebSphere parsing
- Python logging / traceback parsing
- .NET text log parsing
- .NET JSON log parsing
- generic timestamped fallback parsing
- package-level grouping
- exception aggregation
- timeline gap detection
- event artifact generation as `events.ndjson.gz`
- optional Parquet export
- CaseRoot bundle generation
- artifact download
- recent job inspection
- job comparison

## Build

```powershell
mvn clean package
```

## Run

Run from the project root:

```powershell
mvn spring-boot:run
```

Or run the packaged jar:

```powershell
java -jar target\caseroot-loganalyser-0.1.0-SNAPSHOT.jar
```

Default server:

- `http://localhost:8080`

## Configuration

Current default configuration is in [application.yml](C:/workspace/logAnalyser/src/main/resources/application.yml).

Important defaults:

- HTTP port: `8080`
- upload size: `1 GB`
- base artifact path: `var/loganalyser`
- worker threads: `2`
- default large-gap threshold: `2 minutes`
- Parquet export: disabled by default
- JDBC summary store: disabled by default

Key properties:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 1GB
      max-request-size: 1GB

loganalyser:
  storage:
    base-path: var/loganalyser
  execution:
    worker-threads: 2
  output:
    parquet-enabled: false
    large-gap-highlight-threshold: 2m
```

## How To Use The UI

Open:

- `http://localhost:8080/`

### Normal user flow

Use `Upload File` mode for most cases.

1. choose a log file
2. enter `Application` if you want that value in the CaseRoot bundle
3. enter `Environment` if needed
4. set `Gap Highlight (minutes)` if you want a different threshold for this run
5. choose `Analysis Focus`
6. click `Analyze Uploaded File`

### Analysis Focus behavior

The focus controls are runtime options for summary generation.

- `ALL` means all categories are included
- if `ALL` is checked, all specific categories are checked too
- if you uncheck one category, `ALL` is automatically unchecked
- if you check all specific categories manually, `ALL` becomes checked again
- if you want only a subset, leave only those checkboxes selected

Examples:

- only exceptions: check `EXCEPTION`
- only debug and info: check `DEBUG` and `INFO`
- errors plus warnings: check `ERROR` and `WARN`

Important:
- all raw events are still parsed and preserved in artifacts
- focus affects summary grouping and CaseRoot-facing evidence, not raw preservation

### When to use Server Path mode

Use `Server Path` only when the log file already exists on the machine where this service is running.

Supported source types:

- `FILE_PATH`
- `DIRECTORY`

`DIRECTORY` is useful for batch analysis of many related logs.

## How To Use The API

Base path:

- `/api/v1`

### 1. Create a job from an existing file or directory

Endpoint:

- `POST /api/v1/jobs`

Request body:

```json
{
  "sourceType": "FILE_PATH",
  "sourceLocation": "C:\\logs\\xdb.log",
  "application": "db-service",
  "environment": "prod",
  "largeGapHighlightThresholdMinutes": 2,
  "analysisFocus": ["ALL"]
}
```

Directory example:

```json
{
  "sourceType": "DIRECTORY",
  "sourceLocation": "C:\\logs\\batch",
  "application": "db-service",
  "environment": "prod",
  "largeGapHighlightThresholdMinutes": 2,
  "analysisFocus": ["ERROR", "EXCEPTION"]
}
```

### 2. Upload a file directly

Endpoint:

- `POST /api/v1/jobs/upload`

Example with `curl`:

```bash
curl -X POST "http://localhost:8080/api/v1/jobs/upload" \
  -F "file=@C:/logs/xdb.log" \
  -F "application=db-service" \
  -F "environment=prod" \
  -F "largeGapHighlightThresholdMinutes=2" \
  -F "analysisFocus=ALL"
```

Focused example:

```bash
curl -X POST "http://localhost:8080/api/v1/jobs/upload" \
  -F "file=@C:/logs/xdb.log" \
  -F "application=db-service" \
  -F "environment=prod" \
  -F "largeGapHighlightThresholdMinutes=2" \
  -F "analysisFocus=INFO" \
  -F "analysisFocus=EXCEPTION"
```

### 3. List jobs

- `GET /api/v1/jobs`

### 4. Get one job

- `GET /api/v1/jobs/{jobId}`

### 5. Get job summary

- `GET /api/v1/jobs/{jobId}/summary`

### 6. Query parsed events

- `GET /api/v1/jobs/{jobId}/events`

Supported filters:

- `level`
- `parseStatus`
- `loggerContains`
- `exceptionClass`
- `sourceFile`
- `contains`
- `limit`

Example:

```text
GET /api/v1/jobs/{jobId}/events?level=ERROR&contains=subquery&limit=50
```

### 7. Download an artifact

- `GET /api/v1/jobs/{jobId}/artifacts/{artifactKey}`

Common artifact keys:

- `raw`
- `parsed-events`
- `parquet-events` when enabled
- `summary`
- `caseroot-bundle`

### 8. Compare jobs

- `POST /api/v1/compare`

Example:

```json
{
  "jobIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

### 9. Dashboard overview

- `GET /api/v1/dashboard/overview`

### 10. List registered modules

- `GET /api/v1/modules`

## Job Lifecycle

Typical statuses:

- `ACCEPTED`
- `RUNNING`
- `COMPLETED`
- `FAILED`

Recommended polling:

1. create the job
2. keep calling `GET /api/v1/jobs/{jobId}`
3. wait until status becomes `COMPLETED` or `FAILED`

## Generated Artifacts

By default, artifacts are stored under:

- `var/loganalyser`

Per job, common outputs are:

- `raw/<job-id>/...`
- `parsed/<job-id>/events.ndjson.gz`
- `parsed/<job-id>/events.parquet` when enabled
- `summary/<job-id>/summary.json`
- `caseroot/<job-id>/caseroot_input.json`

For directory jobs, the raw area contains staged files and manifest data for the full submission set.

## What Is In summary.json

The summary artifact contains:

- parser and runtime information
- total line and event counts
- focused event counts
- parse-status counts
- level counts
- gap statistics
- top package groups
- top exceptions
- warnings

The summary is optimized for human review and dashboard display.

## What Is In caseroot_input.json

The CaseRoot bundle is optimized for downstream correlation.

It includes:

- `jobId`
- `location`
- `primarySourceFile`
- `evidenceKeys`
- `rankedSections`
- `expiresAt`
- `summary`

The embedded summary includes compact evidence such as:

- package groups
- representative sample messages
- concise sample events
- concise gap highlights
- exception summaries

The bundle intentionally avoids noisy full stack traces and repeated raw event text.

## How This Helps CaseRoot

This service prepares structured evidence so CaseRoot does not have to reason directly over raw log files.

LogAnalyser helps CaseRoot by providing:

- parser-selected runtime context
- normalized package-level grouping
- clustered repeated failures
- compact exception summaries
- timeline anomaly evidence
- stable artifact references for deeper inspection

In practice:

1. LogAnalyser reads and normalizes the logs.
2. LogAnalyser generates a compact CaseRoot bundle.
3. CaseRoot maps package names, exceptions, and runtime hints to the codebase.
4. CaseRoot builds a better LLM context for the final user response.

## Large File Guidance

The service accepts uploads up to `1 GB`, but the best choice depends on the use case.

Use browser upload when:

- the file is local to your machine
- the size is reasonable for browser transfer
- the user wants the simplest workflow

Use `FILE_PATH` or `DIRECTORY` when:

- the logs are already on the server
- the logs are very large
- this is a batch or operational workflow

That avoids unnecessary HTTP upload overhead.

## Retention Model

Default retention:

- raw logs: `15 days`
- parsed artifacts: `30 days`
- CaseRoot bundle: `30 days`
- metadata / compact summaries: `90 days`

Raw logs are not stored in the database by default.

The intended model is:

- raw evidence on filesystem/object-style storage
- metadata in DB only when summary-store is enabled

## Optional Parquet Export

Enable Parquet when downstream analytics or batch comparison needs columnar output:

```yaml
loganalyser:
  output:
    parquet-enabled: true
```

## Optional JDBC Summary Store

Enable aggregate persistence without storing raw logs in the database:

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

This stores compact aggregate data only:

- job state
- summary rows
- level counts
- gap buckets
- signature summaries
- exception summaries

Raw logs and parsed event artifacts remain filesystem-backed.

## Notes

- parser profile selection is automatic for normal use
- every log statement is preserved; the system is designed to be zero-drop
- the UI is an operator console, but every main action is also available via API
- the generated CaseRoot bundle becomes more useful when `application` and `environment` are filled in during job submission
