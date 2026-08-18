package com.careercompass.projectresponsibility.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.exception.RepositorySnapshotException;
import com.careercompass.projectsource.service.PreparedRepositorySnapshot;
import com.careercompass.projectsource.service.RepositorySnapshotService;
import com.careercompass.projectresponsibility.domain.ProjectTechnologyFindingStatus;
import com.careercompass.pythonworker.client.PythonProjectResponsibilityExtractionClient;
import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionEnvelope;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionRequest;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionException;
import com.careercompass.technologytag.domain.TechnologyTagMatchStatus;
import com.careercompass.technologytag.dto.TechnologyTagResolutionResult;
import com.careercompass.technologytag.service.TechnologyTagResolutionService;
import com.careercompass.userprofile.domain.UserProfileTechnologyTag;
import com.careercompass.userprofile.domain.UserProfileVersion;
import org.springframework.stereotype.Service;

@Service
public class ProjectResponsibilityExtractionService {

    private final RepositorySnapshotService snapshotService;
    private final PythonProjectResponsibilityExtractionClient extractionClient;
    private final TechnologyTagResolutionService technologyTagResolutionService;
    private final ProjectResponsibilityExtractionPersistenceService persistenceService;
    private final ProjectResponsibilityExtractionPolicyProperties extractionPolicy;

    public ProjectResponsibilityExtractionService(
            RepositorySnapshotService snapshotService,
            PythonProjectResponsibilityExtractionClient extractionClient,
            TechnologyTagResolutionService technologyTagResolutionService,
            ProjectResponsibilityExtractionPersistenceService persistenceService,
            ProjectResponsibilityExtractionPolicyProperties extractionPolicy
    ) {
        this.snapshotService = snapshotService;
        this.extractionClient = extractionClient;
        this.technologyTagResolutionService = technologyTagResolutionService;
        this.persistenceService = persistenceService;
        this.extractionPolicy = extractionPolicy;
    }

    /**
     * 기능: 분석에 고정된 저장소별 최소 자료를 Python으로 추출하고 미확정 후보를 저장한다.
     * 반환 값: 사용자 확인 필요 여부와 일부 기술 묶음 실패 여부를 반환한다.
     */
    public ProjectResponsibilityExtractionOutcome extract(
            JobAnalysis jobAnalysis,
            UserProfileVersion profileVersion
    ) {
        List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag>
                selectedTechnologyTags = standardTechnologyTags(profileVersion);
        boolean requiresUserConfirmation = false;
        boolean partiallyExtracted = false;

        for (ProjectSource projectSource : jobAnalysis.getProjectSources()) {
            Set<UUID> selectedTechnologyTagIds = selectedTechnologyTags.stream()
                    .map(tag -> UUID.fromString(tag.technologyTagId()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            UUID taskId = persistenceService.createTask(
                    jobAnalysis,
                    projectSource,
                    profileVersion,
                    selectedTechnologyTagIds);
            if (selectedTechnologyTags.isEmpty()) {
                persistenceService.completeWithoutCandidates(taskId);
                continue;
            }

            PreparedRepositorySnapshot snapshot;
            try {
                snapshot = snapshotService.prepare(
                        projectSource, selectedTechnologyTags.size());
            } catch (RepositorySnapshotException exception) {
                persistenceService.failTask(taskId, exception.getFailure().name());
                throw exception;
            }

            List<PythonProjectResponsibilityExtractionEnvelope.Data> successful =
                    new ArrayList<>();
            Set<UUID> failedTechnologyTagIds = new LinkedHashSet<>();
            PythonProjectResponsibilityExtractionException lastFailure = null;
            for (List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag> batch
                    : batches(selectedTechnologyTags)) {
                PythonProjectResponsibilityExtractionRequest request =
                        new PythonProjectResponsibilityExtractionRequest(
                                taskId.toString(),
                                projectSource.getId().toString(),
                                batch,
                                snapshot.requestSnapshot());
                try {
                    successful.add(extractionClient.extract(request));
                } catch (PythonProjectResponsibilityExtractionException exception) {
                    lastFailure = exception;
                    batch.stream()
                            .map(tag -> UUID.fromString(tag.technologyTagId()))
                            .forEach(failedTechnologyTagIds::add);
                }
            }
            if (successful.isEmpty()) {
                String failureCode = lastFailure == null
                        ? "PROJECT_RESPONSIBILITY_EXTRACTION_FAILED"
                        : lastFailure.getFailure().name();
                persistenceService.failTask(taskId, failureCode);
                if (lastFailure != null) {
                    throw lastFailure;
                }
                throw new IllegalStateException(failureCode);
            }

            ProjectResponsibilityExtractionDraft extractionDraft =
                    mergeExtraction(successful, selectedTechnologyTagIds,
                            failedTechnologyTagIds);
            PythonProjectResponsibilityExtractionEnvelope.ModelExecution modelExecution =
                    successful.get(0).modelExecution();
            String failureCode = lastFailure == null
                    ? null : lastFailure.getFailure().name();
            boolean taskRequiresConfirmation = persistenceService.completeTask(
                    taskId,
                    extractionDraft.candidates(),
                    extractionDraft.selectedTechnologyFindings(),
                    extractionDraft.technologySuggestions(),
                    snapshot,
                    failedTechnologyTagIds,
                    failureCode,
                    modelExecution);
            requiresUserConfirmation |= taskRequiresConfirmation;
            partiallyExtracted |= !failedTechnologyTagIds.isEmpty();
        }
        return new ProjectResponsibilityExtractionOutcome(
                requiresUserConfirmation,
                partiallyExtracted);
    }

    private List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag>
    standardTechnologyTags(UserProfileVersion profileVersion) {
        Map<UUID, PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag> unique =
                new LinkedHashMap<>();
        for (UserProfileTechnologyTag profileTechnologyTag
                : profileVersion.getTechnologyTags()) {
            if (profileTechnologyTag.getTechnologyTag() != null) {
                UUID technologyTagId = profileTechnologyTag.getTechnologyTagId();
                unique.putIfAbsent(
                        technologyTagId,
                        new PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag(
                                technologyTagId.toString(),
                                profileTechnologyTag.getTechnologyTag().getDisplayName()));
            }
        }
        return List.copyOf(unique.values());
    }

    private List<List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag>>
    batches(
            List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag> tags
    ) {
        List<List<PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag>>
                batches = new ArrayList<>();
        int batchSize = extractionPolicy.maxSelectedTechnologyTags();
        for (int start = 0; start < tags.size(); start += batchSize) {
            batches.add(tags.subList(start, Math.min(start + batchSize, tags.size())));
        }
        return batches;
    }

    private ProjectResponsibilityExtractionDraft mergeExtraction(
            List<PythonProjectResponsibilityExtractionEnvelope.Data> responses,
            Set<UUID> selectedTechnologyTagIds,
            Set<UUID> failedTechnologyTagIds
    ) {
        Map<String, DetectedTechnologyMerge> detections = new LinkedHashMap<>();
        Map<String, PythonProjectResponsibilityExtractionEnvelope
                .ResponsibilityEvidenceCandidate> candidates = new LinkedHashMap<>();

        for (PythonProjectResponsibilityExtractionEnvelope.Data response : responses) {
            for (PythonProjectResponsibilityExtractionEnvelope.DetectedTechnology detected
                    : response.detectedTechnologies()) {
                String key = detected.detectedName() + "\u0000" + detected.source();
                detections.computeIfAbsent(
                                key,
                                ignored -> new DetectedTechnologyMerge(
                                        detected.detectedName(),
                                        detected.source(),
                                        new LinkedHashSet<>()))
                        .evidenceIds()
                        .addAll(detected.evidenceIds());
            }
            for (PythonProjectResponsibilityExtractionEnvelope
                    .ResponsibilityEvidenceCandidate candidate
                    : response.responsibilityEvidenceCandidates()) {
                List<String> sourceEvidenceIds = candidate.sourceEvidenceIds().stream()
                        .sorted()
                        .toList();
                String key = String.join("\u0000", sourceEvidenceIds);
                candidates.putIfAbsent(key, candidate);
            }
        }

        List<DetectedTechnologyMerge> limitedDetections = detections.values().stream()
                .sorted(Comparator
                        .comparingInt((DetectedTechnologyMerge detected) ->
                                detected.evidenceIds().size())
                        .reversed()
                        .thenComparing(DetectedTechnologyMerge::detectedName))
                .limit(extractionPolicy.maxDetectedTechnologies())
                .toList();
        List<TechnologyTagResolutionResult> resolutions = limitedDetections.isEmpty()
                ? List.of()
                : technologyTagResolutionService.resolveTechnologyNames(
                limitedDetections.stream()
                        .map(DetectedTechnologyMerge::detectedName)
                        .toList()).results();

        Map<UUID, Set<String>> resolvedEvidenceByTechnologyTagId = new LinkedHashMap<>();
        for (int index = 0; index < limitedDetections.size(); index++) {
            TechnologyTagResolutionResult resolution = resolutions.get(index);
            if (resolution.matchStatus() == TechnologyTagMatchStatus.MATCHED) {
                resolvedEvidenceByTechnologyTagId.computeIfAbsent(
                                resolution.technologyTagId(),
                                ignored -> new LinkedHashSet<>())
                        .addAll(limitedDetections.get(index).evidenceIds());
            }
        }

        List<ResolvedDetection> resolvedDetections = resolvedEvidenceByTechnologyTagId
                .entrySet().stream()
                .map(entry -> new ResolvedDetection(entry.getKey(), entry.getValue()))
                .toList();
        List<ProjectResponsibilityCandidateDraft> candidateDrafts = candidates.values().stream()
                .map(candidate -> {
                    Set<String> sourceEvidenceIds =
                            new LinkedHashSet<>(candidate.sourceEvidenceIds());
                    Set<UUID> relatedTechnologyTagIds = resolvedDetections.stream()
                            .filter(detection -> detection.evidenceIds().stream()
                                    .anyMatch(sourceEvidenceIds::contains))
                            .map(ResolvedDetection::technologyTagId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    return new ProjectResponsibilityCandidateDraft(
                            candidate.text(),
                            sourceEvidenceIds.stream().sorted().toList(),
                            relatedTechnologyTagIds);
                })
                .toList();
        List<ProjectTechnologyFindingDraft> findingDrafts = selectedTechnologyTagIds.stream()
                .sorted()
                .map(technologyTagId -> {
                    boolean found = !failedTechnologyTagIds.contains(technologyTagId)
                            && resolvedEvidenceByTechnologyTagId.containsKey(technologyTagId);
                    return new ProjectTechnologyFindingDraft(
                            technologyTagId,
                            found ? ProjectTechnologyFindingStatus.FOUND
                                    : ProjectTechnologyFindingStatus.NEEDS_REVIEW,
                            found ? resolvedEvidenceByTechnologyTagId.get(technologyTagId)
                                    .stream().sorted().toList() : List.of());
                })
                .toList();
        List<ProjectTechnologySuggestionDraft> suggestionDrafts =
                resolvedEvidenceByTechnologyTagId.entrySet().stream()
                        .filter(entry -> !selectedTechnologyTagIds.contains(entry.getKey()))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new ProjectTechnologySuggestionDraft(
                                entry.getKey(), entry.getValue().stream().sorted().toList()))
                        .toList();
        return new ProjectResponsibilityExtractionDraft(
                candidateDrafts, findingDrafts, suggestionDrafts);
    }

    private record DetectedTechnologyMerge(
            String detectedName,
            String source,
            Set<String> evidenceIds
    ) {
    }

    private record ResolvedDetection(
            UUID technologyTagId,
            Set<String> evidenceIds
    ) {
    }
}
