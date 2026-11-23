package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.journey.api_rest_v1.domain.jobs.Job;

/**
 * Spring Data repository for jobs, with Specification support for dynamic filtering.
 */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /** Returns true when a job already uses this name (case-insensitive, trimmed). */
    @Query("SELECT COUNT(j) > 0 FROM Job j WHERE LOWER(TRIM(j.jobName)) = :nameKey")
    boolean existsByNameKey(@Param("nameKey") String nameKey);

    /** Returns true when a job other than {@code id} already uses this name (case-insensitive, trimmed). */
    @Query("SELECT COUNT(j) > 0 FROM Job j WHERE LOWER(TRIM(j.jobName)) = :nameKey AND j.id <> :id")
    boolean existsByNameKeyAndIdNot(@Param("nameKey") String nameKey, @Param("id") UUID id);
}
