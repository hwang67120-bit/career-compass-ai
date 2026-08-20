package com.careercompass.userprofile.domain;

import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_technology_tag")
public class UserProfileTechnologyTag {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_version_id", nullable = false)
    private UserProfileVersion userProfileVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technology_tag_id")
    private TechnologyTag technologyTag;

    @Column(name = "raw_name", nullable = false, length = 100)
    private String rawName;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private UserProfileTechnologyTagSourceType sourceType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected UserProfileTechnologyTag() {
    }

    private UserProfileTechnologyTag(
            UUID id,
            UserProfileVersion userProfileVersion,
            TechnologyTag technologyTag,
            String rawName,
            String normalizedName,
            String displayName,
            UserProfileTechnologyTagSourceType sourceType,
            int displayOrder
    ) {
        this.id = id;
        this.userProfileVersion = userProfileVersion;
        this.technologyTag = technologyTag;
        this.rawName = rawName;
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.displayOrder = displayOrder;
    }

    public static UserProfileTechnologyTag create(
            UUID id,
            UserProfileVersion userProfileVersion,
            TechnologyTag technologyTag,
            String rawName,
            String normalizedName,
            String displayName,
            UserProfileTechnologyTagSourceType sourceType,
            int displayOrder
    ) {
        return new UserProfileTechnologyTag(
                id, userProfileVersion, technologyTag, rawName,
                normalizedName, displayName, sourceType, displayOrder
        );
    }

    public UUID getId() {
        return id;
    }

    public UserProfileVersion getUserProfileVersion() {
        return userProfileVersion;
    }

    public UUID getTechnologyTagId() {
        return technologyTag == null ? null : technologyTag.getId();
    }

    public TechnologyTag getTechnologyTag() {
        return technologyTag;
    }

    public String getRawName() {
        return rawName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserProfileTechnologyTagSourceType getSourceType() {
        return sourceType;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
