package com.journey.api_rest_v1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.traits.TraitCategories;
import com.journey.api_rest_v1.domain.traits.TraitCategoriesId;

/**
 * Spring Data repository for trait/category link rows.
 */
@Repository
public interface TraitCategoriesRepository extends JpaRepository<TraitCategories, TraitCategoriesId> {
}
