package com.appad.repository;

import com.appad.models.PurchasedAlbum;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchasedAlbumRepository extends JpaRepository<PurchasedAlbum, Integer> {
    boolean existsByUserUserIdAndAlbumAlbumId(Integer userId, Integer albumId);
    java.util.List<PurchasedAlbum> findByUserUserId(Integer userId);
}
