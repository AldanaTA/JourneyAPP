package com.journey.api_rest_v1.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.powers.Power;

@Repository 
public interface PowerRepository extends JpaRepository<Power, String> {

    Optional<Power> findByName(String name);
}
