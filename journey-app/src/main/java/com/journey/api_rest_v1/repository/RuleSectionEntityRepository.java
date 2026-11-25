package com.journey.api_rest_v1.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.rules.RuleSectionEntity;

/** Data access for the entity references of rule sections. */
@Repository
public interface RuleSectionEntityRepository extends JpaRepository<RuleSectionEntity, UUID> {

    /** Returns every entity reference made by the given sections. */
    List<RuleSectionEntity> findBySectionIdIn(Collection<UUID> sectionIds);
}
