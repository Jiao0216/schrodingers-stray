package com.catrescue.api.controller;

import com.catrescue.api.service.AssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "File too large (max 15MB per request for assessment image).");
        pd.setTitle("Payload Too Large");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(AssessmentService.NotFoundException.class)
    public ProblemDetail notFound(AssessmentService.NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Not Found");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail illegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Upstream Service Failure");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        pd.setTitle("Data Integrity Violation");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.name());
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail noResource(NoResourceFoundException ex) {
        String path = ex.getResourcePath();
        if (path != null && path.endsWith("favicon.ico")) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "favicon not configured");
            pd.setTitle("Not Found");
            pd.setType(URI.create("about:blank"));
            return pd;
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No static resource " + path + ".");
        pd.setTitle("Not Found");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    /** JDBC / JPA failures (wrong password, packet too large, connection lost, etc.). */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail dataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        Throwable root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String raw = root.getMessage() != null ? root.getMessage() : ex.getClass().getSimpleName();
        if (raw.contains("Packet for query is too large") || raw.contains("max_allowed_packet")) {
            String detail =
                    "MySQL rejected the INSERT because the packet (often image_bytes) exceeds max_allowed_packet. "
                            + "On the server run: SET GLOBAL max_allowed_packet = 67108864; (or set in my.cnf), restart mysqld, "
                            + "or set ASSESSMENT_PERSIST_IMAGES=false to skip storing full image bytes in DB.";
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
            pd.setTitle("MySQL packet limit");
            pd.setType(URI.create("about:blank"));
            return pd;
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, raw);
        pd.setTitle("Database Error");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    /** Catch-all so clients receive RFC7807 JSON with a detail message instead of an empty 500 body. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail serverError(Exception ex) {
        log.error("Unhandled server error", ex);
        Throwable root = deepestCause(ex);
        String msg = root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage()
                : ex.getClass().getSimpleName();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    private static Throwable deepestCause(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
