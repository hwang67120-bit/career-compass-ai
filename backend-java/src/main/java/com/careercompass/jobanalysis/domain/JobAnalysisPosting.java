package com.careercompass.jobanalysis.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_analysis_posting")
public class JobAnalysisPosting {

    @Id
    private UUID id;

    @Column(name = "job_analysis_id", nullable = false)
    private UUID jobAnalysisId;

    @Column(name = "provider_posting_id", nullable = false, length = 100)
    private String providerPostingId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "job_posting_id", nullable = false)
    private UUID jobPostingId;

    @Column(name = "extraction_task_id", nullable = false)
    private UUID extractionTaskId;

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

    @Column(name = "comparison")
    private String comparisonJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobAnalysisPosting() {
    }

    private JobAnalysisPosting(
            UUID id,
            UUID jobAnalysisId,
            String providerPostingId,
            String provider,
            UUID jobPostingId,
            UUID extractionTaskId,
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
        this.provider = provider;
        this.jobPostingId = jobPostingId;
        this.extractionTaskId = extractionTaskId;
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
            String provider,
            UUID jobPostingId,
            UUID extractionTaskId,
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
                provider,
                jobPostingId,
                extractionTaskId,
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

    public String getProvider() {
        return provider;
    }

    public UUID getJobPostingId() {
        return jobPostingId;
    }

    public UUID getExtractionTaskId() {
        return extractionTaskId;
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

    public String getComparisonJson() {
        return comparisonJson;
    }

    public void recordComparison(String comparisonJson) {
        if (comparisonJson == null || comparisonJson.isBlank()) {
            throw new IllegalArgumentException("COMPARISON_JSON_REQUIRED");
        }
        this.comparisonJson = comparisonJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
