package com.journey.api_rest_v1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

@Repository 
public interface ContentSourceRepository extends JpaRepository<ContentSource, String> {

}
