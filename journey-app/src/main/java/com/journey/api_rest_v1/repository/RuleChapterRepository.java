package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.rules.RuleChapter;

/** Data access for rule chapters. */
@Repository
public interface RuleChapterRepository extends JpaRepository<RuleChapter, UUID> {
}
