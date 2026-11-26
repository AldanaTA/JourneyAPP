package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.users.User;

/** Data access for users. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Finds a user by email, ignoring case (emails are unique ignoring case). */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
