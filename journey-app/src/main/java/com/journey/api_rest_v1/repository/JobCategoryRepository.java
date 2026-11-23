package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.jobs.JobCategory;

/**
 * Spring Data repository for the job category lookup table.
 */
@Repository
public interface JobCategoryRepository extends JpaRepository<JobCategory, UUID> {
}
