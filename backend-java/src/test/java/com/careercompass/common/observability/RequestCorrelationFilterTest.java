package com.careercompass.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.careercompass.common.web.ApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @Test
    void doFilter_withApiResponse_usesSameRequestIdForHeaderResponseAndContext() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC)
        );
        AtomicReference<UUID> responseRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                responseRequestId.set(responseFactory.success("ok").requestId()));

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isEqualTo(responseRequestId.get().toString());
        assertThat(RequestCorrelationContext.current()).isEmpty();
    }
}
