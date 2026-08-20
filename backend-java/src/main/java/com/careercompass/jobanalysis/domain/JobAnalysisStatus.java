package com.careercompass.jobanalysis.domain;

public enum JobAnalysisStatus {
    QUEUED,
    RUNNING,
    AWAITING_USER_CONFIRMATION,
    CANCELLATION_REQUESTED,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED
}
