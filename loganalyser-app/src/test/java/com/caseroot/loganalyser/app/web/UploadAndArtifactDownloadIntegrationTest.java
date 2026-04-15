package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UploadAndArtifactDownloadIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Path basePath;

    @Autowired
    private AnalysisApplicationService analysisApplicationService;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        if (basePath == null) {
            basePath = Files.createTempDirectory("loganalyser-ui-it-");
        }
        registry.add("loganalyser.storage.base-path", () -> basePath.toString());
    }

    @Test
    void uploadsLogAndDownloadsSummaryArtifact() throws Exception {
        String boundary = "----LogAnalyserBoundary";
        String multipartBody = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"upload.log\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + """
                2026-04-15 10:15:30,123 ERROR [main] com.acme.OrderService - Failed order 101
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                2026-04-15 10:15:31,000 INFO [main] com.acme.OrderService - Recovered order 101
                """
                + "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"application\"\r\n\r\n"
                + "order-service\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"environment\"\r\n\r\n"
                + "prod\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"requestedParserProfile\"\r\n\r\n"
                + "log4j_pattern\r\n"
                + "--" + boundary + "--\r\n";

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> uploadResponse = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/jobs/upload"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(202, uploadResponse.statusCode());

        UUID jobId = UUID.fromString(String.valueOf(OBJECT_MAPPER.readValue(uploadResponse.body(), Map.class).get("jobId")));
        AnalysisJob completedJob = awaitCompletion(jobId, Duration.ofSeconds(8));

        assertEquals(AnalysisJobStatus.COMPLETED, completedJob.status());
        assertTrue(completedJob.artifacts().containsKey("summary"));

        HttpResponse<byte[]> artifactResponse = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/jobs/" + jobId + "/artifacts/summary"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, artifactResponse.statusCode());
        assertTrue(String.valueOf(artifactResponse.headers().firstValue("Content-Disposition").orElse("")).contains("summary"));
        assertEquals("application/json", artifactResponse.headers().firstValue("Content-Type").orElse(""));
        assertTrue(artifactResponse.body().length > 0);
    }

    private AnalysisJob awaitCompletion(UUID jobId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            AnalysisJob job = analysisApplicationService.getJob(jobId).orElseThrow();
            if (job.status() == AnalysisJobStatus.COMPLETED || job.status() == AnalysisJobStatus.FAILED) {
                return job;
            }
            Thread.sleep(50);
        }

        throw new AssertionError("Timed out waiting for job completion: " + jobId);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
