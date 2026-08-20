package com.careercompass.projectsource.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.careercompass.projectsource.client.GitHubRepositoryBlob;
import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRepositoryTree;
import com.careercompass.projectsource.config.RepositorySnapshotPolicyProperties;
import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.exception.RepositorySnapshotException;
import com.careercompass.projectsource.exception.RepositorySnapshotFailure;
import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionRequest;
import org.springframework.stereotype.Service;

@Service
public class RepositorySnapshotService {

    private static final String FILE_SIZE_LIMIT_EXCEEDED = "FILE_SIZE_LIMIT_EXCEEDED";
    private static final String FILE_COUNT_LIMIT_EXCEEDED = "FILE_COUNT_LIMIT_EXCEEDED";
    private static final String TOTAL_TEXT_LIMIT_EXCEEDED = "TOTAL_TEXT_LIMIT_EXCEEDED";

    private final GitHubRepositoryGateway repositoryGateway;
    private final RepositorySnapshotPolicyProperties snapshotPolicy;
    private final ProjectResponsibilityExtractionPolicyProperties extractionPolicy;
    private final Clock clock;

    public RepositorySnapshotService(
            GitHubRepositoryGateway repositoryGateway,
            RepositorySnapshotPolicyProperties snapshotPolicy,
            ProjectResponsibilityExtractionPolicyProperties extractionPolicy,
            Clock clock
    ) {
        this.repositoryGateway = repositoryGateway;
        this.snapshotPolicy = snapshotPolicy;
        this.extractionPolicy = extractionPolicy;
        this.clock = clock;
    }

    /**
     * 기능: 등록된 고정 커밋에서 허용된 최소 텍스트 파일만 읽어 Python 요청 스냅숏을 만든다.
     * 반환 값: 요청 스냅숏, 근거 식별자별 최소 자료와 제한으로 제외한 파일 목록을 반환한다.
     */
    public PreparedRepositorySnapshot prepare(
            ProjectSource projectSource,
            int selectedTechnologyCount
    ) {
        GitHubRepositoryCoordinates coordinates =
                GitHubRepositoryCoordinates.createFromUrl(projectSource.getRepositoryUrl());
        GitHubRepositoryTree tree = repositoryGateway.fetchTree(
                coordinates, projectSource.getCommitSha());
        if (tree.truncated()) {
            throw new RepositorySnapshotException(RepositorySnapshotFailure.TREE_TRUNCATED);
        }

        List<PreparedRepositorySnapshot.Exclusion> exclusions = new ArrayList<>();
        List<ClassifiedEntry> selected = selectEntries(
                tree.entries(), selectedTechnologyCount, exclusions);
        List<PythonProjectResponsibilityExtractionRequest.ReadmeEvidence> readmes =
                new ArrayList<>();
        List<PythonProjectResponsibilityExtractionRequest.FileEvidence> files =
                new ArrayList<>();
        Map<String, PreparedRepositorySnapshot.Evidence> evidenceById =
                new LinkedHashMap<>();
        int totalTextCodePoints = 0;

        for (ClassifiedEntry selectedEntry : selected) {
            GitHubRepositoryTree.Entry entry = selectedEntry.entry();
            String text = fetchText(coordinates, entry);
            text = limitCodePoints(text, extractionPolicy.maxEvidenceTextCodePoints());
            int textCodePoints = codePointCount(text);
            if (text.isBlank()) {
                continue;
            }
            if (totalTextCodePoints + textCodePoints
                    > extractionPolicy.maxTotalTextCodePoints()) {
                exclusions.add(new PreparedRepositorySnapshot.Exclusion(
                        entry.path(), TOTAL_TEXT_LIMIT_EXCEEDED));
                continue;
            }
            totalTextCodePoints += textCodePoints;
            String evidenceId = evidenceId(projectSource.getCommitSha(), entry.path());
            evidenceById.put(evidenceId, new PreparedRepositorySnapshot.Evidence(
                    evidenceId, entry.path(), text));
            if (selectedEntry.type() == EvidenceType.README) {
                readmes.add(new PythonProjectResponsibilityExtractionRequest.ReadmeEvidence(
                        evidenceId, entry.path(), text));
            } else {
                files.add(new PythonProjectResponsibilityExtractionRequest.FileEvidence(
                        evidenceId, entry.path(), selectedEntry.type().requestName(), text));
            }
        }

        if (evidenceById.isEmpty()) {
            throw new RepositorySnapshotException(
                    RepositorySnapshotFailure.NO_ELIGIBLE_EVIDENCE);
        }
        PythonProjectResponsibilityExtractionRequest.RepositorySnapshot requestSnapshot =
                new PythonProjectResponsibilityExtractionRequest.RepositorySnapshot(
                        projectSource.getRepositoryUrl(),
                        Instant.now(clock),
                        projectSource.getCommitSha(),
                        null,
                        List.copyOf(readmes),
                        List.copyOf(files));
        return new PreparedRepositorySnapshot(
                requestSnapshot,
                Map.copyOf(evidenceById),
                List.copyOf(exclusions));
    }

    private List<ClassifiedEntry> selectEntries(
            List<GitHubRepositoryTree.Entry> entries,
            int selectedTechnologyCount,
            List<PreparedRepositorySnapshot.Exclusion> exclusions
    ) {
        List<ClassifiedEntry> candidates = entries.stream()
                .filter(entry -> "blob".equals(entry.type()))
                .filter(entry -> isSafePath(entry.path()))
                .map(entry -> classify(entry).map(type -> new ClassifiedEntry(entry, type)))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingInt((ClassifiedEntry entry) -> entry.type().priority())
                        .thenComparing(entry -> entry.entry().path()))
                .toList();

        Map<EvidenceType, Integer> selectedTypeCounts = new HashMap<>();
        int sourceAndTestLimit = Math.multiplyExact(
                selectedTechnologyCount,
                snapshotPolicy.maxSourceAndTestFilesPerTechnology());
        int sourceAndTestCount = 0;
        List<ClassifiedEntry> selected = new ArrayList<>();
        for (ClassifiedEntry candidate : candidates) {
            GitHubRepositoryTree.Entry entry = candidate.entry();
            if (entry.size() > snapshotPolicy.maxRemoteFileBytes()) {
                exclusions.add(new PreparedRepositorySnapshot.Exclusion(
                        entry.path(), FILE_SIZE_LIMIT_EXCEEDED));
                continue;
            }
            int typeCount = selectedTypeCounts.getOrDefault(candidate.type(), 0);
            boolean typeLimitReached = switch (candidate.type()) {
                case README -> typeCount >= extractionPolicy.maxReadmes();
                case MANIFEST -> typeCount >= extractionPolicy.maxManifests();
                case CONFIGURATION -> typeCount >= extractionPolicy.maxConfigurations();
                case SOURCE, TEST -> sourceAndTestCount >= sourceAndTestLimit;
            };
            if (typeLimitReached
                    || selected.size() >= extractionPolicy.maxEvidenceItems()) {
                exclusions.add(new PreparedRepositorySnapshot.Exclusion(
                        entry.path(), FILE_COUNT_LIMIT_EXCEEDED));
                continue;
            }
            selected.add(candidate);
            selectedTypeCounts.put(candidate.type(), typeCount + 1);
            if (candidate.type() == EvidenceType.SOURCE
                    || candidate.type() == EvidenceType.TEST) {
                sourceAndTestCount++;
            }
        }
        return selected;
    }

    private String fetchText(
            GitHubRepositoryCoordinates coordinates,
            GitHubRepositoryTree.Entry entry
    ) {
        GitHubRepositoryBlob blob = repositoryGateway.fetchBlob(coordinates, entry.sha());
        if (!"base64".equalsIgnoreCase(blob.encoding())
                || blob.size() != entry.size()
                || blob.size() > snapshotPolicy.maxRemoteFileBytes()) {
            throw new RepositorySnapshotException(
                    RepositorySnapshotFailure.BLOB_RESPONSE_INVALID);
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(blob.content());
            if (decoded.length != blob.size()) {
                throw new RepositorySnapshotException(
                        RepositorySnapshotFailure.BLOB_RESPONSE_INVALID);
            }
            String text = new String(decoded, StandardCharsets.UTF_8);
            if (text.indexOf('\0') >= 0) {
                throw new RepositorySnapshotException(
                        RepositorySnapshotFailure.BLOB_RESPONSE_INVALID);
            }
            return text;
        } catch (IllegalArgumentException exception) {
            throw new RepositorySnapshotException(
                    RepositorySnapshotFailure.BLOB_RESPONSE_INVALID, exception);
        }
    }

    private Optional<EvidenceType> classify(GitHubRepositoryTree.Entry entry) {
        String path = entry.path().toLowerCase(Locale.ROOT);
        String fileName = fileName(path);
        if (fileName.startsWith("readme")) {
            return Optional.of(EvidenceType.README);
        }
        if (containsIgnoreCase(snapshotPolicy.manifestFileNames(), fileName)) {
            return Optional.of(EvidenceType.MANIFEST);
        }
        if (containsIgnoreCase(snapshotPolicy.configurationFileNames(), fileName)) {
            return Optional.of(EvidenceType.CONFIGURATION);
        }
        String extension = extension(fileName);
        if (!containsIgnoreCase(snapshotPolicy.sourceExtensions(), extension)) {
            return Optional.empty();
        }
        return Optional.of(isTestPath(path) ? EvidenceType.TEST : EvidenceType.SOURCE);
    }

    private boolean isSafePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || path.contains("../")) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        List<String> segments = List.of(normalized.split("/"));
        if (snapshotPolicy.excludedPathSegments().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(segments::contains)) {
            return false;
        }
        String fileName = fileName(normalized);
        return snapshotPolicy.excludedFileNameFragments().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .noneMatch(fileName::contains);
    }

    private boolean isTestPath(String path) {
        return path.contains("/test/")
                || path.contains("/tests/")
                || path.startsWith("test/")
                || path.startsWith("tests/")
                || fileName(path).contains("test");
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot);
    }

    private String limitCodePoints(String text, int maximum) {
        if (codePointCount(text) <= maximum) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, maximum);
        return text.substring(0, endIndex);
    }

    private int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private String evidenceId(String repositoryVersion, String path) {
        UUID stableId = UUID.nameUUIDFromBytes(
                (repositoryVersion + ":" + path).getBytes(StandardCharsets.UTF_8));
        return "repo-" + stableId;
    }

    private record ClassifiedEntry(
            GitHubRepositoryTree.Entry entry,
            EvidenceType type
    ) {
    }

    private enum EvidenceType {
        SOURCE("SOURCE", 0),
        TEST("TEST", 1),
        MANIFEST("MANIFEST", 2),
        CONFIGURATION("CONFIGURATION", 3),
        README(null, 4);

        private final String requestName;
        private final int priority;

        EvidenceType(String requestName, int priority) {
            this.requestName = requestName;
            this.priority = priority;
        }

        String requestName() {
            return requestName;
        }

        int priority() {
            return priority;
        }
    }
}
