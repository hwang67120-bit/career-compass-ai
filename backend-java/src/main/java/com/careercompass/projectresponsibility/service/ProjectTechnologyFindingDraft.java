package com.careercompass.projectresponsibility.service;

import java.util.List;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.ProjectTechnologyFindingStatus;

public record ProjectTechnologyFindingDraft(
        UUID technologyTagId,
        ProjectTechnologyFindingStatus findingStatus,
        List<String> sourceEvidenceIds
) {}
