package com.careercompass.projectsource.repository;

import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSourceRepository extends JpaRepository<ProjectSource, UUID> {
}
