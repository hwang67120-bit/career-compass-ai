package com.careercompass.jobanalysis.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Python이 실제로 구조화 추출한 채용공고 한 건의 결과다. 이번 범위에서는 Java가
 * extraction·modelExecutions 내용을 직접 조작하지 않고 저장·조회에만 쓰므로, 구조화된
 * 컬럼이 아니라 JSON 원문 그대로(TEXT)를 저장한다.
 */
@Entity
@Table(name = "job_analysis_posting")
public class JobAnalysisPosting {

    @Id
    private UUID id;

    @Column(name = "job_analysis_id", nullable = false)
    private UUID jobAnalysisId;

    @Column(name = "provider_posting_id", nullable = false, length = 100)
    private String providerPostingId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "original_job_title", length = 300)
    private String originalJobTitle;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "extraction", nullable = false)
    private String extractionJson;

    @Column(name = "model_executions", nullable = false)
    private String modelExecutionsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobAnalysisPosting() {
    }

    private JobAnalysisPosting(
            UUID id,
            UUID jobAnalysisId,
            String providerPostingId,
            String companyName,
            String originalJobTitle,
            String sourceUrl,
            String extractionJson,
            String modelExecutionsJson,
            Instant createdAt
    ) {
        this.id = id;
        this.jobAnalysisId = jobAnalysisId;
        this.providerPostingId = providerPostingId;
        this.companyName = companyName;
        this.originalJobTitle = originalJobTitle;
        this.sourceUrl = sourceUrl;
        this.extractionJson = extractionJson;
        this.modelExecutionsJson = modelExecutionsJson;
        this.createdAt = createdAt;
    }

    public static JobAnalysisPosting create(
            UUID id,
            UUID jobAnalysisId,
            String providerPostingId,
            String companyName,
            String originalJobTitle,
            String sourceUrl,
            String extractionJson,
            String modelExecutionsJson,
            Instant createdAt
    ) {
        return new JobAnalysisPosting(
                id,
                jobAnalysisId,
                providerPostingId,
                companyName,
                originalJobTitle,
                sourceUrl,
                extractionJson,
                modelExecutionsJson,
                createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobAnalysisId() {
        return jobAnalysisId;
    }

    public String getProviderPostingId() {
        return providerPostingId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getOriginalJobTitle() {
        return originalJobTitle;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getExtractionJson() {
        return extractionJson;
    }

    public String getModelExecutionsJson() {
        return modelExecutionsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
