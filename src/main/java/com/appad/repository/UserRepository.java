package com.appad.repository;

import com.appad.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByRole(String role);
    List<User> findByIsBanned(Integer isBanned);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByIsPremiumAndPremiumExpiryBefore(Integer isPremium, LocalDateTime expiryDate);
}
