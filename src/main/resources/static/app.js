const metricsGrid = document.getElementById("metricsGrid");
const jobsTableBody = document.getElementById("jobsTableBody");
const comparisonResult = document.getElementById("comparisonResult");
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

function createFocusController(config) {
    const group = document.getElementById(config.groupId);
    const summary = document.getElementById(config.summaryId);
    const checkboxes = Array.from(group.querySelectorAll("input[type='checkbox']"));
    const allCheckbox = checkboxes.find((checkbox) => checkbox.dataset.focusValue === "ALL");
    const specificCheckboxes = checkboxes.filter((checkbox) => checkbox.dataset.focusValue !== "ALL");

    function selectedSpecificValues() {
        return specificCheckboxes
            .filter((checkbox) => checkbox.checked)
            .map((checkbox) => checkbox.dataset.focusValue);
    }

    function renderSummary() {
        if (allCheckbox.checked) {
            summary.textContent = "All log categories";
        } else {
            const specificValues = selectedSpecificValues();
            if (!specificValues.length) {
                summary.textContent = "Choose one or more categories";
            } else {
                summary.textContent = specificValues
                    .map((value) => value.charAt(0) + value.slice(1).toLowerCase())
                    .join(", ");
            }
        }
    }

    function setAllSelections(checked) {
        allCheckbox.checked = checked;
        specificCheckboxes.forEach((checkbox) => {
            checkbox.checked = checked;
        });
        renderSummary();
    }

    function syncAllCheckbox() {
        const checkedCount = selectedSpecificValues().length;
        if (checkedCount === specificCheckboxes.length) {
            allCheckbox.checked = true;
        } else {
            allCheckbox.checked = false;
        }
        renderSummary();
    }

    allCheckbox.addEventListener("change", () => {
        if (allCheckbox.checked) {
            setAllSelections(true);
            return;
        }

        if (selectedSpecificValues().length === specificCheckboxes.length) {
            specificCheckboxes.forEach((checkbox) => {
                checkbox.checked = false;
            });
        }
        renderSummary();
    });

    specificCheckboxes.forEach((checkbox) => {
        checkbox.addEventListener("change", () => {
            syncAllCheckbox();
        });
    });

    setAllSelections(true);

    return {
        values() {
            if (allCheckbox.checked) {
                return ["ALL"];
            }
            return selectedSpecificValues();
        }
    };
}

const uploadFocusController = createFocusController({
    groupId: "uploadFocusGroup",
    summaryId: "uploadFocusSummary"
});

const pathFocusController = createFocusController({
    groupId: "pathFocusGroup",
    summaryId: "pathFocusSummary"
});

function getSelectedFocusValues(controller) {
    const values = controller.values();
    if (values.includes("ALL")) {
        return ["ALL"];
    }
    return values;
}

function clearOnlyFileInput(inputId) {
    const input = document.getElementById(inputId);
    if (input) {
        input.value = "";
    }
}

function renderOverview(overview) {
    metricsGrid.innerHTML = [
        metricCard("Total Jobs", overview.totalJobs),
        metricCard("Completed", overview.completedJobs),
        metricCard("Running", overview.runningJobs),
        metricCard("Failed", overview.failedJobs),
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
        ? result.commonSignatures.map((item) => `<li>${escapeHtml(item.packageName)}${item.representativeMessage ? ` - ${escapeHtml(item.representativeMessage)}` : ""} <strong>(${item.totalCount})</strong></li>`).join("")
        : `<li class="empty">No shared package groups across the selected jobs.</li>`;

    const exceptions = result.commonExceptions.length
        ? result.commonExceptions.map((item) => `<li>${escapeHtml(item.exceptionClass || "Unknown")} <strong>(${item.totalCount})</strong></li>`).join("")
        : `<li class="empty">No shared exceptions across the selected jobs.</li>`;

    comparisonResult.innerHTML = `
        <div class="subsection">
            <h3>Selected Jobs</h3>
            <p>${result.jobIds.map((jobId) => escapeHtml(jobId)).join(", ")}</p>
        </div>
        <div class="subsection">
            <h3>Shared Package Groups</h3>
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
    jobSubmissionResult.innerHTML = `<div class="${isError ? "feedback-error" : "feedback-info"}">${escapeHtml(message)}</div>`;
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

    const topSignature = (summary.topSignatures || [])[0];
    const topPackage = topSignature
        ? {
            packageName: topSignature.packageName,
            count: topSignature.count,
            exceptionCount: topSignature.exceptionCount,
            largeGapCount: topSignature.largeGapCount,
            sampleMessages: topSignature.sampleMessages
        }
        : null;

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
                analysisOptions: job.analysisOptions,
                runtimeDescriptor: job.runtimeDescriptor,
                failureReason: job.failureReason
            })}</pre>
        </div>
        <div class="subsection">
            <h3>Summary</h3>
            <pre class="code-block">${formatJson({
                totalEvents: counts.totalEvents || 0,
                focusedEvents: counts.focusedEvents || 0,
                parsedEvents: counts.parsedEvents || 0,
                partialEvents: counts.partialEvents || 0,
                unclassifiedEvents: counts.unclassifiedEvents || 0,
                multilineEvents: counts.multilineEvents || 0,
                levelCounts: summary.levelCounts || {},
                topExceptions: summary.topExceptions || []
            })}</pre>
        </div>
        <div class="subsection">
            <h3>Primary Package Group</h3>
            <pre class="code-block">${formatJson(topPackage || {})}</pre>
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
            <button type="button" class="ghost-button" id="refreshSelectedJob">Refresh This Job</button>
        </div>
    `;

    const refreshButton = document.getElementById("refreshSelectedJob");
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
        environment: document.getElementById("pathEnvironment").value.trim(),
        largeGapHighlightThresholdMinutes: Number(document.getElementById("pathGapThresholdMinutes").value || "1"),
        analysisFocus: getSelectedFocusValues(pathFocusController)
    };

    if (!payload.analysisFocus.length) {
        renderMessage("Select at least one analysis focus category or enable all log categories.", true);
        return;
    }

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
    formData.append("largeGapHighlightThresholdMinutes", document.getElementById("uploadGapThresholdMinutes").value || "1");
    const focusValues = getSelectedFocusValues(uploadFocusController);
    if (!focusValues.length) {
        renderMessage("Select at least one analysis focus category or enable all log categories.", true);
        return;
    }
    focusValues.forEach((value) => formData.append("analysisFocus", value));

    try {
        const submittedJob = await fetchJson("/api/v1/jobs/upload", {
            method: "POST",
            body: formData
        });
        renderSubmissionResult(submittedJob, "Upload received. Polling until parsing finishes.");
        clearOnlyFileInput("uploadFile");
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
