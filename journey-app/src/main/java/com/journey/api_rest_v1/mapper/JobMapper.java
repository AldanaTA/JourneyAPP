package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.jobs.JobCategory;
import com.journey.api_rest_v1.dto.job.CreateJobRequest;
import com.journey.api_rest_v1.dto.job.JobCategoryResponseDto;
import com.journey.api_rest_v1.dto.job.JobResponseDto;

/**
 * Maps between job entities and their request/response DTOs.
 */
public interface JobMapper {

    /** Returns a copy of the given create request (kept for parity with {@link TraitMapper}). */
    CreateJobRequest CreateRequest(CreateJobRequest request);

    /** Converts a job entity into the DTO returned by the API. */
    JobResponseDto CreateResponse(Job job);

    /** Converts a job category entity into the DTO returned by the categories endpoint. */
    JobCategoryResponseDto CreateCategoryResponse(JobCategory category);
}
