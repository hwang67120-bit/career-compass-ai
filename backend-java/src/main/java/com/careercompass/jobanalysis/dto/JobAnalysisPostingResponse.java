package com.careercompass.jobanalysis.dto;

/**
 * extraction·modelExecutions는 Jackson 특정 타입이 아니라 순수 Map·List(Object)로
 * 담는다 — 이 컨트롤러의 JSON 직렬화는 스프링이 자동 구성한 Jackson을 쓰는데, 이
 * 프로젝트에서 그게 클라이언트 쪽 Jackson과 다른 버전이라(PythonJobPostingExtractionEnvelope
 * 참고) Jackson 트리 타입을 섞어 쓰면 안 된다.
 */
public record JobAnalysisPostingResponse(
        String providerPostingId,
        String provider,
        String companyName,
        String originalJobTitle,
        String sourceUrl,
        Object extraction,
        Object modelExecutions
) {
}
