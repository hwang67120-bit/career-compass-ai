package com.careercompass.common.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        UUID requestId = UUID.randomUUID();
        long startedAt = System.nanoTime();
        RequestCorrelationContext.set(requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId.toString());
        response.setHeader(REQUEST_ID_HEADER, requestId.toString());

        try {
            log.info("http_request_started method={} path={}",
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt);
            log.info("http_request_completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMillis);
            MDC.remove(REQUEST_ID_MDC_KEY);
            RequestCorrelationContext.clear();
        }
    }
}
