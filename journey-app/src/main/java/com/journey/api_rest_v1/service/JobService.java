package com.journey.api_rest_v1.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.journey.api_rest_v1.dto.job.CreateJobRequest;
import com.journey.api_rest_v1.dto.job.JobCategoryResponseDto;
import com.journey.api_rest_v1.dto.job.JobFilterRequest;
import com.journey.api_rest_v1.dto.job.JobResponseDto;

/**
 * Business operations for compendium jobs: CRUD, paging, filtering, and the category list.
 */
public interface JobService {

    /** Returns a sorted page of all jobs. */
    Page<JobResponseDto> getAllJobs(int page, int size, String sortBy, String sortDir);

    /** Returns a page of jobs matching every filter set on the request, ranked by name when searching. */
    Page<JobResponseDto> filterJobs(JobFilterRequest request);

    /** Returns every job category, sorted by name. */
    List<JobCategoryResponseDto> getJobCategories();

    /** Creates a new job linked to existing traits and categories and returns it. */
    JobResponseDto createJob(CreateJobRequest request);

    /** Returns one job by its UUID string. */
    JobResponseDto getJobById(String id);

    /** Replaces an existing job's fields, traits and categories and returns the updated job. */
    JobResponseDto updateJob(String id, CreateJobRequest request);

    /** Deletes a job (and its trait/category links, not the traits themselves) and returns a confirmation message. */
    String deleteJob(String id);
}
