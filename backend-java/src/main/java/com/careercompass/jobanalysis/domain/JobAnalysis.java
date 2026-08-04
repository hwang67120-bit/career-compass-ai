package com.careercompass.jobanalysis.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "job_analysis")
public class JobAnalysis {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_profile_id", nullable = false)
    private UUID userProfileId;

    @Column(name = "user_profile_version", nullable = false)
    private int userProfileVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private JobAnalysisStatus analysisStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    private JobAnalysisStep currentStep;

    @Column(name = "completed_units", nullable = false)
    private int completedUnits;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OrderColumn(name = "selection_order")
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_analysis_project_source",
            joinColumns = @JoinColumn(name = "job_analysis_id"),
            inverseJoinColumns = @JoinColumn(name = "project_source_id")
    )
    private List<ProjectSource> projectSources = new ArrayList<>();

    protected JobAnalysis() {
    }

    private JobAnalysis(
            UUID id,
            UUID userId,
            UUID userProfileId,
            int userProfileVersion,
            JobAnalysisStatus analysisStatus,
            JobAnalysisStep currentStep,
            int completedUnits,
            int totalUnits,
            Instant queuedAt,
            Instant updatedAt,
            List<ProjectSource> projectSources
    ) {
        this.id = id;
        this.userId = userId;
        this.userProfileId = userProfileId;
        this.userProfileVersion = userProfileVersion;
        this.analysisStatus = analysisStatus;
        this.currentStep = currentStep;
        this.completedUnits = completedUnits;
        this.totalUnits = totalUnits;
        this.queuedAt = queuedAt;
        this.updatedAt = updatedAt;
        this.projectSources.addAll(projectSources);
    }

    public static JobAnalysis createQueued(
            UUID id,
            UUID userId,
            UUID userProfileId,
            int userProfileVersion,
            List<ProjectSource> projectSources,
            Instant queuedAt
    ) {
        return new JobAnalysis(
                id,
                userId,
                userProfileId,
                userProfileVersion,
                JobAnalysisStatus.QUEUED,
                JobAnalysisStep.VALIDATING_INPUTS,
                0,
                0,
                queuedAt,
                queuedAt,
                projectSources
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getUserProfileId() {
        return userProfileId;
    }

    public int getUserProfileVersion() {
        return userProfileVersion;
    }

    public JobAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public JobAnalysisStep getCurrentStep() {
        return currentStep;
    }

    public int getCompletedUnits() {
        return completedUnits;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ProjectSource> getProjectSources() {
        return List.copyOf(projectSources);
    }

    /**
     * 기능: 워커가 이 작업을 선점했음을 표시한다(짧은 트랜잭션 안에서만 호출).
     */
    public void markRunning(Instant now) {
        this.analysisStatus = JobAnalysisStatus.RUNNING;
        this.updatedAt = now;
    }

    /**
     * 기능: 현재 진행 단계를 갱신한다.
     */
    public void advanceStep(JobAnalysisStep step, Instant now) {
        this.currentStep = step;
        this.updatedAt = now;
    }

    /**
     * 기능: 검색·추출 단계까지 일부라도 성공했을 때 부분 완료로 표시한다. 조건판정·유사도
     * 비교(COMPARING_EVIDENCE 이후)는 아직 구현이 없어 여기서 멈춘다(확인 필요, 2026-08-04
     * 임시 작업 — docs/current-work.md 참고).
     */
    public void markPartiallyCompleted(Instant now) {
        this.analysisStatus = JobAnalysisStatus.PARTIALLY_COMPLETED;
        this.currentStep = JobAnalysisStep.EXTRACTING_JOB_POSTINGS;
        this.updatedAt = now;
    }

    /**
     * 기능: 검색·추출이 전부 실패했을 때 실패로 표시한다.
     */
    public void markFailed(Instant now) {
        this.analysisStatus = JobAnalysisStatus.FAILED;
        this.updatedAt = now;
    }
}
