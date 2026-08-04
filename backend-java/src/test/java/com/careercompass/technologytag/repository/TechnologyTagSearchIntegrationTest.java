package com.careercompass.technologytag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercompass.technologytag.config.TechnologyTagPolicyProperties;
import com.careercompass.technologytag.dto.TechnologyTagSearchResponse;
import com.careercompass.technologytag.normalization.TechnologyTagNameNormalizer;
import com.careercompass.technologytag.service.TechnologyTagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "github.api.connect-timeout=3s",
        "github.api.read-timeout=8s",
        "python.worker.internal-token=integration-test-token",
        "technology-tag.policy.max-query-length=50",
        "technology-tag.policy.max-search-results=30",
        "work24.api.auth-key=integration-test-key"
})
class TechnologyTagSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TechnologyTagRepository repository;

    @Test
    void searchTechnologyTags_withSeededTags_supportsDefaultAndAliasSearch() {
        TechnologyTagQueryService service = new TechnologyTagQueryService(
                repository,
                new TechnologyTagNameNormalizer(),
                new TechnologyTagPolicyProperties(50, 30)
        );

        TechnologyTagSearchResponse defaultResponse =
                service.searchTechnologyTags("");
        TechnologyTagSearchResponse aliasResponse =
                service.searchTechnologyTags("k8s");

        assertThat(defaultResponse.technologyTags()).hasSize(30);
        assertThat(defaultResponse.technologyTags().getFirst().displayName())
                .isEqualTo("Java");
        assertThat(aliasResponse.technologyTags()).singleElement().satisfies(tag -> {
            assertThat(tag.displayName()).isEqualTo("Kubernetes");
            assertThat(tag.matchedAlias()).isEqualTo("K8s");
        });
    }
}
