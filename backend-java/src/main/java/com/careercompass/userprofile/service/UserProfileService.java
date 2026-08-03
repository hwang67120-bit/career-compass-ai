package com.careercompass.userprofile.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.domain.TechnologyTagAlias;
import com.careercompass.technologytag.normalization.TechnologyTagNameNormalizer;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import com.careercompass.userprofile.config.UserProfilePolicyProperties;
import com.careercompass.userprofile.domain.UserProfile;
import com.careercompass.userprofile.domain.UserProfileTechnologyTag;
import com.careercompass.userprofile.domain.UserProfileTechnologyTagSourceType;
import com.careercompass.userprofile.domain.UserProfileVersion;
import com.careercompass.userprofile.dto.SaveUserProfileRequest;
import com.careercompass.userprofile.dto.UserProfileResponse;
import com.careercompass.userprofile.dto.UserProfileTechnologyTagRequest;
import com.careercompass.userprofile.dto.UserProfileTechnologyTagResponse;
import com.careercompass.userprofile.exception.InvalidUserProfileRequestException;
import com.careercompass.userprofile.exception.UserProfileNotFoundException;
import com.careercompass.userprofile.exception.UserProfileVersionConflictException;
import com.careercompass.userprofile.repository.UserProfileRepository;
import com.careercompass.userprofile.repository.UserProfileVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileVersionRepository userProfileVersionRepository;
    private final TechnologyTagRepository technologyTagRepository;
    private final TechnologyTagNameNormalizer technologyTagNameNormalizer;
    private final UserProfilePolicyProperties policyProperties;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public UserProfileService(
            UserProfileRepository userProfileRepository,
            UserProfileVersionRepository userProfileVersionRepository,
            TechnologyTagRepository technologyTagRepository,
            TechnologyTagNameNormalizer technologyTagNameNormalizer,
            UserProfilePolicyProperties policyProperties,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userProfileVersionRepository = userProfileVersionRepository;
        this.technologyTagRepository = technologyTagRepository;
        this.technologyTagNameNormalizer = technologyTagNameNormalizer;
        this.policyProperties = policyProperties;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /**
     * 기능: 현재 사용자의 분석 프로필을 검증하고 내용 변경 시 새 불변 버전으로 저장한다.
     * 반환 값: 새로 저장했거나 현재 내용과 동일해서 재사용한 프로필 버전을 반환한다.
     */
    @Transactional
    public UserProfileResponse saveCurrentUserProfile(
            SaveUserProfileRequest request
    ) {
        validateRequest(request);
        String targetJobTitle = normalizeTargetJobTitle(request.targetJobTitle());
        List<ProfileTagCandidate> candidates = resolveTagCandidates(
                request.technologyTags()
        );
        String contentFingerprint = calculateContentFingerprint(
                targetJobTitle,
                candidates
        );
        UUID userId = currentUserProvider.getCurrentUserId();
        Instant now = Instant.now(clock);

        return userProfileRepository.findByUserId(userId)
                .map(userProfile -> saveExistingProfile(
                        userProfile,
                        request.expectedVersion(),
                        targetJobTitle,
                        candidates,
                        contentFingerprint,
                        now
                ))
                .orElseGet(() -> createProfile(
                        userId,
                        request.expectedVersion(),
                        targetJobTitle,
                        candidates,
                        contentFingerprint,
                        now
                ));
    }

    /**
     * 기능: 현재 사용자가 소유한 분석 프로필의 최신 버전을 조회한다.
     * 반환 값: 최신 프로필 버전과 당시 저장된 기술 태그 표시값을 반환한다.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse retrieveCurrentUserProfile() {
        UserProfile userProfile = userProfileRepository
                .findByUserId(currentUserProvider.getCurrentUserId())
                .orElseThrow(UserProfileNotFoundException::new);
        UserProfileVersion currentVersion = retrieveVersion(userProfile);
        return toResponse(userProfile, currentVersion);
    }

    private UserProfileResponse createProfile(
            UUID userId,
            Integer expectedVersion,
            String targetJobTitle,
            List<ProfileTagCandidate> candidates,
            String contentFingerprint,
            Instant now
    ) {
        if (expectedVersion != null) {
            throw new UserProfileVersionConflictException();
        }
        UserProfile userProfile = UserProfile.create(
                UUID.randomUUID(),
                userId,
                now
        );
        UserProfileVersion profileVersion = createVersion(
                userProfile,
                1,
                targetJobTitle,
                candidates,
                contentFingerprint,
                now
        );
        return persistVersion(userProfile, profileVersion);
    }

    private UserProfileResponse saveExistingProfile(
            UserProfile userProfile,
            Integer expectedVersion,
            String targetJobTitle,
            List<ProfileTagCandidate> candidates,
            String contentFingerprint,
            Instant now
    ) {
        UserProfileVersion currentVersion = retrieveVersion(userProfile);
        if (currentVersion.getContentFingerprint().equals(contentFingerprint)) {
            return toResponse(userProfile, currentVersion);
        }
        if (expectedVersion == null
                || expectedVersion != userProfile.getCurrentVersion()) {
            throw new UserProfileVersionConflictException();
        }

        int nextVersion = userProfile.getCurrentVersion() + 1;
        userProfile.advanceVersion(nextVersion, now);
        UserProfileVersion profileVersion = createVersion(
                userProfile,
                nextVersion,
                targetJobTitle,
                candidates,
                contentFingerprint,
                now
        );
        return persistVersion(userProfile, profileVersion);
    }

    private UserProfileResponse persistVersion(
            UserProfile userProfile,
            UserProfileVersion profileVersion
    ) {
        try {
            userProfileRepository.save(userProfile);
            userProfileVersionRepository.saveAndFlush(profileVersion);
            return toResponse(userProfile, profileVersion);
        } catch (ObjectOptimisticLockingFailureException
                 | DataIntegrityViolationException exception) {
            throw new UserProfileVersionConflictException();
        }
    }

    private UserProfileVersion createVersion(
            UserProfile userProfile,
            int version,
            String targetJobTitle,
            List<ProfileTagCandidate> candidates,
            String contentFingerprint,
            Instant createdAt
    ) {
        UserProfileVersion profileVersion = UserProfileVersion.create(
                UUID.randomUUID(),
                userProfile,
                version,
                targetJobTitle,
                contentFingerprint,
                createdAt
        );
        for (int index = 0; index < candidates.size(); index++) {
            ProfileTagCandidate candidate = candidates.get(index);
            profileVersion.addTechnologyTag(UserProfileTechnologyTag.create(
                    UUID.randomUUID(),
                    profileVersion,
                    candidate.technologyTag(),
                    candidate.rawName(),
                    candidate.normalizedName(),
                    candidate.displayName(),
                    candidate.sourceType(),
                    index
            ));
        }
        return profileVersion;
    }

    private UserProfileVersion retrieveVersion(UserProfile userProfile) {
        return userProfileVersionRepository
                .findByUserProfile_IdAndProfileVersion(
                        userProfile.getId(),
                        userProfile.getCurrentVersion()
                )
                .orElseThrow(IllegalStateException::new);
    }

    private List<ProfileTagCandidate> resolveTagCandidates(
            List<UserProfileTechnologyTagRequest> requests
    ) {
        Set<UUID> selectedIds = new LinkedHashSet<>();
        List<String> customNames = new ArrayList<>();
        for (UserProfileTechnologyTagRequest request : requests) {
            if (request.technologyTagId() != null) {
                selectedIds.add(request.technologyTagId());
            } else {
                customNames.add(request.customName().strip());
            }
        }

        Map<UUID, TechnologyTag> selectedTags = loadSelectedTags(selectedIds);
        Map<String, TechnologyTag> resolutionMatches = loadResolutionMatches(
                customNames
        );
        Map<UUID, ProfileTagCandidate> canonicalCandidates =
                new LinkedHashMap<>();
        Map<String, ProfileTagCandidate> customCandidates =
                new LinkedHashMap<>();

        for (UUID selectedId : selectedIds) {
            TechnologyTag technologyTag = selectedTags.get(selectedId);
            canonicalCandidates.put(selectedId, new ProfileTagCandidate(
                    technologyTag,
                    technologyTag.getDisplayName(),
                    technologyTag.getNormalizedKey(),
                    technologyTag.getDisplayName(),
                    UserProfileTechnologyTagSourceType.USER_SELECTED
            ));
        }
        for (String customName : customNames) {
            String normalizedName = technologyTagNameNormalizer.normalize(customName);
            TechnologyTag technologyTag = resolutionMatches.get(normalizedName);
            if (technologyTag != null) {
                canonicalCandidates.putIfAbsent(
                        technologyTag.getId(),
                        new ProfileTagCandidate(
                                technologyTag,
                                customName,
                                technologyTag.getNormalizedKey(),
                                technologyTag.getDisplayName(),
                                UserProfileTechnologyTagSourceType.USER_CUSTOM
                        )
                );
            } else {
                customCandidates.putIfAbsent(
                        normalizedName,
                        new ProfileTagCandidate(
                                null,
                                customName,
                                normalizedName,
                                customName,
                                UserProfileTechnologyTagSourceType.USER_CUSTOM
                        )
                );
            }
        }

        List<ProfileTagCandidate> candidates = new ArrayList<>(
                canonicalCandidates.values()
        );
        candidates.addAll(customCandidates.values());
        candidates.sort(Comparator.comparing(ProfileTagCandidate::identity));
        return List.copyOf(candidates);
    }

    private Map<UUID, TechnologyTag> loadSelectedTags(Set<UUID> selectedIds) {
        Map<UUID, TechnologyTag> selectedTags = new HashMap<>();
        technologyTagRepository.findAllById(selectedIds)
                .stream()
                .filter(TechnologyTag::isActive)
                .forEach(technologyTag -> selectedTags.put(
                        technologyTag.getId(),
                        technologyTag
                ));
        for (UUID selectedId : selectedIds) {
            if (!selectedTags.containsKey(selectedId)) {
                throw invalidRequest(
                        "technologyTags",
                        "선택한 표준 기술 태그를 사용할 수 없습니다."
                );
            }
        }
        return selectedTags;
    }

    private Map<String, TechnologyTag> loadResolutionMatches(
            List<String> customNames
    ) {
        if (customNames.isEmpty()) {
            return Map.of();
        }
        Set<String> normalizedNames = customNames.stream()
                .map(technologyTagNameNormalizer::normalize)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, TechnologyTag> matches = new HashMap<>();
        for (TechnologyTag technologyTag
                : technologyTagRepository.findActiveResolutionMatches(
                        normalizedNames
                )) {
            matches.put(technologyTag.getNormalizedKey(), technologyTag);
            for (TechnologyTagAlias alias : technologyTag.getAliases()) {
                matches.put(alias.getNormalizedAlias(), technologyTag);
            }
        }
        return matches;
    }

    private void validateRequest(SaveUserProfileRequest request) {
        if (request == null) {
            throw invalidRequest("request", "요청 내용을 입력해 주세요.");
        }
        if (request.expectedVersion() != null && request.expectedVersion() < 1) {
            throw invalidRequest(
                    "expectedVersion",
                    "프로필 버전은 1 이상이어야 합니다."
            );
        }
        if (request.targetJobTitle() == null
                || request.targetJobTitle().isBlank()) {
            throw invalidRequest(
                    "targetJobTitle",
                    "희망 직무를 입력해 주세요."
            );
        }
        if (request.targetJobTitle().codePointCount(
                0, request.targetJobTitle().length()
        ) > policyProperties.maxTargetJobTitleLength()) {
            throw invalidRequest(
                    "targetJobTitle",
                    "허용된 희망 직무 길이를 초과했습니다."
            );
        }
        validateTechnologyTags(request.technologyTags());
    }

    private void validateTechnologyTags(
            List<UserProfileTechnologyTagRequest> technologyTags
    ) {
        if (technologyTags == null || technologyTags.isEmpty()) {
            throw invalidRequest(
                    "technologyTags",
                    "기술 태그를 한 개 이상 입력해 주세요."
            );
        }
        if (technologyTags.size() > policyProperties.maxTechnologyTagCount()) {
            throw invalidRequest(
                    "technologyTags",
                    "허용된 기술 태그 개수를 초과했습니다."
            );
        }
        for (int index = 0; index < technologyTags.size(); index++) {
            validateTechnologyTag(technologyTags.get(index), index);
        }
    }

    private void validateTechnologyTag(
            UserProfileTechnologyTagRequest technologyTag,
            int index
    ) {
        String fieldName = "technologyTags[" + index + "]";
        if (technologyTag == null) {
            throw invalidRequest(fieldName, "기술 태그를 입력해 주세요.");
        }
        boolean hasTechnologyTagId = technologyTag.technologyTagId() != null;
        boolean hasCustomName = technologyTag.customName() != null;
        if (hasTechnologyTagId == hasCustomName) {
            throw invalidRequest(
                    fieldName,
                    "technologyTagId와 customName 중 하나만 입력해 주세요."
            );
        }
        if (hasCustomName && technologyTag.customName().isBlank()) {
            throw invalidRequest(
                    fieldName + ".customName",
                    "커스텀 기술명은 비어 있을 수 없습니다."
            );
        }
        if (hasCustomName && technologyTag.customName().codePointCount(
                0, technologyTag.customName().length()
        ) > policyProperties.maxCustomTagNameLength()) {
            throw invalidRequest(
                    fieldName + ".customName",
                    "허용된 커스텀 기술명 길이를 초과했습니다."
            );
        }
    }

    private String normalizeTargetJobTitle(String targetJobTitle) {
        return Normalizer.normalize(targetJobTitle, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
    }

    private String calculateContentFingerprint(
            String targetJobTitle,
            List<ProfileTagCandidate> candidates
    ) {
        StringBuilder canonicalContent = new StringBuilder(
                targetJobTitle.toLowerCase(Locale.ROOT)
        );
        for (ProfileTagCandidate candidate : candidates) {
            canonicalContent.append('\u001f')
                    .append(candidate.sourceType())
                    .append(':')
                    .append(candidate.identity());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonicalContent.toString().getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UserProfileResponse toResponse(
            UserProfile userProfile,
            UserProfileVersion profileVersion
    ) {
        return new UserProfileResponse(
                userProfile.getId(),
                profileVersion.getProfileVersion(),
                profileVersion.getTargetJobTitle(),
                profileVersion.getTechnologyTags().stream()
                        .map(technologyTag ->
                                new UserProfileTechnologyTagResponse(
                                        technologyTag.getTechnologyTagId(),
                                        technologyTag.getRawName(),
                                        technologyTag.getNormalizedName(),
                                        technologyTag.getDisplayName(),
                                        technologyTag.getSourceType()
                                ))
                        .toList(),
                userProfile.getUpdatedAt()
        );
    }

    private InvalidUserProfileRequestException invalidRequest(
            String fieldName,
            String fieldMessage
    ) {
        return new InvalidUserProfileRequestException(
                fieldName,
                fieldMessage
        );
    }

    private record ProfileTagCandidate(
            TechnologyTag technologyTag,
            String rawName,
            String normalizedName,
            String displayName,
            UserProfileTechnologyTagSourceType sourceType
    ) {
        private String identity() {
            if (technologyTag == null) {
                return "custom:" + normalizedName;
            }
            return "standard:" + technologyTag.getId();
        }
    }
}
