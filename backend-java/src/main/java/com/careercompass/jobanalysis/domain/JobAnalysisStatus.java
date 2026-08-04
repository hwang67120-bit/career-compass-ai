package com.careercompass.jobanalysis.domain;

public enum JobAnalysisStatus {
    QUEUED,
    RUNNING,
    CANCELLATION_REQUESTED,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED
}
