package com.appad.repository;

import com.appad.models.RevenueSharing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RevenueSharingRepository extends JpaRepository<RevenueSharing, Integer> {
    List<RevenueSharing> findByShareType(String shareType);
    List<RevenueSharing> findByArtistIdAndShareType(Integer artistId, String shareType);
    java.util.Optional<RevenueSharing> findFirstByShareTypeOrderByCreatedAtDesc(String shareType);
    RevenueSharing findTopByArtistIdAndShareTypeAndCreatedAtBeforeOrderByCreatedAtDesc(Integer artistId, String shareType, java.time.LocalDateTime createdAt);
}

