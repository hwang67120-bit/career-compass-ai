package com.careercompass.projectsource.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateGitHubProjectSourceRequest(
        @NotBlank(message = "GitHub 저장소 주소를 입력해 주세요.")
        String repositoryUrl
) {
}
