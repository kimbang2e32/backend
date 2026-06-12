package com.appad.repository;

import com.appad.models.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.FavoriteId> {
    List<Favorite> findByUserId(Integer userId);
    Optional<Favorite> findByUserIdAndSongId(Integer userId, Integer songId);
    void deleteByUserIdAndSongId(Integer userId, Integer songId);
    boolean existsByUserIdAndSongId(Integer userId, Integer songId);
}
