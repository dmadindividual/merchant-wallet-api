package com.threeline.merchantwallet.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threeline.merchantwallet.common.context.RequestContext;
import com.threeline.merchantwallet.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(ErrorCode code, String detail, List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.httpStatus(), detail);
        problem.setType(URI.create("urn:merchant-wallet:error:" + code.name().toLowerCase().replace('_', '-')));
        problem.setTitle(code.defaultMessage());
        problem.setProperty("code", code.name());
        problem.setProperty("requestId", RequestContext.requestId());
        problem.setProperty("timestamp", Instant.now().toString());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public static void write(HttpServletResponse response, ObjectMapper mapper,
                             ErrorCode code, String detail) throws IOException {
        ProblemDetail problem = create(code, detail, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }

        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body);
    }
}
