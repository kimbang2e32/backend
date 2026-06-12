package com.appad.controllers;

import com.appad.models.Album;
import com.appad.services.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {
    private final AlbumService albumService;

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(Map.of("success", true, "data", albumService.getAllAlbums()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return albumService.getAlbumById(id)
                .map(album -> ResponseEntity.ok(Map.of("success", true, "data", album)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Album not found")));
    }

    @GetMapping("/artist/{artistId}")
    public ResponseEntity<?> getByArtist(@PathVariable Integer artistId) {
        return ResponseEntity.ok(Map.of("success", true, "data", albumService.getAlbumsByArtist(artistId)));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Album album) {
        return ResponseEntity.status(201).body(Map.of("success", true, "data", albumService.createAlbum(album)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Album albumData) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", albumService.updateAlbum(id, albumData)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
