package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    // Sorting is applied through `Pageable` to keep the pagination order deterministic.
    @Query("""
    SELECT u
    FROM User u
    WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);
}
