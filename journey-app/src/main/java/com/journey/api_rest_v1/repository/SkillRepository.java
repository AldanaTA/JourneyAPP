package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.Skill;

/** Data access for skills. */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findAllByOrderBySortOrderAsc();
}
