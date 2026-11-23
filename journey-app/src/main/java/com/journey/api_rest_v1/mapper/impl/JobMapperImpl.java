package com.journey.api_rest_v1.mapper.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.jobs.JobCategory;
import com.journey.api_rest_v1.domain.jobs.JobCategoryAssignment;
import com.journey.api_rest_v1.domain.jobs.JobTraits;
import com.journey.api_rest_v1.dto.job.CreateJobRequest;
import com.journey.api_rest_v1.dto.job.JobCategoryResponseDto;
import com.journey.api_rest_v1.dto.job.JobResponseDto;
import com.journey.api_rest_v1.mapper.JobMapper;
import com.journey.api_rest_v1.mapper.TraitMapper;

/**
 * Default {@link JobMapper} implementation that maps fields by hand.
 * Traits are converted with {@link TraitMapper} so a job embeds the same trait payload as the trait endpoints.
 */
@Component
public class JobMapperImpl implements JobMapper {

    private final TraitMapper traitMapper;

    /** Wires the trait mapper used for the job's embedded traits. */
    public JobMapperImpl(TraitMapper traitMapper) {
        this.traitMapper = traitMapper;
    }

    /** Converts a job entity into a response DTO, ordering traits by sort order and categories primary-first. */
    @Override
    public JobResponseDto CreateResponse(Job job) {
        List<JobCategoryAssignment> categories = job.getCategories().stream()
                .sorted(Comparator
                        .comparing((JobCategoryAssignment link) -> !link.isPrimary())
                        .thenComparing(link -> link.getCategory().getCategory(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new JobResponseDto(
            job.getId() == null ? null : job.getId().toString(),
            job.getJobName(),
            job.getJobDesc(),
            categories.stream()
                .filter(JobCategoryAssignment::isPrimary)
                .map(link -> link.getCategory().getCategory())
                .findFirst()
                .orElse(null),
            categories.stream()
                .map(link -> link.getCategory().getCategory())
                .toList(),
            job.getTraits().stream()
                .sorted(Comparator.comparing(JobTraits::getSortOrder))
                .map(link -> traitMapper.CreateResponse(link.getTrait()))
                .toList(),
            job.getSource() == null ? null : job.getSource().getSource_name()
        );
    }

    /** Converts a job category entity into its response DTO. */
    @Override
    public JobCategoryResponseDto CreateCategoryResponse(JobCategory category) {
        return new JobCategoryResponseDto(
            category.getId() == null ? null : category.getId().toString(),
            category.getCategory(),
            category.getDescription()
        );
    }

    /** Returns a field-by-field copy of the given create request. */
    @Override
    public CreateJobRequest CreateRequest(CreateJobRequest request) {
        return new CreateJobRequest(
            request.job_name(),
            request.job_desc(),
            request.trait_ids(),
            request.categories(),
            request.primary_category(),
            request.content_source_id()
        );
    }
}
