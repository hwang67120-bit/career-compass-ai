package com.careercompass.projectresponsibility.domain;

import java.util.UUID;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.userprofile.domain.UserProfileVersion;
import jakarta.persistence.*;

@Entity
@Table(name = "user_profile_project_responsibility")
public class UserProfileProjectResponsibility {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_version_id", nullable = false)
    private UserProfileVersion userProfileVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_candidate_id", nullable = false)
    private ProjectResponsibilityCandidate sourceCandidate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_source_id", nullable = false)
    private ProjectSource projectSource;
    @Column(name = "confirmed_text", nullable = false, length = 500)
    private String confirmedText;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    protected UserProfileProjectResponsibility() {}

    public static UserProfileProjectResponsibility create(
            UUID id, UserProfileVersion version, ProjectResponsibilityCandidate candidate,
            String text, int order) {
        UserProfileProjectResponsibility responsibility = new UserProfileProjectResponsibility();
        responsibility.id = id;
        responsibility.userProfileVersion = version;
        responsibility.sourceCandidate = candidate;
        responsibility.projectSource = candidate.getExtractionTask().getProjectSource();
        responsibility.confirmedText = text;
        responsibility.displayOrder = order;
        return responsibility;
    }

    public static UserProfileProjectResponsibility copy(
            UUID id, UserProfileVersion version,
            UserProfileProjectResponsibility source, int displayOrder) {
        UserProfileProjectResponsibility responsibility = new UserProfileProjectResponsibility();
        responsibility.id = id;
        responsibility.userProfileVersion = version;
        responsibility.sourceCandidate = source.sourceCandidate;
        responsibility.projectSource = source.projectSource;
        responsibility.confirmedText = source.confirmedText;
        responsibility.displayOrder = displayOrder;
        return responsibility;
    }

    public ProjectSource getProjectSource() {
        return projectSource;
    }
}
