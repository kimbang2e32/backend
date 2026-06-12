package com.appad.repository;

import com.appad.models.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Integer> {
    java.util.Optional<Genre> findByName(String name);
}
