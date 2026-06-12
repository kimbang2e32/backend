package com.appad.repository;

import com.appad.models.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findBySongIdOrderByCreatedAtDesc(Integer songId);

    @Query("SELECT AVG(c.rating) FROM Comment c WHERE c.songId = :songId AND c.rating IS NOT NULL")
    Double getAverageRating(Integer songId);

    @Query("SELECT c FROM Comment c JOIN Song s ON c.songId = s.songId WHERE s.artistId = :artistId ORDER BY c.createdAt DESC")
    List<Comment> findByArtistId(Integer artistId);

    java.util.Optional<Comment> findBySongIdAndUserUserId(Integer songId, Integer userId);
}
