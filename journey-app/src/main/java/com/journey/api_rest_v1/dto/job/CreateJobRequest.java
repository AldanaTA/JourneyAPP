package com.journey.api_rest_v1.dto.job;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Request body used to create or fully update a job.
 * Field names use snake_case to match {@code CreateTraitRequest} and the database columns.
 *
 * <p>{@code trait_ids} are existing trait UUIDs, in display order. {@code categories} and
 * {@code primary_category} are job category names (e.g. "Caster"), matched case-insensitively.
 * When {@code primary_category} is omitted, the first entry of {@code categories} becomes primary.</p>
 */
public record CreateJobRequest(
    @NotBlank(message = "Job name" + CreateJobRequest.ERROR_MESSAGE_NOT_BLANK) String job_name,
    @NotBlank(message = "Job description" + CreateJobRequest.ERROR_MESSAGE_NOT_BLANK) String job_desc,
    @NotEmpty(message = "Trait ids field must contain at least one trait") List<String> trait_ids,
    List<String> categories,
    String primary_category,
    @NotBlank(message = "Content source id" + CreateJobRequest.ERROR_MESSAGE_NOT_BLANK) String content_source_id
) {
    static final String ERROR_MESSAGE_NOT_BLANK = " field must not be blank";
}
