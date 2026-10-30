package com.journey.api_rest_v1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategoriesId;

@Repository 
public interface PowerCategoriesRepository extends JpaRepository<PowerCategories, PowerCategoriesId> {

}
