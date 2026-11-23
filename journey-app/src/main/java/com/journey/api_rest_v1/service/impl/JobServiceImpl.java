package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.jobs.Job;
import com.journey.api_rest_v1.domain.jobs.JobCategory;
import com.journey.api_rest_v1.domain.jobs.JobCategoryAssignment;
import com.journey.api_rest_v1.domain.jobs.JobCategoryAssignmentId;
import com.journey.api_rest_v1.domain.jobs.JobTraits;
import com.journey.api_rest_v1.domain.jobs.JobTraitsId;
import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.job.CreateJobRequest;
import com.journey.api_rest_v1.dto.job.JobCategoryResponseDto;
import com.journey.api_rest_v1.dto.job.JobFilterRequest;
import com.journey.api_rest_v1.dto.job.JobResponseDto;
import com.journey.api_rest_v1.mapper.JobMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.JobCategoryRepository;
import com.journey.api_rest_v1.repository.JobRepository;
import com.journey.api_rest_v1.repository.TraitRepository;
import com.journey.api_rest_v1.service.JobService;
import com.journey.api_rest_v1.specification.JobSpecifications;

/**
 * Default {@link JobService} implementation backed by JPA, with Redis caching for reads.
 * Follows the same structure as {@code TraitServiceImpl}.
 */
@Service
public class JobServiceImpl implements JobService {

    private static final String DEFAULT_SORT_FIELD = "jobName";

    private final JobRepository jobRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final TraitRepository traitRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final JobMapper jobMapper;

    /** Wires the repositories and mapper this service depends on. */
    public JobServiceImpl(JobRepository jobRepository,
                          JobCategoryRepository jobCategoryRepository,
                          TraitRepository traitRepository,
                          ContentSourceRepository contentSourceRepository,
                          JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.jobCategoryRepository = jobCategoryRepository;
        this.traitRepository = traitRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.jobMapper = jobMapper;
    }

    /** Creates a job, rejecting duplicate names with 409, then attaches its traits and categories. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"jobsById", "jobsAll"}, allEntries = true)
    public JobResponseDto createJob(CreateJobRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ContentSource contentSource = findContentSource(request.content_source_id());
        List<Trait> traits = resolveTraits(request.trait_ids());
        ResolvedCategories categories = resolveCategories(request.categories(), request.primary_category());

        if (jobRepository.existsByNameKey(normalizeName(request.job_name()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A job named '" + request.job_name().trim() + "' already exists");
        }

        Job job = new Job(
                null,
                request.job_name().trim(),
                request.job_desc().trim(),
                new ArrayList<>(),
                new ArrayList<>(),
                contentSource,
                now,
                now
        );

        Job savedJob = jobRepository.save(job);

        // The saved job is managed, so new links are persisted by cascade when the transaction flushes.
        addLinks(savedJob, traits, categories, now);

        return jobMapper.CreateResponse(savedJob);
    }

    /**
     * Updates every field of a job and replaces its trait and category links.
     * Links are cleared and flushed before re-adding, because the (job_id, sort_order) and
     * one-primary-per-job unique constraints would otherwise collide mid-flush when reordering.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"jobsById", "jobsAll"}, allEntries = true)
    public JobResponseDto updateJob(String id, CreateJobRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID jobId = parseUuid(id, "job");
        Job existingJob = findJob(jobId);
        ContentSource contentSource = findContentSource(request.content_source_id());
        List<Trait> traits = resolveTraits(request.trait_ids());
        ResolvedCategories categories = resolveCategories(request.categories(), request.primary_category());

        if (jobRepository.existsByNameKeyAndIdNot(normalizeName(request.job_name()), jobId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A job named '" + request.job_name().trim() + "' already exists");
        }

        existingJob.setJobName(request.job_name().trim());
        existingJob.setJobDesc(request.job_desc().trim());
        existingJob.setSource(contentSource);
        existingJob.setUpdatedAt(now);

        existingJob.getTraits().clear();
        existingJob.getCategories().clear();
        jobRepository.saveAndFlush(existingJob);

        addLinks(existingJob, traits, categories, now);

        return jobMapper.CreateResponse(jobRepository.save(existingJob));
    }

    /** Deletes a job; its trait and category links are removed by cascade/orphan removal. Traits are kept. */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"jobsById", "jobsAll"}, allEntries = true)
    public String deleteJob(String id) {
        Job existingJob = findJob(parseUuid(id, "job"));
        String name = existingJob.getJobName();
        jobRepository.delete(existingJob);
        return "Job with id " + id + " (name: " + name + ") has been deleted.";
    }

    /** Returns one job by id, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "jobsById", key = "#id")
    public JobResponseDto getJobById(String id) {
        return jobMapper.CreateResponse(findJob(parseUuid(id, "job")));
    }

    /** Returns a sorted page of all jobs, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "jobsAll", key = "#page + ':' + #size + ':' + #sortBy + ':' + #sortDir")
    public Page<JobResponseDto> getAllJobs(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return jobRepository.findAll(pageable).map(jobMapper::CreateResponse);
    }

    /** Returns every job category, sorted by name, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "jobCategories")
    public List<JobCategoryResponseDto> getJobCategories() {
        return jobCategoryRepository.findAll(Sort.by(Sort.Direction.ASC, "category")).stream()
                .map(jobMapper::CreateCategoryResponse)
                .toList();
    }

    /**
     * Applies every filter on the request. When a name is given, an exact match is returned alone;
     * otherwise results are ranked so names containing the search come first, then by edit distance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<JobResponseDto> filterJobs(JobFilterRequest request) {
        if (request.categories() != null && !request.categories().isEmpty()) {
            // Validates the names so a typo returns 400 instead of silently matching nothing.
            resolveCategoryNames(request.categories());
        }

        Specification<Job> specification = JobSpecifications.all()
                .and(JobSpecifications.withCategories(
                        request.categories(), request.getMatchAll(), request.getPrimaryOnly()))
                .and(JobSpecifications.withTraitName(request.traitName()));

        boolean searchByName = request.name() != null && !request.name().isBlank();
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                searchByName ? Sort.unsorted() : Sort.by(Sort.Direction.ASC, DEFAULT_SORT_FIELD));

        if (!searchByName) {
            return jobRepository.findAll(specification, pageable).map(jobMapper::CreateResponse);
        }

        String searchName = normalizeName(request.name());
        List<Job> exactMatches = jobRepository
                .findAll(specification.and(JobSpecifications.withExactName(searchName)), PageRequest.of(0, 1))
                .getContent();
        if (!exactMatches.isEmpty()) {
            JobResponseDto exactMatch = jobMapper.CreateResponse(exactMatches.get(0));
            return new PageImpl<>(List.of(exactMatch), PageRequest.of(0, request.getSize()), 1);
        }

        List<Job> rankedJobs = jobRepository.findAll(specification).stream()
                .sorted(Comparator
                        .comparingInt((Job job) ->
                                job.getJobName().toLowerCase(Locale.ROOT).contains(searchName) ? 0 : 1)
                        .thenComparingInt(job -> levenshteinDistance(
                                searchName, job.getJobName().toLowerCase(Locale.ROOT)))
                        .thenComparing(Job::getJobName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int start = (int) Math.min(pageable.getOffset(), rankedJobs.size());
        int end = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rankedJobs.size());
        List<JobResponseDto> pageContent = rankedJobs.subList(start, end).stream()
                .map(jobMapper::CreateResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, rankedJobs.size());
    }

    /** Attaches trait links (in request order) and category links to a managed job. */
    private static void addLinks(Job job, List<Trait> traits, ResolvedCategories categories, OffsetDateTime now) {
        for (int index = 0; index < traits.size(); index++) {
            Trait trait = traits.get(index);
            job.getTraits().add(new JobTraits(
                    new JobTraitsId(job.getId(), trait.getId()), job, trait, index, now, now));
        }

        for (JobCategory category : categories.categories()) {
            boolean primary = category.equals(categories.primary());
            job.getCategories().add(new JobCategoryAssignment(
                    new JobCategoryAssignmentId(job.getId(), category.getId()), job, category, primary, now, now));
        }
    }

    /**
     * Loads the requested traits in request order.
     * Rejects malformed or repeated ids with 400 and unknown ids with 404.
     */
    private List<Trait> resolveTraits(List<String> traitIds) {
        List<UUID> ids = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (String traitId : traitIds) {
            UUID id = parseUuid(traitId, "trait");
            if (!seen.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Trait id " + traitId + " is listed more than once");
            }
            ids.add(id);
        }

        Map<UUID, Trait> found = traitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Trait::getId, Function.identity()));

        List<UUID> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Traits not found with ids: " + missing);
        }

        return ids.stream().map(found::get).toList();
    }

    /** The categories to assign to a job, primary first, plus which one is primary (null when none). */
    private record ResolvedCategories(List<JobCategory> categories, JobCategory primary) {
    }

    /**
     * Resolves category names to entities. The primary category is always included and listed first;
     * when it is not given, the first listed category becomes primary.
     */
    private ResolvedCategories resolveCategories(List<String> categoryNames, String primaryName) {
        List<String> names = new ArrayList<>();
        if (primaryName != null && !primaryName.isBlank()) {
            names.add(primaryName);
        }
        if (categoryNames != null) {
            categoryNames.stream().filter(Objects::nonNull).forEach(names::add);
        }

        List<JobCategory> categories = resolveCategoryNames(names);
        return new ResolvedCategories(categories, categories.isEmpty() ? null : categories.get(0));
    }

    /** Resolves category names case-insensitively, keeping order and dropping duplicates; unknown names are a 400. */
    private List<JobCategory> resolveCategoryNames(List<String> names) {
        Map<String, JobCategory> byKey = jobCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(category -> normalizeName(category.getCategory()), Function.identity()));

        LinkedHashMap<String, JobCategory> resolved = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();

        for (String name : names) {
            String key = normalizeName(name);
            JobCategory category = byKey.get(key);
            if (category == null) {
                unknown.add(name);
            } else {
                resolved.putIfAbsent(key, category);
            }
        }

        if (!unknown.isEmpty()) {
            Set<String> valid = byKey.values().stream()
                    .map(JobCategory::getCategory)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown job categories: " + unknown + ". Valid categories: " + valid);
        }

        return new ArrayList<>(resolved.values());
    }

    /** Loads a job or throws 404 when it does not exist. */
    private Job findJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Job not found with id: " + jobId));
    }

    /** Loads a content source or throws 404 when it does not exist. */
    private ContentSource findContentSource(String contentSourceId) {
        UUID sourceId = parseUuid(contentSourceId, "content source");
        return contentSourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Content source not found with id: " + contentSourceId));
    }

    /** Parses a UUID string, turning malformed input into a 400 instead of a 500. */
    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label + " id: " + value);
        }
    }

    /** Normalizes a name for case-insensitive comparison (trimmed, lowercase). */
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Computes the Levenshtein edit distance between two strings, used to rank fuzzy name matches. */
    private static int levenshteinDistance(String first, String second) {
        String columns = first.length() <= second.length() ? first : second;
        String rows = first.length() <= second.length() ? second : first;
        int[] previous = new int[columns.length() + 1];
        int[] current = new int[columns.length() + 1];

        for (int column = 0; column <= columns.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= rows.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= columns.length(); column++) {
                int substitutionCost = rows.charAt(row - 1) == columns.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[columns.length()];
    }
}
