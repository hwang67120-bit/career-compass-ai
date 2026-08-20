package com.careercompass.technologytag.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "technology_tag")
public class TechnologyTag {

    @Id
    private UUID id;

    @Column(name = "tag_key", nullable = false, length = 100)
    private String tagKey;

    @Column(name = "normalized_key", nullable = false, length = 100)
    private String normalizedKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private TechnologyTagCategory category;

    @Column(name = "default_display_order", nullable = false)
    private int defaultDisplayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "technologyTag")
    private Set<TechnologyTagAlias> aliases = new LinkedHashSet<>();

    protected TechnologyTag() {
    }

    public UUID getId() {
        return id;
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getNormalizedKey() {
        return normalizedKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TechnologyTagCategory getCategory() {
        return category;
    }

    public int getDefaultDisplayOrder() {
        return defaultDisplayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Set<TechnologyTagAlias> getAliases() {
        return Set.copyOf(aliases);
    }
}
