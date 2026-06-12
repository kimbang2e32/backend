package com.appad.controllers;

import com.appad.models.Song;
import com.appad.services.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
public class SongController {
    private final SongService songService;
    private final com.appad.services.HistoryService historyService;

    private Integer getCurrentUserId() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Integer) {
                return (Integer) auth.getPrincipal();
            }
        } catch (Exception e) {}
        return null;
    }

    public ResponseEntity<?> getAll(@RequestParam(defaultValue = "20") int limit, 
                                   @RequestParam(defaultValue = "0") int offset) {
        List<Song> songs = songService.getAllSongs(limit, offset);
        songService.populateAccessInfo(songs, getCurrentUserId());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", songs);
        response.put("pagination", Map.of("limit", limit, "offset", offset));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatest(@RequestParam(defaultValue = "20") int limit, 
                                     @RequestParam(defaultValue = "0") int offset) {
        System.out.println("SongController: Getting latest songs, limit=" + limit + ", offset=" + offset);
        try {
            List<Song> songs = songService.getLatestSongs(limit, offset);
            System.out.println("SongController: Found " + songs.size() + " latest songs");
            songService.populateAccessInfo(songs, getCurrentUserId());
            return ResponseEntity.ok(Map.of("success", true, "data", songs));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/trending")
    public ResponseEntity<?> getTrending(@RequestParam(defaultValue = "20") int limit,
                                        @RequestParam(defaultValue = "0") int offset) {
        List<Song> songs = songService.getTrendingSongs(limit, offset);
        songService.populateAccessInfo(songs, getCurrentUserId());
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @GetMapping("/recommended")
    public ResponseEntity<?> getRecommended(@RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(defaultValue = "0") int offset) {
        List<Song> songs = songService.getRecommendedSongs(getCurrentUserId(), limit, offset);
        songService.populateAccessInfo(songs, getCurrentUserId());
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return songService.getSongById(id)
                .map(song -> {
                    songService.populateAccessInfo(song, getCurrentUserId());
                    return ResponseEntity.ok(Map.of("success", true, "data", song));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Song not found")));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "newest") String sort) {
        try {
            List<Song> songs = songService.searchAllSongs(q, limit, offset, sort);
            songService.populateAccessInfo(songs, getCurrentUserId());
            return ResponseEntity.ok(Map.of("success", true, "data", songs));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/artist/{artistId}")
    public ResponseEntity<?> getByArtist(@PathVariable Integer artistId) {
        Integer userId = getCurrentUserId();
        List<Song> songs = songService.getSongsByArtist(artistId, userId);
        songService.populateAccessInfo(songs, userId);
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @GetMapping("/album/{albumId}")
    public ResponseEntity<?> getByAlbum(@PathVariable Integer albumId) {
        Integer userId = getCurrentUserId();
        List<Song> songs = songService.getSongsByAlbum(albumId, userId);
        songService.populateAccessInfo(songs, userId);
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @GetMapping("/genre/{genreId}")
    public ResponseEntity<?> getByGenre(@PathVariable Integer genreId) {
        List<Song> songs = songService.getSongsByGenre(genreId);
        songService.populateAccessInfo(songs, getCurrentUserId());
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @PostMapping("/{id}/play")
    public ResponseEntity<?> play(@PathVariable Integer id, 
                                 @RequestBody Map<String, Object> playData,
                                 @org.springframework.security.core.annotation.AuthenticationPrincipal Integer userId) {
        // Increment global count
        songService.incrementListenCount(id);
        
        // Record history
        if (userId != null) {
            // Support both duration_seconds (new) and duration_listened (old) for backward compatibility
            Integer duration = null;
            if (playData.containsKey("duration_seconds")) {
                duration = ((Number) playData.get("duration_seconds")).intValue();
            } else if (playData.containsKey("duration_listened")) {
                duration = ((Number) playData.get("duration_listened")).intValue();
            }
            Boolean isCompleted = playData.containsKey("is_completed") ? (Boolean) playData.get("is_completed") : null;
            boolean incrementCount = !playData.containsKey("increment_count") || Boolean.TRUE.equals(playData.get("increment_count"));
            
            historyService.recordListen(userId, id, duration, isCompleted, incrementCount);
        }
        
        return ResponseEntity.ok(Map.of("success", true, "message", "Song played"));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Song song) {
        Song created = songService.createSong(song);
        return ResponseEntity.status(201).body(Map.of("success", true, "data", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Song songData) {
        try {
            Song updated = songService.updateSong(id, songData);
            return ResponseEntity.ok(Map.of("success", true, "data", updated));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        songService.deleteSong(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Song deleted successfully"));
    }
}
