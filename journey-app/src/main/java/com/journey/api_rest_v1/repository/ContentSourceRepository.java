package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

public interface ContentSourceRepository extends JpaRepository<ContentSource, UUID> {

}
