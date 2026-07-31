package com.careercompass.projectsource.repository;

import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import org.springframework.data.repository.Repository;

public interface ProjectSourceQueryRepository extends Repository<ProjectSource, UUID> {

    List<ProjectSource> findAllByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
}
