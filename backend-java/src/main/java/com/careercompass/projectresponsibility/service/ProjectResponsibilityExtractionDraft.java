package com.careercompass.projectresponsibility.service;

import java.util.List;

public record ProjectResponsibilityExtractionDraft(
        List<ProjectResponsibilityCandidateDraft> candidates,
        List<ProjectTechnologyFindingDraft> selectedTechnologyFindings,
        List<ProjectTechnologySuggestionDraft> technologySuggestions
) {}
