package com.appad.controllers;

import com.appad.models.Favorite;
import com.appad.repository.FavoriteRepository;
import com.appad.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteRepository favoriteRepository;
    private final SongRepository songRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getFavorites(@PathVariable Integer userId) {
        var favorites = favoriteRepository.findByUserId(userId);
        var songIds = favorites.stream()
                               .map(Favorite::getSongId)
                               .collect(Collectors.toList());
        var songs = songRepository.findAllById(songIds);
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleFavorite(@RequestBody Map<String, Long> payload) {
        Integer userId = payload.get("userId").intValue();
        Integer songId = payload.get("songId").intValue();
        
        var existing = favoriteRepository.findByUserIdAndSongId(userId, songId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("success", true, "isFavorite", false));
        } else {
            favoriteRepository.save(Favorite.builder().userId(userId).songId(songId).build());
            return ResponseEntity.ok(Map.of("success", true, "isFavorite", true));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkFavorite(@RequestParam Integer userId, @RequestParam Integer songId) {
        boolean isFavorite = favoriteRepository.existsByUserIdAndSongId(userId, songId);
        return ResponseEntity.ok(Map.of("success", true, "isFavorite", isFavorite));
    }
}
