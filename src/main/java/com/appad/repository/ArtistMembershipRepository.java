package com.appad.repository;

import com.appad.models.ArtistMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ArtistMembershipRepository extends JpaRepository<ArtistMembership, Long> {
    List<ArtistMembership> findByExpiryDateBeforeAndStatus(LocalDateTime date, String status);
    List<ArtistMembership> findByUserUserIdAndArtistArtistId(Integer userId, Integer artistId);
    List<ArtistMembership> findByUserId(Integer userId);
}
