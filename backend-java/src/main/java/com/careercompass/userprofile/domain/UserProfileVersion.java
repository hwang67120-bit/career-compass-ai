package com.careercompass.userprofile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_version")
public class UserProfileVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Column(name = "target_job_title", nullable = false, length = 100)
    private String targetJobTitle;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OrderBy("displayOrder ASC")
    @OneToMany(
            mappedBy = "userProfileVersion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<UserProfileTechnologyTag> technologyTags = new ArrayList<>();

    protected UserProfileVersion() {
    }

    private UserProfileVersion(
            UUID id,
            UserProfile userProfile,
            int profileVersion,
            String targetJobTitle,
            String contentFingerprint,
            Instant createdAt
    ) {
        this.id = id;
        this.userProfile = userProfile;
        this.profileVersion = profileVersion;
        this.targetJobTitle = targetJobTitle;
        this.contentFingerprint = contentFingerprint;
        this.createdAt = createdAt;
    }

    public static UserProfileVersion create(
            UUID id,
            UserProfile userProfile,
            int profileVersion,
            String targetJobTitle,
            String contentFingerprint,
            Instant createdAt
    ) {
        return new UserProfileVersion(
                id, userProfile, profileVersion, targetJobTitle,
                contentFingerprint, createdAt
        );
    }

    public void addTechnologyTag(UserProfileTechnologyTag technologyTag) {
        technologyTags.add(technologyTag);
    }

    public UUID getId() {
        return id;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public int getProfileVersion() {
        return profileVersion;
    }

    public String getTargetJobTitle() {
        return targetJobTitle;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<UserProfileTechnologyTag> getTechnologyTags() {
        return List.copyOf(technologyTags);
    }
}
