package com.appad.controllers;

import com.appad.models.Genre;
import com.appad.services.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {
    private final GenreService genreService;

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(Map.of("success", true, "data", genreService.getAllGenres()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return genreService.getGenreById(id)
                .map(genre -> ResponseEntity.ok(Map.of("success", true, "data", genre)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Genre not found")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Genre genre) {
        return ResponseEntity.status(201).body(Map.of("success", true, "data", genreService.createGenre(genre)));
    }
}
