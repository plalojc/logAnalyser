# Comprehensive Log Analysis System - Technical Design

## Overview

### Purpose

Design a log analysis system for heterogeneous applications, with primary focus on legacy Java systems such as:

- Log4j 1.x and 2.x
- Logback
- java.util.logging (JUL)
- WebLogic / WebSphere / JBoss / Tomcat style server logs
- Mixed plain-text, rolling file, and partially structured logs

The system must ingest large log files, reconstruct log events accurately, extract operational signals, and produce search-ready analysis outputs for troubleshooting, incident review, downstream reporting, and CaseRoot context generation.

### Primary Goals

- Analyze large log files up to 1 GB with bounded memory usage
- Support multiple application log formats without requiring code changes in the source applications
- Use an extensible parser and enricher architecture so new runtimes can be added without redesigning the core system
- Correctly merge multiline Java stack traces into a single event
- Extract timestamps, levels, logger names, thread names, MDC-like key-values, hostnames, IPs, ports, request IDs, session IDs, error codes, and durations
- Detect repeated error signatures and normalize noisy values such as IDs, UUIDs, timestamps, and IPs
- Measure timestamp gaps, bursts, silence windows, and error spikes
- Store outputs in a form that supports both API retrieval and future UI/search use cases
- Produce deterministic, evidence-rich output that CaseRoot can correlate with the codebase before sending context to an LLM
- Preserve every log statement from the input, including unknown or partially parsed statements

### Non-Goals

- Real-time stream processing in the first release
- Full observability replacement for ELK, Splunk, or OpenSearch
- AI-generated root cause analysis in the first release
- Guaranteed extraction of every business field from every custom log message

### Extensibility Direction

Release 1 is optimized for legacy Java logs, but the architecture must remain language-neutral.

Future supported sources should include:

- Python applications using `logging`, structlog, gunicorn, uvicorn, Django, Flask, Celery
- C# and .NET applications using Serilog, NLog, log4net, ASP.NET request logs, Windows service logs
- Node.js, Go, and other services with timestamped or JSON logs
- Infrastructure and proxy logs that may need to be correlated with application logs

The system should therefore treat parser support as a pluggable capability, not a Java-only assumption embedded in the core pipeline.

### Downstream Consumer: CaseRoot

The analyzer is not the final user-facing system. Its primary downstream consumer is CaseRoot.

CaseRoot will:

- ingest analyzer output
- correlate it with the codebase
- build a grounded context package
- send that context to an LLM
- generate an end-user-facing response

Because of that, the analyzer output must be:

- deterministic instead of interpretive
- traceable back to raw log evidence
- compact enough for retrieval and ranking
- rich in code-correlation hints such as logger names, package names, class names, methods, stack frames, and source files
- complete, with no silent drops of unmatched or malformed log statements


## Problem Context

Legacy Java application logs are usually inconsistent. Common challenges include:

- Different timestamp formats across applications and environments
- Stack traces spread across tens or hundreds of lines
- Thread dumps and nested exception chains
- Missing correlation IDs in older applications
- Application server prefixes wrapped around application logs
- Mixed encodings, partial lines, and rotated file fragments
- Repeated messages that differ only by dynamic values

A useful system must therefore do more than simple line-by-line regex parsing. It needs event reconstruction, normalization, and analysis layers.

Just as important, it must be zero-drop: every input line must be accounted for, even if the parser cannot fully classify it.

The same principle should continue to hold as new runtimes are added. The core engine should not depend on Java-specific types beyond optional enrichers.


## High-Level Architecture

```text
Upload/API
  -> Job Manager
  -> File Reader
  -> Event Boundary Detector
  -> Parser Registry
  -> Format Parser
  -> Field Extractor
  -> Runtime Enrichers
  -> Event Normalizer
  -> Aggregation Engine
  -> Storage Writers
  -> CaseRoot Export Builder
  -> Query/API Layer
```

### Main Components

- `API service`
  Accepts file uploads, local-path ingestion requests, and status queries.

- `Job manager`
  Creates analysis jobs, tracks progress, and coordinates parsing and persistence.

- `File reader`
  Streams files in chunks and preserves line boundaries without loading the whole file into memory.

- `Event boundary detector`
  Identifies when a new log event starts and appends following lines to the same event if they belong to a stack trace or multiline message.

- `Format parser`
  Applies parser plugins for known Java and generic formats.

- `Field extractor`
  Extracts structured attributes and metadata from the parsed event.

- `Parser registry`
  Selects parser plugins and versioned parsing rules based on auto-detection, explicit profile selection, and runtime confidence.

- `Runtime enrichers`
  Add runtime-specific hints such as Java stack frames, Python module names, or .NET namespaces without changing the canonical event contract.

- `Event normalizer`
  Produces stable signatures by replacing dynamic tokens with placeholders.

- `Aggregation engine`
  Computes counts, patterns, gap statistics, top errors, and anomaly-friendly summaries.

- `Storage writers`
  Persist raw structured events, normalized signatures, and summary tables separately.

- `CaseRoot export builder`
  Produces a curated machine-readable bundle optimized for codebase correlation and LLM context assembly.

### Core Principle: Zero-Drop Ingestion

The system must not ignore any log statement.

That means:

- no line is silently skipped
- no event is discarded because a parser profile did not match
- malformed or unknown events are retained as raw evidence
- downstream outputs include both parsed and unclassified records

The system may mark records as:

- fully parsed
- partially parsed
- unclassified

But it must not drop them.

### Core Principle: Extensible-by-Plugin

The ingestion pipeline should have a stable core and move runtime-specific logic into plugins.

That means:

- parser detection rules are pluggable
- event boundary logic can be specialized per profile
- field extractors can be runtime-aware
- runtime-specific enrichers can be added without changing downstream contracts
- CaseRoot output format stays stable even as new runtimes are added


## Recommended Deployment Model

### Release 1

- Java 21 parser worker for ingestion, event reconstruction, extraction, and normalization
- API and orchestration layer in Java or Python, depending on team preference
- PostgreSQL for job metadata and summary tables
- Object storage or local compressed files for per-event outputs
- Docker deployment for repeatable runtime setup

### Why this split

Storing every parsed event directly in PostgreSQL is possible for small volumes, but it becomes expensive and slow for repeated 1 GB uploads. The better design is:

- PostgreSQL for searchable metadata, summaries, and top signatures
- Compressed NDJSON or Parquet for full event-level output
- Raw uploaded log files in object storage or managed file storage, not in the relational database

This keeps the system practical for large legacy log archives.


## Implementation Language Comparison: Java vs Python

If the primary decision criterion is parsing large log files as fast as possible, Java is the stronger choice for the core parsing engine.

### Comparison Table

| Criterion | Java | Python | Better Fit |
| :-- | :-- | :-- | :-- |
| Large-file parsing throughput | Excellent | Good | Java |
| CPU-heavy regex and normalization | Excellent | Moderate to good | Java |
| Multi-core parallelism | Excellent | Limited by GIL for CPU-bound work | Java |
| Memory efficiency under heavy load | Good with careful streaming design | Acceptable but higher object overhead | Java |
| Developer speed and prototyping | Good | Excellent | Python |
| API development speed | Good | Excellent | Python |
| Long-running worker robustness | Excellent | Good | Java |
| Plugin and data-processing flexibility | Good | Excellent | Python |
| Best choice for this system's parser core | Excellent | Possible but not optimal | Java |

### Java Strengths

- better sustained throughput for large text parsing and regex-heavy workloads
- true multi-threaded execution for CPU-bound parsing stages
- better fit for parallel batch processing when multiple files or partitions are analyzed
- more predictable performance for long-running worker services
- strong alignment with the initial domain, which is mostly legacy Java application logs

### Python Strengths

- faster to prototype and iterate on parser rules
- very productive for API, orchestration, and data-export layers
- simpler for rapid experimentation with new enrichers and transformations
- strong ecosystem for JSON handling and service development

### Python Limitations for This Use Case

- CPU-bound parsing does not scale as well because of the GIL
- multiprocessing can recover throughput, but adds IPC overhead and operational complexity
- per-object memory overhead is usually higher for millions of parsed records and intermediate structures
- parser throughput is more likely to degrade as enrichment and normalization logic grows

### Java Limitations

- slower development speed for early parser experimentation
- more boilerplate unless a lightweight framework and careful internal abstractions are used
- regex-heavy code can still become allocation-heavy if implemented carelessly

### Practical Recommendation

For this system, use:

- Java for the core parser worker
- streaming I/O, bounded-memory event reconstruction, normalization, and artifact writing in Java
- a language-neutral output contract for CaseRoot

Then choose one of these two operational models:

- pure Java implementation if the team wants one runtime and maximum throughput
- hybrid model with Java parser worker plus Python FastAPI control plane if the team values faster API development

### Final Recommendation

If the success metric is "parse very large log files in less time," the parser core should be implemented in Java.

Python remains a valid choice for:

- orchestration
- job APIs
- admin tooling
- offline analysis helpers

But for the performance-critical parsing path, Java is the safer long-term foundation.


## Ingestion and Parsing Strategy

### 1. Input Sources

Support these sources:

- Uploaded file via API
- File path on shared disk
- Directory batch mode for multiple rotated logs

Each analysis job should capture:

- application name
- environment
- server or host if known
- source file name
- optional timezone override
- parser profile if supplied manually

For every job, the system must also track:

- total physical lines read
- total logical events reconstructed
- total fully parsed events
- total partially parsed events
- total unclassified events
- dropped events, which should always be `0`
- selected parser plugin and parser plugin version

### 2. Event Reconstruction

This is the most important requirement for Java logs.

For non-Java runtimes, the same event reconstruction stage should support runtime-specific multiline rules, for example:

- Python tracebacks
- C# / .NET exception blocks
- structured JSON events spanning wrapped lines

### Start-of-event detection

A new event usually begins when a line matches one of these:

- timestamp at line start
- app-server prefix plus timestamp
- JSON object with timestamp field
- known log pattern such as `LEVEL logger - message`

### Continuation detection

A line is appended to the previous event if it matches patterns such as:

- `at com.company.Class.method(Class.java:123)`
- `Caused by: ...`
- `Suppressed: ...`
- `... 23 more`
- indented continuation text

### Result

Instead of treating each line as a record, the parser emits one logical event:

```text
2026-04-12 20:30:45,123 ERROR [http-nio-8080-exec-7] c.acme.OrderService - Failed to place order
java.sql.SQLTransientConnectionException: Connection is not available
    at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)
    at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:181)
Caused by: java.net.ConnectException: Connection refused
```

That entire block becomes one event with:

- main message
- exception class
- root cause
- stack trace text
- normalized signature

### Reconstruction Invariants

To guarantee that no statement is ignored:

- every physical input line must belong to exactly one logical event or one ingestion anomaly record
- every logical event must be written to output, even when parsing fails
- if event boundaries are ambiguous, the raw text must still be emitted with `parse_status=unclassified`


## Supported Parser Profiles

Release 1 should explicitly support these parser profiles:

| Profile | Typical Source | Notes |
| :-- | :-- | :-- |
| `log4j_pattern` | Log4j 1.x/2.x text logs | Timestamp, level, thread, logger, message |
| `logback_pattern` | Spring / standalone Java apps | Similar to Log4j with layout variations |
| `jul_pattern` | java.util.logging | Different date and source field layout |
| `tomcat_catalina` | Tomcat / Catalina logs | Includes server-style prefixes |
| `weblogic_server` | WebLogic logs | May have subsystem and server fields |
| `websphere_systemout` | WebSphere SystemOut.log | Legacy enterprise format |
| `generic_timestamped` | Unknown text logs | Best-effort parsing |
| `json_logs` | Structured logs | Parse JSON fields directly |
| `raw_unclassified` | Anything unmatched | Preserve raw evidence without dropping |

Future parser plugins should include:

| Profile | Typical Source | Notes |
| :-- | :-- | :-- |
| `python_logging` | Python stdlib logging | Level, logger, module, thread, message |
| `python_traceback` | Python exception output | Traceback frame extraction |
| `structlog_json` | Python structured logs | JSON-first parsing |
| `serilog_text` | .NET / C# Serilog text output | Namespace, level, message template |
| `serilog_json` | .NET / C# Serilog JSON output | Structured event parsing |
| `nlog_text` | NLog text output | Logger and thread extraction |
| `log4net_pattern` | log4net legacy logs | Similar to Java layout-style parsing |

Parser selection should work in this order:

1. Use manually selected profile if the caller provides one.
2. Otherwise sample the first N events and auto-detect the highest-confidence parser.
3. Fall back to `generic_timestamped`.

Parser profiles should be versioned so detection and extraction rules can evolve safely over time.


## Extensibility Model

### Canonical Event Contract

All parsers, regardless of runtime, must emit the same canonical event shape. Runtime-specific fields should go into dedicated enrichment blocks, not replace the shared contract.

Canonical fields should include:

- event identity and source
- line range
- parse status
- timestamp and level
- logger or category
- thread or execution context
- normalized message and signature
- raw event text
- key-value fields
- runtime metadata

### Runtime Metadata Block

Use a runtime-neutral top-level field such as:

```json
{
  "runtime": {
    "family": "java",
    "framework": "log4j",
    "profile": "log4j_pattern",
    "profile_version": "1.0.0"
  }
}
```

Examples for future runtimes:

- `family=python`, `framework=logging`
- `family=dotnet`, `framework=serilog`
- `family=nodejs`, `framework=pino`

### Plugin Types

The system should support these plugin types:

- parser plugins
- boundary detector plugins
- field extractor plugins
- runtime enricher plugins
- normalization plugins
- CaseRoot signal mappers

### Plugin Responsibilities

Parser plugins should define:

- detection rules
- start-of-event patterns
- continuation patterns
- base field extraction rules
- confidence scoring

Runtime enrichers should define:

- runtime-specific stack or traceback parsing
- code-hint extraction rules
- framework-specific correlation fields
- normalization hints for message templates

### Backward Compatibility Rule

New plugins must not require changes to:

- storage schema for core fields
- CaseRoot base contract
- job lifecycle APIs

They may add optional enrichment sections, but the core event model must remain compatible.


## Field Extraction

### Core Fields Per Event

```json
{
  "event_id": "uuid",
  "job_id": "uuid",
  "sequence": 42,
  "source_file": "server.log",
  "line_start": 455220,
  "line_end": 455225,
  "runtime": {
    "family": "java",
    "framework": "log4j",
    "profile": "log4j_pattern",
    "profile_version": "1.0.0"
  },
  "timestamp": "2026-04-12T20:30:45.123+05:30",
  "timestamp_precision": "millisecond",
  "timezone_source": "parsed",
  "parse_status": "parsed",
  "classification": "application_log",
  "unparsed_reason": null,
  "level": "ERROR",
  "thread": "http-nio-8080-exec-7",
  "logger": "com.acme.order.OrderService",
  "logger_package": "com.acme.order",
  "logger_class": "OrderService",
  "component": "database",
  "host": "app-server-01",
  "application": "order-service",
  "message": "Failed to place order",
  "raw_event": "full multiline event text",
  "stack_trace": "full stack trace if present",
  "exception_class": "java.sql.SQLTransientConnectionException",
  "root_cause_class": "java.net.ConnectException",
  "stack_frames": [
    {
      "class": "com.acme.order.OrderService",
      "method": "placeOrder",
      "file": "OrderService.java",
      "line": 214,
      "in_app": true
    }
  ],
  "normalized_message": "Failed to place order",
  "signature": "ERROR|com.acme.order.OrderService|java.net.ConnectException|Connection refused",
  "code_hints": {
    "packages": ["com.acme.order", "com.acme.dao"],
    "classes": ["OrderService", "OrderDao"],
    "methods": ["placeOrder", "save"],
    "files": ["OrderService.java", "OrderDao.java"]
  },
  "kv_fields": {
    "requestId": "abc123",
    "tenantId": "42"
  },
  "runtime_enrichment": {
    "java": {
      "logger_package": "com.acme.order",
      "top_in_app_frames": [
        {
          "class": "com.acme.order.OrderService",
          "method": "placeOrder",
          "file": "OrderService.java",
          "line": 214
        }
      ]
    }
  }
}
```

### Additional Extraction Targets

- UUIDs
- order IDs or transaction IDs
- HTTP method and path
- HTTP status code
- SQL state and vendor error code
- elapsed time in ms
- hostname, IP, port
- user ID, session ID, correlation ID, trace ID if present
- environment markers such as `UAT`, `PROD`, `DR`
- top in-application stack frames
- package, class, method, and source file names inferred from logger names and stack traces
- Python traceback file, function, module, and line number
- .NET namespace, class, method, and source file hints where available

### Extraction Approach

Use layered extraction:

1. Parser-specific capture groups
2. Generic key-value extraction
3. Targeted regex for common operational fields
4. Runtime-specific exception or traceback analysis
5. Optional runtime enricher plugins

This is more reliable than relying only on a small regex list.

If all extraction layers fail, the system must still emit the record with:

- raw text preserved
- source line range preserved
- `parse_status` set to `partial` or `unclassified`
- best-effort metadata such as file, sequence, and surrounding timestamps


## Normalization and Signature Generation

Counting exact raw lines is not enough for Java application support. Messages often differ only by dynamic values.

### Example

Raw messages:

- `Order 981233 failed for customer 5501 in 4231ms`
- `Order 981240 failed for customer 5509 in 4198ms`

Normalized message:

- `Order <NUM> failed for customer <NUM> in <DURATION_MS>`

### Tokens to normalize

- integers
- UUIDs
- hex values
- timestamps embedded in message text
- IP addresses
- hostnames if needed
- file paths

### Outputs

For every event, produce:

- `raw_message`
- `normalized_message`
- `signature_hash`

This enables:

- top recurring failures
- grouping by error signature
- trend analysis without cardinality explosion


## Timestamp and Sequence Analysis

For each job, compute:

- gap between consecutive parsed events
- max silence window
- burst windows by level
- out-of-order timestamps
- duplicate timestamps
- missing timestamp ratio

### Gap Severity

| Gap Range | Meaning |
| :-- | :-- |
| `0-100 ms` | normal dense logging |
| `100-1000 ms` | short processing delay |
| `1-10 s` | slow transaction or stall |
| `10-60 s` | likely pause or missing logs |
| `60 s+` | significant silence window |

Important note:

For multi-host or merged logs, timestamp gap analysis should also be calculated per source stream if host/thread/source-file fields allow it. A single global gap can be misleading when events from multiple applications are interleaved.


## Summary Outputs

The API should not return one giant in-memory JSON document containing every event for large uploads. Instead, return a job result with summary plus downloadable event artifacts.

### API Summary Response

```json
{
  "job_id": "uuid",
  "status": "completed",
  "metadata": {
    "source_files": 1,
    "total_input_lines": 10422344,
    "total_events": 8234567,
    "total_lines": 10422344,
    "multiline_events": 187442,
    "parser_profile": "log4j_pattern",
    "parse_success_rate": 0.992,
    "partial_parse_rate": 0.006,
    "unclassified_events": 16491,
    "dropped_events": 0,
    "coverage_ratio": 1.0
  },
  "level_counts": {
    "ERROR": 1244,
    "WARN": 8780,
    "INFO": 8123412
  },
  "top_signatures": [
    {
      "signature": "ERROR|OrderDao|java.sql.SQLTransientConnectionException",
      "count": 148,
      "first_seen": "2026-04-12T20:30:45.123+05:30",
      "last_seen": "2026-04-12T21:11:02.011+05:30"
    }
  ],
  "gap_statistics": {
    "max_gap_ms": 312450,
    "p95_gap_ms": 523,
    "p99_gap_ms": 1234
  },
  "artifacts": {
    "events_ndjson_gz": "/artifacts/job-id/events.ndjson.gz",
    "summary_json": "/artifacts/job-id/summary.json",
    "signatures_csv": "/artifacts/job-id/signatures.csv"
  }
}
```

### Generated Artifacts

- `summary.json`
- `events.ndjson.gz`
- `signatures.csv`
- `exceptions.csv`
- `timeline.csv`
- `caseroot_input.json`


## Coverage and Accounting Guarantees

Every job must publish explicit accounting metrics so CaseRoot and operators can verify completeness.

### Required Guarantees

- every input line is accounted for
- every reconstructed event is persisted
- every persisted event appears in either parsed, partial, or unclassified status
- dropped event count is always reported
- the expected value for dropped event count is `0`

### Required Quality Metrics

- `total_input_lines`
- `total_events`
- `parsed_events`
- `partial_events`
- `unclassified_events`
- `dropped_events`
- `coverage_ratio`

If `coverage_ratio < 1.0` or `dropped_events > 0`, the job should be marked with a data-quality warning or failure state.


## CaseRoot Output Contract

CaseRoot should not have to scan millions of raw events before it can relate logs to code. The analyzer should produce a compact, retrieval-friendly bundle that highlights the most relevant evidence.

### Design Principles for CaseRoot Input

- include only deterministic facts and computed aggregates
- preserve references back to raw events and source files
- provide stable IDs and hashes for signatures and exception fingerprints
- separate evidence from inference
- rank important patterns so CaseRoot can prioritize context assembly
- keep runtime-neutral field names for cross-language retrieval

### Required Correlation Signals

Each CaseRoot-oriented record should carry as many of these as possible:

- logger fully qualified name
- package name
- class name
- method name from stack frame
- source filename such as `OrderService.java`
- line number from stack trace
- exception class and root cause class
- request or correlation ID
- thread name
- host, application, environment
- signature hash and normalized message
- runtime family, framework, and parser profile

### Recommended `caseroot_input.json` Shape

```json
{
  "job_id": "uuid",
  "application": "order-service",
  "environment": "prod",
  "generated_at": "2026-04-12T21:30:00+05:30",
  "parser_profile": "log4j_pattern",
  "runtime": {
    "family": "java",
    "framework": "log4j"
  },
  "summary": {
    "total_events": 8234567,
    "error_events": 1244,
    "warn_events": 8780,
    "multiline_events": 187442,
    "parsed_events": 8172076,
    "partial_events": 60000,
    "unclassified_events": 16491,
    "dropped_events": 0,
    "coverage_ratio": 1.0
  },
  "top_incidents": [
    {
      "incident_id": "sig_01",
      "signature_hash": "abc123",
      "normalized_message": "Order <NUM> failed for customer <NUM> in <DURATION_MS>",
      "dominant_level": "ERROR",
      "count": 148,
      "first_seen": "2026-04-12T20:30:45.123+05:30",
      "last_seen": "2026-04-12T21:11:02.011+05:30",
      "exception_class": "java.sql.SQLTransientConnectionException",
      "root_cause_class": "java.net.ConnectException",
      "code_hints": {
        "packages": ["com.acme.order", "com.acme.dao"],
        "classes": ["OrderService", "OrderDao"],
        "methods": ["placeOrder", "save"],
        "files": ["OrderService.java", "OrderDao.java"]
      },
      "top_stack_frames": [
        {
          "class": "com.acme.order.OrderService",
          "method": "placeOrder",
          "file": "OrderService.java",
          "line": 214
        }
      ],
      "sample_event_refs": [
        {
          "event_id": "evt_1001",
          "sequence": 455220,
          "source_file": "server.log"
        }
      ]
    }
  ],
  "timeline_anomalies": [
    {
      "type": "silence_window",
      "start": "2026-04-12T20:31:00+05:30",
      "end": "2026-04-12T20:36:12+05:30",
      "gap_ms": 312450
    }
  ],
  "artifacts": {
    "events_ndjson_gz": "/artifacts/job-id/events.ndjson.gz",
    "summary_json": "/artifacts/job-id/summary.json"
  }
}
```

### Why this Contract Works for CaseRoot

It lets CaseRoot do three things efficiently:

1. Find the most important incidents without reading the entire event stream.
2. Map log evidence to the repository using packages, classes, files, methods, and stack-frame line numbers.
3. Build grounded LLM context that contains evidence, code locations, and ranked failure patterns instead of raw noisy logs.

The analyzer should stop at evidence packaging. CaseRoot should own codebase matching, context assembly, and LLM prompting.


## Storage Design

### Storage Principles

The system should not store raw uploaded log files in the relational database as the default design.

Instead:

- database stores job metadata, summaries, signatures, retention state, and artifact pointers
- raw uploaded log files are stored in object storage or managed file storage
- parsed event artifacts are stored as compressed files
- CaseRoot consumes curated bundles and event references, not database-resident raw logs

This is better because:

- large raw files put unnecessary pressure on database storage and backups
- retention and deletion are easier to implement on object storage
- artifact lifecycle can be controlled independently from metadata lifecycle
- CaseRoot mainly needs evidence access and stable references, not raw BLOB rows in PostgreSQL

### PostgreSQL Tables

Use PostgreSQL for jobs and summaries, not as the only store for raw events.

### `analysis_jobs`

- job metadata
- status
- source info
- parser profile
- runtime family and framework
- parser plugin version
- runtime metrics
- raw artifact location
- parsed artifact location
- CaseRoot bundle location
- retention class
- expires at

### `event_signatures`

- signature hash
- normalized message
- first seen / last seen
- count
- dominant level
- top exception class

### `job_level_counts`

- per-job counts by level

### `job_gap_stats`

- per-job timeline statistics

### `job_exception_stats`

- exception class, root cause class, count

## Event-Level Storage

Store raw structured events in one of these:

- gzipped NDJSON for simplest implementation
- Parquet for better analytical efficiency later

Release 1 recommendation:

- Start with gzipped NDJSON
- Keep the schema stable so Parquet can be added later without changing APIs

### Raw Upload Storage

Store the original uploaded log file outside the database:

- object storage bucket
- encrypted filesystem path
- network file share if object storage is not yet available

For every upload, capture:

- immutable artifact ID
- original filename
- content hash
- size in bytes
- upload timestamp
- retention expiry timestamp

### Retention Strategy

Retention must be configurable by environment, tenant, or job class.

Recommended retention categories:

| Data Type | Default Retention | Configurable | Notes |
| :-- | :-- | :-- | :-- |
| Raw uploaded log file | 15 days | Yes | Short-lived, high sensitivity |
| Parsed event artifact | 15-30 days | Yes | Useful for rehydration and audit |
| `caseroot_input.json` | 30 days | Yes | Needed for support replay and investigation |
| Summary metadata in DB | 90 days or more | Yes | Small and useful for reporting |
| Aggregated signatures and counts | 90 days or more | Yes | Useful for trend analysis |

Suggested defaults:

- `raw_log_retention_days=15`
- `parsed_artifact_retention_days=30`
- `caseroot_bundle_retention_days=30`
- `summary_retention_days=90`

### Retention Policy Rules

- retention must be configurable, not hard-coded
- raw log retention should usually be shorter than summary retention
- production environments may require shorter raw retention because of sensitivity
- incident-tagged jobs may be pinned for longer retention
- legal or compliance hold should override deletion

### Deletion and Expiry Workflow

When a retention window expires:

1. delete raw uploaded file from object or file storage
2. delete parsed event artifacts if they are also expired
3. either delete or anonymize CaseRoot bundles if required
4. keep only lightweight DB metadata if policy allows
5. mark job as expired or purged in the metadata table

### Recommended Database Role in Retention

The database should keep:

- artifact references
- content hashes
- retention deadlines
- purge status
- audit metadata

The database should not be the long-term storage for:

- original raw log file content
- very large event payload blobs
- full artifact copies duplicated from object storage

### CaseRoot Consumption Pattern

CaseRoot should read from:

- `caseroot_input.json`
- referenced parsed event artifacts
- raw uploaded file only when absolutely necessary for audit or reprocessing

That means raw log retention can be short-lived by default, as long as:

- CaseRoot ingestion happens promptly
- curated CaseRoot bundles are produced during job finalization
- event references remain valid during the configured support window


## API Design

### Endpoints

`POST /api/jobs`

- Create analysis job from file upload or file path

`GET /api/jobs/{job_id}`

- Job status and progress

`GET /api/jobs/{job_id}/summary`

- Summary metrics, top signatures, top exceptions, parser confidence

`GET /api/jobs/{job_id}/artifacts`

- Download links for generated files

`GET /api/jobs/{job_id}/retention`

- Current retention policy, expiry timestamps, and purge status

`GET /api/jobs/{job_id}/signatures`

- Paginated recurring-pattern view

`GET /api/jobs/{job_id}/events`

- Optional filtered event retrieval for small ranges or top findings

Avoid returning millions of events in a default API response.


## Processing Flow

1. Create job and persist metadata.
2. Stream file and reconstruct logical events.
3. Parse each event using selected parser profile.
4. Extract fields and stack-trace metadata.
5. Normalize dynamic tokens and generate signature hash.
6. If parsing is incomplete, emit a partial or unclassified event instead of discarding it.
7. Write every structured, partial, or unclassified event to compressed artifact output.
8. Update in-memory rolling aggregates and line-accounting metrics.
9. Flush aggregate batches to PostgreSQL.
10. Build `caseroot_input.json` from ranked incidents, code hints, anomaly summaries, and coverage metrics.
11. Persist artifact locations, retention policy, and expiry timestamps.
12. Finalize summary files and mark job complete.


## Performance Targets

| Metric | Target |
| :-- | :-- |
| File size supported | up to 1 GB per job in release 1 |
| Peak RAM | less than 1.5 GB for standard 1 GB jobs |
| Parse throughput | 30K to 80K events per second depending on format |
| Parser auto-detect time | less than 10 seconds |
| Stack trace reconstruction accuracy | more than 95 percent on supported Java formats |
| Summary API response | less than 3 seconds after job completion |

The original target of returning a complete giant JSON response is not recommended for large files. Artifact-based output is safer and more scalable.


## Failure Handling

The parser must degrade gracefully when logs are messy.

### Error Scenarios

- malformed timestamp
- unknown encoding
- broken multiline event at file boundary
- mixed parser formats in same file
- invalid JSON line
- extremely large stack trace

### Required Behavior

- continue processing where possible
- count parse failures
- emit parse warnings in job summary
- keep raw event text for failed parses
- emit unclassified records for unmatched content
- preserve source line ranges for failed parses
- keep dropped event count at zero
- never drop the whole job because of a few bad records


## Security and Compliance

- support configurable PII masking for emails, phone numbers, account numbers, and tokens
- allow artifact retention policy by environment
- avoid executing any content from logs
- sanitize downloadable filenames and paths
- record audit trail for who uploaded and downloaded analysis artifacts
- encrypt raw uploaded files and parsed artifacts at rest where possible
- support scheduled purge of expired artifacts and bundles


## Recommended Implementation Phases

### Phase 1

- Job API
- streaming file reader
- multiline event reconstruction
- Log4j / Logback / generic timestamped parsers
- canonical runtime-neutral event schema
- summary JSON and NDJSON artifact generation

### Phase 2

- normalization and signature clustering
- exception extraction
- gap analysis
- PostgreSQL summary tables
- plugin registry and versioned parser profiles

### Phase 3

- WebLogic / WebSphere / JUL profiles
- Python logging and traceback plugins
- .NET text and JSON log plugins
- directory batch ingestion
- filtered event query APIs
- retention and masking controls

### Phase 4

- Parquet output option
- comparative analysis across multiple jobs
- UI search and dashboard layer


## Final Recommendation

The current idea is directionally right, but for legacy Java applications the system should be designed around logical event reconstruction, parser profiles, signature normalization, and split storage for summaries versus raw events.

If we build those four capabilities first, the system will be far more useful than a generic line parser and will scale better for real production logs.
