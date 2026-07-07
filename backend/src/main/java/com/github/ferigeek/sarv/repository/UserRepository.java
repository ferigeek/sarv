package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);
    User findByUsername(String username);

    @Query("""
    SELECT u
    FROM User u
    WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
    ORDER BY u.username
    """)
    List<User> searchUsers(@Param("query") String query);
}
