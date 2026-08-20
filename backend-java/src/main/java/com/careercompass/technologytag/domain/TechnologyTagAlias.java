package com.careercompass.technologytag.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "technology_tag_alias")
public class TechnologyTagAlias {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technology_tag_id", nullable = false)
    private TechnologyTag technologyTag;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 100)
    private String normalizedAlias;

    protected TechnologyTagAlias() {
    }

    public String getAlias() {
        return alias;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }
}
