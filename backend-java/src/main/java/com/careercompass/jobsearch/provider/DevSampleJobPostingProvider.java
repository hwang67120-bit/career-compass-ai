package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.domain.JobPostingCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 승인된 채용 API의 데이터가 개발 직무에 부적합함을 확인한 뒤(2026-08-18, 공공 API가
 * 2008년 행정 공고 반환) 기술 콘텐츠 있는 대표 공고로 전체 파이프라인을 시연하기 위한
 * Provider다(PR #49, docs/claude-dev-sample-provider-handoff.md). 네트워크를 호출하지
 * 않고 API 키도 필요 없다.
 *
 * `dev`(로컬) 또는 `demo`(의도적 시연) 프로필에서, 그리고 `job-search.provider=dev-sample`
 * 설정이 있을 때만 빈이 만들어진다 — 운영 기본(profile 미지정) 또는 기본 provider
 * 설정에서는 절대 선택되지 않는다. 서버 시연은 `SPRING_PROFILES_ACTIVE=prod,demo` +
 * `JOB_SEARCH_PROVIDER=dev-sample`로 명시적으로만 켠다.
 *
 * 이 결과는 실제 채용 시장 데이터가 아니다 — 시장 통계·배포 완료 근거로 쓰지 않는다.
 * 경력·학력처럼 명세에 없는 조건은 지어내지 않고 비워 둔다.
 */
@Component
@Profile({"dev", "demo"})
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
