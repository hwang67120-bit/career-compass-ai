package com.careercompass.projectsource.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubRestClientSnapshotTest {

    private MockRestServiceServer server;
    private GitHubRestClient client;
    private GitHubRepositoryCoordinates coordinates;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubRestClient(builder.build());
        coordinates = GitHubRepositoryCoordinates.createFromUrl(
                "https://github.com/octocat/Hello-World");
    }

    @Test
    void fetchTree_withRecursiveResponse_returnsOnlyBlobEntriesAndTruncatedFlag() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World/git/trees/abc?recursive=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "tree": [
                            {"path":"src","type":"tree","sha":"tree-sha"},
                            {"path":"src/App.java","type":"blob","sha":"blob-sha","size":12}
                          ],
                          "truncated": true
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubRepositoryTree tree = client.fetchTree(coordinates, "abc");

        assertThat(tree.truncated()).isTrue();
        assertThat(tree.entries()).containsExactly(
                new GitHubRepositoryTree.Entry(
                        "src/App.java", "blob", "blob-sha", 12));
        server.verify();
    }

    @Test
    void fetchBlob_withBase64Response_returnsContentMetadata() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World/git/blobs/blob-sha"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "content": "aGVsbG8=",
                          "encoding": "base64",
                          "size": 5
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubRepositoryBlob blob = client.fetchBlob(coordinates, "blob-sha");

        assertThat(blob).isEqualTo(
                new GitHubRepositoryBlob("aGVsbG8=", "base64", 5));
        server.verify();
    }
}
