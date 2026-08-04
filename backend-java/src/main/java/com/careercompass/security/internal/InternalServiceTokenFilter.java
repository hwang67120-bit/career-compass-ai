package com.careercompass.security.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final byte[] expectedToken;
    private final InternalServiceErrorResponseWriter errorResponseWriter;

    public InternalServiceTokenFilter(
            PythonWorkerProperties properties,
            InternalServiceErrorResponseWriter errorResponseWriter
    ) {
        this.expectedToken = properties.internalToken()
                .getBytes(StandardCharsets.UTF_8);
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!isValidToken(providedToken)) {
            errorResponseWriter.writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValidToken(String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken,
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
