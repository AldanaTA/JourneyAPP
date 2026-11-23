package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.journey.api_rest_v1.domain.jobs.Job;

/**
 * Spring Data repository for jobs, with Specification support for dynamic filtering.
 */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /** Returns true when a job already uses this normalized (lowercase, trimmed) name. */
    boolean existsByNameKey(String nameKey);

    /** Returns true when a job other than {@code id} already uses this normalized name. */
    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);
}
