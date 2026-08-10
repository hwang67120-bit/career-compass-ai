package com.careercompass.jobsearch.client;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.careercompass.jobsearch.config.PublicEmploymentApiProperties;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PublicEmploymentJobSearchClient {

    private static final Logger log = LoggerFactory.getLogger(PublicEmploymentJobSearchClient.class);
    private static final String LIST_API_PATH = "/getList";
    private static final String DETAIL_API_PATH = "/getItem";
    private static final String SUCCESS_CODE = "00";
    private static final String PUBLIC_INSTITUTION_TYPE = "g03";
    private static final String PUBLIC_INSTITUTION_POSTING_TYPE = "e08";
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("0\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
    private static final Pattern LABELED_NAME_PATTERN = Pattern.compile(
            "((?:담당자(?:명)?|성명|이름)\\s*[:：]?\\s*)([가-힣]{2,4})(?=\\s|[,/]|$)"
    );

    private final RestClient restClient;
    private final PublicEmploymentApiProperties properties;
    private final XmlMapper xmlMapper;

    public PublicEmploymentJobSearchClient(
            @Qualifier("publicEmploymentApiRestClient") RestClient restClient,
            PublicEmploymentApiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.xmlMapper = new XmlMapper();
    }

    public List<JobPostingCandidate> search(String keyword, int display) {
        if (display <= 0) {
            throw new IllegalArgumentException("검색 결과 수는 양수여야 합니다.");
        }
        PublicEmploymentResponse response = parse(fetchListXml(keyword, display));
        validateSuccess(response);
        PublicEmploymentBody body = requireBody(response);
        List<PublicEmploymentItem> items = body.items() != null && body.items().item() != null
                ? body.items().item()
                : List.of();
        if (body.totalCount() != null && body.totalCount() > 0 && items.isEmpty()) {
            throw invalidResponse();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(this::isPublicInstitutionPosting)
                .map(this::toCandidate)
                .limit(display)
                .toList();
    }

    public String fetchSourceText(String providerPostingId) {
        if (providerPostingId == null || providerPostingId.isBlank()) {
            throw new IllegalArgumentException("공공취업정보 일련번호가 필요합니다.");
        }
        PublicEmploymentResponse response = parse(fetchDetailXml(providerPostingId));
        validateSuccess(response);
        PublicEmploymentItem item = requireBody(response).item();
        if (item == null
                || !providerPostingId.equals(item.idx())
                || !isPublicInstitutionPosting(item)
                || item.contents() == null
                || item.contents().isBlank()) {
            throw invalidResponse();
        }
        String sourceText = sanitizeSourceText(item.title(), item.contents());
        if (sourceText.isBlank()) {
            throw invalidResponse();
        }
        return sourceText.length() > properties.maxSourceTextLength()
                ? sourceText.substring(0, properties.maxSourceTextLength())
                : sourceText;
    }

    private byte[] fetchListXml(String keyword, int display) {
        return fetchXml(LIST_API_PATH, uriBuilder -> uriBuilder
                .path(LIST_API_PATH)
                .queryParam("serviceKey", requireServiceKey())
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", display)
                .queryParam("Pblanc_ty", PUBLIC_INSTITUTION_POSTING_TYPE)
                .queryParam("Instt_se", PUBLIC_INSTITUTION_TYPE)
                .queryParam("Kwrd", keyword)
                .queryParam("Sort_order", properties.sortOrder())
                .build());
    }

    private byte[] fetchDetailXml(String providerPostingId) {
        return fetchXml(DETAIL_API_PATH, uriBuilder -> uriBuilder
                .path(DETAIL_API_PATH)
                .queryParam("serviceKey", requireServiceKey())
                .queryParam("idx", providerPostingId)
                .build());
    }

    private byte[] fetchXml(
            String apiPath,
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction
    ) {
        try {
            return restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, response) -> {
                        throw new PublicEmploymentAccessException(
                                PublicEmploymentAccessFailure.SERVICE_UNAVAILABLE);
                    })
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            (request, response) -> {
                                throw new PublicEmploymentAccessException(
                                        PublicEmploymentAccessFailure.ACCESS_DENIED);
                            })
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new PublicEmploymentAccessException(
                                PublicEmploymentAccessFailure.RATE_LIMITED);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new PublicEmploymentAccessException(
                                PublicEmploymentAccessFailure.SERVICE_UNAVAILABLE);
                    })
                    .body(byte[].class);
        } catch (PublicEmploymentAccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("public_employment_request_failed apiPath={}", apiPath);
            throw new PublicEmploymentAccessException(
                    PublicEmploymentAccessFailure.SERVICE_UNAVAILABLE,
                    exception
            );
        }
    }

    private String requireServiceKey() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new PublicEmploymentAccessException(
                    PublicEmploymentAccessFailure.NOT_CONFIGURED);
        }
        return properties.serviceKey();
    }

    private PublicEmploymentResponse parse(byte[] rawXml) {
        if (rawXml == null || rawXml.length == 0) {
            throw invalidResponse();
        }
        try {
            return xmlMapper.readValue(rawXml, PublicEmploymentResponse.class);
        } catch (IOException exception) {
            throw new PublicEmploymentAccessException(
                    PublicEmploymentAccessFailure.INVALID_RESPONSE,
                    exception
            );
        }
    }

    private void validateSuccess(PublicEmploymentResponse response) {
        if (response == null || response.header() == null
                || response.header().resultCode() == null) {
            throw invalidResponse();
        }
        String resultCode = response.header().resultCode();
        if (SUCCESS_CODE.equals(resultCode)) {
            return;
        }
        log.warn("public_employment_response_rejected resultCode={}", resultCode);
        throw new PublicEmploymentAccessException(mapFailure(resultCode));
    }

    private PublicEmploymentAccessFailure mapFailure(String resultCode) {
        return switch (resultCode) {
            case "20", "30", "31" -> PublicEmploymentAccessFailure.ACCESS_DENIED;
            case "22", "23" -> PublicEmploymentAccessFailure.RATE_LIMITED;
            case "01", "04", "05", "12" -> PublicEmploymentAccessFailure.SERVICE_UNAVAILABLE;
            default -> PublicEmploymentAccessFailure.INVALID_RESPONSE;
        };
    }

    private PublicEmploymentBody requireBody(PublicEmploymentResponse response) {
        if (response.body() == null) {
            throw invalidResponse();
        }
        return response.body();
    }

    private boolean isPublicInstitutionPosting(PublicEmploymentItem item) {
        return PUBLIC_INSTITUTION_TYPE.equals(item.type01())
                && PUBLIC_INSTITUTION_POSTING_TYPE.equals(item.type02());
    }

    private JobPostingCandidate toCandidate(PublicEmploymentItem item) {
        if (item.idx() == null || item.idx().isBlank()
                || item.insttname() == null || item.insttname().isBlank()
                || item.title() == null || item.title().isBlank()) {
            throw invalidResponse();
        }
        return new JobPostingCandidate(
                item.idx(),
                item.insttname(),
                item.title(),
                item.areacode(),
                null,
                null
        );
    }

    private String sanitizeSourceText(String title, String contents) {
        Document document = Jsoup.parse(contents);
        document.select("script, style, nav, header, footer, noscript").remove();
        String bodyText = document.body() != null ? document.body().wholeText() : "";
        String textWithTitle = title != null && !title.isBlank()
                ? title + System.lineSeparator() + bodyText
                : bodyText;
        String withoutEmails = EMAIL_PATTERN.matcher(textWithTitle).replaceAll(REDACTED);
        String withoutPhones = PHONE_PATTERN.matcher(withoutEmails).replaceAll(REDACTED);
        return LABELED_NAME_PATTERN.matcher(withoutPhones).replaceAll("$1" + REDACTED).trim();
    }

    private PublicEmploymentAccessException invalidResponse() {
        return new PublicEmploymentAccessException(
                PublicEmploymentAccessFailure.INVALID_RESPONSE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicEmploymentResponse(
            PublicEmploymentHeader header,
            PublicEmploymentBody body
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicEmploymentHeader(String resultCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicEmploymentBody(
            Integer totalCount,
            PublicEmploymentItems items,
            PublicEmploymentItem item
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicEmploymentItems(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            List<PublicEmploymentItem> item
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PublicEmploymentItem(
            String idx,
            String title,
            String type01,
            String type02,
            String contents,
            String insttname,
            String areacode
    ) {
    }
}
