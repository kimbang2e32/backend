package com.appad.repository;

import com.appad.models.PurchasedSong;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchasedSongRepository extends JpaRepository<PurchasedSong, Integer> {
    boolean existsByUserUserIdAndSongSongId(Integer userId, Integer songId);
    java.util.List<com.appad.models.PurchasedSong> findByUserUserId(Integer userId);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(ps) FROM PurchasedSong ps WHERE ps.song.artistId = :artistId")
    long countByArtistId(@org.springframework.data.repository.query.Param("artistId") Integer artistId);
}
