package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.domain.JobPostingCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 승인된 채용 API가 없는 동안 Java–PostgreSQL–Python–LLM–브라우저 파이프라인을
 * 검증하기 위한 개발 전용 Provider다(PR #49, docs/claude-dev-sample-provider-handoff.md).
 * 네트워크를 호출하지 않고 API 키도 필요 없다. `dev` 프로필과 `job-search.provider=
 * dev-sample` 설정이 모두 있어야 빈이 만들어진다 — 운영·기본 프로필에서는 절대 선택되지
 * 않는다.
 *
 * 이 결과는 실제 채용 시장 데이터가 아니다 — 시장 통계·배포 완료 근거로 쓰지 않는다.
 * 경력·학력처럼 명세에 없는 조건은 지어내지 않고 비워 둔다.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "job-search", name = "provider", havingValue = "dev-sample")
public class DevSampleJobPostingProvider implements JobPostingProvider {

    private static final String PROVIDER_NAME = "DEV_SAMPLE";

    private static final JobPostingCandidate SAMPLE_CANDIDATE = new JobPostingCandidate(
            "dev-sample-0001",
            "샘플 회사",
            "백엔드 개발자",
            "서울",
            "https://example.invalid/dev-sample/0001",
            null
    );

    private static final String SAMPLE_SOURCE_TEXT = """
            백엔드 개발자 채용

            담당업무
            - REST API 개발
            - 데이터베이스 설계
            - 외부 AI 서비스 연동

            자격요건
            - Java, Spring Boot 활용 경험
            - PostgreSQL 등 RDB 활용 경험
            - Git을 이용한 협업 경험

            우대사항
            - Docker 활용 경험
            - AWS 등 클라우드 인프라 경험
            """;

    @Override
    public List<JobPostingCandidate> search(String keyword, int display) {
        return List.of(SAMPLE_CANDIDATE);
    }

    @Override
    public String fetchSourceText(JobPostingCandidate candidate) {
        return SAMPLE_SOURCE_TEXT;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
