package com.appad.repository;

import com.appad.models.PremiumListeningStats;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface PremiumListeningStatsRepository extends JpaRepository<PremiumListeningStats, Long> {
    Optional<PremiumListeningStats> findByUserIdAndSongIdAndListenDate(Long userId, Long songId, LocalDate date);

    @org.springframework.data.jpa.repository.Query("SELECT s.artistId as artistId, SUM(s.count) as totalStreams " +
           "FROM PremiumListeningStats s " +
           "WHERE s.listenDate BETWEEN :startDate AND :endDate " +
           "GROUP BY s.artistId")
    java.util.List<Object[]> findStreamStatsByPeriod(@org.springframework.data.repository.query.Param("startDate") LocalDate startDate, 
                                                    @org.springframework.data.repository.query.Param("endDate") LocalDate endDate);
}
