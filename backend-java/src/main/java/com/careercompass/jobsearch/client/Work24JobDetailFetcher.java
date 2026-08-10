package com.careercompass.jobsearch.client;

import java.util.regex.Pattern;

import com.careercompass.jobsearch.exception.Work24AccessException;
import com.careercompass.jobsearch.exception.Work24AccessFailure;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String REDACTED = "[REDACTED]";

    /**
     * ai-python의 app/guardrails/contact_info_redaction.py와 같은 정규식이다 — Python은
     * Gemini로 보낼 때만 이 치환을 거치는데(job-posting-extraction.md 7절), AGENTS.md의
     * "Python과 외부 AI에는 개인정보가 제거된 최소 필드만 전달한다" 규칙은 Ollama 호출도
     * 포함하므로 Java가 보내기 전에 한 번 더 제거한다. 담당자 이름 등 연락처 이외 개인정보는
     * 확인된 방법이 없어 이번에는 처리하지 않는다(확인 필요).
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("0\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

    private final RestClient restClient;
    private final int maxSourceTextLength;

    public Work24JobDetailFetcher(
            @Qualifier("work24ApiRestClient") RestClient restClient,
            @Value("${work24.detail.max-source-text-length}") int maxSourceTextLength
    ) {
        this.restClient = restClient;
        this.maxSourceTextLength = maxSourceTextLength;
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
            String redactedText = redactContactInfo(text);
            return redactedText.length() > maxSourceTextLength
                    ? redactedText.substring(0, maxSourceTextLength)
                    : redactedText;
        } catch (Work24AccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new Work24AccessException(Work24AccessFailure.INVALID_RESPONSE, exception);
        }
    }

    private String redactContactInfo(String text) {
        String withoutEmails = EMAIL_PATTERN.matcher(text).replaceAll(REDACTED);
        return PHONE_PATTERN.matcher(withoutEmails).replaceAll(REDACTED);
    }
}
