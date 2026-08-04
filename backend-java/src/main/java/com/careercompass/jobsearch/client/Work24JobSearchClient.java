package com.careercompass.jobsearch.client;

import java.util.List;

import com.careercompass.jobsearch.config.Work24ApiProperties;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.Work24AccessException;
import com.careercompass.jobsearch.exception.Work24AccessFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 고용24 공식 Open API(목록 조회, callTp=L)를 호출한다. 이 응답에는 메타데이터만 있고
 * 실제 공고 본문은 없다 — 본문은 {@link Work24JobDetailFetcher}가 별도로 가져온다.
 */
@Component
public class Work24JobSearchClient {

    private static final String LIST_API_PATH = "/cm/openApi/call/wk/callOpenApiSvcInfo210L01.do";

    private final RestClient restClient;
    private final Work24ApiProperties properties;
    private final XmlMapper xmlMapper;

    public Work24JobSearchClient(
            @Qualifier("work24ApiRestClient") RestClient restClient,
            Work24ApiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.xmlMapper = new XmlMapper();
    }

    /**
     * 기능: 키워드로 채용정보 목록을 검색한다.
     * 반환 값: 본문 텍스트가 비어 있는 채용공고 후보 목록(최대 display건)을 반환한다.
     */
    public List<JobPostingCandidate> search(String keyword, int display) {
        String rawXml = fetchRawXml(keyword, display);
        return parse(rawXml);
    }

    private String fetchRawXml(String keyword, int display) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(LIST_API_PATH)
                            .queryParam("authKey", properties.authKey())
                            .queryParam("callTp", "L")
                            .queryParam("returnType", "XML")
                            .queryParam("startPage", 1)
                            .queryParam("display", display)
                            .queryParam("keyword", keyword)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, response) -> {
                        throw new Work24AccessException(Work24AccessFailure.REDIRECTED);
                    })
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new Work24AccessException(Work24AccessFailure.RATE_LIMITED);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new Work24AccessException(Work24AccessFailure.SERVICE_UNAVAILABLE);
                    })
                    .body(String.class);
        } catch (Work24AccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new Work24AccessException(Work24AccessFailure.SERVICE_UNAVAILABLE, exception);
        }
    }

    private List<JobPostingCandidate> parse(String rawXml) {
        try {
            Work24SearchApiResponse response = xmlMapper.readValue(rawXml, Work24SearchApiResponse.class);
            List<Work24WantedItem> items = response.wanted() != null ? response.wanted() : List.of();
            return items.stream()
                    .map(item -> new JobPostingCandidate(
                            item.wantedAuthNo(),
                            item.company(),
                            item.title(),
                            item.region(),
                            item.wantedInfoUrl(),
                            null
                    ))
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new Work24AccessException(Work24AccessFailure.INVALID_RESPONSE, exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Work24SearchApiResponse(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "wanted")
            List<Work24WantedItem> wanted
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Work24WantedItem(
            String wantedAuthNo,
            String company,
            String title,
            String region,
            String wantedInfoUrl
    ) {
    }
}
