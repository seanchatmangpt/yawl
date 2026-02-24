package org.yawlfoundation.yawl.ggen.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * REST API servlet for process conversion requests.
 * Handles HTTP POST for job submission and GET for status polling.
 * Pure Jakarta Servlet API, no Spring Boot dependency.
 */
public class ProcessConversionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final InMemoryJobQueue jobQueue = new InMemoryJobQueue();
    private static final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        if (pathInfo != null && pathInfo.equals("/api/v1/process/convert")) {
            handleConvertRequest(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            writeErrorResponse(response, 404, "Endpoint not found");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");

        if (pathInfo != null && pathInfo.equals("/api/v1/health")) {
            handleHealthCheck(response);
        } else if (pathInfo != null && pathInfo.startsWith("/api/v1/process/jobs/")) {
            String jobId = pathInfo.substring("/api/v1/process/jobs/".length());
            handleJobStatusRequest(response, jobId);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeErrorResponse(response, 404, "Endpoint not found");
        }
    }

    /**
     * Handle POST /api/v1/process/convert
     * Parse request body, validate, submit to queue, return 202 Accepted with QUEUED response.
     */
    private void handleConvertRequest(HttpServletRequest request,
                                      HttpServletResponse response)
            throws IOException {
        try {
            String requestBody = readRequestBody(request);
            ProcessConversionRequest conversionRequest =
                ProcessConversionRequest.fromJson(requestBody);

            conversionRequest.validate();

            String jobId = jobQueue.submit(conversionRequest);

            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            response.setContentType("application/json");

            ProcessConversionResponse conversionResponse =
                ProcessConversionResponse.queued(jobId);

            try (PrintWriter writer = response.getWriter()) {
                writer.print(conversionResponse.toJson());
                writer.flush();
            }

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            writeErrorResponse(response, 400, e.getMessage());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            writeErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handle GET /api/v1/process/jobs/{jobId}
     * Look up job status, return current ProcessConversionResponse.
     */
    private void handleJobStatusRequest(HttpServletResponse response, String jobId)
            throws IOException {
        try {
            ConversionJob job = jobQueue.get(jobId);

            if (job == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeErrorResponse(response, 404, "Job not found: " + jobId);
                return;
            }

            ProcessConversionResponse apiResponse = new ProcessConversionResponse();
            apiResponse.setJobId(job.getJobId());
            apiResponse.setStatus(job.getStatus().toString());
            apiResponse.setCreatedAt(job.getCreatedAt());

            if (job.getStatus() == ConversionJob.Status.COMPLETE) {
                apiResponse.setOutputContent(job.getResult());
                apiResponse.setCompletedAt(System.currentTimeMillis());
                response.setStatus(HttpServletResponse.SC_OK);
            } else if (job.getStatus() == ConversionJob.Status.FAILED) {
                apiResponse.setErrorMessage(job.getError());
                apiResponse.setCompletedAt(System.currentTimeMillis());
                response.setStatus(HttpServletResponse.SC_OK);
            } else if (job.getStatus() == ConversionJob.Status.PROCESSING) {
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }

            try (PrintWriter writer = response.getWriter()) {
                writer.print(apiResponse.toJson());
                writer.flush();
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handle GET /api/v1/health
     * Return health status.
     */
    private void handleHealthCheck(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        JsonObject json = new JsonObject();
        json.addProperty("status", "UP");
        json.addProperty("version", "1.0.0");

        try (PrintWriter writer = response.getWriter()) {
            writer.print(json.toString());
            writer.flush();
        }
    }

    /**
     * Read the full HTTP request body as string.
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Write a standardized error response.
     */
    private void writeErrorResponse(HttpServletResponse response, int code, String message)
            throws IOException {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("error", code);
        errorJson.addProperty("message", message);

        try (PrintWriter writer = response.getWriter()) {
            writer.print(errorJson.toString());
            writer.flush();
        }
    }
}
