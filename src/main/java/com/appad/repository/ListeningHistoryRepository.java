package com.appad.repository;

import com.appad.models.ListeningHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, Long> {
    
    List<ListeningHistory> findByUserUserIdAndSongSongId(Integer userId, Integer songId);
    
    // Find by user, song, and same day (comparing date part of 'day' column)
    @Query("SELECT h FROM ListeningHistory h WHERE h.user.userId = :userId AND h.song.songId = :songId AND CAST(h.day AS LocalDate) = :date")
    List<ListeningHistory> findByUserAndSongAndDate(
            @Param("userId") Integer userId, 
            @Param("songId") Integer songId, 
            @Param("date") LocalDate date);
    
    // Get history ordered by day descending
    List<ListeningHistory> findByUserUserIdOrderByDayDesc(Integer userId);
    
    // For artist revenue calculation
    @Query("SELECT h FROM ListeningHistory h WHERE h.artistId = :artistId AND h.day >= :startDate")
    List<ListeningHistory> findByArtistIdAndDayAfter(
            @Param("artistId") Integer artistId, 
            @Param("startDate") LocalDateTime startDate);
    
    // For premium stream stats
    @Query("SELECT h FROM ListeningHistory h WHERE h.isPremiumStream = true AND h.artistId = :artistId AND h.day > :startDate AND h.day <= :endDate")
    List<ListeningHistory> findPremiumStreamsInRange(
            @Param("artistId") Integer artistId, 
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
