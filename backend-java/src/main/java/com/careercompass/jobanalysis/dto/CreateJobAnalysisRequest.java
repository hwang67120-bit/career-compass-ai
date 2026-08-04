package com.careercompass.jobanalysis.dto;

import java.util.List;
import java.util.UUID;

public record CreateJobAnalysisRequest(
        UUID userProfileId,
        Integer userProfileVersion,
        List<UUID> projectSourceIds
) {
}
