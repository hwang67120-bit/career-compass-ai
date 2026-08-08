package com.careercompass.jobsearch.exception;

/**
 * 활성화된 {@link com.careercompass.jobsearch.provider.JobPostingProvider} 빈이
 * 하나도 없을 때 던진다 — 검색 결과 0건으로 조용히 넘어가지 않고 구성 오류로
 * 구분하기 위함이다(PR #49 요구사항).
 */
public class JobPostingProviderNotConfiguredException extends RuntimeException {
}
