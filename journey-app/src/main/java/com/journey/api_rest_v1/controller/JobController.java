package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.job.CreateJobRequest;
import com.journey.api_rest_v1.dto.job.JobCategoryResponseDto;
import com.journey.api_rest_v1.dto.job.JobFilterRequest;
import com.journey.api_rest_v1.dto.job.JobResponseDto;
import com.journey.api_rest_v1.service.JobService;

import com.journey.api_rest_v1.service.campaign.ContentAccess;

import jakarta.validation.Valid;

/**
 * REST endpoints for compendium jobs under {@code /api/v1/jobs}.
 * Mirrors {@link TraitController}: CRUD, a paged list, and a POST filter endpoint,
 * plus a category list for building filter options.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final ContentAccess contentAccess;

    /** Wires the job service. */
    public JobController(JobService jobService, ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
        this.jobService = jobService;
    }

    /** Creates a job from existing traits and returns it with 201 Created. */
    @PostMapping()
    public ResponseEntity<JobResponseDto> createJob(@Valid @RequestBody CreateJobRequest request) {
        contentAccess.requireWritableSource(request.content_source_id());
        contentAccess.requireUsableTraits(request.content_source_id(), request.trait_ids());
        return new ResponseEntity<>(jobService.createJob(request), HttpStatus.CREATED);
    }

    /** Returns every job category (name and description). */
    @GetMapping("/categories")
    public ResponseEntity<List<JobCategoryResponseDto>> getJobCategories() {
        return new ResponseEntity<>(jobService.getJobCategories(), HttpStatus.OK);
    }

    /** Returns a single job by id, with its traits in display order. */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getJob(@PathVariable String id) {
        contentAccess.requireReadable(ContentAccess.Kind.JOB, id);
        return new ResponseEntity<>(jobService.getJobById(id), HttpStatus.OK);
    }

    /** Returns a sorted page of all jobs. */
    @GetMapping()
    public ResponseEntity<Iterable<JobResponseDto>> getAllJobs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(defaultValue = "jobName") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(jobService.getAllJobs(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    /** Returns a page of jobs matching the filters in the request body (name, categories, trait name). */
    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<JobResponseDto>> filterJobs(@Valid @RequestBody JobFilterRequest request) {
        return new ResponseEntity<>(jobService.filterJobs(request), HttpStatus.OK);
    }

    /** Replaces an existing job and returns the updated version. */
    @PutMapping("/{id}")
    public ResponseEntity<JobResponseDto> updateJob(@PathVariable String id,
            @Valid @RequestBody CreateJobRequest request) {
        contentAccess.requireWritable(ContentAccess.Kind.JOB, id);
        contentAccess.requireWritableSource(request.content_source_id());
        contentAccess.requireUsableTraits(request.content_source_id(), request.trait_ids());
        return new ResponseEntity<>(jobService.updateJob(id, request), HttpStatus.OK);
    }

    /** Deletes a job (its traits are kept) and returns a confirmation message. */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteJob(@PathVariable String id) {
        contentAccess.requireWritable(ContentAccess.Kind.JOB, id);
        return ResponseEntity.ok(jobService.deleteJob(id));
    }
}
