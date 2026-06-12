package com.appad.repository;

import com.appad.models.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Follow.FollowId> {
    boolean existsByUserUserIdAndArtistArtistId(Integer userId, Integer artistId);
    void deleteByUserUserIdAndArtistArtistId(Integer userId, Integer artistId);
    java.util.List<Follow> findByUserUserId(Integer userId);
    java.util.List<Follow> findByArtistArtistId(Integer artistId);
    long countByUserUserId(Integer userId);
}
