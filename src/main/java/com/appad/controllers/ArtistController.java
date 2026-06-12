package com.appad.controllers;

import com.appad.models.Artist;
import com.appad.services.ArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistService artistService;

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(defaultValue = "50") int limit, 
                                   @RequestParam(defaultValue = "0") int offset) {
        List<Artist> artists = artistService.getAllArtists(limit, offset);
        return ResponseEntity.ok(Map.of("success", true, "data", artists));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return artistService.getArtistById(id)
                .map(artist -> ResponseEntity.ok(Map.of("success", true, "data", artist)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Artist not found")));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable Integer userId) {
        return artistService.getArtistByUserId(userId)
                .map(artist -> ResponseEntity.ok(Map.of("success", true, "data", artist)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Artist not found")));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Artist artist) {
        Artist created = artistService.createArtist(artist);
        return ResponseEntity.status(201).body(Map.of("success", true, "data", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Artist artistData) {
        try {
            Artist updated = artistService.updateArtist(id, artistData);
            return ResponseEntity.ok(Map.of("success", true, "data", updated));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{artistId}/dashboard")
    public ResponseEntity<?> getDashboard(@PathVariable Integer artistId) {
        try {
            Map<String, Object> dashboard = artistService.getArtistDashboard(artistId);
            return ResponseEntity.ok(Map.of("success", true, "data", dashboard));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/membership")
    public ResponseEntity<?> updateMembership(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        try {
            artistService.updateMembership(id, payload);
            return ResponseEntity.ok(Map.of("success", true, "message", "Cập nhật thông tin hội viên thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
