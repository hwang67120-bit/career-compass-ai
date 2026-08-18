package com.careercompass.projectresponsibility.service;

import java.util.List;
import java.util.UUID;

public record ProjectTechnologySuggestionDraft(UUID technologyTagId, List<String> sourceEvidenceIds) {}
