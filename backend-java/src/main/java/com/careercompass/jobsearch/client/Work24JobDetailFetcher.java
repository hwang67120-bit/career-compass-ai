package com.careercompass.jobsearch.client;

import com.careercompass.jobsearch.exception.Work24AccessException;
import com.careercompass.jobsearch.exception.Work24AccessFailure;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 고용24 채용정보 상세 페이지(공식 사이트의 사람용 HTML, 인증키 불필요)에서 본문 텍스트를
 * 가져온다. 목록 API(callTp=L)에는 본문이 없어서 이 페이지가 유일한 출처다.
 *
 * 이 페이지의 정확한 DOM 구조(탭 패널 등)는 표본 1건으로만 확인했다 — 특정 영역만 선택하는
 * 선택자에 의존하면 구조가 다른 공고에서 조용히 빈 텍스트를 반환할 위험이 있다. 대신 페이지
 * 전체에서 스크립트·스타일·내비게이션만 제거한 텍스트를 넘기고, 실제로 근거가 되는 부분만
 * 골라내는 건 Python LLM의 근거 기반 필터링(evidence 검증)에 맡긴다(확인 필요 — 실제
 * 여러 공고로 검증 안 됨).
 */
@Component
public class Work24JobDetailFetcher {

    private static final String DETAIL_PAGE_PATH = "/wk/a/b/1500/empDetailAuthView.do";
    private static final int MAX_SOURCE_TEXT_LENGTH = 8000;

    private final RestClient restClient;

    public Work24JobDetailFetcher(@Qualifier("work24ApiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 기능: 채용공고 상세 페이지에서 본문으로 쓸 텍스트를 가져온다.
     * 반환 값: 스크립트·스타일·내비게이션을 제거한 페이지 텍스트(최대 길이로 자름)를 반환한다.
     */
    public String fetchSourceText(String wantedAuthNo) {
        String html = fetchRawHtml(wantedAuthNo);
        return extractText(html);
    }

    private String fetchRawHtml(String wantedAuthNo) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DETAIL_PAGE_PATH)
                            .queryParam("wantedAuthNo", wantedAuthNo)
                            .queryParam("infoTypeCd", "CJK")
                            .queryParam("infoTypeGroup", "tb_workinfogubun")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, response) -> {
                        throw new Work24AccessException(Work24AccessFailure.REDIRECTED);
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

    private String extractText(String html) {
        try {
            Document document = Jsoup.parse(html);
            document.select("script, style, nav, header, footer, noscript").remove();
            String text = document.body() != null ? document.body().text() : "";
            if (text.isBlank()) {
                throw new Work24AccessException(Work24AccessFailure.INVALID_RESPONSE);
            }
            return text.length() > MAX_SOURCE_TEXT_LENGTH
                    ? text.substring(0, MAX_SOURCE_TEXT_LENGTH)
                    : text;
        } catch (Work24AccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new Work24AccessException(Work24AccessFailure.INVALID_RESPONSE, exception);
        }
    }
}
