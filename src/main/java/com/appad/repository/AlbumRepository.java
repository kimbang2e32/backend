package com.appad.repository;

import com.appad.models.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Integer> {
    List<Album> findByArtistId(Integer artistId);
    List<Album> findByTitleContainingIgnoreCase(String title);
}
