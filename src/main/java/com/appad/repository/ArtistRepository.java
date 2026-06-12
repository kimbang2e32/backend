package com.appad.repository;

import com.appad.models.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    Optional<Artist> findByUserId(Integer userId);
    java.util.List<Artist> findByNameContainingIgnoreCase(String name);
}
