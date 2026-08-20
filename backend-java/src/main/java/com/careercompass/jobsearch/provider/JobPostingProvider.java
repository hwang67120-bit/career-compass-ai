package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.domain.JobPostingCandidate;

/**
 * 채용공고 검색·본문 조회의 공통 경계다. 인사혁신처 공공취업정보 API와 개발 전용
 * 샘플 데이터가 이 경계 뒤에서 서로 다른 구현으로 존재한다(PR #49,
 * docs/claude-dev-sample-provider-handoff.md). {@link #providerName()}은 저장·응답·
 * 화면에서 어떤 출처인지 구분하는 데 쓴다.
 */
public interface JobPostingProvider {

    List<JobPostingCandidate> search(String keyword, int display);

    String fetchSourceText(JobPostingCandidate candidate);

    String providerName();
}
