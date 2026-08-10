package com.careercompass.jobsearch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.careercompass.jobsearch.config.PublicEmploymentApiProperties;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessException;
import com.careercompass.jobsearch.exception.PublicEmploymentAccessFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PublicEmploymentJobSearchClientTest {

    private static final String BASE_URL =
            "https://apis.data.go.kr/1760000/PblJobService";
    private static final String SERVICE_KEY = "decoded-test-service-key";

    private PublicEmploymentJobSearchClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PublicEmploymentJobSearchClient(
                builder.build(),
                properties(SERVICE_KEY)
        );
    }

    @Test
    void search_withPublicInstitutionFilters_returnsOnlyPublicInstitutionPosting() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/getList")))
                .andExpect(queryParam("serviceKey", SERVICE_KEY))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("numOfRows", "5"))
                .andExpect(queryParam("Pblanc_ty", "e08"))
                .andExpect(queryParam("Instt_se", "g03"))
                .andExpect(queryParam("Kwrd", "%EB%B0%B1%EC%97%94%EB%93%9C%20%EA%B0%9C%EB%B0%9C%EC%9E%90"))
                .andExpect(queryParam("Sort_order", "1"))
                .andRespond(withSuccess(listResponseXml(), MediaType.APPLICATION_XML));

        List<JobPostingCandidate> candidates = client.search("백엔드 개발자", 5);

        assertThat(candidates).containsExactly(new JobPostingCandidate(
                "210354",
                "한국공공기관",
                "백엔드 개발자 채용",
                "11",
                null,
                null
        ));
        server.verify();
    }

    @Test
    void fetchSourceText_withContactInformation_redactsBeforeReturning() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/getItem")))
                .andExpect(queryParam("serviceKey", SERVICE_KEY))
                .andExpect(queryParam("idx", "210354"))
                .andRespond(withSuccess(detailResponseXml(), MediaType.APPLICATION_XML));

        String sourceText = client.fetchSourceText("210354");

        assertThat(sourceText)
                .contains("백엔드 개발자 채용", "Spring Boot 서비스 개발")
                .doesNotContain("hong@example.com", "010-1234-5678", "홍길동", "삭제대상")
                .contains("담당자: [REDACTED]", "[REDACTED]");
        server.verify();
    }

    @Test
    void search_withRateLimitResultCode_throwsRateLimited() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/getList")))
                .andRespond(withSuccess("""
                        <response>
                          <header><resultCode>22</resultCode><resultMsg>LIMITED</resultMsg></header>
                        </response>
                        """, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.search("백엔드", 5))
                .isInstanceOf(PublicEmploymentAccessException.class)
                .satisfies(exception -> assertThat(
                        ((PublicEmploymentAccessException) exception).getFailure())
                        .isEqualTo(PublicEmploymentAccessFailure.RATE_LIMITED));
    }

    @Test
    void search_withoutServiceKey_throwsNotConfigured() {
        PublicEmploymentJobSearchClient unconfiguredClient = new PublicEmploymentJobSearchClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                properties("")
        );

        assertThatThrownBy(() -> unconfiguredClient.search("백엔드", 5))
                .isInstanceOf(PublicEmploymentAccessException.class)
                .satisfies(exception -> assertThat(
                        ((PublicEmploymentAccessException) exception).getFailure())
                        .isEqualTo(PublicEmploymentAccessFailure.NOT_CONFIGURED));
    }

    private PublicEmploymentApiProperties properties(String serviceKey) {
        return new PublicEmploymentApiProperties(
                URI.create(BASE_URL),
                serviceKey,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                8_000,
                "1"
        );
    }

    private String listResponseXml() {
        return """
                <response>
                  <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE</resultMsg></header>
                  <body>
                    <totalCount>2</totalCount>
                    <items>
                      <item>
                        <idx>210354</idx><title>백엔드 개발자 채용</title>
                        <type01>g03</type01><type02>e08</type02>
                        <insttname>한국공공기관</insttname><areacode>11</areacode>
                      </item>
                      <item>
                        <idx>210355</idx><title>국가공무원 채용</title>
                        <type01>g01</type01><type02>e01</type02>
                        <insttname>중앙부처</insttname><areacode>11</areacode>
                      </item>
                    </items>
                  </body>
                </response>
                """;
    }

    private String detailResponseXml() {
        return """
                <response>
                  <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE</resultMsg></header>
                  <body>
                    <item>
                      <idx>210354</idx><title>백엔드 개발자 채용</title>
                      <type01>g03</type01><type02>e08</type02>
                      <contents><![CDATA[
                        <p>Spring Boot 서비스 개발</p>
                        <p>담당자: 홍길동 / hong@example.com / 010-1234-5678</p>
                        <script>삭제대상</script>
                      ]]></contents>
                    </item>
                  </body>
                </response>
                """;
    }
}
