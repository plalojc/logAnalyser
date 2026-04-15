const metricsGrid = document.getElementById("metricsGrid");
const jobsTableBody = document.getElementById("jobsTableBody");
const comparisonResult = document.getElementById("comparisonResult");
const eventQueryForm = document.getElementById("eventQueryForm");
const eventQueryResult = document.getElementById("eventQueryResult");
const refreshButton = document.getElementById("refreshButton");
const compareButton = document.getElementById("compareButton");
const pathJobForm = document.getElementById("pathJobForm");
const uploadJobForm = document.getElementById("uploadJobForm");
const jobSubmissionResult = document.getElementById("jobSubmissionResult");
const jobDetail = document.getElementById("jobDetail");
const uploadModeButton = document.getElementById("uploadModeButton");
const pathModeButton = document.getElementById("pathModeButton");
const uploadModePanel = document.getElementById("uploadModePanel");
const pathModePanel = document.getElementById("pathModePanel");

let selectedJobId = null;

async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const body = await response.text();
        throw new Error(body || `Request failed: ${response.status}`);
    }
    return response.json();
}

function metricCard(label, value) {
    return `
        <article class="metric-card">
            <div class="metric-label">${label}</div>
            <div class="metric-value">${value}</div>
        </article>
    `;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function formatJson(value) {
    return escapeHtml(JSON.stringify(value, null, 2));
}

function renderOverview(overview) {
    metricsGrid.innerHTML = [
        metricCard("Total Jobs", overview.totalJobs),
        metricCard("Completed", overview.completedJobs),
        metricCard("Running", overview.runningJobs),
        metricCard("Failed", overview.failedJobs),
        metricCard("Accepted", overview.acceptedJobs),
        metricCard("Events", overview.totalEventsAcrossCompletedJobs)
    ].join("");
}

function renderJobs(jobs) {
    if (!jobs.length) {
        jobsTableBody.innerHTML = `<tr><td colspan="7" class="empty">No jobs available yet.</td></tr>`;
        return;
    }

    jobsTableBody.innerHTML = jobs.map((job) => `
        <tr>
            <td><input type="checkbox" class="job-select" value="${job.jobId}"></td>
            <td><span class="pill">${job.jobId.slice(0, 8)}</span></td>
            <td><button type="button" class="compact-button view-job-button" data-job-id="${job.jobId}">Open</button></td>
            <td>${escapeHtml(job.application || "-")}</td>
            <td>${escapeHtml(job.runtimeDescriptor ? job.runtimeDescriptor.family : "-")}</td>
            <td>${escapeHtml(job.status)}</td>
            <td>${job.summary ? job.summary.counts.totalEvents : "-"}</td>
        </tr>
    `).join("");
}

function renderComparison(result) {
    const signatures = result.commonSignatures.length
        ? result.commonSignatures.map((item) => `<li>${escapeHtml(item.normalizedMessage)} <strong>(${item.totalCount})</strong></li>`).join("")
        : `<li class="empty">No shared signatures across the selected jobs.</li>`;

    const exceptions = result.commonExceptions.length
        ? result.commonExceptions.map((item) => `<li>${escapeHtml(item.exceptionClass || "Unknown")} <strong>(${item.totalCount})</strong></li>`).join("")
        : `<li class="empty">No shared exceptions across the selected jobs.</li>`;

    comparisonResult.innerHTML = `
        <div class="subsection">
            <h3>Selected Jobs</h3>
            <p>${result.jobIds.map((jobId) => escapeHtml(jobId)).join(", ")}</p>
        </div>
        <div class="subsection">
            <h3>Shared Signatures</h3>
            <ul>${signatures}</ul>
        </div>
        <div class="subsection">
            <h3>Shared Exceptions</h3>
            <ul>${exceptions}</ul>
        </div>
        <div class="subsection">
            <h3>Aggregated Levels</h3>
            <pre class="code-block">${formatJson(result.aggregatedLevelCounts)}</pre>
        </div>
    `;
}

function renderSubmissionResult(job, message) {
    const failureBlock = job.failureReason
        ? `<div class="subsection"><strong>Reason:</strong> ${escapeHtml(job.failureReason)}</div>`
        : "";
    jobSubmissionResult.classList.remove("empty");
    jobSubmissionResult.innerHTML = `
        <div class="status-line">
            <span class="status-badge ${String(job.status || "").toLowerCase()}">${escapeHtml(job.status)}</span>
            <span class="pill">${escapeHtml(job.jobId)}</span>
        </div>
        <div>${escapeHtml(message)}</div>
        ${failureBlock}
    `;
}

function renderMessage(message, isError = false) {
    jobSubmissionResult.classList.remove("empty");
    jobSubmissionResult.innerHTML = `<div class="${isError ? "empty" : ""}">${escapeHtml(message)}</div>`;
}

function setSubmissionMode(mode) {
    const uploadActive = mode === "upload";
    uploadModeButton.classList.toggle("active", uploadActive);
    pathModeButton.classList.toggle("active", !uploadActive);
    uploadModePanel.classList.toggle("active", uploadActive);
    pathModePanel.classList.toggle("active", !uploadActive);
}

function artifactLink(jobId, key) {
    return `/api/v1/jobs/${jobId}/artifacts/${encodeURIComponent(key)}`;
}

function renderJobDetail(job) {
    if (!job) {
        jobDetail.classList.add("empty");
        jobDetail.textContent = "Select a job from the table or submit a new one.";
        return;
    }

    const summary = job.summary || {};
    const counts = summary.counts || {};
    const artifactLinks = Object.keys(job.artifacts || {}).map((key) => `
        <a class="artifact-link" href="${artifactLink(job.jobId, key)}" target="_blank" rel="noopener noreferrer">
            Download ${escapeHtml(key)}
        </a>
    `).join("");

    const warnings = summary.warnings && summary.warnings.length
        ? `<ul>${summary.warnings.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`
        : `<p class="empty">No summary warnings recorded.</p>`;

    jobDetail.classList.remove("empty");
    jobDetail.innerHTML = `
        <div class="status-line">
            <span class="status-badge ${String(job.status || "").toLowerCase()}">${escapeHtml(job.status)}</span>
            <span class="pill">${escapeHtml(job.jobId)}</span>
            <span class="pill">${escapeHtml(job.selectedParserPlugin || "parser-pending")}</span>
        </div>
        <div class="subsection">
            <h3>Context</h3>
            <pre class="code-block">${formatJson({
                sourceType: job.sourceType,
                sourceLocation: job.sourceLocation,
                originalFileName: job.originalFileName,
                application: job.application,
                environment: job.environment,
                requestedParserProfile: job.requestedParserProfile,
                runtimeDescriptor: job.runtimeDescriptor,
                failureReason: job.failureReason
            })}</pre>
        </div>
        <div class="subsection">
            <h3>Counts</h3>
            <pre class="code-block">${formatJson({
                totalEvents: counts.totalEvents || 0,
                parsedEvents: counts.parsedEvents || 0,
                partialEvents: counts.partialEvents || 0,
                unclassifiedEvents: counts.unclassifiedEvents || 0,
                multilineEvents: counts.multilineEvents || 0
            })}</pre>
        </div>
        <div class="subsection">
            <h3>Top Signatures</h3>
            <pre class="code-block">${formatJson(summary.topSignatures || [])}</pre>
        </div>
        <div class="subsection">
            <h3>Top Exceptions</h3>
            <pre class="code-block">${formatJson(summary.topExceptions || [])}</pre>
        </div>
        <div class="subsection">
            <h3>CaseRoot Bundle</h3>
            <pre class="code-block">${formatJson(job.caseRootBundle || {})}</pre>
        </div>
        <div class="subsection">
            <h3>Warnings</h3>
            ${warnings}
        </div>
        <div class="subsection">
            <h3>Artifacts</h3>
            <div class="artifact-links">${artifactLinks || `<span class="empty">No artifacts available yet.</span>`}</div>
        </div>
        <div class="inline-actions">
            <button type="button" class="ghost-button" id="prefillEventQuery">Search This Job</button>
            <button type="button" class="ghost-button" id="refreshSelectedJob">Refresh This Job</button>
        </div>
    `;

    const prefillButton = document.getElementById("prefillEventQuery");
    const refreshButton = document.getElementById("refreshSelectedJob");
    if (prefillButton) {
        prefillButton.addEventListener("click", () => {
            document.getElementById("queryJobId").value = job.jobId;
            document.getElementById("queryContains").focus();
        });
    }
    if (refreshButton) {
        refreshButton.addEventListener("click", () => loadJobDetail(job.jobId));
    }
}

async function refreshDashboard() {
    const [overview, jobs] = await Promise.all([
        fetchJson("/api/v1/dashboard/overview"),
        fetchJson("/api/v1/jobs")
    ]);
    renderOverview(overview);
    renderJobs(jobs);
}

async function loadJobDetail(jobId) {
    selectedJobId = jobId;
    const job = await fetchJson(`/api/v1/jobs/${jobId}`);
    renderJobDetail(job);
    return job;
}

function sleep(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function waitForTerminalState(jobId, maxAttempts = 45) {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        const job = await loadJobDetail(jobId);
        if (job.status === "COMPLETED" || job.status === "FAILED") {
            return job;
        }
        await sleep(1500);
    }
    return loadJobDetail(jobId);
}

pathJobForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    renderMessage("Submitting path-based analysis job...");
    const payload = {
        sourceType: document.getElementById("sourceType").value,
        sourceLocation: document.getElementById("sourceLocation").value.trim(),
        originalFileName: document.getElementById("originalFileName").value.trim(),
        application: document.getElementById("pathApplication").value.trim(),
        environment: document.getElementById("pathEnvironment").value.trim()
    };

    try {
        const submittedJob = await fetchJson("/api/v1/jobs", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        renderSubmissionResult(submittedJob, "Job accepted. Polling until parsing finishes.");
        await refreshDashboard();
        const finalJob = await waitForTerminalState(submittedJob.jobId);
        renderSubmissionResult(finalJob, `Job finished with status ${finalJob.status}.`);
        await refreshDashboard();
    } catch (error) {
        renderMessage(error.message, true);
    }
});

uploadJobForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    renderMessage("Uploading log and creating analysis job...");
    const file = document.getElementById("uploadFile").files[0];
    if (!file) {
        renderMessage("Choose a log file before uploading.", true);
        return;
    }

    const formData = new FormData();
    formData.append("file", file);

    const application = document.getElementById("uploadApplication").value.trim();
    const environment = document.getElementById("uploadEnvironment").value.trim();
    if (application) {
        formData.append("application", application);
    }
    if (environment) {
        formData.append("environment", environment);
    }

    try {
        const submittedJob = await fetchJson("/api/v1/jobs/upload", {
            method: "POST",
            body: formData
        });
        renderSubmissionResult(submittedJob, "Upload received. Polling until parsing finishes.");
        uploadJobForm.reset();
        await refreshDashboard();
        const finalJob = await waitForTerminalState(submittedJob.jobId);
        renderSubmissionResult(finalJob, `Job finished with status ${finalJob.status}.`);
        await refreshDashboard();
    } catch (error) {
        renderMessage(error.message, true);
    }
});

uploadModeButton.addEventListener("click", () => setSubmissionMode("upload"));
pathModeButton.addEventListener("click", () => setSubmissionMode("path"));

jobsTableBody.addEventListener("click", async (event) => {
    const button = event.target.closest(".view-job-button");
    if (!button) {
        return;
    }
    try {
        await loadJobDetail(button.dataset.jobId);
    } catch (error) {
        jobDetail.classList.remove("empty");
        jobDetail.textContent = error.message;
    }
});

compareButton.addEventListener("click", async () => {
    const selectedIds = Array.from(document.querySelectorAll(".job-select:checked")).map((input) => input.value);
    if (selectedIds.length < 2) {
        comparisonResult.innerHTML = `<p class="empty">Select at least two jobs to compare.</p>`;
        return;
    }

    try {
        const result = await fetchJson("/api/v1/compare", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ jobIds: selectedIds })
        });
        renderComparison(result);
    } catch (error) {
        comparisonResult.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
    }
});

eventQueryForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const params = new URLSearchParams();
    const jobId = document.getElementById("queryJobId").value.trim();

    [["level", "queryLevel"], ["sourceFile", "querySourceFile"], ["contains", "queryContains"], ["limit", "queryLimit"]]
        .forEach(([name, id]) => {
            const value = document.getElementById(id).value.trim();
            if (value) {
                params.set(name, value);
            }
        });

    if (!jobId) {
        eventQueryResult.textContent = "A job id is required.";
        return;
    }

    try {
        const result = await fetchJson(`/api/v1/jobs/${jobId}/events?${params.toString()}`);
        eventQueryResult.textContent = JSON.stringify(result, null, 2);
    } catch (error) {
        eventQueryResult.textContent = error.message;
    }
});

refreshButton.addEventListener("click", async () => {
    try {
        await refreshDashboard();
        if (selectedJobId) {
            await loadJobDetail(selectedJobId);
        }
    } catch (error) {
        comparisonResult.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
    }
});

refreshDashboard()
    .then(async () => {
        setSubmissionMode("upload");
        const jobs = await fetchJson("/api/v1/jobs");
        if (jobs.length) {
            await loadJobDetail(jobs[0].jobId);
        }
    })
    .catch((error) => {
        comparisonResult.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
    });
