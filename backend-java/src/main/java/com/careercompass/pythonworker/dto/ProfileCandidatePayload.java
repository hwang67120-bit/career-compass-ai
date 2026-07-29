package com.careercompass.pythonworker.dto;

import java.util.List;

public record ProfileCandidatePayload(
        List<CandidateSkill> skills,
        List<CandidateWorkExperience> workExperiences,
        List<CandidateProject> projects,
        List<CandidateEducation> education,
        List<CandidateCertification> certifications,
        List<CandidateEvidence> evidence
) {

    public record CandidateSkill(
            String rawName,
            String normalizedName,
            List<String> evidenceIds
    ) {
    }

    public record CandidateWorkExperience(
            String companyName,
            String jobTitle,
            String rawPeriod,
            String startedOn,
            String endedOn,
            List<String> responsibilities,
            List<String> evidenceIds
    ) {
    }

    public record CandidateProject(
            String projectName,
            String role,
            String summary,
            List<CandidateSkill> technologies,
            List<String> evidenceIds
    ) {
    }

    public record CandidateEducation(
            String institutionName,
            String major,
            String degree,
            String rawPeriod,
            List<String> evidenceIds
    ) {
    }

    public record CandidateCertification(
            String name,
            String issuer,
            String acquiredOn,
            List<String> evidenceIds
    ) {
    }

    public record CandidateEvidence(
            String evidenceId,
            String fieldPath,
            String value,
            String sourceText,
            int pageNumber
    ) {
    }
}
