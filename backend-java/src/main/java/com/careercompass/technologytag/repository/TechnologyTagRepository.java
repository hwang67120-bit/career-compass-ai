package com.careercompass.technologytag.repository;

import java.util.List;
import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnologyTagRepository extends JpaRepository<TechnologyTag, UUID> {

    @EntityGraph(attributePaths = "aliases")
    @Query("""
            SELECT technologyTag
            FROM TechnologyTag technologyTag
            WHERE technologyTag.active = true
              AND (
                    :normalizedQuery = ''
                    OR technologyTag.normalizedKey LIKE CONCAT('%', :normalizedQuery, '%')
                    OR EXISTS (
                        SELECT alias.id
                        FROM TechnologyTagAlias alias
                        WHERE alias.technologyTag = technologyTag
                          AND alias.normalizedAlias LIKE CONCAT('%', :normalizedQuery, '%')
                    )
              )
            ORDER BY technologyTag.defaultDisplayOrder ASC, technologyTag.displayName ASC
            """)
    List<TechnologyTag> searchActiveTechnologyTags(
            @Param("normalizedQuery") String normalizedQuery,
            Pageable pageable
    );
}
